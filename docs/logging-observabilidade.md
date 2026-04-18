# Logging e Observabilidade — SLF4J, Swagger e Actuator

## Logging Estruturado (SLF4J)

### Explicação não técnica

Imagine o **diário de bordo** de um avião. Toda decolagem, pouso, turbulência e problema técnico é registrado com data, hora e detalhes. Se acontecer um incidente, os investigadores consultam o diário para entender exatamente o que aconteceu.

Logging no backend funciona igual. Toda operação importante (criou uma conta, fez uma transação, saldo insuficiente) é registrada com timestamp e detalhes. Se algo der errado em produção, os logs são a primeira fonte de investigação.

### Explicação técnica

O projeto usa **SLF4J** (Simple Logging Facade for Java) como abstração de logging, com **Logback** como implementação (padrão do Spring Boot). SLF4J permite trocar a implementação (ex: para Log4j2) sem alterar o código.

Níveis de log usados:

| Nível | Quando usar | Exemplo no projeto |
|---|---|---|
| `log.info` | Operação concluída com sucesso | "Conta criada: id=5, nome='Nubank'" |
| `log.warn` | Violação de regra de negócio (não é erro, é decisão do usuário) | "Tentativa de pagar fatura já paga" |
| `log.error` | Exceção inesperada (bug ou falha de infraestrutura) | "Exceção inesperada capturada: NullPointerException" |

### Padrão de declaração

```java
// Todos os services seguem este padrão
private static final Logger log = LoggerFactory.getLogger(NomeDaClasse.class);
```

Ou, para classes com Lombok:

```java
// FaturaSchedulerService usa @Slf4j do Lombok
@Slf4j
public class FaturaSchedulerService {
    // log.info() disponível automaticamente
}
```

---

## Tabela de Log Points por Service

### FaturaService

| Nível | Método | Mensagem |
|---|---|---|
| `log.info` | `adicionarTransacao` | Transação de R$ X adicionada na fatura do cartão Y |
| `log.info` | `pagarFatura` | Fatura X paga: R$ Y. Novo total: R$ Z |
| `log.warn` | `pagarFatura` | Tentativa de pagar fatura já paga |
| `log.warn` | `pagarFatura` | Pagamento excede dívida restante |

### CategoriaService

| Nível | Método | Mensagem |
|---|---|---|
| `log.info` | `criarCategoria` | Categoria 'X' criada para usuário Y |
| `log.info` | `deletarCategoria` | Categoria X desativada |
| `log.warn` | `criarCategoria` | Tentativa de criar categoria duplicada 'X' |

### TransacaoService

| Nível | Método | Mensagem |
|---|---|---|
| `log.info` | `criarTransacao` | Transação X criada: DESPESA R$ 150.00 status=PAGO |
| `log.info` | `atualizarTransacao` | Transação X atualizada: RECEITA R$ 200.00 |
| `log.info` | `deletarTransacao` | Transação X desativada (soft delete) |
| `log.warn` | `criarTransacao` | contaId e cartaoId informados simultaneamente |

### InvestimentoService

| Nível | Método | Mensagem |
|---|---|---|
| `log.info` | `criarInvestimento` | Investimento X 'Reserva' criado. Valor: R$ 1000 |
| `log.info` | `adicionarDeposito` | Depósito de R$ 500 no investimento X |
| `log.info` | `resgatarInvestimento` | Resgate de R$ 200 do investimento X |
| `log.warn` | `resgatarInvestimento` | Resgate excede saldo |
| `log.warn` | `atualizarMeta` | Meta <= 0 |

### MovimentacaoFinanceiraService

| Nível | Método | Mensagem |
|---|---|---|
| `log.info` | `processarTransacaoConta` | Débito/Crédito de R$ X na conta Y |
| `log.info` | `processarDespesaCartao` | Despesa de R$ X processada no cartão Y |
| `log.info` | `desfazerEfeitoFinanceiro` | Efeito financeiro da transação X revertido |

### EmailVerificacaoService

| Nível | Método | Mensagem |
|---|---|---|
| `log.info` | `gerarCodigo` | Código de verificação gerado para e-mail X |
| `log.info` | `verificarEmail` | E-mail X verificado com sucesso |
| `log.warn` | `verificarEmail` | Código expirado para e-mail X |
| `log.warn` | `reenviarCodigo` | Tentativa de reenviar para e-mail já verificado |

