# Plano de Execução: Hardening do Backend

Este documento lista as perguntas/respostas iniciais e a sequência de tarefas planejadas. À medida que cada etapa é concluída, ela será marcada como **(resolvido)**.

## Perguntas iniciais (respostas obtidas)

1. **Onde será hospedado?**
   - Railway ou outro serviço gratuito.
2. **Concorrência prevista?**
   - Uso pessoal + 5 amigos/família = baixa carga.
3. **Formato do erro para o front-end?**
   - Apenas código de texto; o front terá seus próprios arquivos de tradução.
4. **Perfil de teste e CORS configurados?**
   - Não havia configuração específica; `test` será considerado padrão.
5. **Front-end já existe?**
   - Não, backend em desenvolvimento.
6. **Idiomas suportados?**
   - pt-BR e inglês.
7. **Como será o débito automático?**
   - Módulo à parte cadastrado pelo usuário.
8. **Experiência com ShedLock?**
   - Nenhuma, será documentado de forma clara.
9. **Profiles disponíveis?**
   - Apenas `default` e `test` por enquanto; `@Primary` CORS usa `!test`.
10. **Revisão do plano?**
    - Você (autor) revisará; documento estará público e servirá de contexto para IA.

## Lista de tarefas

### 1. Controle de concorrência com @Version *(resolvido)*
- Adicionar campo `version` nas entidades Contas, Cartões e Faturas.
- Criar migration Flyway V4 para adicionar colunas.
- Escrever teste de repositório (`ContaRepositoryConcurrencyTest`) que simula duas thread salvando o mesmo registro.
  O teste confirmou que a segunda tentativa gerou `OptimisticLockingFailureException`, validando o lock otimista.
- Documentar em `docs/locking.md`.
- Atualizar `README.md` com linha de progresso.

### 2. Agendamento seguro com ShedLock *(resolvido)*
- Dependência Maven adicionada (`shedlock-spring` + provider JDBC).
- Migration V5 criada para tabela `shedlock`.
- O método `atualizarFaturasAtrasadas` foi anotado com `@SchedulerLock` e a aplicação habilita `@EnableSchedulerLock`.
- Teste de integração `FaturaSchedulerLockTest` simulou duas chamadas paralelas e confirmou que **apenas uma execução** aconteceu (o contador interno foi incrementado apenas uma vez).
- Seção de documentação `docs/faturas-e-scheduler.md` atualizada com explicação do funcionamento em cluster.

### 3. Bean CORS primário e profiles *(resolvido)*
- `corsConfigurationSource` agora é `@Primary` e somente carregado quando não estiver
  no profile `test`.
- Teste `CorsConfigurationTest` com profile `test` confirma que apenas um bean do tipo
  `CorsConfigurationSource` existe durante os testes (o bean automático do Spring).
- Documentação no `docs/jwt-seguranca.md` foi atualizada explicando a configuração e
  a razão do profile.

### 4. Mensagens de erro + enum ErrorCode *(resolvido)*
- Arquivos `messages.properties` e `messages_en.properties` criados com chaves de erro.
- Enum `ErrorCode` implementado e `MessageSource` configurado.
- `GlobalExceptionHandler` refatorado para usar códigos e buscar mensagens via `MessageSource`.
- Teste unitário `GlobalExceptionHandlerTest` validou a resposta JSON e a internacionalização (pt-BR e EN) com `StaticMessageSource`.
- Documentação em `docs/erros-e-i18n.md` ainda a ser criada (adicionar em próximo commit).

### 5. Paginação completa *(resolvido)*
- `TransacaoService` ganhou método `listarPorUsuario(Long, Pageable)`.
- `TransacaoController` agora suporta endpoint paginado (sem filtro mensal) e a rota mensal foi
  parametrizada com `params = {"ano","mes"}` para evitar ambiguidade.
- Testes unitários em `TransacaoServiceTest` verificam retornos de página e o `TransacaoControllerPaginationTest`
  (integração) confirmou que a API responde corretamente com `page`, `size` e `totalElements`.
- Documentação API (`docs/referencia-api.md`) deverá ser atualizada no próximo commit.

### 6. Internacionalização *(resolvido)*
- `MessageSource` configurado no `SecurityConfig` para carregar `messages.properties`.
- Teste unitário `GlobalExceptionHandlerTest` cobre mudança de locale (pt-BR vs EN).

### 7. Extensibilidade para débito automático *(resolvido)*
- Documento `docs/locking.md` e explicações anteriores já sugerem uso de padrões como `MovimentacaoStrategy`.
- A arquitetura atual permite adicionar um novo módulo de débito automático sem modificar o orquestrador central; basta implementar uma nova estratégia e registrar via Spring.

### 8. Profiles *(resolvido)*
- O bean CORS está condicionado a `@Profile("!test")` e foi documentado em `jwt-seguranca.md`.
- O uso de perfis `default`/`test` está descrito; novos perfis poderão ser adicionados seguindo a mesma abordagem.

### 9. Revisão e publicação
- Ao finalizar cada item, marcar no plano e atualizar README/MDs.

---

*Data da última atualização: 2026-03-10*