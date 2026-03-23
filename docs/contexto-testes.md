# Contexto de Testes — Backend Controle Financeiro Pessoal

## Stack de Testes

| Ferramenta       | Finalidade                          |
|------------------|-------------------------------------|
| JUnit 5          | Framework de testes                 |
| Mockito          | Mocking de dependências             |
| AssertJ          | Asserções fluentes                  |
| Testcontainers   | PostgreSQL real em container Docker  |
| Spring Boot Test | Contexto de teste Spring            |

## Padrão de Teste

Todos os testes seguem o padrão **Given-When-Then** (Arrange-Act-Assert):

```java
@Test
void deveRegistrarUsuarioComSucesso() {
    // Given (Arrange)
    var dto = new UsuarioRegistroRequestDTO("teste@email.com", "Senha123!", "João");

    // When (Act)
    var resultado = usuarioService.registrarUsuario(dto);

    // Then (Assert)
    assertNotNull(resultado);
    assertEquals("teste@email.com", resultado.email());
}
```

## Convenção de Nomenclatura

- Métodos de teste: `deve<Ação>Quando<Condicao>` ou `deveLancar<Excecao>Quando<Condicao>`
- Exemplo: `deveCriarContaComSucesso`, `deveLancarExcecaoQuandoSaldoInsuficiente`

---

## Services e Regras de Negócio

### UsuarioService

**Dependências para mock:** `UsuarioRepository`, `PasswordEncoder` (PepperedPasswordEncoder), `EmailVerificacaoService`, `UsuarioMapper`

> **Nota:** O `PasswordEncoder` é mockado como `PepperedPasswordEncoder`. O `UsuarioService` chama `encode(senha)` diretamente — o pepper é transparente (decorator pattern). Os testes **não** prepend pepper manualmente.

| Cenário                                       | Resultado Esperado                    |
|-----------------------------------------------|---------------------------------------|
| Registrar usuário com dados válidos           | Salva e retorna UsuarioResponseDTO    |
| Registrar com email já existente              | Lança `RegraDeNegocioException`       |
| Login com credenciais válidas                 | Retorna UsuarioEntity                 |
| Login com email inexistente                   | Lança `CredenciaisInvalidasException` |
| Login com senha incorreta                     | Lança `CredenciaisInvalidasException` |
| Login com email não verificado                | Lança `RegraDeNegocioException`       |
| Buscar por ID existente                       | Retorna UsuarioEntity                 |
| Buscar por ID inexistente                     | Lança `RecursoNaoEncontradoException` |
| Desativar usuário existente                   | Soft delete (ativo = false)           |
| Reativar conta com senha correta              | Seta ativo = true                     |
| Reativar conta com senha incorreta            | Lança `CredenciaisInvalidasException` |
| Reativar conta inexistente ou ativa           | Lança `RecursoNaoEncontradoException` |

---

### ContaService

**Dependências para mock:** `ContaRepository`, `UsuarioService`, `ContaMapper`

| Cenário                                         | Resultado Esperado                    |
|-------------------------------------------------|---------------------------------------|
| Criar conta com dados válidos                   | Salva e retorna ContaResponseDTO      |
| Criar conta sem saldo inicial                   | Saldo default = BigDecimal.ZERO       |
| Atualizar saldo manualmente                     | Atualiza e retorna DTO               |
| Atualizar saldo com valor negativo              | Lança `RegraDeNegocioException`       |
| Debitar saldo com saldo suficiente              | Subtrai e retorna ContaEntity         |
| Debitar saldo insuficiente                      | Lança `SaldoInsuficienteException`    |
| Creditar saldo                                  | Soma e retorna ContaEntity            |
| Deletar conta sem saldo                         | Soft delete (ativo = false)           |
| Deletar conta com saldo > 0                     | Lança `RegraDeNegocioException`       |
| Buscar conta de outro usuário                   | Lança `RecursoNaoEncontradoException` |

---

### CartaoService

**Dependências para mock:** `CartaoRepository`, `FaturaRepository`, `UsuarioService`, `CartaoMapper`

