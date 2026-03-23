# Controle de Concorrência — Versão Optimista

Para evitar inconsistências quando duas requisições modificam o mesmo registro ao mesmo tempo, passamos a usar *optimistic locking*.

### Como funciona

1. Cada tabela sensível (contas, cartões, faturas) ganhou uma coluna `version`.
2. O JPA mapeia essa coluna com a anotação `@Version`. A cada `update` ela é incrementada automaticamente.
3. Quando uma transação lê um objeto e outro a modifica antes do `save()`, a versão não bate e o Hibernate lança
   `ObjectOptimisticLockingFailureException` (ou `OptimisticLockingFailureException`).
4. O código de chamada pode capturar essa exceção e decidir se tenta de novo ou retorna erro.

### Por que *optimistic*?

- Não bloqueia a linha no banco, permitindo alta concorrência de leitura.
- Indicado quando a probabilidade de conflito é baixa (caso de uso pessoal). Se os testes de carga demonstrarem
  muitos conflitos, podemos migrar para *pessimistic locking* (`@Lock(PESSIMISTIC_WRITE)` ou `SELECT ... FOR UPDATE`).

### Migração de schema

A migration `V4__add_version_columns.sql` já adiciona as colunas com valor default `0`:
```sql
ALTER TABLE contas ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cartoes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE faturas ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

> **Atenção:** em produção, execute essa migration antes de deploy para evitar erros de coluna não encontrada.

### Testes

- O novo teste `ContaRepositoryConcurrencyTest` demonstra o disparo da exceção ao tentar salvar um registro
  com versão desatualizada.


---

> Nota: se algum dia precisarmos de *pessimistic locking*, basta adicionar `@Lock` no repositório ou utilizar
> transações em `Serializable`/`REPEATABLE_READ` e testar novamente. Mantemos este documento para orientar futuros
> contribuintes e a IA do projeto.