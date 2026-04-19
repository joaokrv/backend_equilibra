# Erros e Internacionalização (i18n)

## Padrão de Resposta de Erro

Todas as exceções são tratadas pelo `GlobalExceptionHandler` e retornam um JSON padronizado:

```json
{
  "timestamp": "2026-03-12T14:30:00",
  "status": 422,
  "erro": "Erro de regra de negócio",
  "mensagem": "O saldo não pode ser negativo",
  "errorCode": "REGRA_DE_NEGOCIO"
}
```

## Error Codes

| ErrorCode | HTTP Status | Descrição |
|---|---|---|
| `SALDO_INSUFICIENTE` | 422 | Saldo da conta insuficiente |
| `LIMITE_INSUFICIENTE` | 422 | Limite do cartão insuficiente |
| `OPERACAO_NAO_PERMITIDA` | 422 | Regra de negócio bloqueou operação |
| `EMAIL_NAO_VERIFICADO` | 403 | Login sem verificação de e-mail |
| `EMAIL_JA_CADASTRADO` | 409 | E-mail duplicado |
| `REGISTRO_NAO_ENCONTRADO` | 404 | Recurso não encontrado |
| `CREDENCIAIS_INVALIDAS` | 401 | E-mail ou senha incorretos |
| `REGRA_DE_NEGOCIO` | 422 | Violação genérica de regra |
| `CONTA_JA_ATIVA` | 422 | Reativar conta já ativa |
| `METHOD_NOT_ALLOWED` | 405 | Verbo HTTP incorreto p/ rota (Hardening Security) |

## i18n — Como funciona

O sistema usa `MessageSource` do Spring com arquivos `.properties`:

| Arquivo | Idioma |
|---|---|
| `messages.properties` | pt-BR (padrão) |
| `messages_en.properties` | Inglês |

1. Cliente envia `Accept-Language: en` no header
2. Spring resolve via `LocaleContextHolder`
3. `GlobalExceptionHandler` usa `messageSource.getMessage()` para o idioma correto
4. Fallback: pt-BR (configurado via `ms.setDefaultLocale(new Locale("pt", "BR"))` no `ApplicationConfig`)

### Adicionando idioma

Crie `messages_XX.properties`, copie as chaves e traduza. O Spring detecta automaticamente.

### Chaves

```properties
# Regras de negócio
error.saldo_insuficiente=...
error.limite_insuficiente=...
error.operacao_nao_permitida=...
error.email_nao_verificado=...
error.email_ja_cadastrado=...
error.registro_nao_encontrado=...
error.credenciais_invalidas=...
error.regra_de_negocio=...
error.conta_ja_ativa=...

# Segurança / Autenticação
error.bad_credentials=...
error.conta_desativada=...
error.autenticacao_generica=...
```