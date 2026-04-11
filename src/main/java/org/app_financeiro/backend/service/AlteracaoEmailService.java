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

/**
 * Serviço responsável pelo fluxo de alteração de e-mail em duas etapas:
 *
 * 1. Solicitar: valida identidade (senha atual), verifica disponibilidade do novo email,
 *    gera OTP de 6 dígitos e envia ao NOVO email.
 * 2. Confirmar: valida o OTP, efetiva a troca de email e invalida sessões.
 */
@Service
public class AlteracaoEmailService {

    private static final Logger log = LoggerFactory.getLogger(AlteracaoEmailService.class);
    private static final int MINUTOS_EXPIRACAO = 15;

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

    /**
     * Etapa 1: Solicita a alteração de e-mail.
     * Valida a senha atual, verifica que o novo email não está em uso,
     * gera OTP e envia ao novo endereço.
     */
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

    /**
     * Etapa 2: Confirma a alteração de e-mail com o código OTP.
     * Valida o código, atualiza o email do usuário e invalida sessões ativas.
     */
    @Transactional
    public void confirmarAlteracao(Long usuarioId, ConfirmarAlteracaoEmailRequestDTO dto) {
        SolicitacaoAlteracaoEmailEntity solicitacao = solicitacaoRepository
                .findByUsuarioIdAndCodigoAndIsUtilizadoFalse(usuarioId, dto.codigo())
                .orElseThrow(() -> new CodigoVerificacaoInvalidoException("Código inválido ou já utilizado."));

        if (solicitacao.getDataExpiracao().isBefore(LocalDateTime.now())) {
            throw new CodigoVerificacaoInvalidoException("Código expirado. Solicite uma nova alteração de e-mail.");
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
