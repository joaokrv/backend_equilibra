# Guia de Referência da API e Arquitetura — Equilibra

Este documento é o seu **Mapa do Sistema**. Ele serve como uma referência rápida para entender onde encontrar cada funcionalidade, como os endpoints estão estruturados (estilo Swagger) e como os serviços se comunicam.

Se você se sentir perdido no código, consulte este guia.

---

## 🗺️ Mapa de Componentes (Onde cada coisa mora)

O backend do Equilibra é dividido em "domínios" (assuntos). Para cada domínio, existe um trio clássico: `Controller` (recebe a requisição) → `Service` (faz a regra de negócio) → `Repository` (fala com o banco).

Aqui estão os principais serviços e suas responsabilidades:

| Service                       | O que ele faz? | Onde está o segredo? |
|-------------------------------|----------------|----------------------|
| `UsuarioService`              | Autenticação, registro, hash de senhas (Argon2+Pepper), soft delete de usuário. | Arquivo mais denso de segurança. |
| `ContaService`                | CRUD de contas bancárias, validação de saldo insuficiente. | A lógica de saldo virtual vive aqui. |
| `CartaoService`               | CRUD de cartões de crédito. Calcula limite em tempo real sem salvar no banco. | Como o limite "fórum" é calculado na hora. |
| `FaturaService`               | Criar faturas "fantasmas", fechar faturas na data certa, processar pagamentos. | Lógica de datas (ghost closing) está aqui. |
| `TransacaoService`            | Gravar receitas/despesas, aplicar estornos, derivar o `StatusTransacao`. | Maior arquivo de CRUD básico. |
| `InvestimentoService`         | Manter metas de poupança, fazer saques e depósitos. | Lida com dinheiro "preso" em metas. |
| `MovimentacaoFinanceiraService`| **O Maestro.** Quando uma transação acontece, ele avisa a Conta ou o Cartão. | O fluxo do dinheiro passa obrigatoriamente por aqui. |
| `EmailVerificacaoService`     | Gerar OTP de 6 dígitos, expiração e validação de tentativas. | Lógica de `SecureRandom`, expiração e envio. |
| `FaturaSchedulerService`      | Um robô invisível que roda de madrugada para marcar faturas atrasadas. | Usa `@Scheduled(cron = ...)` do Spring. |

---

## 📡 Endpoints da API (Estilo Swagger)

Aqui está a lista detalhada de todas as "portas" de entrada do servidor. Todas as rotas (exceto `/api/auth/**`) precisam do cabeçalho: `Authorization: Bearer <seu-jwt-token>`.

### 🛡️ Autenticação e Registro (`UsuarioController`)

*   `POST /api/auth/pre-registrar`
    *   **Body:** `{ "nome", "email", "senha" }`
    *   **O que faz:** Cria pré-registro e envia OTP (se permitido), retornando `registroId`.
*   `POST /api/auth/login`
    *   **Body:** `{ "email", "senha" }`
    *   **O que faz:** Se verificado, retorna `{ accessToken, expiresIn }`. Se não verificado, retorna 403 `EMAIL_NAO_VERIFICADO` e dispara OTP (respeitando cooldown/bloqueio).
*   `POST /api/auth/verificar-email`
    *   **Body:** `{ "registroId", "codigo" }`
    *   **O que faz:** Valida o OTP e cria o usuário definitivo.
*   `POST /api/auth/reenviar-codigo`
    *   **Body:** `{ "registroId" }`
    *   **O que faz:** Reenvia OTP respeitando cooldown e lockout.
*   `GET /api/auth/otp-status?registroId=...`
    *   **O que faz:** Retorna status do OTP (ATIVO, EXPIRADO, BLOQUEADO, USADO) e datas.
*   `POST /api/auth/refresh`
    *   **Cookie:** `refreshToken=<rt>` (HttpOnly, enviado automaticamente pelo browser)
    *   **O que faz:** Lê o refresh token via cookie HttpOnly, rotaciona-o e devolve um novo access token. O refresh token **nunca** é enviado no body — apenas via cookie.

**Observação de segurança:** respostas do pré-registro são neutras para evitar enumeração de e-mail.

---

### 🏦 Contas Bancárias (`ContaController`)

