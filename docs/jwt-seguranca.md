# JWT e Segurança — Autenticação Stateless

## O que é JWT?

### Explicação não técnica

Imagine que você vai a um festival de música. Na entrada, você mostra seu ingresso e recebe uma **pulseira**. A partir dali, você não precisa mais mostrar o ingresso — basta mostrar a pulseira para acessar qualquer palco.

JWT funciona da mesma forma:
- **Ingresso** = seu e-mail e senha (usados uma vez no login)
- **Pulseira** = o token JWT (enviado em toda requisição)
- **Segurança do festival** = o `JwtAuthenticationFilter` (verifica se a pulseira é válida)

Se a pulseira expirar (final do dia), você pode ir ao balcão de renovação (endpoint `/api/auth/refresh`) e trocar por uma nova, Esta arquitetura foi desenhada para o **Equilibra**, focando em três pilares: Autenticação Stateless (JWT), Hash Robusto de Senhas (Argon2 + Pepper) e Segurança de Transporte (TLS).

## 1. Hash de Senhas: Argon2 + Pepper

Ao contrário de sistemas comuns que usam apenas BCrypt ou SHA, o Equilibra utiliza o **Argon2**, o algoritmo vencedor do *Password Hashing Competition*, combinado com um **Pepper** (Pimenta).

### Por que Argon2?
O Argon2 é resistente a ataques de força bruta realizados por hardware especializado (como GPUs ou ASICs). Ele exige uma quantidade específica de memória e paralelismo, o que torna extremamente caro para um hacker tentar "quebrar" milhões de senhas por segundo.

### O que é o Pepper?
O Pepper é uma chave secreta de 256 bits (armazenada na variável de ambiente `AUTH_PEPPER`) que é misturada à senha do usuário **antes** de gerar o hash.

*   **Diferença do Salt:** O Salt é único por usuário e fica no banco de dados. O Pepper é único para a aplicação e **NUNCA** fica no banco de dados.
*   **Vantagem:** Se o seu banco de dados vazar, o atacante verá os hashes, mas ele não tem o Pepper. Sem o Pepper, os hashes Argon2 são inúteis, pois ele não sabe a string real que foi criptografada.

### PepperedPasswordEncoder — Decorator Pattern

O pepper é aplicado de forma **transparente** pelo `PepperedPasswordEncoder`, um decorator que envolve o `Argon2PasswordEncoder`. O `UsuarioService` não precisa saber da existência do pepper — ele simplesmente chama `passwordEncoder.encode(senha)`.

```java
// ApplicationConfig — Bean de configuração
@Bean
public PasswordEncoder passwordEncoder() {
    return new PepperedPasswordEncoder(
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
            pepper  // injetado via @Value("${auth.pepper}")
    );
}

// PepperedPasswordEncoder — Decorator
public String encode(CharSequence rawPassword) {
    return delegate.encode(pepper + rawPassword); // pepper é prepend transparente
}

// UsuarioService — NÃO precisa saber do pepper
String hash = passwordEncoder.encode(dto.senha()); // simples e limpo
```

> **Importante:** O `UsuarioService` chama `encode(senha)` diretamente. O pepper é adicionado internamente pelo decorator, seguindo o princípio Open/Closed (SOLID).

## 2. JWT (JSON Web Token) — Fluxo de Acesso
### Explicação técnica

