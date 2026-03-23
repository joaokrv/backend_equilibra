# Faturas e Scheduler — Lazy Creation, Ghost Closing e Automação

## Lazy Creation de Faturas

### Explicação não técnica

Imagine um **carnê de pagamentos**. O carnê não é impresso antecipadamente para todos os meses futuros — ele só existe quando tem pelo menos uma parcela. Se você não fez nenhuma compra no cartão em março, não existe fatura de março. A fatura é criada automaticamente quando a primeira compra do mês é registrada.

### Explicação técnica

Faturas são criadas sob demanda (lazy) pelo `FaturaService.adicionarTransacao()`. Quando uma despesa é registrada em um cartão:

1. O sistema calcula o mês de referência a partir da data da transação
2. Busca se já existe uma fatura ABERTA para aquele cartão + mês
3. Se **não existe**: cria uma nova fatura com `valorTotal = valor` e status `ABERTA`
4. Se **já existe**: incrementa o `valorTotal` da fatura existente

```java
// FaturaService.java — trecho real
@Transactional
public FaturaEntity adicionarTransacao(CartaoEntity cartao, LocalDate dataTransacao,
                                       BigDecimal valor) {
    int mesReferencia = dataTransacao.getMonthValue();
    int anoReferencia = dataTransacao.getYear();

    FaturaEntity fatura = faturaRepository
            .findByCartaoAndMesReferenciaAndAnoReferencia(cartao, mesReferencia, anoReferencia)
            .orElseGet(() -> {
                FaturaEntity novaFatura = new FaturaEntity();
                novaFatura.setCartao(cartao);
                novaFatura.setMesReferencia(mesReferencia);
                novaFatura.setAnoReferencia(anoReferencia);
                novaFatura.setValorTotal(BigDecimal.ZERO);
                novaFatura.setDataFechamento(
                    LocalDate.of(anoReferencia, mesReferencia, cartao.getDiaFechamento()));
                novaFatura.setDataVencimento(
                    LocalDate.of(anoReferencia, mesReferencia, cartao.getDiaVencimento()));
                novaFatura.setStatus(StatusFatura.ABERTA);
                novaFatura.setAtivo(true);
                return novaFatura;
            });

    fatura.setValorTotal(fatura.getValorTotal().add(valor));
    log.info("Transação de R$ {} adicionada na fatura do cartão {} ({}/{}). Novo total: R$ {}",
             valor, cartao.getId(), mesReferencia, anoReferencia, fatura.getValorTotal());
    return faturaRepository.save(fatura);
}
```

**Por que lazy?** Porque seria desperdício criar 12 faturas por ano para cada cartão, especialmente se o usuário não faz compras em todos os meses.

---

## Ghost Closing — Atualização Automática de Status

### Explicação não técnica

Imagine um **relógio de parede** que, ao bater meia-noite do dia de fechamento, automaticamente muda a etiqueta da fatura de "aberta" para "fechada". E se passar o dia do vencimento sem pagamento, muda para "atrasada". Tudo sem ninguém precisar intervir.

### Explicação técnica

Quando o cliente lista as faturas (`GET /api/faturas/cartao/{id}`), o `FaturaService` verifica a data atual e atualiza automaticamente os status antes de retornar os dados:

```
ABERTA    →  (hoje > dataFechamento)   →  FECHADA
FECHADA   →  (hoje > dataVencimento)   →  ATRASADA
```

Isso é feito inline, sem depender de jobs agendados. O scheduler (seção abaixo) serve como rede de segurança para faturas que ninguém consultou.

---

## Ciclo de Vida de uma Fatura

```
                    ┌──────────┐
                    │  ABERTA  │
                    └──────┬───┘
                           │ passou dataFechamento
                           ▼
                    ┌──────────┐
                    │ FECHADA  │
                    └──────┬───┘
                           │ passou dataVencimento
                           ▼
                    ┌──────────┐
                    │ ATRASADA │
                    └──────┬───┘
                           │ pagarFatura() (integral)
                           ▼
                    ┌──────────┐
                    │   PAGA   │
                    └──────────┘
```

**Pagamento parcial:** se o usuário paga apenas parte da fatura, o `valorTotal` é reduzido mas o status **não muda** para PAGA. Só quando `valorTotal == 0` a fatura é marcada como PAGA.

---

## FaturaSchedulerService — Automação Diária

### Explicação não técnica