*   `GET /api/contas`
    *   **O que faz:** Lista todas as suas contas que não sofreram Soft Delete. Traz o `saldoAtual`.
*   `POST /api/contas`
    *   **Body:** `{ "nome", "tipoDaConta", "saldoAtual" }`
    *   **O que faz:** Cria uma nova caixinha para o seu dinheiro.
*   `PUT /api/contas/{id}/saldo`
    *   **Body:** `{ "novoSaldo", "motivoAjuste" }`
    *   **O que faz:** Acerto manual se a planilha desincronizar com o banco real. (Isso gera uma transação de ajuste invisível).
*   `DELETE /api/contas/{id}`
    *   **O que faz:** Esconde a conta do sistema (`ativo = false`), mas mentém todo o histórico financeiro.

---

### 💳 Cartões de Crédito (`CartaoController`)

*   `GET /api/cartoes`
    *   **O que faz:** Traz os cartões. O pulo do gato: o backend faz uma conta matemática de padaria (`Limte Total - Gastos da Fatura em Aberto`) e te devolve o **Limite Disponível** mágico.
*   `POST /api/cartoes`
    *   **Body:** `{ "nome", "diaFechamento", "diaVencimento", "limiteTotal" }`
    *   **O que faz:** Cria o cartão. **🚨 Atenção:** Não cria faturas ainda. O Equilibra usa *Lazy Creation*.

---

### 🧾 Faturas (`FaturaController`)

*   `GET /api/faturas/cartao/{cartaoId}`
    *   **O que faz:** Traz todas as faturas do cartão. Antes de devolver, roda o *Ghost Closing* (olha pro relógio e verifica se alguma fatura "passou do dia" e precisa ser fechada).

*   **Paginação em transações:** use `GET /api/transacoes?page=0&size=10&sort=data,desc`. O endpoint mensal (`?ano=&mes=`) e o paginado não conflitam.
*   `POST /api/faturas/{faturaId}/pagar`
    *   **Body:** `{ "contaOrigemId", "valorPago" }`
    *   **O que faz:** A mágica suprema. Tira dinheiro da sua `ContaBancaria` (débito) e muda o status da Fatura para `PAGA` (ou `FECHADA` se você pagar só uma parte).

---

### 💸 Transações (`TransacaoController`)

*   `POST /api/transacoes`
    *   **Body:** `{ "descricao", "valor", "tipo" (RECEITA/DESPESA), "metodoPagamento", "categoriaId", "contaId" (ou "cartaoId") }`
    *   **O que faz:** Chama o Maestro (`MovimentacaoFinanceiraService`). Analisa se é PIX/Dinheiro (tira do saldo na hora) ou se é no Cartão (adiciona na fatura do mês atual).
*   `GET /api/transacoes?ano=X&mes=Y`
    *   **O que faz:** Traz seu extrato mensal organizado.
*   `PUT /api/transacoes/{id}`
    *   **O que faz:** Muito complexo! Ele "desfaz" financeiramente a transação antiga e "aplica" a transação nova para calcular a diferença correta.

---

### 📈 Investimentos / Metas (`InvestimentoController`)

*   `POST /api/investimentos`
    *   **Body:** `{ "nome", "descricao", "metaAlvo", "valorInicial" }`
    *   **O que faz:** Cria uma gavetinha para sua reserva de emergência.
*   `POST /api/investimentos/{id}/depositar` / `resgatar`
    *   **Body:** `{ "valor", "contaId" }`
    *   **O que faz:** Transfere dinheiro (e a responsabilidade do saldo) entre uma Conta e um Investimento.

---

## 🧠 Como Estudar Acidentes de Percurso

Se algo der errado (ex: usuário tentou gastar R$ 500 num cartão com limite de R$ 100):
1.  O Service lança uma exceção customizada (ex: `throw new LimiteInsuficienteException()`).
2.  A classe magrela de exceção apenas passa a mensagem para frente.
3.  O `GlobalExceptionHandler` escuta esse "grito" e a converte em um JSON HTTP 422 bonito de se ler, com `log.warn` no console para você rastrear.

**Dica Final:** Se o código parecer complexo demais, procure no Service quem ele está chamando. 90% da magia negra do Equilibra acontece dentro de `src/main/java.../service`.