| Cenário                                         | Resultado Esperado                         |
|-------------------------------------------------|--------------------------------------------|
| Criar cartão com dados válidos                  | Limite disponível = 100% do limite total   |
| Buscar cartão por ID                            | Calcula limite disponível dinamicamente    |
| Listar cartões do usuário                       | Usa query agregada (evita N+1)             |
| Consumir limite com limite suficiente           | Retorna CartaoEntity                       |
| Consumir limite insuficiente                    | Lança `LimiteInsuficienteException`        |
| Deletar cartão sem faturas pendentes            | Soft delete                                |
| Deletar cartão com faturas pendentes            | Lança `RegraDeNegocioException`            |

**Cálculo do limite disponível:**
`Limite Disponível = Limite Total - Σ(valorTotal - valorPago) das faturas NÃO PAGAS`

---

### FaturaService

**Dependências para mock:** `FaturaRepository`, `UsuarioService`, `CartaoService`, `ContaService`, `FaturaMapper`

| Cenário                                          | Resultado Esperado                        |
|--------------------------------------------------|-------------------------------------------|
| Adicionar transação a fatura existente           | Incrementa valorTotal                     |
| Adicionar transação sem fatura para o mês        | Cria fatura nova                          |
| Remover transação de fatura                      | Decrementa valorTotal                     |
| Pagar fatura integralmente                       | Status = PAGA, debita conta               |
| Pagar fatura parcialmente                        | Status mantém, incrementa valorPago       |
| Listar faturas com fechamento fantasma           | Atualiza status de vencidas → ATRASADA    |
| Buscar fatura de outro usuário                   | Lança `RecursoNaoEncontradoException`     |

**Lógica de mês de referência:**
- Se `dataTransacao.dia > diaFechamento` → fatura do mês seguinte
- Se `dataTransacao.dia <= diaFechamento` → fatura do mês atual

---

### TransacaoService

**Dependências para mock:** `TransacaoRepository`, `MovimentacaoFinanceiraService`, `CategoriaService`, `UsuarioService`, `TransacaoMapper`

| Cenário                                                  | Resultado Esperado                         |
|----------------------------------------------------------|--------------------------------------------|
| Criar despesa via conta com PAGO                         | Debita saldo da conta                      |
| Criar despesa via cartão                                 | Status forçado PENDENTE, consome limite    |
| Criar receita via conta com PAGO                         | Credita saldo da conta                     |
| Criar transação com contaId E cartaoId                   | Lança `RegraDeNegocioException` (XOR)      |
| Criar transação sem contaId e sem cartaoId               | Lança `RegraDeNegocioException`            |
| Criar transação com categoria incompatível               | Lança `RegraDeNegocioException`            |
| Atualizar transação (reverte antigo, aplica novo)        | 4 fases: reverter → validar → processar → salvar |
| Deletar transação                                        | Soft delete + desfaz impacto financeiro    |
| Buscar por mês                                           | Filtra por intervalo de datas              |

**Regra de status automático:**
- PIX, DINHEIRO, CARTAO_DEBITO, VALE_ALIMENTACAO, TRANSFERENCIA → `PAGO`
- BOLETO, CARTAO_CREDITO, ou null → `PENDENTE`
- Cartão sempre força `PENDENTE` independente do método

---

### CategoriaService

**Dependências para mock:** `CategoriaRepository`, `UsuarioService`, `CategoriaMapper`

| Cenário                                         | Resultado Esperado                         |
|-------------------------------------------------|--------------------------------------------|
| Criar categoria com nome e tipo únicos          | Salva e retorna DTO                        |
| Criar com nome duplicado para mesmo tipo        | Lança `OperacaoNaoPermitidaException`      |
| Listar todas do usuário                         | Retorna lista (pode ser vazia)             |
| Filtrar por tipo (RECEITA/DESPESA)              | Retorna lista filtrada                     |
| Deletar categoria                               | Soft delete                                |

---

### InvestimentoService

**Dependências para mock:** `InvestimentoRepository`, `ContaService`, `UsuarioService`, `InvestimentoMapper`

