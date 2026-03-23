# 📄 Documento de Design e Planejamento: Próximos Passos — Frontend, CI/CD e Deploy

**Data:** 23 de Março de 2026
**Status:** 🟡 Proposto / Em Discussão

## 1. 🎯 Visão Geral (Para Negócios / Não-Técnico)
**O que estamos fazendo e por quê?**
O "cérebro" do sistema (Backend) está pronto. Nosso objetivo agora é torná-lo útil e acessível. Primeiro, vamos automatizar a verificação de qualidade da API para que nenhum código quebrado entre no repositório. Em seguida, vamos definir o terreno na infraestrutura web gratuita para que o sistema funcione na internet. Por fim, vamos focar no desenvolvimento das telas (Frontend) para que você e sua família consigam gerenciar o controle financeiro diretamente do celular ou navegador localmente, evoluindo depois para a nuvem. Essa abordagem passo a passo garante aprendizado sólido e entrega de valor sem afobação.

## 2. ⚙️ Solução Técnica (Para Engenharia)
**Como vamos resolver o problema?**
Dividiremos o problema em 3 etapas de engenharia:
1.  **CI/CD Pipeline (GitHub Actions):** Vamos criar um fluxo simples `.github/workflows/ci.yml`. Sempre que houver um push, o GitHub vai baixar o Java 25, compilar o projeto e rodar todos os testes unitários. *Opcional:* podemos rodar os testes de integração provendo um serviço de banco de dados diretamente na Action.
2.  **Arquitetura de Hospedagem (Tier Gratuito Estável):** 
    *   **Banco de Dados (PostgreSQL):** Recomendarei o **Neon.tech** ou **Supabase**. O Render expira o banco gratuito em 90 dias, e o Railway cortou projetos gratuitos permanentes. O Neon é focado no plano gratuito (Serverless Postgres) e não deleta seu banco.
    *   **Backend (Java/Spring):** Recomendarei o **Render**. É fácil de usar, gratuito para Web Services, e suporta Docker. O único contraponto é que ele "dorme" (spin-down) após 15 minutos sem uso, levando cerca de 50 segundos para acordar na primeira requisição do dia. Mas para uso pessoal, é a melhor opção livre de custos.
3.  **Desenvolvimento do Frontend:** A API foi desenhada de forma "Stateless" (independente). Qualquer cliente (Web ou Mobile) com capacidade de salvar o token JWT e enviar requisições HTTP servirá. Decidiremos a stack (ex: React, Next.js, ou Vue) com foco em produtividade.

## 3. 🗺️ Plano de Execução (Passo a Passo)
**A ordem exata de implementação:**

*   **Fase 1: Configuração do CI (Continuous Integration)**
    *   Passo 1.1: Criar arquivo `.github/workflows/maven.yml` no repositório. -> *Por que:* Para aprender a conceituar pipelines e barrar código quebrado de subir para a branch principal (rodando `mvn test` automaticamente).
*   **Fase 2: Estrutura do Frontend (Local-First)**
    *   Passo 2.1: Definir a tecnologia da interface (Ex: React + Vite ou HTML/JS puro). -> *Por que:* Alinhamento das ferramentas que você possui mais afinidade ou quer mais aprender.
    *   Passo 2.2: Criar repositório separado ("equilibra-frontend") e construir a tela de Login consumindo o backend em `localhost:8080`. -> *Por que:* Validar a comunicação de CORS, integração do token JWT e injeção do cabeçalho `Authorization` antes de focar em telas complexas.
*   **Fase 3: Provisionamento da Infraestrutura Nuvem (Deploy API)**
    *   Passo 3.1: Criar conta e instanciar um banco PostgreSQL no Neon.tech. -> *Por que:* Banco de dados serverless gratuito, estável, e sem validade de 90 dias.
    *   Passo 3.2: Exportar variáveis de ambiente (Database URL, JWT Secret, Pepper) no Dashboard do Render e fazer o link com o repositório do backend. -> *Por que:* Para habilitar o CD (Continuous Deployment), em que o Render subirá a aplicação Java automaticamente da branch main.
*   **Fase 4: Consumo em Nuvem**
    *   Passo 4.1: Alterar a URL Base da API no código do Frontend para apontar para a URL do Render em vez de `localhost`. -> *Por que:* O software ganha vida e fica acessível de qualquer dispositivo.

## 4. 🛡️ Estratégia de Testes (TDD)
**Como garantimos que funciona e não quebra no futuro?**
*   **Cenários de Sucesso (Happy Path):** Ao realizar um Push no GitHub, o selo (badge) verde de *✅ build passing* deve aparecer no README, significando que o CI rodou e todos os 33 arquivos de testes do Spring passaram. O Frontend, via localhost, consegue realizar o login devolvendo o token.
*   **Casos Extremos (Edge Cases):** Subir um código e o Docker do Testcontainers flertar com a Action do Github gerando *Connection Refused*. Validaremos a estabilidade tirando a dependência do testcontainers no CI se necessário, provisionando um Postgres nativo via *Service Containers* nas Actions.
*   **Testes de Integração:** O deploy da API no Render precisará se conectar no Postgres do Neon com sucesso. Para testar, faremos requisição no Swagger pelo IP do Render, criando uma conta para garantir a vida real da pipeline (Health Check).

## 5. ⚠️ Riscos e Mitigações
**O que pode dar errado?**
*   **Excesso de Consumo de RAM no Render:** Plataformas gratuitas dão apenas 512MB de RAM. Spring Boot pode chegar nesse limite e ser "morto" (OOMKilled) se a aplicação inflar. *Mitigação:* Ajustar o build do Docker com `JAVA_OPTS="-Xmx256m"` para evitar esgotamento de memória e garantir deploy estável.
*   **Dificuldade Inicial na Pipeline CI:** O CI dar erro constantemente por falta de configurações de porta entre o workflow do GitHub e o backend. *Mitigação:* Iniciaremos apenas rodando os `unit tests` isolados no CI, incrementando a complexidade conforme o aprendizado for internalizado.
*   **CORS Bloqueando Requisições:** Quando subir o backend para o Render e rodar o front localmente, os blocos de origem cruzarão políticas do browser. *Mitigação:* Usar os parâmetros `CORS_ALLOWED_ORIGINS` expostos no `application.properties` que deixamos prontas anteriormente.
