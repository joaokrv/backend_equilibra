package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ConfirmarAlteracaoEmailRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarAlteracaoEmailRequestDTO;
import org.app_financeiro.backend.entity.SolicitacaoAlteracaoEmailEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CodigoVerificacaoInvalidoException;
import org.app_financeiro.backend.exception.CredenciaisInvalidasException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.SolicitacaoAlteracaoEmailRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/** Fluxo de alteração de e-mail em duas etapas: OTP enviado ao novo endereço e invalidação de sessões ao confirmar. */
@Service
public class AlteracaoEmailService {

    private static final Logger log = LoggerFactory.getLogger(AlteracaoEmailService.class);
    private static final int MINUTOS_EXPIRACAO = 15;
    private static final int MAX_TENTATIVAS_OTP = 5;

    private final SolicitacaoAlteracaoEmailRepository solicitacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ExternalEmailSenderService externalEmailSenderService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AlteracaoEmailService(SolicitacaoAlteracaoEmailRepository solicitacaoRepository,
                                 UsuarioRepository usuarioRepository,
                                 PasswordEncoder passwordEncoder,
                                 ExternalEmailSenderService externalEmailSenderService) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.externalEmailSenderService = externalEmailSenderService;
    }

    @Transactional
    public void solicitarAlteracao(Long usuarioId, SolicitarAlteracaoEmailRequestDTO dto) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenha())) {
            throw new CredenciaisInvalidasException();
        }

        if (usuario.getEmail().equalsIgnoreCase(dto.novoEmail())) {
            throw new RegraDeNegocioException("O novo e-mail deve ser diferente do atual.");
        }

        if (usuarioRepository.existsByEmailIncludingInactive(dto.novoEmail())) {
            throw new RegraDeNegocioException("Este e-mail já está vinculado a outra conta.");
        }

        solicitacaoRepository.invalidarSolicitacoesAnteriores(usuarioId);

        String codigo = String.format("%06d", secureRandom.nextInt(1_000_000));

        SolicitacaoAlteracaoEmailEntity solicitacao = new SolicitacaoAlteracaoEmailEntity();
        solicitacao.setUsuarioId(usuarioId);
        solicitacao.setNovoEmail(dto.novoEmail());
        solicitacao.setCodigo(codigo);
        solicitacao.setDataExpiracao(LocalDateTime.now().plusMinutes(MINUTOS_EXPIRACAO));
        solicitacaoRepository.save(solicitacao);

        enviarEmail(dto.novoEmail(), codigo);
        log.debug("Solicitação de alteração de e-mail criada: usuarioId={}, novoEmail={}", usuarioId, dto.novoEmail());
    }

    @Transactional
    public void confirmarAlteracao(Long usuarioId, ConfirmarAlteracaoEmailRequestDTO dto) {
        SolicitacaoAlteracaoEmailEntity solicitacao = solicitacaoRepository
                .findTopByUsuarioIdAndIsUtilizadoFalseOrderByDataCriacaoDesc(usuarioId)
                .orElseThrow(() -> new CodigoVerificacaoInvalidoException("Nenhuma solicitação ativa. Solicite uma nova alteração."));

        if (solicitacao.getTentativasFalhas() >= MAX_TENTATIVAS_OTP) {
            log.warn("OTP de alteração de e-mail bloqueado por excesso de tentativas: usuarioId={}", usuarioId);
            throw new CodigoVerificacaoInvalidoException("Código bloqueado após múltiplas tentativas. Solicite uma nova alteração.");
        }

        if (solicitacao.getDataExpiracao().isBefore(LocalDateTime.now())) {
            throw new CodigoVerificacaoInvalidoException("Código expirado. Solicite uma nova alteração de e-mail.");
        }

        if (!solicitacao.getCodigo().equals(dto.codigo())) {
            solicitacao.setTentativasFalhas(solicitacao.getTentativasFalhas() + 1);
            if (solicitacao.getTentativasFalhas() >= MAX_TENTATIVAS_OTP) {
                solicitacao.setUtilizado(true);
                log.warn("OTP de alteração invalidado após {} tentativas: usuarioId={}", MAX_TENTATIVAS_OTP, usuarioId);
            }
            solicitacaoRepository.save(solicitacao);
            throw new CodigoVerificacaoInvalidoException("Código inválido ou já utilizado.");
        }

        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        usuario.setEmail(solicitacao.getNovoEmail());
        usuario.setEmailVerificado(true);
        usuario.setChaveSessao(null);
        usuarioRepository.save(usuario);

        solicitacao.setUtilizado(true);
        solicitacaoRepository.save(solicitacao);

        log.info("E-mail alterado: usuarioId={}, novoEmail={}", usuarioId, solicitacao.getNovoEmail());
    }

    private void enviarEmail(String destinatario, String codigo) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/alteracao-email.html");
            String htmlTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String htmlContent = htmlTemplate.replace("{{CODIGO}}", codigo);

            externalEmailSenderService.sendHtml(destinatario, "Equilibra - Alteracao de E-mail", htmlContent);
            log.debug("E-mail de alteração enviado para {}", destinatario);

        } catch (IOException e) {
            log.warn("Falha ao enviar e-mail de alteração para {}: {}", destinatario, e.getMessage());
        }
    }
}