Mesmo com o "relógio automático" do ghost closing, existe um **guarda noturno** que faz uma ronda toda meia-noite. Ele verifica todas as faturas e marca como "atrasada" aquelas que já passaram do vencimento. É uma rede de segurança para faturas que ninguém consultou durante o dia.

### Explicação técnica — Código real

```java
@Service
@RequiredArgsConstructor
@Slf4j
// Nota: o agendador é protegido por ShedLock para evitar execução
// simultânea em múltiplas instâncias/cópias do aplicativo.
public class FaturaSchedulerService {

    private final FaturaRepository faturaRepository;

    /**
     * Executa diariamente à meia-noite (00:00:00).
     * Verifica faturas ABERTAS ou FECHADAS cuja data de vencimento é anterior a hoje
     * e altera o status para ATRASADA.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void atualizarFaturasAtrasadas() {
        log.info("Iniciando rotina de verificação de faturas atrasadas...");

        LocalDate hoje = LocalDate.now();
        int atualizadas = faturaRepository.marcarFaturasComoAtrasadas(
                hoje, StatusFatura.ATRASADA, StatusFatura.ABERTA, StatusFatura.FECHADA);

        log.info("Rotina de verificação concluída. {} faturas marcadas como ATRASADA.",
                 atualizadas);
    }
}
```

### Expressão Cron explicada

**Execução única em cluster**

Mesmo que a aplicação seja escalada (várias réplicas em um serviço como Railway/Kubernetes), queremos garantir que
apenas uma instância execute a rotina diariamente. Para isso usamos **ShedLock**, que cria uma tabela `shedlock` no
banco e obtém um lock antes de executar o método. A segunda instância vê que o lock já está ocupado e não faz nada.

A tabela é criada pela migration V5 (veja `db/migration/V5__create_shedlock_table.sql`).


```
@Scheduled(cron = "0 0 0 * * ?")
                   │ │ │ │ │ │
                   │ │ │ │ │ └─ dia da semana (? = qualquer)
                   │ │ │ │ └─── mês (= todos)
                   │ │ │ └───── dia do mês (* = todos)
                   │ │ └─────── hora (0 = meia-noite)
                   │ └───────── minuto (0)
                   └─────────── segundo (0)
```

Para que o `@Scheduled` funcione, é necessário anotar a classe principal com `@EnableScheduling`:

```java
@SpringBootApplication
@EnableScheduling
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
```

---

## Pagamento de Fatura — Código Real

```java
// FaturaService.java — trecho real
@Transactional
public FaturaResponseDTO pagarFatura(Long faturaId, BigDecimal valorPagamento, Long usuarioId) {
    FaturaEntity fatura = buscarFaturaValidada(faturaId, usuarioId);

    if (fatura.getStatus() == StatusFatura.PAGA) {
        log.warn("Tentativa de pagar fatura {} já paga", faturaId);
        throw new OperacaoNaoPermitidaException("Esta fatura já foi paga");
    }

    if (valorPagamento.compareTo(fatura.getValorTotal()) > 0) {
        log.warn("Pagamento de R$ {} excede dívida restante de R$ {} na fatura {}",
                 valorPagamento, fatura.getValorTotal(), faturaId);
        throw new RegraDeNegocioException(
            "Valor de pagamento excede a dívida restante da fatura");
    }

    // Debita da conta
    contaService.debitarSaldo(fatura.getCartao().getContaPagamento().getId(),
                              valorPagamento, usuarioId);

    // Reduz valor da fatura
    fatura.setValorTotal(fatura.getValorTotal().subtract(valorPagamento));

    // Se zerou, marca como PAGA e devolve limite
    if (fatura.getValorTotal().compareTo(BigDecimal.ZERO) == 0) {
        fatura.setStatus(StatusFatura.PAGA);
        cartaoService.restaurarLimite(fatura.getCartao().getId(), valorPagamento, usuarioId);
    }

    faturaRepository.save(fatura);
    log.info("Fatura {} paga: R$ {}. Novo total: R$ {}", faturaId, valorPagamento,
             fatura.getValorTotal());
    return faturaMapper.toResponse(fatura);
}
```

---

## Fontes

- [Spring @Scheduled — Documentação Oficial](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Cron Expressions — Spring Docs](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-cron-expressions)
- [Spring @EnableScheduling](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Lazy Initialization Pattern — Wikipedia](https://en.wikipedia.org/wiki/Lazy_initialization)
