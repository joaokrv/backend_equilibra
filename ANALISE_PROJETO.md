# Analise Completa do Projeto - Controle Financeiro Pessoal

> **Data:** 28/02/2026
> **Stack:** Java 21, Spring Boot 3.5.11, Spring Web/Data JPA/Security/Validation, Lombok, Docker Compose
> **Objetivo:** Evolucao de uma "planilha de gastos" para um sistema completo (Web + Mobile)

---

## Indice

1. [Aulao de Arquitetura (Nivelamento)](#1-aulao-de-arquitetura-nivelamento)
2. [Diagnostico de Status](#2-diagnostico-de-status)
3. [Bugs e Correcoes Necessarias](#3-bugs-e-correcoes-necessarias)
4. [Regras de Negocio](#4-regras-de-negocio)
5. [Arquivos Faltantes (Esqueletos a Criar)](#5-arquivos-faltantes-esqueletos-a-criar)
6. [Roadmap e Primeiro Passo](#6-roadmap-e-primeiro-passo)

---

## 1. Aulao de Arquitetura (Nivelamento)

### O Fluxo: Controller -> Service -> Repository -> Entity

Pense numa fabrica com setores bem divididos. Cada setor faz UMA coisa e nao se mete no trabalho do outro.

```
[Cliente / Frontend / Postman]
         |
         | Requisicao HTTP (POST /api/contas, GET /api/transacoes, etc.)
         v
+------------------------+
|     CONTROLLER         |   RECEPCAO - Recebe o pedido do cliente
|     (@RestController)  |   - Valida o FORMATO dos dados (@Valid)
|                        |   - NAO decide nada sobre regras
|                        |   - Chama o Service e devolve a resposta HTTP
+------------------------+
         |
         | DTO (Request) --> entra
         | DTO (Response) <-- sai
         v
+------------------------+
|     SERVICE            |   FABRICA - Onde a LOGICA DE NEGOCIO vive
|     (@Service)         |   - Valida regras: "saldo suficiente?", "email duplicado?"
|                        |   - Orquestra operacoes entre multiplos repositories
|                        |   - Converte Entity <-> DTO
|                        |   - Lanca exceptions quando algo esta errado
+------------------------+
         |
         | Entity (objeto Java) --> entra/sai
         v
+------------------------+
|     REPOSITORY         |   ALMOXARIFADO - Interface com o banco de dados
|     (@Repository)      |   - Sabe buscar, salvar, deletar
|                        |   - NAO tem logica de negocio
|                        |   - Spring Data JPA gera a implementacao automaticamente
+------------------------+
         |
         | SQL (gerado pelo Hibernate)
         v
+------------------------+
|     BANCO DE DADOS     |   Os dados persistidos (PostgreSQL)
+------------------------+
```

### E as Entities?

As **Entities** (`@Entity`) sao as classes Java que representam as tabelas do banco.
Cada campo da Entity = uma coluna da tabela. Os relacionamentos (`@ManyToOne`, `@OneToMany`)
representam as foreign keys.

### E os DTOs?

Os **DTOs** (Data Transfer Objects) sao os "envelopes" que trafegam entre as camadas:

| Tipo | Direcao | Funcao |
|------|---------|--------|
| **Request DTO** | Frontend -> Controller -> Service | O que o usuario envia. Contem validacoes (`@NotBlank`, `@Email`). |
| **Response DTO** | Service -> Controller -> Frontend | O que o backend devolve. Filtra campos sensiveis (ex: nunca devolve `senha`). |

**Regra de ouro:** Entity NUNCA vai direto para o frontend. Sempre passa por um DTO.

### Tabela de Responsabilidades

| Camada | Pode fazer | NAO pode fazer |
|--------|-----------|----------------|
| **Controller** | Receber HTTP, validar formato (`@Valid`), retornar status codes | Ter logica de negocio, acessar Repository |
| **Service** | Logica de negocio, validacoes inteligentes, conversao DTO/Entity | Saber sobre HTTP, receber HttpServletRequest |
| **Repository** | Acessar banco, definir queries customizadas | Ter logica de negocio |
| **Entity** | Mapear tabela, definir relacionamentos | Ter logica complexa |

---

## 2. Diagnostico de Status

### 2.1 Progresso por Camada

```
Entities       |####################| 100%  6/6 completas
Enums          |####################| 100%  3/3 completos
Repositories   |####################| 100%  6/6 completos (com queries customizadas)
Request DTOs   |####################| 100%  6/6 completos (falta Investimento)
Response DTOs  |##################  |  83%  5/6 completos (falta Investimento)
Exceptions     |###############     |  75%  4/5 classes ok, handler incompleto
Services       |###                 |  17%  Apenas UsuarioService implementado
Controllers    |                    |   0%  NENHUM existe
Config/Infra   |##                  |  10%  Sem DB, sem Docker, sem JWT
Testes         |                    |   0%  Apenas contextLoads (vai falhar)
-----------------------------------------------------
TOTAL GERAL    |#######             | ~35%
```

### 2.2 O que esta PRONTO

| Arquivo | Status | Observacoes |
|---------|--------|-------------|
| `UsuarioEntity` | COMPLETO | Soft-delete, timestamps, email unico |
| `ContaEntity` | COMPLETO | Relacionamento ManyToOne com Usuario |
| `CartaoEntity` | COMPLETO | Limite, dia fechamento/vencimento |
| `CategoriaEntity` | COMPLETO | Tipo (RECEITA/DESPESA) por usuario |
| `TransacaoEntity` | COMPLETO | Relaciona com conta/cartao/categoria, indice composto |
| `InvestimentoEntity` | COMPLETO | Valor inicial, atual e meta |
| `TipoTransacao` | COMPLETO | RECEITA, DESPESA |
| `StatusTransacao` | COMPLETO | PAGO, PENDENTE |
| `MetodoPagamento` | COMPLETO | 7 opcoes (PIX, cartao credito/debito, etc.) |
| Todos os 6 Repositories | COMPLETO | Queries por usuario, por periodo, por tipo, paginacao |
| Todos os 6 Request DTOs | COMPLETO | Validacoes Jakarta |
| 5 Response DTOs | COMPLETO | Conversao Entity -> DTO no construtor |
| `UsuarioService` | COMPLETO | Registro (com hash BCrypt), Login, busca por id/email |
| 4 Exceptions customizadas | COMPLETO | Hierarquia: BusinessRule, ResourceNotFound, EmailExists, InsufficientBalance |
| `SecurityConfig` | PARCIAL | Apenas BCryptEncoder + permitAll() |
| `GlobalExceptionHandler` | PARCIAL | Trata ResourceNotFound e BusinessRule, falta validacao |

### 2.3 O que esta ESQUELETO (metodos retornando `null`)

| Service | Metodos | Status |
|---------|---------|--------|
| `ContaService` | `criarConta`, `atualizarSaldo`, `buscarPorId`, `buscarTodasDoUsuario` | Todos retornam `null` |
| `CartaoService` | `criarCartao`, `buscarPorId`, `consumirLimite`, `buscarTodosDoUsuario` | Todos retornam `null` |
| `CategoriaService` | `criarCategoria`, `buscarTodasDoUsuario`, `buscarPorTipo`, `deletarCategoria` | Todos retornam `null` |
| `TransacaoService` | `criarTransacao`, `atualizarTransacao`, `deletarTransacao`, `buscarPorMes` | Todos retornam `null` |
| `InvestimentoService` | `criarInvestimento`, `adicionarDeposito`, `buscarTodosDoUsuario` | Todos retornam `null` |

### 2.4 O que NAO EXISTE

| Item | Impacto |
|------|---------|
| **ZERO Controllers** | Nenhum endpoint HTTP. A app nao recebe requisicoes. |
| **Driver de banco no `pom.xml`** | Sem PostgreSQL/H2, o JPA nao conecta em nada. |
| **`application.properties` vazio** | Sem URL do banco, sem ddl-auto, sem porta, sem CORS. |
| **`compose.yaml` vazio** | Sem container de PostgreSQL. |
| **DTOs de Investimento** | Service aceita Entity crua (quebra o padrao). |
| **Autenticacao JWT** | Security esta permitAll(). Nenhum endpoint protegido. |
| **Verificacao de email** | Nao existe fluxo de confirmacao de conta. |
| **Testes unitarios** | Zero testes de logica. |

---

## 3. Bugs e Correcoes Necessarias

### 3.1 Bugs Encontrados

| # | Arquivo | Bug | Correcao |
|---|---------|-----|----------|
| 1 | `CartaoRegistroRequestDTO` | `@Min(0)` em `BigDecimal` -- `@Min` e para tipos inteiros (`int`, `long`) | Trocar por `@DecimalMin(value = "0", message = "...")` |
| 2 | `TransacaoRegistroRequestDTO` | `valor` nao tem `@Positive` -- aceita valor 0 ou negativo | Adicionar `@Positive(message = "O valor deve ser maior que zero")` |
| 3 | `UsuarioEntity` | `nome` e `@Column` sem `nullable = false` -- banco aceita nome nulo | Adicionar `@Column(nullable = false)` |
| 4 | `GlobalExceptionHandler` | Nao trata `MethodArgumentNotValidException` -- erros de `@Valid` viram 500 generico | Adicionar handler que extrai as mensagens de validacao |
| 5 | `GlobalExceptionHandler` | `EmailAlreadyExistsException` cai no handler de `BusinessRuleException` (400) | Criar handler especifico retornando 409 CONFLICT |
| 6 | `InvestimentoService` | Recebe `InvestimentoEntity` em vez de DTO | Criar `InvestimentoRegistroRequestDTO` e `InvestimentoResponseDTO` |
| 7 | `ContaRegistroRequestDTO` | `saldo` aceita valor negativo (nao tem `@DecimalMin`) | Adicionar `@DecimalMin(value = "0")` ou tratar no Service |

### 3.2 Melhorias de Design

| # | Sugestao | Motivo |
|---|----------|--------|
| 1 | Adicionar `@OneToMany(mappedBy = "usuario")` no `UsuarioEntity` | Permitir navegacao bidirecional (util para cascading e queries) |
| 2 | Criar um `ErrorResponseDTO` padronizado | Substituir os `Map<String, Object>` no GlobalExceptionHandler por um objeto tipado |
| 3 | Adicionar `@Transactional(readOnly = true)` nos metodos de leitura | Otimizacao de performance (Hibernate nao faz dirty-checking) |
| 4 | Adicionar `@Size` no `nome` das entidades | Prevenir nomes absurdamente longos (ex: `@Size(max = 100)`) |

---

## 4. Regras de Negocio

### 4.1 Regras Ja Identificadas no Codigo

| # | Regra | Onde esta | Status |
|---|-------|-----------|--------|
| 1 | Email unico no cadastro | `UsuarioService.registrarUsuario()` | Implementada |
| 2 | Senha minimo 6 chars, hasheada com BCrypt | `UsuarioRegistroRequestDTO` + `UsuarioService` | Implementada |
| 3 | Transacao pode vincular a Conta OU Cartao (ambos nullable) | `TransacaoEntity` | Modelada |
| 4 | Categorias sao por tipo (RECEITA/DESPESA) e por usuario | `CategoriaEntity` + `CategoriaRepository` | Modelada |
| 5 | Cartao tem dia fechamento e vencimento | `CartaoEntity` | Modelado |
| 6 | Transacao suporta parcelas (numeroParcela/totalParcelas) | `TransacaoEntity` | Modelado |
| 7 | Soft delete com campo `ativo` em todas entidades | Todas as entities | Modelado |
| 8 | Busca de transacoes por periodo (mes) com indice composto | `TransacaoRepository` + indice | Modelada |

### 4.2 Regras Essenciais que Voce Esta Deixando Passar

Estas regras DEVEM existir nos Services quando voce implementar a logica:

#### Conta

| # | Regra | Onde implementar | Descricao |
|---|-------|-----------------|-----------|
| 9 | Saldo default zero | `ContaService.criarConta()` | Se `saldo` vier null no DTO, assumir `BigDecimal.ZERO` |
| 10 | Saldo atualiza automaticamente | `ContaService.atualizarSaldo()` | DESPESA paga debita saldo. RECEITA credita saldo. |
| 11 | Validar propriedade do recurso | Todos os metodos | Verificar que a conta pertence ao `usuarioId` antes de qualquer operacao |
| 12 | Saldo nao pode ficar negativo (decisao) | `ContaService.atualizarSaldo()` | Lancar `InsufficientBalanceException` OU apenas permitir e alertar |

#### Cartao

| # | Regra | Onde implementar | Descricao |
|---|-------|-----------------|-----------|
| 13 | Limite consumido em despesas | `CartaoService.consumirLimite()` | Ao pagar com cartao credito, reduz limite disponivel |
| 14 | Limite insuficiente | `CartaoService.consumirLimite()` | Lancar `InsufficientBalanceException` se valor > limite disponivel |
| 15 | Limite restaura ao pagar fatura | Futuro | Quando a fatura e paga, o limite volta |

#### Categoria

| # | Regra | Onde implementar | Descricao |
|---|-------|-----------------|-----------|
| 16 | Nao deletar categoria em uso | `CategoriaService.deletarCategoria()` | Se existem transacoes vinculadas, impedir ou desvincular |
| 17 | Nome unico por tipo e usuario | `CategoriaService.criarCategoria()` | Nao permitir duas categorias "Alimentacao" do tipo DESPESA para o mesmo usuario |

#### Transacao

| # | Regra | Onde implementar | Descricao |
|---|-------|-----------------|-----------|
| 18 | Status default PENDENTE | `TransacaoService.criarTransacao()` | Se `status` vier null, assumir `StatusTransacao.PENDENTE` |
| 19 | Debitar conta ao criar despesa PAGA | `TransacaoService.criarTransacao()` | Se tipo=DESPESA, status=PAGO e contaId!=null, debitar saldo da conta |
| 20 | Creditar conta ao criar receita PAGA | `TransacaoService.criarTransacao()` | Se tipo=RECEITA, status=PAGO e contaId!=null, creditar saldo da conta |
| 21 | Consumir limite ao criar despesa no cartao | `TransacaoService.criarTransacao()` | Se tipo=DESPESA e cartaoId!=null, consumir limite do cartao |
| 22 | Reverter saldo ao deletar transacao | `TransacaoService.deletarTransacao()` | Se a transacao era PAGA, reverter o impacto no saldo/limite |
| 23 | Reverter e reaplicar ao atualizar | `TransacaoService.atualizarTransacao()` | Desfaz o efeito antigo, aplica o novo (mudou valor? conta? status?) |
| 24 | Validar consistencia conta/cartao | `TransacaoService.criarTransacao()` | Se metodo=CARTAO_CREDITO, exigir cartaoId. Se metodo=PIX/DEBITO, exigir contaId. |

#### Seguranca / Usuario

| # | Regra | Onde implementar | Descricao |
|---|-------|-----------------|-----------|
| 25 | Cada usuario so ve seus dados | TODOS os services | Em cada busca/alteracao, verificar que o recurso.usuario.id == usuarioId |
| 26 | Soft delete no usuario | `UsuarioService` (futuro) | Desativar usuario (ativo=false) em vez de deletar do banco |

### 4.3 Regras Novas Sugeridas (Planilha Inteligente)

#### Nivel 1 -- Proximo Sprint (alta prioridade)

| # | Regra | Descricao | Impacto |
|---|-------|-----------|---------|
| 27 | **Verificacao de email** | Ao registrar, enviar email com codigo de 6 digitos (ou link). Usuario so esta "verificado" apos confirmar. | Seguranca basica da conta |
| 28 | **Verificacao por celular (opcional)** | Alternativa: enviar SMS/WhatsApp com codigo OTP. Pode ser um segundo fator. | Seguranca extra |
| 29 | **Despesas recorrentes** | Flag `recorrente` + `diaRecorrencia` na transacao. Um job/scheduler cria a transacao todo mes automaticamente. | Contas fixas (aluguel, streaming, etc.) |
| 30 | **Transferencia entre contas** | Tipo especial de transacao: debita conta A, credita conta B, tudo atomico com `@Transactional`. | Organizar dinheiro entre contas |

#### Nivel 2 -- Sprint Seguinte (media prioridade)

| # | Regra | Descricao | Impacto |
|---|-------|-----------|---------|
| 31 | **Fatura do cartao vira despesa** | No dia de fechamento, soma transacoes do cartao no periodo e gera uma despesa PENDENTE na conta bancaria. Quando paga, restaura limite. | Controle real de cartao |
| 32 | **Orcamento por categoria** | Nova entidade `OrcamentoCategoria` com limite mensal. Ao criar transacao, verificar se ultrapassou. | Controle de gastos |
| 33 | **Alertas de orcamento** | Quando gastos da categoria atingem 80% e 100% do limite, incluir flag `alerta` no response. | Prevencao de gastos |
| 34 | **Categorias padrao (seed)** | Ao criar usuario, criar automaticamente categorias basicas: Alimentacao, Transporte, Moradia, Salario, etc. | Usabilidade no dia 1 |

#### Nivel 3 -- Futuro (dashboard/relatorios)

| # | Regra | Descricao | Impacto |
|---|-------|-----------|---------|
| 35 | **Resumo mensal** | Endpoint que retorna: total receitas, total despesas, saldo do mes, top 5 categorias | Dashboard principal |
| 36 | **Comparativo mensal** | Gastou mais ou menos que o mes anterior, por categoria | Insights |
| 37 | **Projecao de saldo** | Baseado nas recorrentes e pendentes, projetar saldo no final do mes | Planejamento |
| 38 | **Exportar para CSV/PDF** | Gerar relatorio das transacoes do mes em formato planilha | Backup/controle |

---

## 5. Arquivos Faltantes (Esqueletos a Criar)

Estes sao todos os arquivos que precisam ser criados. Eu (mentor) crio as **cascas** (assinaturas, anotacoes, estrutura).
Voce preenche a **logica de negocio**.

### 5.1 Infraestrutura (eu configuro)

| Arquivo | O que faz |
|---------|-----------|
| `compose.yaml` | Container PostgreSQL + pgAdmin (opcional) |
| `application.properties` | URL do banco, ddl-auto, porta, CORS, config de email |
| `pom.xml` (alteracoes) | Adicionar: driver PostgreSQL, H2 (testes), java-mail-sender, jjwt (JWT) |

### 5.2 Controllers (6 arquivos novos)

Todos seguem o padrao: `@RestController`, `@RequestMapping("/api/...")`, `@Valid` nos requests.

| Arquivo | Endpoints | Metodos HTTP |
|---------|-----------|-------------|
| `UsuarioController` | `/api/auth/registrar`, `/api/auth/login`, `/api/auth/verificar-email` | POST |
| `ContaController` | `/api/contas` (CRUD) | GET, POST, PUT, DELETE |
| `CartaoController` | `/api/cartoes` (CRUD) | GET, POST, PUT, DELETE |
| `CategoriaController` | `/api/categorias` (CRUD + filtro por tipo) | GET, POST, DELETE |
| `TransacaoController` | `/api/transacoes` (CRUD + busca por mes) | GET, POST, PUT, DELETE |
| `InvestimentoController` | `/api/investimentos` (CRUD + deposito) | GET, POST, PUT |

### 5.3 Verificacao de Email (arquivos novos)

| Arquivo | Pacote | O que faz |
|---------|--------|-----------|
| `CodigoVerificacaoEntity` | `entity/` | Tabela que armazena: codigo (6 digitos), email, dataExpiracao, usado (boolean) |
| `CodigoVerificacaoRepository` | `repository/` | Busca por email e codigo |
| `EmailVerificacaoService` | `service/` | Gera codigo aleatorio, salva no banco, envia email, valida codigo |
| `VerificarEmailRequestDTO` | `dto/request/` | Email + codigo de 6 digitos |
| `ReenviarCodigoRequestDTO` | `dto/request/` | Apenas o email |
| `EmailConfig` | `config/` | Configuracao do JavaMailSender |
| Campo `emailVerificado` | `UsuarioEntity` | Boolean, default false. So permite login se true. |

**Fluxo da verificacao:**

```
1. Usuario se registra (POST /api/auth/registrar)
   -> UsuarioService cria usuario com emailVerificado = false
   -> EmailVerificacaoService gera codigo de 6 digitos
   -> Salva CodigoVerificacaoEntity (expira em 15 min)
   -> Envia email com o codigo

2. Usuario recebe o email e envia o codigo (POST /api/auth/verificar-email)
   -> EmailVerificacaoService valida: codigo existe? nao expirou? nao foi usado?
   -> Se valido: marca emailVerificado = true no UsuarioEntity
   -> Se invalido: lanca BusinessRuleException

3. Usuario tenta fazer login (POST /api/auth/login)
   -> UsuarioService verifica: emailVerificado == true?
   -> Se nao: lanca BusinessRuleException("Verifique seu email antes de fazer login")

4. Reenvio de codigo (POST /api/auth/reenviar-codigo)
   -> Invalida codigos anteriores
   -> Gera novo codigo e envia por email
```

### 5.4 Autenticacao JWT (arquivos novos)

| Arquivo | Pacote | O que faz |
|---------|--------|-----------|
| `JwtTokenProvider` | `security/` | Gera e valida tokens JWT (access token + refresh token) |
| `JwtAuthenticationFilter` | `security/` | Filtro que intercepta cada request, extrai o token do header e autentica |
| `SecurityConfig` (atualizar) | `config/` | Proteger rotas, configurar CORS, sessao stateless |
| `TokenResponseDTO` | `dto/response/` | Retorna accessToken + refreshToken + expiresIn |

### 5.5 DTOs Faltantes

| Arquivo | Pacote | Motivo |
|---------|--------|--------|
| `InvestimentoRegistroRequestDTO` | `dto/request/` | Service aceita Entity crua -- precisa de DTO com validacoes |
| `InvestimentoResponseDTO` | `dto/response/` | Padronizar com os outros modulos |
| `ErrorResponseDTO` | `dto/response/` | Substituir `Map<String, Object>` no GlobalExceptionHandler |
| `ResumoMensalResponseDTO` | `dto/response/` | Para o endpoint de dashboard (futuro) |

### 5.6 Novos Enums Sugeridos

| Arquivo | Valores | Motivo |
|---------|---------|--------|
| `TipoConta` | `CONTA_CORRENTE`, `POUPANCA`, `CARTEIRA`, `INVESTIMENTO` | Diferenciar tipos de conta |
| `BandeiraCartao` | `VISA`, `MASTERCARD`, `ELO`, `AMEX`, `HIPERCARD`, `OUTRO` | Informacao util no cartao |

### 5.7 Resumo Visual dos Arquivos a Criar

```
src/main/java/org/app_financeiro/backend/
|
+-- config/
|   +-- SecurityConfig.java          [ATUALIZAR - JWT + CORS]
|   +-- EmailConfig.java             [NOVO]
|
+-- controller/                      [PACOTE INTEIRO NOVO]
|   +-- UsuarioController.java       [NOVO]
|   +-- ContaController.java         [NOVO]
|   +-- CartaoController.java        [NOVO]
|   +-- CategoriaController.java     [NOVO]
|   +-- TransacaoController.java     [NOVO]
|   +-- InvestimentoController.java  [NOVO]
|
+-- entity/
|   +-- UsuarioEntity.java           [ATUALIZAR - campo emailVerificado]
|   +-- CodigoVerificacaoEntity.java [NOVO]
|
+-- repository/
|   +-- CodigoVerificacaoRepository.java [NOVO]
|
+-- service/
|   +-- EmailVerificacaoService.java [NOVO - logica e sua!]
|   +-- ContaService.java           [PREENCHER LOGICA]
|   +-- CartaoService.java          [PREENCHER LOGICA]
|   +-- CategoriaService.java       [PREENCHER LOGICA]
|   +-- TransacaoService.java       [PREENCHER LOGICA]
|   +-- InvestimentoService.java    [PREENCHER LOGICA]
|
+-- security/                        [PACOTE INTEIRO NOVO]
|   +-- JwtTokenProvider.java        [NOVO]
|   +-- JwtAuthenticationFilter.java [NOVO]
|
+-- dto/
|   +-- request/
|   |   +-- InvestimentoRegistroRequestDTO.java [NOVO]
|   |   +-- VerificarEmailRequestDTO.java       [NOVO]
|   |   +-- ReenviarCodigoRequestDTO.java       [NOVO]
|   +-- response/
|       +-- InvestimentoResponseDTO.java        [NOVO]
|       +-- TokenResponseDTO.java               [NOVO]
|       +-- ErrorResponseDTO.java               [NOVO]
|
+-- enums/
|   +-- TipoConta.java              [NOVO - opcional]
|   +-- BandeiraCartao.java         [NOVO - opcional]
|
+-- exception/
    +-- GlobalExceptionHandler.java  [ATUALIZAR - mais handlers]
    +-- EmailNaoVerificadoException.java [NOVO]
    +-- CodigoVerificacaoException.java  [NOVO]
```

**Total: ~20 arquivos novos + ~5 atualizacoes**

---

## 6. Roadmap e Primeiro Passo

### 6.1 Roadmap em 5 Sprints

```
SPRINT 1 -- "A app roda e eu consigo criar/ver contas"
+-----------------------------------------------------+
| [x] Configurar PostgreSQL (Docker + properties)     |
| [x] Corrigir bugs nos DTOs                          |
| [x] Completar GlobalExceptionHandler                |
| [x] Criar ContaController (casca)                   |
| [ ] VOCE: Implementar logica do ContaService        |
| [x] Testes unitarios do ContaService                |
+-----------------------------------------------------+

SPRINT 2 -- "CRUD completo de tudo"
+-----------------------------------------------------+
| [x] Criar CartaoController + CategoriaController    |
| [ ] VOCE: Implementar CartaoService                 |
| [ ] VOCE: Implementar CategoriaService              |
| [x] Criar TransacaoController                       |
| [ ] VOCE: Implementar TransacaoService (o boss!)    |
| [x] Testes unitarios de todos                       |
+-----------------------------------------------------+

SPRINT 3 -- "Seguranca real"
+-----------------------------------------------------+
| [x] Implementar JWT (token provider + filtro)        |
| [x] Proteger endpoints (so autenticado acessa)       |
| [x] Implementar verificacao de email                 |
| [ ] VOCE: Logica do EmailVerificacaoService          |
| [x] Atualizar SecurityConfig com CORS               |
| [x] Testes de autenticacao                           |
+-----------------------------------------------------+

SPRINT 4 -- "Planilha inteligente"
+-----------------------------------------------------+
| [x] Criar InvestimentoController + DTOs              |
| [ ] VOCE: Implementar InvestimentoService            |
| [ ] VOCE: Despesas recorrentes (scheduler)           |
| [ ] VOCE: Fatura do cartao vira despesa              |
| [ ] VOCE: Orcamento por categoria                    |
+-----------------------------------------------------+

SPRINT 5 -- "Dashboard e polish"
+-----------------------------------------------------+
| [x] Criar endpoint de resumo mensal                  |
| [ ] VOCE: Logica de comparativo mensal               |
| [ ] VOCE: Projecao de saldo                          |
| [ ] Swagger/OpenAPI                                  |
| [ ] Flyway migrations                                |
| [ ] Testes de integracao                             |
+-----------------------------------------------------+
```

**Legenda:** `[x]` = eu preparo a casca/infra | `[ ] VOCE:` = voce implementa a logica

### 6.2 Primeiro Passo Pratico (Sprint 1)

**O que EU vou fazer (cascas e infra):**

1. Atualizar `pom.xml` -- adicionar PostgreSQL driver e H2 para testes
2. Configurar `compose.yaml` -- container PostgreSQL
3. Configurar `application.properties` -- conexao, JPA, porta
4. Corrigir os bugs nos DTOs (itens 1-4 da secao 3)
5. Completar `GlobalExceptionHandler` -- handler de validacao + fallback
6. Criar `ContaController` -- casca com endpoints e assinaturas

**O que VOCE vai fazer (logica):**

1. Implementar `ContaService.criarConta()` -- Regras: validar usuario, default saldo zero, salvar, retornar DTO
2. Implementar `ContaService.buscarPorId()` -- Regras: validar propriedade, lancar ResourceNotFound se nao existe
3. Implementar `ContaService.buscarTodasDoUsuario()` -- Buscar todas ativas do usuario, converter para lista de DTOs
4. Implementar `ContaService.atualizarSaldo()` -- Regras: validar propriedade, somar/subtrair, verificar saldo negativo

**Depois que voce implementar, EU gero:**

- Testes unitarios com JUnit 5 + Mockito para cada metodo do `ContaService`
- Testes validam cenarios de sucesso E de erro (usuario invalido, saldo insuficiente, conta nao encontrada)

### 6.3 Dicas para o ContaService (sem entregar a logica!)

Para cada metodo, pense nesta checklist:

```
1. O usuarioId existe no banco? (buscar via UsuarioService)
   -> Se nao: lancar ResourceNotFoundException

2. O recurso pertence ao usuario? (comparar IDs)
   -> Se nao: lancar ResourceNotFoundException (NAO BusinessRule -- nao revele que existe)

3. As regras de negocio estao ok? (saldo suficiente? nome valido?)
   -> Se nao: lancar BusinessRuleException ou InsufficientBalanceException

4. Executar a operacao (save/update/delete)

5. Converter Entity -> DTO e retornar
```

---

## Proximos Passos

Leia este documento com calma. Quando estiver pronto, me diga:

1. **Concorda com o roadmap?** Quer mudar a ordem de algo?
2. **Verificacao de email:** prefere codigo de 6 digitos por email, link clicavel, ou SMS/WhatsApp?
3. **Regras de negocio:** alguma regra que eu sugeri que nao faz sentido pro seu dia a dia? Alguma que faltou?
4. **Saldo negativo:** quer bloquear (exception) ou permitir (apenas alertar)?

Assim que alinharmos, eu comeco a gerar as cascas do Sprint 1 para voce implementar hoje.
