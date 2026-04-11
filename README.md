# Equilibra — Backend

Backend da aplicação **Equilibra**, um sistema de controle financeiro pessoal. Este repositório contém a API REST que alimenta o app Web e Mobile.

---

## A ideia

Muita gente controla as finanças em uma planilha do Excel ou Google Sheets. Funciona, mas tem limites claros: não avisa quando o cartão está perto do limite, não fecha a fatura automaticamente, não calcula o saldo real depois de todos os gastos do mês, e só funciona no desktop.

Este projeto é a evolução dessa planilha para uma aplicação real, acessível de qualquer lugar, que faz os cálculos automaticamente e mantém o histórico completo sem precisar de fórmulas manuais.

O objetivo não é concorrer com apps como Mobills ou Guiabolso — é uma aplicação própria, construída do zero para uso pessoal, que reflete exatamente como o usuário organiza as suas finanças.

---

## O que o sistema faz

- Cadastrar **contas bancárias** e acompanhar o saldo em tempo real
- Cadastrar **cartões de crédito** com limite disponível calculado automaticamente
- Registrar **transações** de receita e despesa, vinculadas a contas ou cartões
- Acompanhar **faturas** de cartão com geração automática, fechamento e controle de status
- Organizar gastos por **categorias** personalizadas
- Registrar e acompanhar **investimentos e metas de poupança**
- Verificar identidade via **código OTP por e-mail** no cadastro
- **Autenticação JWT** com access token + refresh token
- **Reativação de conta** após desativação (soft delete), com validação de senha
- **Internacionalização (i18n)** de mensagens de erro (pt-BR e EN)

---

## Stack Técnica

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 25 |
| Framework | Spring Boot 3.5.x |
| Persistência | Spring Data JPA + Hibernate |
| Segurança | Spring Security + JWT (jjwt 0.12.x) |
| Mapeamento | MapStruct 1.6.x |
| Validação | Jakarta Bean Validation |
| Logging | SLF4J + Logback |
| Documentação API | Springdoc OpenAPI (Swagger UI) |
| Monitoramento | Spring Boot Actuator |
| Utilitários | Lombok |
| Build | Maven |
| Testes | JUnit 5 + Mockito 5.17.0 + Testcontainers (PostgreSQL real) |
| Banco de Dados | PostgreSQL 16 + Flyway (Migrations) |

---

## Arquitetura

O projeto segue a arquitetura em camadas padrão do Spring Boot:

```
Controller  →  Service  →  Repository  →  Banco de Dados
     ↕              ↕
   DTOs          Entities
     ↕
  Mappers (MapStruct)
```

**Padrões e decisões técnicas adotadas:**

- **Fail-Fast**: validações acontecem no início de cada método. Saldo negativo e limite insuficiente bloqueiam a operação com exceção, nunca apenas alertam.
- **Soft Delete + @SQLRestriction**: nenhum registro é apagado fisicamente. O campo `ativo = false` desativa o registro, e `@SQLRestriction("ativo = true")` filtra automaticamente em todas as queries.
- **Lazy Creation de Faturas**: faturas não são criadas antecipadamente. Elas surgem automaticamente quando a primeira transação do mês é lançada no cartão.
- **Ghost Closing de Faturas + Scheduler**: ao listar faturas, o sistema verifica a data atual e atualiza o status. Adicionalmente, um `@Scheduled` diário marca faturas vencidas como ATRASADA.
- **Status automático de Transações**: se o usuário não informar o status, ele é derivado automaticamente do método de pagamento (PIX/DINHEIRO/DEBITO → PAGO; BOLETO/CARTAO_CREDITO → PENDENTE; cartão sempre força PENDENTE).
- **Limite dinâmico de Cartões**: o limite disponível é calculado em tempo real como `limiteTotal - somaFaturasAbertas`, sem campo persistido.
- **MapStruct para mapeamento**: todas as conversões Entity ↔ DTO são feitas por interfaces MapStruct em tempo de compilação, eliminando código manual.
- **Orquestrador de Movimentações**: `MovimentacaoFinanceiraService` encapsula os impactos financeiros em Contas, Cartões e Faturas, evitando duplicação entre criar/atualizar/deletar transações.
- **JWT Stateless**: autenticação via Access Token (curta duração) + Refresh Token (longa duração), sem sessão no servidor.
- **Logging Estruturado**: todos os services possuem `log.info` para operações de sucesso e `log.warn` para violações de regra de negócio. O `GlobalExceptionHandler` usa `log.error` para exceções inesperadas.
- **Lombok seguro em Entities**: usamos `@Getter`, `@Setter`, `@NoArgsConstructor` e `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` vinculado apenas ao `id`, evitando loops infinitos e queries N+1 que o `@Data` causaria.
- **Nomenclatura pt-BR**: nomes de classes, variáveis, exceções e mensagens seguem o português brasileiro.

