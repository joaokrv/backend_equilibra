package org.app_financeiro.backend.service;

import org.app_financeiro.backend.entity.UsuarioFotoEntity;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.UsuarioFotoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioFotoServiceTest {

    @Mock
    private UsuarioFotoRepository usuarioFotoRepository;

    @InjectMocks
    private UsuarioFotoService usuarioFotoService;

    @Test
    void deveRejeitarUploadComAssinaturaInvalida() {
        byte[] scriptMalicioso = "<?php echo 'Hacked'; ?>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", scriptMalicioso);

        assertThatThrownBy(() -> usuarioFotoService.atualizar(1L, file))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Formato de arquivo inválido");

        verify(usuarioFotoRepository, never()).save(any());
    }

    @Test
    void deveGravarFotoComAssinaturaValidaEContentTypeDetectado() {
        byte[] pngValido = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", pngValido);
        when(usuarioFotoRepository.findById(1L)).thenReturn(Optional.empty());

        usuarioFotoService.atualizar(1L, file);

        ArgumentCaptor<UsuarioFotoEntity> captor = ArgumentCaptor.forClass(UsuarioFotoEntity.class);
        verify(usuarioFotoRepository).save(captor.capture());
        UsuarioFotoEntity salvo = captor.getValue();
        assertThat(salvo.getUsuarioId()).isEqualTo(1L);
        assertThat(salvo.getFoto()).isEqualTo(pngValido);
        assertThat(salvo.getContentType()).isEqualTo("image/png");
    }
}
