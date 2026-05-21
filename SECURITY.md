# Política de Segurança

## Versões Suportadas

Apenas a branch `main` recebe correções de segurança. Fork mantido por terceiros é responsabilidade do mantenedor do fork.

## Reportando uma Vulnerabilidade

Se você descobrir uma vulnerabilidade de segurança no Equilibra, **NÃO abra uma issue pública**. Use um dos canais privados abaixo:

1. **E-mail:** `joaovictooroc@gmail.com`
2. **LinkedIn:** [linkedin.com/in/joaokrv](https://www.linkedin.com/in/joaokrv)
3. **Instagram:** [@joaokrv](https://www.instagram.com/joaokrv)

### Informações úteis ao reportar

- Descrição do problema e impacto potencial
- Passos para reproduzir (idealmente com PoC mínimo)
- Versão/commit afetado
- Mitigações temporárias, se conhecer alguma

### Compromissos do mantenedor

- **Confirmação de recebimento:** até 72 horas
- **Avaliação inicial e severidade (CVSS):** até 7 dias
- **Correção e disclosure coordenado:** prazo combinado conforme severidade

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

## Rate Limiting

Buckets in-memory por IP via Bucket4j, configurados em `RateLimitInterceptor` e registrados em `WebMvcConfig`.

### Escopos e limites

| Escopo   | Endpoints                                          | Limite                          |
|----------|----------------------------------------------------|---------------------------------|
| `auth`   | `/api/auth/**`                                     | 3 req / minuto                  |
| `relatorio` | `/api/**/relatorios/exportar`                   | 2 req / minuto                  |
| `health` | `/actuator/health`                                 | 10 req / minuto                 |
| `geral`  | demais rotas registradas (mercado, perfil, etc.)   | 5 / min · 15 / hora · 30 / dia  |

Resposta de bloqueio: **HTTP 429** com `ErroResponseDTO` (`code=RATE_LIMIT_EXCEEDED`) + header `Retry-After` (segundos).

### Kill switch e variáveis de ambiente

| Variável                                       | Default | Função                                                          |
|------------------------------------------------|---------|-----------------------------------------------------------------|
| `SECURITY_RATE_LIMIT_ENABLED`                  | `true`  | Liga/desliga sem deploy de código (reinicia Render via env var) |
| `SECURITY_RATE_LIMIT_MAX_BUCKETS`              | `10000` | Limite global do cache de buckets em memória                    |
| `SECURITY_RATE_LIMIT_BUCKET_IDLE_TTL_MINUTES`  | `120`   | Expira bucket inativo, reduz risco de DoS por memória           |
| `SECURITY_TRUST_FORWARDED_FOR`                 | `false` | Habilita leitura de `X-Forwarded-For` (requer proxy confiável)  |

### Política de spoofing de IP

`X-Forwarded-For` só é lido quando **ambos**:
1. `SECURITY_TRUST_FORWARDED_FOR=true`
2. `request.getRemoteAddr()` ∈ faixa privada (10/, 172.16-31/, 192.168/, fc/fd, localhost) — proxies internos confiáveis.

Em ambientes Render/Railway atrás de proxy: habilitar `SECURITY_TRUST_FORWARDED_FOR=true` para que o limite não bloqueie pelo IP do edge.

### Observabilidade

- Counter Micrometer: `equilibra.ratelimit.blocked{escopo}` — total de bloqueios por escopo
- Log WARN por bloqueio: escopo, URI, IP mascarado, `retryAfterSeconds`
- Métricas expostas em `/actuator/metrics` (acesso restrito a `ROLE_ADMIN`)

### Testes

`security.rate-limit.enabled=false` em `application-test.properties` por padrão.
`RateLimitIntegrationTest` habilita explicitamente via `@TestPropertySource` para validar o fluxo 429 fim-a-fim.
