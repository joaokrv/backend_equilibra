# 🚀 Guia de Início Rápido — Equilibra Backend

Bem-vindo ao backend do **Equilibra**! Este guia vai te ajudar a configurar e rodar o projeto na sua máquina local em poucos minutos.

---

## 📋 Pré-requisitos

Antes de começar, certifique-se de que você tem as seguintes ferramentas instaladas:

| Ferramenta | Versão Mínima | Para quê? |
|---|---|---|
| **Java (JDK)** | 25 | Linguagem do projeto |
| **Maven** | 3.9+ | Gerenciador de build e dependências |
| **Docker Desktop** | 24+ | Subir o banco PostgreSQL local e rodar Testcontainers |
| **Git** | 2.40+ | Controle de versão |

> 💡 **Dica:** Se você usa Windows, recomendamos instalar o Java via [SDKMAN](https://sdkman.io/) ou [Adoptium Temurin](https://adoptium.net/). O Maven pode ser baixado em [maven.apache.org](https://maven.apache.org/download.cgi).

---

## 📥 1. Clone o Repositório

```bash
git clone <url-do-repositorio>
cd backend
```

---

## ⚙️ 2. Configure as Variáveis de Ambiente

O projeto **nunca armazena credenciais no código**. Todas as configurações sensíveis são lidas de variáveis de ambiente.

### Crie o arquivo `.env`

```bash
cp .env.example .env   # se existir o template
# ou crie manualmente:
touch .env
```

### Variáveis obrigatórias

Preencha o `.env` com os valores correspondentes ao seu ambiente:

| Variável | Descrição | Exemplo |
|---|---|---|
| `DB_URL` | URL JDBC do PostgreSQL | `jdbc:postgresql://localhost:5432/equilibra` |
| `DB_USERNAME` | Usuário do banco | `postgres` |
| `DB_PASSWORD` | Senha do banco | *(sua senha local)* |
| `JWT_SECRET` | Chave para assinar tokens JWT (mín. 256 bits) | Gere com `openssl rand -hex 32` |
| `AUTH_PEPPER` | Pepper para hash Argon2 das senhas | Gere com `openssl rand -hex 16` |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas pelo CORS | `http://localhost:5173` |

### Variáveis de e-mail (envio de código OTP)

| Variável | Descrição |
|---|---|
| `MAIL_HOST` | Servidor SMTP (ex: `smtp-relay.brevo.com`) |
| `MAIL_PORT` | Porta SMTP (ex: `587`) |
| `MAIL_USERNAME` | Usuário/email SMTP |
| `MAIL_PASSWORD` | Senha SMTP |
| `MAIL_FROM` | Remetente exibido nos e-mails (opcional, usa `MAIL_USERNAME` como fallback) |
| `MAIL_SMTP_ENABLED` | `true` para usar SMTP direto, `false` para usar API Brevo como fallback |

### Variáveis opcionais (integrações externas)

| Variável | Descrição |
|---|---|
| `BREVO_API_KEY` | Chave da API HTTP do Brevo (fallback quando SMTP falha) |
| `BREVO_API_FALLBACK_ENABLED` | Habilita fallback para API Brevo (`true` por padrão) |
| `HG_API_KEY` | Chave da API HG Brasil (indicadores econômicos) |
| `BRAPI_TOKEN` | Token da API Brapi (cotações) |

> ⚠️ **Importante:** O arquivo `.env` está no `.gitignore` e **nunca deve ser commitado**. Ele contém credenciais reais do seu ambiente.

---

## 🐘 3. Suba o Banco de Dados

O projeto usa **PostgreSQL 16**. A maneira mais simples é via Docker:

```bash
docker run -d \
  --name equilibra-db \
  -e POSTGRES_DB=equilibra \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=sua-senha-local \
  -p 127.0.0.1:5432:5432 \
  postgres:16
```

> 🔒 O bind em `127.0.0.1` garante que o banco só aceita conexões locais — nunca expondo para a rede externa.

Após subir o container, as **migrations do Flyway** serão aplicadas automaticamente na primeira execução da aplicação.

---

## ▶️ 4. Rode a Aplicação

```bash
# Com Maven Wrapper (recomendado)
./mvnw spring-boot:run

# Ou com Maven global
mvn spring-boot:run
```

Se tudo estiver configurado corretamente, você verá no console:

```
Started BackendApplication in X.XXX seconds
```

### Endpoints disponíveis após iniciar

| URL | Descrição |
|---|---|
| `http://localhost:8080` | API REST |
| `http://localhost:8080/swagger-ui.html` | 📖 Documentação interativa (Swagger UI) |
| `http://localhost:8080/v3/api-docs` | Especificação OpenAPI em JSON |
| `http://localhost:8080/actuator/health` | Health Check da aplicação |

---

## 🧪 5. Rode os Testes

O projeto possui testes **unitários** (Mockito) e de **integração** (Testcontainers com PostgreSQL real).

```bash
# Compilar + rodar TODOS os testes (requer Docker rodando)
./mvnw clean verify

# Apenas testes unitários (sem Docker)
./mvnw test -Dtest="org.app_financeiro.backend.service.*Test,\
org.app_financeiro.backend.config.*Test,\
org.app_financeiro.backend.exception.*Test"

# Apenas testes de integração (requer Docker)
./mvnw test -Dtest="org.app_financeiro.backend.*IntegrationTest"
```

> 💡 O **Testcontainers** sobe automaticamente um PostgreSQL 16 em container Docker para os testes de integração. Não é necessário configurar banco separadamente — basta ter o Docker Desktop rodando.

---

## 🏗️ 6. Perfis Spring

| Perfil | Quando usar | Detalhes |
|---|---|---|
| `default` | Desenvolvimento local | Lê `.env`, Swagger habilitado, logs detalhados |
| `test` | Testes automatizados | CORS aberto, H2 in-memory para testes leves, rate limit desabilitado |
| `prod` | Produção (Railway) | Variáveis de ambiente obrigatórias, Swagger controlado |

---

## 🔧 Solução de Problemas Comuns

### "Connection refused" ao iniciar

- Verifique se o Docker está rodando: `docker ps`
- Verifique se o container do banco está ativo: `docker start equilibra-db`
- Confirme que a URL no `.env` aponta para `localhost:5432`

### "JWT_SECRET must be at least 256 bits"

- Gere uma chave suficientemente longa: `openssl rand -hex 32`
- Cole o resultado na variável `JWT_SECRET` do seu `.env`

### Testes de integração falhando

- Certifique-se de que o **Docker Desktop** está rodando (Testcontainers precisa dele)
- No Windows, verifique se o WSL2 está habilitado nas configurações do Docker

### Flyway migration error

- Nunca altere migrations já aplicadas. Crie uma nova versão (ex: `V23__descricao.sql`)
- Se necessário resetar o banco local: `docker rm -f equilibra-db` e recrie-o

---

## 📚 Links Úteis

| Recurso | URL |
|---|---|
| Spring Boot 3.5 Reference | [docs.spring.io](https://docs.spring.io/spring-boot/3.5.11/reference/) |
| Spring Data JPA | [docs.spring.io/data](https://docs.spring.io/spring-boot/3.5.11/reference/data/sql.html#data.sql.jpa-and-spring-data) |
| Springdoc OpenAPI | [springdoc.org](https://springdoc.org/) |
| Testcontainers | [testcontainers.com](https://testcontainers.com/) |
| Flyway | [documentation.red-gate.com](https://documentation.red-gate.com/flyway) |
| Argon2 (Password Hashing) | [OWASP Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) |