---

## Estrutura de Pacotes

```
src/main/java/org/app_financeiro/backend/
├── controller/          # Endpoints REST (11 controllers)
├── service/             # Regras de negocio (18 services)
├── repository/          # Interfaces Spring Data JPA (14 repositorios)
├── entity/              # Entidades JPA (14 entidades)
├── dto/
│   ├── request/         # DTOs de entrada (10 classes)
│   └── response/        # DTOs de saída (9 classes, incluindo AuthResponseDTO)
├── mapper/              # MapStruct mappers (7 interfaces)
├── enums/               # TipoTransacao, StatusFatura, MetodoPagamento, StatusTransacao
├── exception/           # Exceções de negócio (9 classes + GlobalExceptionHandler)
├── config/              # ApplicationConfig, SecurityConfig, JwtAuthenticationFilter, PepperedPasswordEncoder
└── util/                # ValidacaoUtils
```

---

## Segurança e Autenticação

A API usa **JWT (JSON Web Tokens)** para autenticação stateless.

### Fluxo de autenticação

```
1. POST /api/auth/registrar                → Cria conta (emailVerificado=false)
2. POST /api/auth/verificar-email          → Valida código OTP de 6 dígitos
3. POST /api/auth/login                    → Retorna accessToken + refreshToken + expiresIn
4. Requisições protegidas                  → Header "Authorization: Bearer <accessToken>"
5. POST /api/auth/refresh                  → Renova accessToken usando refreshToken
```

### Rotas públicas (sem autenticação)

- `/api/auth/**` — registro, login, verificação de e-mail
- `/swagger-ui/**`, `/v3/api-docs/**` — documentação Swagger

### Rotas de observabilidade

- `/actuator/**` exige perfil com `ROLE_ADMIN` (conforme `SecurityConfig`)

### Rotas protegidas

Todas as demais rotas exigem um `accessToken` válido no header `Authorization: Bearer <token>`. O usuário autenticado é injetado automaticamente via `@AuthenticationPrincipal UsuarioEntity`.

### Configuracao JWT

As credenciais sao definidas via **variaveis de ambiente** (nunca hardcoded):

```bash
# Gere uma chave secreta segura:
openssl rand -hex 32
```

```bash
# Defina as variaveis de ambiente (ou use um arquivo .env local):
export JWT_SECRET=<chave-gerada>
export CORS_ALLOWED_ORIGINS=http://localhost:3000
```

> Atencao: atualmente nao ha `.env.example` versionado no backend. Crie o `.env` manualmente com base nas variaveis documentadas neste README e em `application.properties`.

> ⚠️ **Nunca commite o arquivo `.env`** - ele esta no `.gitignore`.

---

## Endpoints da API

### Autenticação (`/api/auth`)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/auth/registrar` | Cadastra novo usuário |
| `POST` | `/api/auth/login` | Autentica e retorna access + refresh tokens |
| `POST` | `/api/auth/refresh` | Renova access token usando refresh token |
| `POST` | `/api/auth/verificar-email` | Valida código OTP de 6 dígitos |
| `POST` | `/api/auth/reenviar-codigo` | Reenvia código de verificação |
| `POST` | `/api/auth/reativar-conta` | Reativa conta desativada (exige senha) |

### Contas Bancárias (`/api/contas`)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/contas` | Cria nova conta |
| `GET` | `/api/contas` | Lista contas ativas do usuário |
| `GET` | `/api/contas/{id}` | Busca conta por ID |
| `PUT` | `/api/contas/{id}/saldo` | Atualiza saldo manualmente |
| `DELETE` | `/api/contas/{id}` | Soft delete da conta |

### Cartões de Crédito (`/api/cartoes`)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/cartoes` | Cria novo cartão |
| `GET` | `/api/cartoes` | Lista cartões ativos com limite disponível |
| `GET` | `/api/cartoes/{id}` | Busca cartão por ID |
| `DELETE` | `/api/cartoes/{id}` | Soft delete do cartão |

