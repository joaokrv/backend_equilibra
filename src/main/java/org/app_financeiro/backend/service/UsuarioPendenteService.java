package org.app_financeiro.backend.service;

import lombok.RequiredArgsConstructor;
import org.app_financeiro.backend.dto.response.OtpStatusResponseDTO;
import org.app_financeiro.backend.entity.UsuarioPendenteEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioPendenteService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioPendenteService.class);
    private static final int MAX_TENTATIVAS = 5;

    private final UsuarioPendenteRepository usuarioPendenteRepository;

    public UsuarioPendenteEntity buscarOuFalhar(UUID id) {
        return usuarioPendenteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro de pré-cadastro não encontrado ou expirado."));
    }

    public UsuarioPendenteEntity buscarOuFalharComLock(UUID id) {
        return usuarioPendenteRepository.findByIdWithLock(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro de pré-cadastro não encontrado."));
    }

    public OtpStatusResponseDTO mapearParaStatus(UsuarioPendenteEntity pendente) {
        String status = "ATIVO";
        LocalDateTime agora = LocalDateTime.now();

        if (pendente.getBloqueadoAte() != null && pendente.getBloqueadoAte().isAfter(agora)) {
            status = "BLOQUEADO";
        } else if (pendente.getExpiraEm().isBefore(agora)) {
            status = "EXPIRADO";
        }

        Integer tentativasRestantes = MAX_TENTATIVAS - (pendente.getTentativasFalhas() != null ? pendente.getTentativasFalhas() : 0);
        if (tentativasRestantes < 0) tentativasRestantes = 0;

        LocalDateTime proximoReenvio = pendente.getUltimoEnvioEm() != null ? pendente.getUltimoEnvioEm().plusMinutes(1) : null;

        OffsetDateTime expiraEm = pendente.getExpiraEm().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime bloqueadoAte = pendente.getBloqueadoAte() != null
            ? pendente.getBloqueadoAte().atZone(ZoneId.systemDefault()).toOffsetDateTime()
            : null;
        OffsetDateTime proximoReenvioEm = proximoReenvio != null
            ? proximoReenvio.atZone(ZoneId.systemDefault()).toOffsetDateTime()
            : null;

        return new OtpStatusResponseDTO(
                status,
                tentativasRestantes,
            expiraEm,
            bloqueadoAte,
            proximoReenvioEm,
            null,
                pendente.getId().toString()
        );
    }

    @Transactional
    public void registrarTentativaFalha(UsuarioPendenteEntity pendente) {
        int tentativas = (pendente.getTentativasFalhas() != null ? pendente.getTentativasFalhas() : 0) + 1;
        pendente.setTentativasFalhas(tentativas);
        pendente.setUltimaTentativaEm(LocalDateTime.now());

        if (tentativas >= MAX_TENTATIVAS) {
            log.warn("[SECURITY-LOCKOUT] E-mail {} bloqueado por excesso de tentativas de OTP.", pendente.getEmail());
            pendente.setBloqueadoAte(LocalDateTime.now().plusMinutes(30));
        }

        usuarioPendenteRepository.save(pendente);
    }

    @Transactional
    public void limparTentativas(UsuarioPendenteEntity pendente) {
        pendente.setTentativasFalhas(0);
        pendente.setBloqueadoAte(null);
        usuarioPendenteRepository.save(pendente);
    }
}
