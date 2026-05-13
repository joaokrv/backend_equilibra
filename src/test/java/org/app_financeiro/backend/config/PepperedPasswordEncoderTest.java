package org.app_financeiro.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do {@link PepperedPasswordEncoder}.
 *
 * <p>Verifica que o decorator prepend o pepper à senha bruta
 * antes de delegar para o encoder real (Argon2).</p>
 */
class PepperedPasswordEncoderTest {

    @Test
    void devePreprenderPepperAoEncodar() {
        PasswordEncoder delegate = mock(PasswordEncoder.class);
        when(delegate.encode("pepper-secreto" + "senha123")).thenReturn("hash_result");

        PepperedPasswordEncoder encoder = new PepperedPasswordEncoder(delegate, "pepper-secreto");

        String result = encoder.encode("senha123");

        assertThat(result).isEqualTo("hash_result");
        verify(delegate).encode("pepper-secreto" + "senha123");
    }

    @Test
    void devePreprenderPepperAoVerificar() {
        PasswordEncoder delegate = mock(PasswordEncoder.class);
        when(delegate.matches("pepper-secreto" + "senha123", "hash_salvo")).thenReturn(true);

        PepperedPasswordEncoder encoder = new PepperedPasswordEncoder(delegate, "pepper-secreto");

        boolean result = encoder.matches("senha123", "hash_salvo");

        assertThat(result).isTrue();
        verify(delegate).matches("pepper-secreto" + "senha123", "hash_salvo");
    }

    @Test
    void deveTratarPepperNuloComoStringVazia() {
        PasswordEncoder delegate = mock(PasswordEncoder.class);
        when(delegate.encode("senha123")).thenReturn("hash_sem_pepper");

        PepperedPasswordEncoder encoder = new PepperedPasswordEncoder(delegate, null);

        String result = encoder.encode("senha123");

        assertThat(result).isEqualTo("hash_sem_pepper");
        verify(delegate).encode("senha123");
    }

    @Test
    void deveDelegarUpgradeEncoding() {
        PasswordEncoder delegate = mock(PasswordEncoder.class);
        when(delegate.upgradeEncoding("hash_antigo")).thenReturn(true);

        PepperedPasswordEncoder encoder = new PepperedPasswordEncoder(delegate, "pepper");

        boolean result = encoder.upgradeEncoding("hash_antigo");

        assertThat(result).isTrue();
        verify(delegate).upgradeEncoding("hash_antigo");
    }
}
