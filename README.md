# Controle Financeiro Pessoal — Backend

Backend da aplicação de controle financeiro pessoal. Este repositório contém a API REST que alimenta o app Web e Mobile.

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

---

## Stack Técnica

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5.x |
| Persistência | Spring Data JPA + Hibernate |
| Segurança | Spring Security (BCrypt) |
| Validação | Jakarta Bean Validation |
| Utilitários | Lombok |
| Build | Maven |
| Banco de Dados | A definir (sem driver configurado ainda) |

---

## Arquitetura

O projeto segue a arquitetura em camadas padrão do Spring Boot:

```
Controller  →  Service  →  Repository  →  Banco de Dados
     ↕              ↕
   DTOs          Entities
```

**Padrões e decisões técnicas adotadas:**

- **Fail-Fast**: validações acontecem no início de cada método. Saldo negativo e limite insuficiente bloqueiam a operação com exceção, nunca apenas alertam.
- **Soft Delete**: nenhum registro é apagado fisicamente. O campo `ativo = false` desativa o registro, preservando o histórico financeiro.
- **Lazy Creation de Faturas**: faturas não são criadas antecipadamente. Elas surgem automaticamente quando a primeira transação do mês é lançada no cartão.
- **Ghost Closing de Faturas**: ao listar faturas, o sistema verifica a data atual e atualiza o status (`ABERTA → FECHADA → ATRASADA`) sem precisar de jobs agendados.
- **Lombok seguro em Entities**: usamos `@Getter`, `@Setter`, `@NoArgsConstructor` e `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` vinculado apenas ao `id`, evitando loops infinitos e queries N+1 que o `@Data` causaria.
- **Nomenclatura pt-BR**: nomes de classes, variáveis, exceções e mensagens seguem o português brasileiro.

---

## Estrutura de Pacotes

```
src/main/java/org/app_financeiro/backend/
├── controller/          # Endpoints REST (7 controllers, 23 endpoints)
├── service/             # Regras de negócio
├── repository/          # Interfaces Spring Data JPA
├── entity/              # Entidades JPA (8 tabelas)
├── dto/
│   ├── request/         # DTOs de entrada (9 classes)
│   └── response/        # DTOs de saída (8 classes)
├── enums/               # TipoTransacao, StatusFatura, MetodoPagamento, StatusTransacao
├── exception/           # Exceções de negócio (9 classes)
├── config/              # SecurityConfig
└── handler/             # GlobalExceptionHandler
```

---

## Endpoints da API

> Nota: o header `UsuarioId` é temporário. Será substituído por JWT quando a autenticação for implementada.

### Autenticação (`/api/auth`)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/auth/registrar` | Cadastra novo usuário |
| `POST` | `/api/auth/login` | Autentica usuário (exige e-mail verificado) |
| `POST` | `/api/auth/verificar-email` | Valida código OTP de 6 dígitos |
| `POST` | `/api/auth/reenviar-codigo` | Reenvia código de verificação |

### Contas Bancárias (`/api/contas`)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/contas` | Cria nova conta |
| `GET` | `/api/contas` | Lista contas ativas do usuário |
| `GET` | `/api/contas/{id}` | Busca conta por ID |
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

---

## Modelo de Dados

```
UsuarioEntity
    ├── ContaEntity          (saldo modificado por transações)
    ├── CartaoEntity         (limite disponível calculado dinamicamente)
    │     └── FaturaEntity   (criação lazy, status atualizado por ghost closing)
    │           └── TransacaoEntity (via cartão)
    ├── TransacaoEntity      (via conta)
    ├── CategoriaEntity      (classificação de transações)
    └── InvestimentoEntity   (metas de poupança)
```

### Ciclo de vida de uma Fatura

```
ABERTA  →  (passou dataFechamento)   →  FECHADA
FECHADA →  (passou dataVencimento)   →  ATRASADA
FECHADA ou ATRASADA  →  (paga integralmente)  →  PAGA
```

### Regras de impacto de uma Transação

