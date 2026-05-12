package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.OtpStatusResponseDTO;
import org.app_financeiro.backend.entity.UsuarioPendenteEntity;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsuarioPendenteServiceTest {

    @Mock
    private UsuarioPendenteRepository usuarioPendenteRepository;

    @InjectMocks
    private UsuarioPendenteService usuarioPendenteService;

    @Test
    void deveBloquearAposQuintaTentativaFalha() {
        UsuarioPendenteEntity pendente = new UsuarioPendenteEntity();
        pendente.setEmail("otp-lockout@email.com");
        pendente.setExpiraEm(LocalDateTime.now().plusMinutes(15));
        pendente.setTentativasFalhas(4);

        usuarioPendenteService.registrarTentativaFalha(pendente);

        assertThat(pendente.getTentativasFalhas()).isEqualTo(5);
        assertThat(pendente.getUltimaTentativaEm()).isNotNull();
        assertThat(pendente.getBloqueadoAte()).isNotNull();
        assertThat(pendente.getBloqueadoAte()).isAfter(LocalDateTime.now());

        verify(usuarioPendenteRepository).save(pendente);
    }

    @Test
    void deveMapearStatusComoBloqueadoQuandoBloqueioEstiverAtivo() {
        UsuarioPendenteEntity pendente = new UsuarioPendenteEntity();
        pendente.setId(java.util.UUID.randomUUID());
        pendente.setEmail("otp-status@email.com");
        pendente.setExpiraEm(LocalDateTime.now().plusMinutes(15));
        pendente.setBloqueadoAte(LocalDateTime.now().plusMinutes(20));
        pendente.setTentativasFalhas(5);

        OtpStatusResponseDTO status = usuarioPendenteService.mapearParaStatus(pendente);

        assertThat(status.status()).isEqualTo("BLOQUEADO");
        assertThat(status.tentativasRestantes()).isEqualTo(0);
        assertThat(status.registroId()).isEqualTo(pendente.getId().toString());
    }
}