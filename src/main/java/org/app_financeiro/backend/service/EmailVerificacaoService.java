package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ReenviarCodigoRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CodigoVerificacaoInvalidoException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Serviço responsável pelo fluxo de verificação de e-mail via código OTP.
 *
 * FLUXO COMPLETO:
 * 1. Usuário se registra via UsuarioService.registrarUsuario()
 * 2. O controller chama gerarCodigo(email) — cria um OTP de 6 dígitos com validade de 15 minutos
 * 3. (Futuro) O código é enviado por e-mail via JavaMailSender
 * 4. Usuário envia email + código via verificarEmail() — se válido, marca emailVerificado = true
 * 5. Se o código expirou ou foi perdido, o usuário pode chamar reenviarCodigo()
 *
 * DEPENDÊNCIAS:
 * - CodigoVerificacaoRepository: persistência dos códigos OTP
 * - UsuarioRepository: para buscar e atualizar o flag emailVerificado
 *
 * DECISÕES TÉCNICAS:
 * - O vínculo entre código e usuário é feito pelo campo "email" (String), não por @ManyToOne.
 *   Isso simplifica o fluxo pré-login (o usuário ainda não está autenticado quando verifica).
 * - Códigos antigos NÃO são deletados — são apenas marcados como utilizado = true.
 *   Isso preserva o histórico e é consistente com o padrão de soft delete do projeto.
 * - O envio real de e-mail será adicionado futuramente (spring-boot-starter-mail + SMTP).
 *   (INSTRUÇÃO DE IMPLEMENTAÇÃO FUTURA: Injetar JavaMailSender, configurar propriedades de SMTP no
 *   application.yml (host, port, username, password) e construir a lógica de envio de mensagem
 *   MimeMessage para template HTML contendo o OTP).
 *   Por enquanto, o código é retornado pelo gerarCodigo() para facilitar testes.
 */