JWT (JSON Web Token) é um padrão aberto ([RFC 7519](https://datatracker.ietf.org/doc/html/rfc7519)) para transmitir informações de forma compacta e segura entre partes como um objeto JSON assinado digitalmente. O token é composto por 3 partes separadas por pontos:

```
HEADER.PAYLOAD.SIGNATURE
eyJhbGciOiJIUzI1NiJ9.eyJ1c3VhcmlvSWQiOjEsInN1YiI6InVzZXJAZW1haWwuY29tIiwiZXhwIjoxNjk5...}.HMAC_SHA256
```

- **Header**: algoritmo de assinatura (HS256)
- **Payload**: dados do usuário (e-mail, usuarioId, expiração)
- **Signature**: hash HMAC-SHA256 que garante que o token não foi alterado

---

## Fluxo Completo de Autenticação

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FLUXO DE AUTENTICAÇÃO                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  1. REGISTRO                                                          │
│     POST /api/auth/registrar  { nome, email, senha }                  │
│     → Cria conta com emailVerificado = false                          │
│     → Gera código OTP de 6 dígitos                                   │
│                                                                       │
│  2. VERIFICAÇÃO DE E-MAIL                                             │
│     POST /api/auth/verificar-email  { email, codigo: "048372" }       │
│     → Se válido: emailVerificado = true                               │
│                                                                       │
│  3. LOGIN                                                             │
│     POST /api/auth/login  { email, senha }                            │
│     → Valida credenciais                                              │
│     → Verifica emailVerificado == true                                │
│     → Retorna: { accessToken, expiresIn }  (refreshToken = null)      │
│     → refreshToken enviado apenas via cookie HttpOnly "refreshToken"  │
│                                                                       │
│  4. REQUISIÇÕES PROTEGIDAS                                            │
│     GET /api/contas  (Header: Authorization: Bearer <accessToken>)    │
│     → JwtAuthenticationFilter extrai e valida o token                 │
│     → Controller recebe @AuthenticationPrincipal UsuarioEntity        │
│                                                                       │
│  5. RENOVAÇÃO                                                         │
│     POST /api/auth/refresh  (Cookie: refreshToken=<rt>)               │
│     → Lê refresh token via @CookieValue — não aceita no body          │
│     → Rotaciona: invalida RT antigo, emite novo RT via cookie HttpOnly │
│     → Retorna novo { accessToken, expiresIn }                         │
│                                                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Componentes do Sistema

### 1. JwtService — Geração e Validação de Tokens

Código real do projeto:

```java
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;       // 1 hora (3600000 ms)

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;      // 7 dias

    /**
     * Gera um Access Token (curta duração) para o usuário autenticado.
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (userDetails instanceof UsuarioEntity usuario) {
            extraClaims.put("usuarioId", usuario.getId());
        }
        return buildToken(extraClaims, userDetails, accessTokenExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims,
                              UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equalsIgnoreCase(userDetails.getUsername()))
                && !isTokenExpired(token);
    }
}
```

**Pontos importantes:**
- O `usuarioId` é incluído como claim extra — isso permite que qualquer parte do sistema extraia o ID do usuário direto do token, sem consultar o banco.
- A chave secreta é injetada via `@Value`, nunca hardcoded no código.
- O mesmo `buildToken()` serve para access e refresh — só muda a duração.

### 2. JwtAuthenticationFilter — Interceptação de Requisições

Código real do projeto:

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Se não tem header ou não começa com "Bearer ", pula o filtro
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extrai o token e o e-mail do usuário
        final String jwt = authHeader.substring(7);
        final String userEmail = jwtService.extractUsername(jwt);

        // Se tem e-mail e não está autenticado ainda
        if (userEmail != null &&
            SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            if (jwtService.isTokenValid(jwt, userDetails)) {
                // Seta a autenticação no contexto do Spring Security
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                    );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

**Como funciona na prática:**
1. Toda requisição HTTP passa por este filtro
2. Ele verifica se existe o header `Authorization: Bearer <token>`
3. Se existe, extrai o token, valida assinatura e expiração
4. Se válido, carrega o `UsuarioEntity` do banco e seta no `SecurityContext`
5. A partir daí, qualquer Controller pode acessar via `@AuthenticationPrincipal`

### 3. SecurityConfig — Rotas Públicas e Protegidas

Código real do projeto:

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    private static final String[] WHITE_LIST_URL = {
        "/api/auth/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(req ->
                req.requestMatchers(WHITE_LIST_URL)
                    .permitAll()
                    .requestMatchers("/actuator/**").hasRole("ADMIN")  // ← protegido
                    .anyRequest()
                    .authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

**Decisões de design:**
- **CSRF desabilitado**: API stateless não usa cookies, então CSRF não se aplica.
- **SessionCreationPolicy.STATELESS**: o Spring não cria sessão HTTP — toda autenticação vem do token.
- **`addFilterBefore`**: o `JwtAuthenticationFilter` executa ANTES do filtro padrão do Spring, interceptando o token antes de qualquer verificação de credenciais.
- **`addHeaderWriter` (HSTS)**: Garante transporte estrito via HTTPS mitigando vetores de ataque MITM e protocol downgrading.

> **Obs. CORS nos testes**
>
> O bean `corsConfigurationSource` é marcado como `@Primary` e somente carrega em perfis diferentes de `test`.
> Isso evita o erro de bean duplicado que aparecia ao rodar os testes de integração, garantindo que
> os testes usem a configuração automática do Spring não customizada.
> Veja `CorsConfigurationTest` para um exemplo de verificação.
---

## Migração: Header UsuarioId → @AuthenticationPrincipal

### Antes (código real removido)

```java
// Todos os controllers recebiam o ID do usuário via header manual
@PostMapping
public ResponseEntity<ContaResponseDTO> criarConta(
        @Valid @RequestBody ContaRegistroRequestDTO dto,
        @RequestHeader("UsuarioId") Long usuarioId) {  // ← Inseguro!
    ContaResponseDTO conta = contaService.criarConta(dto, usuarioId);
    return ResponseEntity.status(HttpStatus.CREATED).body(conta);
}
```

**Problema**: qualquer pessoa poderia enviar `UsuarioId: 999` no header e se passar por outro usuário.

### Depois (código real atual)

```java
@PostMapping
public ResponseEntity<ContaResponseDTO> criarConta(
        @Valid @RequestBody ContaRegistroRequestDTO dto,
        @AuthenticationPrincipal UsuarioEntity usuario) {
    ContaResponseDTO conta = contaService.criarConta(dto, usuario.getId());
    return ResponseEntity.status(HttpStatus.CREATED).body(conta);
}
```

**Seguro**: o `@AuthenticationPrincipal` extrai o usuário do `SecurityContext`, que foi setado pelo `JwtAuthenticationFilter` a partir do token JWT validado. É impossível falsificar.

---

## Configuração

### application.properties

```properties
# Chave secreta para assinar os tokens (Base64 de 256 bits)
jwt.secret=SuaChaveSecretaBase64Aqui

# Access token expira em 1 hora (em milissegundos)
jwt.access-token-expiration=3600000

# Refresh token expira em 7 dias (em milissegundos)
jwt.refresh-token-expiration=604800000
```

### Dependências no pom.xml

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

---

## Fontes

- [RFC 7519 — JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- [Spring Security — Reference Documentation](https://docs.spring.io/spring-security/reference/)
- [jjwt — GitHub Repository (Java JWT)](https://github.com/jwtk/jjwt)
- [Baeldung — Spring Security with JWT](https://www.baeldung.com/spring-security-oauth-jwt)
- [Spring Security — OncePerRequestFilter](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/web/filter/OncePerRequestFilter.html)
