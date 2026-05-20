# Política de Segurança

## Versões Suportadas

Apenas a branch `main` recebe correções de segurança. Fork mantido por terceiros é responsabilidade do mantenedor do fork.

## Reportando uma Vulnerabilidade

Se você descobrir uma vulnerabilidade de segurança no Equilibra, **NÃO abra uma issue pública**. Use um dos canais privados abaixo:

1. **GitHub Security Advisory** (preferencial): [Reportar uma vulnerabilidade](https://www.linkedin.com/in/joaokrv)
2. **E-mail direto:** `joaovictooroc@gmail.com`

### Informações úteis ao reportar

- Descrição do problema e impacto potencial
- Passos para reproduzir (idealmente com PoC mínimo)
- Versão/commit afetado
- Mitigações temporárias, se conhecer alguma

### Compromissos do mantenedor

- **Confirmação de recebimento:** até 72 horas
- **Avaliação inicial e severidade (CVSS):** até 7 dias
- **Correção e disclosure coordenado:** prazo combinado conforme severidade
- **Crédito público** ao reporter no advisory (se desejado)

### Escopo

**Dentro do escopo:**
- Vulnerabilidades em código deste repositório
- Configurações inseguras default (Spring Security, CORS, JWT, rate-limit)
- Vazamentos de PII via logs ou respostas de API
- Falhas de autenticação, autorização (IDOR) ou validação de input

**Fora do escopo:**
- Engenharia social / phishing contra o mantenedor
- Ataques de negação de serviço (DoS) que dependem de volume bruto
- Vulnerabilidades em dependências já reportadas no NVD sem PoC adicional
- Issues que exigem acesso físico ao servidor

## Práticas de Segurança Aplicadas

- JWT HS256 com revogação via `chaveSessao` (logout invalida tokens)
- Argon2 com pepper (via env var) para hash de senha
- Rate-limit Bucket4j com buckets dedicados por categoria
- IDOR bloqueado: todos os recursos filtrados por `usuario.getId()` no service layer
- OTP com `MessageDigest.isEqual` (constant-time) + lockout após 5 tentativas
- Logs com mascaramento de e-mail (LGPD)
- CORS estrito com Assert contra `*` quando `allowCredentials=true`
- Swagger desabilitado em produção (`SPRINGDOC_ENABLED=false` default)
- Refresh token em cookie httpOnly + SameSite + Secure
- Cookie CSRF Origin Interceptor em endpoints baseados em cookie