### GlobalExceptionHandler

| Nível | Método | Mensagem |
|---|---|---|
| `log.error` | `handleExcecaoGenerica` | Exceção inesperada capturada: mensagem + stack trace |

---

## Exemplos de Código Real

### log.info — Operação de sucesso

```java
// ContaService.java — trecho real
contaRepository.save(conta);
log.info("Conta criada: id={}, nome='{}', usuarioId={}",
         conta.getId(), conta.getNome(), usuarioId);
```

**Saída no console:**

```
2026-03-04 18:30:00 INFO  o.a.b.s.ContaService - Conta criada: id=5, nome='Nubank', usuarioId=1
```

### log.warn — Violação de regra

```java
// CategoriaService.java — trecho real
if (categoriaRepository.existsByNomeAndUsuarioId(dto.nome(), usuarioId)) {
    log.warn("Tentativa de criar categoria com nome duplicado: '{}' para usuário {}",
             dto.nome(), usuarioId);
    throw new RegraDeNegocioException("Já existe uma categoria com este nome");
}
```

### log.error — Exceção inesperada

```java
// GlobalExceptionHandler.java — trecho real
@ExceptionHandler(Exception.class)
public ResponseEntity<ErroResponseDTO> handleExcecaoGenerica(Exception ex) {
    log.error("Exceção inesperada capturada: {}", ex.getMessage(), ex);
    // O terceiro argumento (ex) inclui o stack trace completo
    // ...
}
```

**Por que `log.error` recebe `ex` como terceiro argumento?** Porque o SLF4J, quando o último argumento é um `Throwable`, imprime o stack trace completo. Isso é essencial para debugging em produção.

---

## Swagger / OpenAPI

### Explicação não técnica

Swagger é como um **cardápio interativo** de uma API. Em vez de um PDF estático com os endpoints, você tem uma página web onde pode ver todos os endpoints disponíveis, seus parâmetros e até **testar as chamadas direto do navegador**.

### Explicação técnica

O projeto usa **Springdoc OpenAPI** para gerar automaticamente a documentação da API a partir das anotações dos Controllers (`@RestController`, `@GetMapping`, `@PostMapping`, etc.).

### Como acessar

| URL | Descrição |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interface interativa Swagger UI |
| `http://localhost:8080/v3/api-docs` | Especificação OpenAPI em JSON |

### Dependência no pom.xml

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.6</version>
</dependency>
```

**Zero configuração necessária:** o Springdoc escaneia automaticamente todos os Controllers e gera a documentação. As rotas do Swagger estão na whitelist do `SecurityConfig`, então não precisam de autenticação.

---

## Actuator — Monitoramento de Saúde

### Explicação não técnica

Actuator é como o **painel de instrumentos** de um carro. Mostra se o motor está funcionando, se o óleo está ok, qual a temperatura. Para a API, mostra se a aplicação está rodando, se o banco de dados está conectado, e informações sobre a versão.

### Explicação técnica

Spring Boot Actuator expõe endpoints de monitoramento para health checks e métricas:

| Endpoint | Descrição |
|---|---|
| `/actuator/health` | Status da aplicação (UP/DOWN) |
| `/actuator/info` | Informações da aplicação (versão, descrição) |

### Dependência no pom.xml

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Exemplo de resposta (`/actuator/health`)

```json
{
  "status": "UP"
}
```

As rotas do Actuator são protegidas no `SecurityConfig` com `.requestMatchers("/actuator/**").hasRole("ADMIN")` — não são públicas. Ferramentas de monitoramento externas devem se autenticar com um usuário ADMIN ou acessar o health check diretamente pela rede interna sem passar pelo proxy.

---

## Fontes

- [SLF4J — Documentação Oficial](https://www.slf4j.org/manual.html)
- [Logback — Documentação Oficial](https://logback.qos.ch/documentation.html)
- [Springdoc OpenAPI — GitHub](https://github.com/springdoc/springdoc-openapi)
- [Swagger UI — Documentação](https://swagger.io/tools/swagger-ui/)
- [Spring Boot Actuator — Documentação Oficial](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [Baeldung — Spring Boot Actuator](https://www.baeldung.com/spring-boot-actuators)