```
DESPESA + conta   →  debitarSaldo()    (bloqueia se saldo insuficiente)
DESPESA + cartão  →  consumirLimite()  (bloqueia se limite insuficiente)
RECEITA + conta   →  creditarSaldo()
RECEITA + cartão  →  erro (não permitido)
```

---

## Tratamento de Erros

Todas as exceções são capturadas pelo `GlobalExceptionHandler` e retornam um JSON padronizado:

```json
{
  "timestamp": "2026-02-28T14:30:00",
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
| `RegraDeNegocioException` | 400 |
| `MethodArgumentNotValidException` | 422 |

---

## Status do Projeto

### Progresso geral: 60%

| Módulo | Status | Detalhes |
|---|---|---|
| Entidades (8/8) | ✅ Completo | Todas modeladas, Lombok seguro aplicado |
| Repositórios (8/8) | ✅ Completo | Queries customizadas definidas |
| DTOs (18/18) | ✅ Completo | Proteção NPE nos ResponseDTOs |
| Exceções + Handler | ✅ Completo | 9 exceções, GlobalExceptionHandler |
| SecurityConfig | ✅ Completo | BCrypt configurado, JWT pendente |
| `UsuarioService` | ✅ Completo | Registro, login, soft delete, BCrypt |
| `ContaService` | ✅ Completo | CRUD, débito/crédito, bloqueio de saldo negativo |
| `CartaoService` | ✅ Completo | CRUD, limite disponível dinâmico |
| `FaturaService` | ✅ Completo | Criação lazy, ghost closing, pagamento parcial |
| `CategoriaService` | ⏳ Pendente | Esqueleto com regras documentadas |
| `TransacaoService` | ⏳ Pendente | Esqueleto com regras documentadas — módulo mais complexo |
| `InvestimentoService` | ⏳ Pendente | Esqueleto com regras documentadas |
| `EmailVerificacaoService` | ⏳ Pendente | Esqueleto com regras documentadas |
| Testes | ⏳ Pendente | Apenas o teste de contexto padrão existe |
| Banco de dados | ⏳ Pendente | Driver e configuração não definidos ainda |
| JWT / Spring Security | ⏳ Pendente | Header temporário `UsuarioId` em uso |
| CORS | ⏳ Pendente | Não configurado |

---

## Próximos Passos

### Fase 5 — CategoriaService (mais simples, bom ponto de partida)
Implementar os 5 métodos do `CategoriaService`: criar com validação de duplicidade, listar, listar por tipo, deletar e o método interno `buscarPorIdOuFalhar` que será usado pelo `TransacaoService`.

### Fase 6 — TransacaoService (núcleo do sistema)
O módulo mais crítico. Requer que o `CategoriaService` e dois novos métodos no `CartaoService` (`consumirLimite` e `restaurarLimite`) sejam implementados primeiro. A lógica de atualização exige reverter o impacto financeiro antigo antes de aplicar o novo.

### Fase 7 — InvestimentoService
Implementar criação de metas, listagem e depósitos (que debitam de uma conta).

### Fase 8 — EmailVerificacaoService
Implementar geração de código OTP, validação e reenvio. Exigirá a adição do `spring-boot-starter-mail` ao `pom.xml` e configuração SMTP.

### Fase 9 — Banco de Dados
Escolher o banco (PostgreSQL recomendado), adicionar o driver ao `pom.xml`, configurar `application.properties` e o `compose.yaml` para desenvolvimento local.

### Fase 10 — JWT e Spring Security
Substituir o header `UsuarioId` por autenticação real com tokens JWT. Configurar filtros de segurança, CORS e proteger os endpoints.

### Fase 11 — Testes
Escrever testes unitários para os Services (JUnit 5 + Mockito) e testes de integração para os Controllers.

---

## Como Executar

> O projeto ainda não possui banco de dados configurado. Para rodar localmente, é necessário primeiro configurar um banco de dados e adicionar as credenciais no `application.properties`.

```bash
# Clonar o repositório
git clone <url-do-repositorio>

# Entrar na pasta do backend
cd backend

# Compilar
mvn compile

# Rodar
mvn spring-boot:run
```