@Service
public class EmailVerificacaoService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificacaoService.class);

    private final CodigoVerificacaoRepository codigoVerificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificacaoService(CodigoVerificacaoRepository codigoVerificacaoRepository,
                                   UsuarioRepository usuarioRepository,
                                   JavaMailSender mailSender) {
        this.codigoVerificacaoRepository = codigoVerificacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.mailSender = mailSender;
    }

    // =============================================
    // MÉTODOS PÚBLICOS — PARA VOCÊ IMPLEMENTAR
    // =============================================

    /**
     * Gera um código OTP de 6 dígitos aleatório e salva no banco vinculado ao e-mail.
     *
     * REGRAS:
     * 1. Gerar código numérico aleatório de 6 dígitos:
     *    - Usar {@link SecureRandom} (não Math.random() — SecureRandom é criptograficamente seguro)
     *    - int numero = secureRandom.nextInt(1_000_000)
     *    - String codigo = String.format("%06d", numero)
     *      (o %06d garante padding com zeros: 42 vira "000042")
     * 2. Criar um novo {@link CodigoVerificacaoEntity}:
     *    - email         = parâmetro email
     *    - codigo        = código gerado
     *    - dataExpiracao = LocalDateTime.now().plusMinutes(15)
     *    - utilizado     = false (já é o default da entity, mas seja explícito)
     * 3. Salvar no codigoVerificacaoRepository.save()
     * 4. Retornar o código gerado (String)
     *
     * PERGUNTAS PARA REFLETIR:
     * - Por que SecureRandom e não Random? Porque Random é previsível — dado o seed,
     *   alguém poderia calcular o próximo código. SecureRandom usa entropia do SO.
     * - O SecureRandom deve ser um campo da classe (instanciado uma vez) ou criado
     *   a cada chamada? (Dica: instanciar uma vez no campo da classe é mais eficiente
     *   e perfeitamente thread-safe)
     * - E se o mesmo e-mail chamar gerarCodigo() várias vezes seguidas?
     *   Vários códigos ficam válidos ao mesmo tempo. Isso é um problema?
     *   (Veja reenviarCodigo() para uma possível solução)
     *
     * @param email E-mail do usuário para vincular ao código
     * @return Código de 6 dígitos gerado (ex: "048372")
     */
    @Transactional
    public String gerarCodigo(String email) {
        int numero = secureRandom.nextInt(1_000_000);
        String codigo = String.format("%06d", numero);

        CodigoVerificacaoEntity codigoEntity = new CodigoVerificacaoEntity();
        codigoEntity.setEmail(email);
        codigoEntity.setCodigo(codigo);
        codigoEntity.setDataExpiracao(LocalDateTime.now().plusMinutes(15));
        codigoEntity.setUtilizado(false);

        codigoVerificacaoRepository.save(codigoEntity);
        log.info("Código de verificação gerado para e-mail {}", email);

        enviarEmail(email, codigo);

        return codigo;
    }

    /**
     * Valida o código de verificação enviado pelo usuário e marca o e-mail como verificado.
     *
     * REGRAS:
     * 1. Buscar código no repository por email + codigo + utilizado=false:
     *    codigoVerificacaoRepository.findByEmailAndCodigoAndUtilizadoFalse(dto.getEmail(), dto.getCodigo())
     * 2. Se não encontrar (Optional vazio):
     *    Lançar CodigoVerificacaoInvalidoException("Código inválido ou já utilizado")
     * 3. Se encontrou, verificar expiração:
     *    if (codigoEntity.getDataExpiracao().isBefore(LocalDateTime.now()))
     *    Lançar CodigoVerificacaoInvalidoException("Código expirado. Solicite um novo código.")
     * 4. Marcar código como utilizado:
     *    codigoEntity.setUtilizado(true)
     * 5. Buscar o UsuarioEntity pelo e-mail:
     *    usuarioRepository.findByEmail(dto.getEmail())
     *    Se não encontrar: lançar RecursoNaoEncontradoException("Usuário não encontrado")
     * 6. Marcar e-mail como verificado:
     *    usuario.setEmailVerificado(true)
     * 7. Salvar ambos:
     *    codigoVerificacaoRepository.save(codigoEntity)
     *    usuarioRepository.save(usuario)
     *
     * PERGUNTAS PARA REFLETIR:
     * - A ordem das validações importa? Sim — fail-fast. Se o código não existe,
     *   nem precisa verificar expiração. Se está expirado, nem precisa buscar o usuário.
     * - E se o e-mail já estiver verificado (emailVerificado = true)?
     *   Opção A: lançar exceção ("E-mail já verificado"). Opção B: ignorar silenciosamente.
     *   Qual faz mais sentido para a experiência do usuário?
     * - Por que salvar explicitamente os dois objetos? O JPA dirty checking salvaria
     *   automaticamente ao final da transação, mas ser explícito torna o código mais legível.
     *   Você pode escolher não chamar .save() se preferir confiar no dirty checking.
     * - E se o usuário errar o código 10 vezes seguidas? Deveria haver um rate limit?
     *   (Isso é uma preocupação de segurança — por agora não precisa implementar,
     *   mas é importante pensar nisso para o futuro)
     *
     * @param dto Contém email e código de 6 dígitos
     * @throws CodigoVerificacaoInvalidoException se o código não existir, já foi usado ou expirou
     * @throws RecursoNaoEncontradoException      se o usuário não existir
     */
    @Transactional
    public void verificarEmail(VerificarEmailRequestDTO dto) {
        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository
                .findByEmailAndCodigoAndUtilizadoFalse(dto.email(), dto.codigo())
                .orElseThrow(() -> new CodigoVerificacaoInvalidoException("Código inválido ou já utilizado"));

        if (codigoEntity.getDataExpiracao().isBefore(LocalDateTime.now())) {
            log.warn("Código de verificação expirado para e-mail {}", dto.email());
            throw new CodigoVerificacaoInvalidoException("Código expirado. Solicite um novo código.");
        }

        codigoEntity.setUtilizado(true);

        UsuarioEntity usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        usuario.setEmailVerificado(true);

        codigoVerificacaoRepository.save(codigoEntity);
        usuarioRepository.save(usuario);
        log.info("E-mail {} verificado com sucesso", dto.email());
    }

    /**
     * Reenvia um novo código de verificação para o e-mail informado.
     *
     * REGRAS:
     * 1. Buscar o usuário pelo e-mail:
     *    usuarioRepository.findByEmail(dto.getEmail())
     *    Se não encontrar: lançar RecursoNaoEncontradoException("Usuário não encontrado para este e-mail")
     * 2. Verificar se o e-mail já está verificado:
     *    if (usuario.isEmailVerificado())
     *    Lançar RegraDeNegocioException("Este e-mail já foi verificado")
     * 3. (Opcional) Invalidar códigos anteriores não utilizados:
     *    Buscar todos os códigos não utilizados do e-mail e marcar como utilizado = true.
     *    Isso evita que o usuário tenha múltiplos códigos válidos ao mesmo tempo.
     *    (Você precisaria de um novo método no repository, ou iterar sobre os resultados)
     * 4. Gerar novo código:
     *    gerarCodigo(dto.getEmail())
     * 5. (Futuro) Enviar o novo código por e-mail via JavaMailSender
     *
     * PERGUNTAS PARA REFLETIR:
     * - Por que verificar se o e-mail já está verificado? Porque seria desperdício
     *   (e confuso para o usuário) gerar um código para um e-mail que já foi confirmado.
     * - O passo 3 (invalidar antigos) é "opcional" no sentido de funcionalidade,
     *   mas é uma boa prática de segurança. Por quê?
     *   Porque se o atacante interceptou o código anterior, ele não poderia mais usá-lo.
     * - E se o usuário chamar reenviarCodigo() várias vezes em 1 minuto?
     *   Deveria haver um cooldown (ex: "Aguarde 60 segundos antes de solicitar um novo código")?
     *   (Por agora não precisa implementar, mas anote como melhoria futura)
     * - Para invalidar os antigos sem um método novo no repository, você pode usar:
     *   codigoVerificacaoRepository.findTopByEmailAndUtilizadoFalseOrderByDataCriacaoDesc()
     *   e marcar como utilizado. Mas isso só pega o mais recente. Para pegar TODOS,
     *   você precisaria criar um: List<CodigoVerificacaoEntity> findByEmailAndUtilizadoFalse(email)
     *
     * @param dto Contém o e-mail para reenvio
     * @throws RecursoNaoEncontradoException se o e-mail não estiver cadastrado
     * @throws RegraDeNegocioException       se o e-mail já estiver verificado
     */
    @Transactional
    public void reenviarCodigo(ReenviarCodigoRequestDTO dto) {
        UsuarioEntity usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado para este e-mail"));

        if (usuario.isEmailVerificado()) {
            log.warn("Tentativa de reenviar código para e-mail já verificado: {}", dto.email());
            throw new RegraDeNegocioException("Este e-mail já foi verificado");
        }

        codigoVerificacaoRepository.findTopByEmailAndUtilizadoFalseOrderByDataCriacaoDesc(dto.email())
                .ifPresent(codigoAntigo -> {
                    codigoAntigo.setUtilizado(true);
                    codigoVerificacaoRepository.save(codigoAntigo);
                });

        String novoCodigo = gerarCodigo(dto.email());
    }

    // =============================================
    // MÉTODO PRIVADO — ENVIO DE E-MAIL
    // =============================================

    /**
     * Envia o código de verificação por e-mail usando o template HTML.
     *
     * <p>Carrega o template de {@code templates/verificacao-email.html},
     * substitui o placeholder {@code {{CODIGO}}} pelo código gerado
     * e envia como e-mail HTML via SMTP.</p>
     *
     * @param destinatario E-mail do destinatário
     * @param codigo Código OTP de 6 dígitos
     */
    private void enviarEmail(String destinatario, String codigo) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/verificacao-email.html");
            String htmlTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String htmlContent = htmlTemplate.replace("{{CODIGO}}", codigo);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject("Equilibra — Código de Verificação");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("E-mail de verificação enviado para {}", destinatario);

        } catch (MessagingException | IOException e) {
            log.error("Falha ao enviar e-mail de verificação para {}: {}", destinatario, e.getMessage(), e);
            throw new RegraDeNegocioException("Não foi possível enviar o e-mail de verificação. Tente novamente.");
        }
    }
}