### Faturas (`/api/faturas`)

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/faturas/cartao/{cartaoId}` | Lista faturas do cartão (com fechamento automático) |
| `POST` | `/api/faturas/{id}/pagar` | Paga fatura (total ou parcial) |

### Transações (`/api/transacoes`)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/transacoes` | Cria transação (impacta saldo ou limite) |
| `GET` | `/api/transacoes?ano=X&mes=Y` | Lista transações do mês |
| `PUT` | `/api/transacoes/{id}` | Atualiza transação (reverte e reaplica impacto) |
| `DELETE` | `/api/transacoes/{id}` | Soft delete + reverte impacto financeiro |

### Categorias (`/api/categorias`)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/categorias` | Cria categoria |
| `GET` | `/api/categorias` | Lista categorias (filtro opcional `?tipo=RECEITA/DESPESA`) |
| `DELETE` | `/api/categorias/{id}` | Soft delete da categoria |

### Investimentos (`/api/investimentos`)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/investimentos` | Cria investimento / meta de poupança |
| `GET` | `/api/investimentos` | Lista investimentos ativos |
| `POST` | `/api/investimentos/{id}/depositar` | Deposita valor (debita de uma conta) |
| `POST` | `/api/investimentos/{id}/resgatar` | Resgata valor (credita em uma conta) |
| `PUT` | `/api/investimentos/{id}/meta` | Atualiza a meta do investimento |
| `DELETE` | `/api/investimentos/{id}` | Soft delete do investimento |

---

## Modelo de Dados

```
UsuarioEntity
    ├── ContaEntity          (saldo modificado por transações)
    ├── CartaoEntity         (limite disponível calculado dinamicamente)
    │     └── FaturaEntity   (criação lazy, status atualizado por ghost closing + scheduler)
    │           └── TransacaoEntity (via cartão)
    ├── TransacaoEntity      (via conta)
    ├── CategoriaEntity      (classificação de transações)
    ├── InvestimentoEntity   (metas de poupança)
    └── CodigoVerificacaoEntity (códigos OTP vinculados por e-mail)
```

### Ciclo de vida de uma Fatura

```
ABERTA  →  (passou dataFechamento)   →  FECHADA
FECHADA →  (passou dataVencimento)   →  ATRASADA
FECHADA ou ATRASADA  →  (paga integralmente)  →  PAGA
```

### Regras de impacto de uma Transação

```
DESPESA + conta + PAGO       →  debitarSaldo()    (bloqueia se saldo insuficiente)
DESPESA + conta + PENDENTE   →  Nenhum impacto    (apenas registra no banco)
DESPESA + cartão             →  consumirLimite() + adiciona na Fatura (sempre PENDENTE)
RECEITA + conta + PAGO       →  creditarSaldo()
RECEITA + conta + PENDENTE   →  Nenhum impacto    (apenas registra no banco)
RECEITA + cartão             →  Estorno/Cashback: subtrai da Fatura (clamp em zero)
```

### Determinação automática de status

```
Método de pagamento → Status derivado:
  PIX, DINHEIRO, DEBITO, VALE_ALIMENTACAO, TRANSFERENCIA  →  PAGO
  BOLETO, CARTAO_CREDITO                                  →  PENDENTE
  Transação via cartão (qualquer método)                   →  Sempre PENDENTE (forçado)
  Se o usuário informou status explicitamente              →  Respeita (exceto cartão)
```

---

## Tratamento de Erros

Todas as exceções são capturadas pelo `GlobalExceptionHandler` e retornam um JSON padronizado:

```json
{
  "timestamp": "2026-03-01T14:30:00",
  "status": 422,
  "erro": "Saldo insuficiente",
  "mensagem": "Saldo insuficiente para realizar a operação"
}
```

| Exceção | HTTP |
|---|---|
| `RecursoNaoEncontradoException` | 404 |
| `CredenciaisInvalidasException` | 401 |
| `EmailNaoVerificadoException` | 403 |
| `EmailJaCadastradoException` | 409 |
| `OperacaoNaoPermitidaException` | 409 |
| `SaldoInsuficienteException` | 422 |
| `LimiteInsuficienteException` | 422 |
| `CodigoVerificacaoInvalidoException` | 400 |
| `RegraDeNegocioException` | 400 |
| `MethodArgumentNotValidException` | 422 |

---

## Observabilidade

