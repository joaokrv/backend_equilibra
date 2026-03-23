# Movimentação Financeira — Orquestrador de Impactos

## O que é o MovimentacaoFinanceiraService?

### Explicação não técnica

Imagine um **gerente de banco** que coordena todas as operações. Quando você faz uma compra no cartão, o gerente:
1. Reduz o limite do cartão
2. Adiciona a compra na fatura do mês

Se fosse uma transferência bancária, ele:
1. Debita o valor da sua conta

O gerente não decide as regras — ele apenas **coordena** os departamentos (Conta, Cartão, Fatura) para que trabalhem juntos sem erros.

O `MovimentacaoFinanceiraService` é esse gerente. Ele não tem regras de negócio próprias — ele orquestra os outros services.

### Explicação técnica

É uma implementação do **padrão Facade/Orchestrator**. O `TransacaoService` é responsável pelas regras de negócio das transações, mas o impacto financeiro envolve 3 entidades (Conta, Cartão, Fatura). Em vez de o `TransacaoService` chamar diretamente ContaService + CartaoService + FaturaService (acoplamento alto), ele delega para o `MovimentacaoFinanceiraService`.

**Benefícios:**
- Elimina duplicação de código entre `criarTransacao()`, `atualizarTransacao()` e `deletarTransacao()`
- Centraliza a lógica de impacto financeiro em um único lugar
- Facilita testes unitários (mockar apenas o orquestrador)

---

## Os 4 Métodos — Código Real

### 1. processarTransacaoConta — Impacto em Conta Bancária

```java
/**
 * Aplica impacto financeiro em conta bancária.
 * Só impacta o saldo quando status = PAGO (DESPESA debita, RECEITA credita).
 * Sempre retorna a entidade da conta validada.
 */
@Transactional
public ContaEntity processarTransacaoConta(TipoTransacao tipo, StatusTransacao status,
                                           Long contaId, BigDecimal valor, Long usuarioId) {
    if (status == StatusTransacao.PAGO) {
        if (tipo == TipoTransacao.DESPESA) {
            log.info("Processando débito de R$ {} na conta {} (DESPESA+PAGO)", valor, contaId);
            return contaService.debitarSaldo(contaId, valor, usuarioId);
        } else {
            log.info("Processando crédito de R$ {} na conta {} (RECEITA+PAGO)", valor, contaId);
            return contaService.creditarSaldo(contaId, valor, usuarioId);
        }
    }
    // PENDENTE: valida que a conta existe, mas NÃO impacta o saldo
    return contaService.buscarContaValidada(contaId, usuarioId);
}
```

**Regra importante:** Transações PENDENTE em conta **não movimentam saldo**. O saldo só é afetado quando o status é PAGO. Isso permite registrar gastos futuros (boletos, por exemplo) sem alterar o saldo.

### 2. processarDespesaCartao — Despesa no Cartão de Crédito

```java
/**
 * Aplica impacto financeiro de despesa em cartão de crédito.
 * Consome o limite do cartão e adiciona a transação na fatura correspondente.
 */
@Transactional
public ResultadoMovimentacaoCartao processarDespesaCartao(Long cartaoId, LocalDate data,
                                                          BigDecimal valor, Long usuarioId) {
    CartaoEntity cartao = cartaoService.consumirLimite(cartaoId, valor, usuarioId);
    FaturaEntity fatura = faturaService.adicionarTransacao(cartao, data, valor);
    log.info("Despesa de R$ {} processada no cartão {}. Limite consumido + fatura atualizada",
             valor, cartaoId);
    return new ResultadoMovimentacaoCartao(cartao, fatura);
}
```

**Por que retorna um record?** Ao processar uma despesa em cartão, precisamos de volta tanto o cartão (para vincular à transação) quanto a fatura (idem). O record `ResultadoMovimentacaoCartao` agrupa ambos:

```java
public record ResultadoMovimentacaoCartao(
    CartaoEntity cartao,
    FaturaEntity fatura
) {}
```

### 3. processarEstornoCartao — Receita/Cashback em Cartão

```java
/**
 * Registra receita (estorno/cashback) vinculada a um cartão.
 * Reduz o valor total da fatura.
 */
@Transactional
public ResultadoMovimentacaoCartao processarEstornoCartao(Long cartaoId, LocalDate data,
                                                          BigDecimal valor, Long usuarioId) {
    CartaoEntity cartao = cartaoService.buscarCartaoValidado(cartaoId, usuarioId);
    FaturaEntity fatura = faturaService.registrarCredito(cartao, data, valor);
    return new ResultadoMovimentacaoCartao(cartao, fatura);
}
```

**Nota:** estornos não devolvem limite do cartão — apenas reduzem o valor da fatura. A fatura nunca fica negativa (clamp em R$ 0,00).

### 4. desfazerEfeitoFinanceiro — Reversão para Update/Delete

```java
/**
 * Desfaz o efeito financeiro de uma transação.
 * Usado antes de atualizar ou deletar transações.
 */
@Transactional
public void desfazerEfeitoFinanceiro(TransacaoEntity transacao, Long usuarioId) {
    if (transacao.getConta() != null && transacao.getStatus() == StatusTransacao.PAGO) {
        if (transacao.getTipo() == TipoTransacao.DESPESA) {
            contaService.creditarSaldo(transacao.getConta().getId(),
                                       transacao.getValor(), usuarioId);
        } else {
            contaService.debitarSaldo(transacao.getConta().getId(),
                                      transacao.getValor(), usuarioId);
        }
    } else if (transacao.getCartao() != null && transacao.getFatura() != null) {
        if (transacao.getTipo() == TipoTransacao.DESPESA) {
            faturaService.removerTransacaoPorFatura(transacao.getFatura(),
                                                    transacao.getValor());
        } else {
            faturaService.adicionarTransacaoPorFatura(transacao.getFatura(),
                                                      transacao.getValor());
        }
    }
    log.info("Efeito financeiro da transação {} revertido", transacao.getId());
}
```

**Por que "inverter" as operações?** Se a transação era DESPESA+PAGO em conta, o saldo foi debitado. Para desfazer, creditamos. O oposto para RECEITA. O `@Transactional` garante que se qualquer operação falhar, tudo é revertido (rollback).

---

## Fluxo: Criar → Atualizar → Deletar Transação

```
CRIAR TRANSAÇÃO:
  TransacaoService.criarTransacao()
    └→ MovimentacaoFinanceiraService
          ├→ processarTransacaoConta()    (se via conta)
          └→ processarDespesaCartao()     (se via cartão)

ATUALIZAR TRANSAÇÃO:
  TransacaoService.atualizarTransacao()
    └→ MovimentacaoFinanceiraService
          ├→ desfazerEfeitoFinanceiro()   (reverte o impacto antigo)
          └→ processarTransacaoConta()    (aplica novo impacto)
              ou processarDespesaCartao()

DELETAR TRANSAÇÃO:
  TransacaoService.deletarTransacao()
    └→ MovimentacaoFinanceiraService
          └→ desfazerEfeitoFinanceiro()   (reverte o impacto)
    └→ transacao.setAtivo(false)          (soft delete)
```

---

## Fontes

- [Padrão Facade — Refactoring Guru](https://refactoring.guru/design-patterns/facade)
- [Spring @Transactional — Documentação Oficial](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)
- [Service Layer Pattern — Martin Fowler](https://martinfowler.com/eaaCatalog/serviceLayer.html)