| Cenário                                              | Resultado Esperado                       |
|------------------------------------------------------|------------------------------------------|
| Criar investimento com valorInicial <= meta          | Salva entidade                           |
| Criar investimento com valorInicial > meta           | Lança `RegraDeNegocioException`          |
| Depositar com saldo suficiente na conta              | Debita conta, incrementa valorAtual      |
| Depositar com saldo insuficiente                     | Lança `SaldoInsuficienteException`       |
| Resgatar com valorAtual >= valor                     | Decrementa investimento, credita conta   |
| Resgatar com valorAtual < valor                      | Lança `RegraDeNegocioException`          |
| Atualizar meta positiva                              | Atualiza meta                            |
| Atualizar meta <= 0                                  | Lança `RegraDeNegocioException`          |
| Deletar investimento com saldo = 0                   | Soft delete                              |
| Deletar investimento com saldo > 0                   | Lança `RegraDeNegocioException`          |

---

### EmailVerificacaoService

**Dependências para mock:** `CodigoVerificacaoRepository`, `UsuarioRepository`

| Cenário                                           | Resultado Esperado                       |
|---------------------------------------------------|------------------------------------------|
| Gerar código para email válido                    | Invalida anteriores, salva novo OTP 6 dígitos |
| Verificar com código correto e não expirado       | Marca emailVerificado = true             |
| Verificar com código inexistente                  | Lança `CodigoVerificacaoInvalidoException` |
| Verificar com código expirado                     | Lança `CodigoVerificacaoInvalidoException` |
| Reenviar código para email não verificado         | Gera novo código                         |
| Reenviar código para email já verificado          | Lança `RegraDeNegocioException`          |

---

### MovimentacaoFinanceiraService

**Dependências para mock:** `ContaService`, `CartaoService`, `FaturaService`

| Cenário                                                   | Resultado Esperado                     |
|-----------------------------------------------------------|----------------------------------------|
| Processar conta: DESPESA + PAGO                           | Debita saldo                           |
| Processar conta: RECEITA + PAGO                           | Credita saldo                          |
| Processar conta: qualquer + PENDENTE                      | Apenas valida conta, sem impacto       |
| Processar despesa cartão                                  | Consome limite + adiciona à fatura     |
| Processar estorno/cashback cartão                         | Registra crédito na fatura             |
| Desfazer DESPESA+conta+PAGO                               | Credita valor de volta                 |
| Desfazer RECEITA+conta+PAGO                               | Debita valor de volta                  |
| Desfazer DESPESA+cartão                                   | Remove da fatura                       |

---

### JwtService

**Dependências para mock:** Nenhuma (teste direto com chaves configuradas)

| Cenário                                     | Resultado Esperado                       |
|---------------------------------------------|------------------------------------------|
| Gerar access token                          | Token JWT válido com expiração curta     |
| Gerar refresh token                         | Token JWT válido com expiração longa     |
| Extrair username de token válido            | Retorna email do usuário                 |
| Extrair usuarioId de token válido           | Retorna Long do claim                    |
| Validar token expirado                      | Retorna false                            |
| Validar token com username diferente        | Retorna false                            |

---

## Componentes de Segurança

### PepperedPasswordEncoder

**Tipo de teste:** Unitário (sem Spring Context)

| Cenário                                        | Resultado Esperado                      |
|------------------------------------------------|------------------------------------------|
| Encode prefixa pepper na senha                 | Delega `pepper + rawPassword` ao Argon2  |
| Matches prefixa pepper na verificação          | Delega `pepper + rawPassword` ao Argon2  |
| Pepper null não quebra                         | Trata como string vazia                  |
| upgradeEncoding delega ao encoder interno      | Retorna resposta do delegate             |

---

### JwtAuthenticationFilter

**Tipo de teste:** Unitário (MockMvc não necessário — testa o filtro diretamente)

| Cenário                                        | Resultado Esperado                      |
|------------------------------------------------|------------------------------------------|
| Requisição sem header Authorization            | Continua chain sem autenticar            |
| Token JWT inválido (assinatura errada)         | Continua chain sem autenticar            |
| Token JWT válido                               | Seta autenticação no SecurityContext     |
| Token JWT expirado                             | Continua chain sem autenticar            |