### Swagger / OpenAPI

A documentação interativa da API está disponível em:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

Gerada automaticamente pelo Springdoc OpenAPI a partir das anotações dos Controllers.

### Actuator

Endpoints de monitoramento disponíveis em:

- **Health**: `http://localhost:8080/actuator/health`
- **Info**: `http://localhost:8080/actuator/info`

### Logging

Todos os services utilizam logging estruturado via SLF4J:

- `log.info` — operações concluídas com sucesso (criação, atualização, exclusão)
- `log.warn` — violações de regra de negócio (saldo insuficiente, duplicatas, etc.)
- `log.error` — exceções inesperadas no fallback do `GlobalExceptionHandler`

---

## Status do Projeto

### Progresso geral: ~95%

| Módulo | Status | Detalhes |
|---|---|---|
| Entidades | ✅ Completo | 14 entidades JPA no codigo atual |
| Repositorios | ✅ Completo | 14 repositorios Spring Data JPA |
| Controllers | ✅ Completo | 11 controllers REST |
| Services | ✅ Completo | 18 services de negocio |
| Seguranca JWT | ✅ Completo | Access/Refresh token, CORS, rotas protegidas |
| OpenAPI/Swagger | ✅ Completo | Configurado com `OpenApiConfig` e `SpringDocUtils` |
| Actuator | ✅ Completo | Endpoints expostos com restricao de acesso em `SecurityConfig` |
| Flyway migrations | ✅ Completo | Versoes V1 ate V22 presentes |
| Testes automatizados | ✅ Completo | 37 classes `*Test` (8 de integracao) |
| CI (GitHub Actions) | ✅ Parcial | Workflow Maven roda testes unitarios explicitos |
| Template de ambiente | ⚠️ Pendente | `.env.example` ainda nao versionado |
| Deploy producao | ⚠️ Pendente | Nao documentado como concluido |

### Checklist objetivo (auditoria)

- Build backend com Maven: configurado
- Testes backend: presentes e pipeline ativa
- Seguranca de secrets: `.env` ignorado no git
- Documentacao tecnica em `docs/`: presente e versionada
- Coerencia README x codigo: atualizada nesta revisao

---

## Documentação Técnica

Módulos detalhados sobre cada decisão arquitetural estão disponíveis em `docs/`:

| Módulo | Descrição |
|---|---|
| [`mapstruct.md`](docs/mapstruct.md) | MapStruct — mapeamento automático Entity ↔ DTO |
| [`jwt-seguranca.md`](docs/jwt-seguranca.md) | JWT — autenticação stateless + Argon2 + PepperedPasswordEncoder |
| [`movimentacao-financeira.md`](docs/movimentacao-financeira.md) | Orquestrador de impactos financeiros |
| [`faturas-e-scheduler.md`](docs/faturas-e-scheduler.md) | Faturas: lazy creation, ghost closing, scheduler |
| [`soft-delete-e-sqlrestriction.md`](docs/soft-delete-e-sqlrestriction.md) | Soft delete + @SQLRestriction + BigDecimal |
| [`logging-observabilidade.md`](docs/logging-observabilidade.md) | Logging SLF4J + Swagger + Actuator |
| [`referencia-api.md`](docs/referencia-api.md) | Guia de Referência da API (Endpoints e Arquitetura) |
| [`erros-e-i18n.md`](docs/erros-e-i18n.md) | Tratamento de erros, ErrorCodes e internacionalização |
| [`contexto-testes.md`](docs/contexto-testes.md) | Catálogo completo de cenários de teste |
| [`locking.md`](docs/locking.md) | Controle de concorrência com @Version |

---

## Próximos Passos

### Fase 9 — Deploy

Definir plataforma de hospedagem gratuita (Railway, Render) e banco de dados (Supabase ou equivalente). Configurar CI/CD para deploy automático.

---

## Como Executar

> Nota: É necessário ter o **Docker Desktop** rodando na máquina local.

```bash
# Clonar o repositório
git clone <url-do-repositorio>

# Entrar na pasta do backend
cd backend

# Subir a infraestrutura (PostgreSQL) em background
docker compose up -d

# Compilar e Rodar os Testes Automatizados (TDD com Testcontainers)
mvn clean install

# Rodar
mvn spring-boot:run
```

Após iniciar, acesse:
- **API**: `http://localhost:8080`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **Health Check**: `http://localhost:8080/actuator/health`
