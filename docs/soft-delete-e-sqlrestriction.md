# Soft Delete e @SQLRestriction — Exclusão Segura e Precisão Monetária

## Soft Delete

### Explicação não técnica

Quando você deleta um arquivo no computador, ele vai para a **lixeira** — não é apagado de verdade. Você pode restaurá-lo depois se precisar. Só quando esvazia a lixeira é que o arquivo some de vez.

Soft delete funciona da mesma forma no banco de dados. Ao "deletar" uma conta, transação ou categoria, o sistema não apaga o registro. Ele apenas marca como **inativo** (`ativo = false`). Isso preserva todo o histórico financeiro.

**Por que isso é importante para finanças?** Se você apagasse uma transação de R$ 500,00 do mês passado, o saldo ficaria inconsistente. Com soft delete, a transação continua existindo para manter o histórico correto, mas não aparece mais nas listagens do usuário.

### Explicação técnica

Cada entity do sistema possui um campo booleano `ativo`:

```java
// ContaEntity.java — trecho real
@Entity
@Table(name = "contas")
@SQLRestriction("ativo = true")
public class ContaEntity {
    // ... outros campos ...

    @Column(nullable = false)
    private boolean ativo = true;
}
```

Nos services, a "exclusão" é feita assim:

```java
// ContaService.java — trecho real
@Transactional
public void deletarConta(Long contaId, Long usuarioId) {
    ContaEntity conta = buscarContaValidada(contaId, usuarioId);
    conta.setAtivo(false);
    contaRepository.save(conta);
    log.info("Conta {} '{}' desativada (soft delete)", contaId, conta.getNome());
}
```

---

## @SQLRestriction — Filtro Automático

### Explicação não técnica

Imagine que você tem uma gaveta cheia de fichas de clientes. Algumas fichas estão marcadas com um carimbo "INATIVO". Toda vez que você procura uma ficha, **automaticamente ignora** as que têm o carimbo. Você não precisa lembrar de verificar o carimbo — ele é filtrado por padrão.

`@SQLRestriction` faz exatamente isso no banco de dados. Toda query que o Hibernate gera para aquela entity já inclui `WHERE ativo = true` automaticamente.

### Explicação técnica

A anotação `@SQLRestriction("ativo = true")` do Hibernate adiciona automaticamente uma cláusula `WHERE ativo = true` a todas as queries geradas para a entity.

### Antes — sem @SQLRestriction

```java
// Era necessário ter "AndAtivoTrue" em TODOS os métodos do repository
public interface ContaRepository extends JpaRepository<ContaEntity, Long> {
    List<ContaEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);
    Optional<ContaEntity> findByIdAndAtivoTrue(Long id);
    // ... repetido em CADA query
}
```

```java
// E nos services, verificar manualmente
ContaEntity conta = contaRepository.findById(id)
        .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
if (!conta.isAtivo()) {
    throw new RecursoNaoEncontradoException("Conta não encontrada");
}
```

### Depois — com @SQLRestriction

```java
// Repository simplificado (código real)
public interface ContaRepository extends JpaRepository<ContaEntity, Long> {
    List<ContaEntity> findByUsuarioId(Long usuarioId);
    // O filtro "ativo = true" é aplicado automaticamente pelo Hibernate
}
```

```java
// Service simplificado (código real)
public ContaEntity buscarContaValidada(Long contaId, Long usuarioId) {
    return contaRepository.findById(contaId)
            .filter(c -> c.getUsuario().getId().equals(usuarioId))
            .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    // Não precisa verificar isAtivo() — @SQLRestriction já filtrou
}
```

**Resultado:** eliminamos a duplicação de `AndAtivoTrue` em 8 repositories e a verificação manual `isAtivo()` em todos os services.

### Entities que usam @SQLRestriction

Todas as 8 entities principais do sistema:

| Entity | Tabela |
|---|---|
| `UsuarioEntity` | `usuarios` |
| `ContaEntity` | `contas` |
| `CartaoEntity` | `cartoes` |
| `FaturaEntity` | `faturas` |
| `TransacaoEntity` | `transacoes` |
| `TransacaoRecorrenteEntity` | `transacoes_recorrentes` |
| `CategoriaEntity` | `categorias` |
| `InvestimentoEntity` | `investimentos` |

A única entity sem `@SQLRestriction` é `CodigoVerificacaoEntity`, que usa `utilizado` (boolean) e não `ativo`.

---

## BigDecimal — Por que não usar double para dinheiro?

### Explicação não técnica

Tente somar R$ 0,10 + R$ 0,20 em uma calculadora de um computador. O resultado deveria ser R$ 0,30, certo? Mas com `double`, o resultado pode ser `0.30000000000000004`. Isso acontece porque o computador armazena números decimais em formato binário, e nem todos os valores podem ser representados com exatidão.

Para dinheiro, **qualquer centavo de diferença é um erro**. Se você tem R$ 1.000,00 e faz 1.000 operações de R$ 1,00, com `double` o resultado pode ser R$ 0,01 em vez de R$ 0,00. `BigDecimal` resolve isso representando o número com **precisão exata**.

### Explicação técnica

`double` é um tipo IEEE 754 de ponto flutuante binário. Ele tem precisão de ~15-17 dígitos significativos, mas não pode representar exatamente todas as frações decimais.

`BigDecimal` é uma representação decimal exata com precisão arbitrária. É o tipo recomendado para cálculos monetários em Java.

### Configuração no projeto — precision e scale

Todas as entities que possuem campos monetários usam `precision = 19, scale = 2`:

```java
// ContaEntity.java — trecho real
@Column(nullable = false, precision = 19, scale = 2)
private BigDecimal saldo;
```

- **precision = 19**: até 19 dígitos no total (permite valores até 9.999.999.999.999.999,99)
- **scale = 2**: exatamente 2 casas decimais (centavos)

Isso é traduzido pelo Hibernate para `DECIMAL(19,2)` no banco de dados.

### Operações com BigDecimal no código real

```java
// ContaService.java — comparação e operação
public ContaEntity debitarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
    ContaEntity conta = buscarContaValidada(contaId, usuarioId);

    // NUNCA usar < ou > com BigDecimal! Usar compareTo()
    if (conta.getSaldo().compareTo(valor) < 0) {
        throw new SaldoInsuficienteException("Saldo insuficiente");
    }

    // Subtração com BigDecimal
    conta.setSaldo(conta.getSaldo().subtract(valor));
    contaRepository.save(conta);
    return conta;
}
```

**Armadilha comum:** `BigDecimal` é imutável. `conta.getSaldo().subtract(valor)` **não modifica** o saldo — retorna um novo objeto. É necessário usar `setSaldo()` com o resultado.

---

## Fontes

- [Hibernate @SQLRestriction — Documentação Oficial](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#pc-filter-sql-restriction)
- [Vlad Mihalcea — Hibernate @SQLRestriction](https://vladmihalcea.com/hibernate-sql-restriction-annotation/)
- [Java BigDecimal — Oracle JavaDoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [Why Not Use Double for Money — Stack Overflow](https://stackoverflow.com/questions/3730019/why-not-use-double-or-float-to-represent-currency)
- [Soft Delete Pattern — Baeldung](https://www.baeldung.com/spring-jpa-soft-delete)