---

### GlobalExceptionHandler

**Tipo de teste:** Unitário (com `StaticMessageSource`)

| Cenário                                        | Resultado Esperado                      |
|------------------------------------------------|------------------------------------------|
| SaldoInsuficienteException com locale pt-BR    | Mensagem em português                    |
| SaldoInsuficienteException com locale en       | Mensagem em inglês                       |

---

## Exceções do Sistema

| Exceção                              | HTTP Status | Quando usar                                  |
|--------------------------------------|-------------|----------------------------------------------|
| `RecursoNaoEncontradoException`      | 404         | Entidade não encontrada / não pertence ao usuário |
| `RegraDeNegocioException`            | 400/422     | Violação de regra de negócio genérica        |
| `SaldoInsuficienteException`         | 400         | Saldo da conta insuficiente para débito      |
| `LimiteInsuficienteException`        | 400         | Limite do cartão insuficiente                |
| `CredenciaisInvalidasException`      | 401         | Email ou senha incorretos no login           |
| `OperacaoNaoPermitidaException`      | 403/409     | Ação bloqueada (ex: categoria duplicada)     |
| `CodigoVerificacaoInvalidoException` | 400         | Código OTP inválido ou expirado              |

---

## Configuração de Teste

### application-test.properties (Testcontainers + PostgreSQL real)

```properties
# Configurações para testes de integração
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# JWT para Testes
jwt.secret=706b6e4d6a457a41416e6f7a42695462713136544a434d314c4441544a434d31
jwt.access-token-expiration=3600000
jwt.refresh-token-expiration=604800000

# Pepper Fixo para Testes (Argon2)
auth.pepper=test-pepper-123

# Desabilita o envio de e-mail real nos testes
spring.mail.host=localhost
spring.mail.port=3025
management.health.mail.enabled=false
```

> **Nota:** O banco de dados para testes de integração é provisionado automaticamente pelo **Testcontainers** (PostgreSQL 16 em Docker). O `AbstractIntegrationTest` usa `@DynamicPropertySource` para injetar as credenciais dinâmicas.

### Estrutura de Diretórios de Teste

```
src/test/java/org/app_financeiro/backend/
├── AbstractIntegrationTest.java         # Base com Testcontainers
├── BaseRepositoryTest.java              # Base para @DataJpaTest
├── AccountIntegrationTest.java          # Integração de Contas
├── AuthIntegrationTest.java             # Integração de Autenticação
├── CategoryIntegrationTest.java         # Integração de Categorias
├── InvestmentIntegrationTest.java       # Integração de Investimentos
├── TransactionIntegrationTest.java      # Integração de Transações
├── config/
│   ├── CorsConfigurationTest.java
│   ├── JwtAuthenticationFilterTest.java # ← NOVO
│   └── PepperedPasswordEncoderTest.java # ← NOVO
├── controller/
│   └── TransacaoControllerPaginationTest.java
├── exception/
│   └── GlobalExceptionHandlerTest.java  # ← NOVO
├── repository/
│   ├── CartaoRepositoryTest.java
│   ├── CategoriaRepositoryTest.java
│   ├── CodigoVerificacaoRepositoryTest.java
│   ├── ContaRepositoryConcurrencyTest.java
│   ├── ContaRepositoryTest.java
│   ├── FaturaRepositoryTest.java
│   ├── InvestimentoRepositoryTest.java
│   ├── TransacaoRepositoryTest.java
│   └── UsuarioRepositoryTest.java
└── service/
    ├── CartaoServiceTest.java
    ├── CategoriaServiceTest.java
    ├── ContaServiceTest.java
    ├── EmailVerificacaoServiceTest.java
    ├── FaturaSchedulerLockTest.java
    ├── FaturaServiceTest.java
    ├── InvestimentoServiceTest.java
    ├── JwtServiceTest.java
    ├── MovimentacaoFinanceiraServiceTest.java
    ├── TransacaoServiceTest.java
    └── UsuarioServiceTest.java
```
