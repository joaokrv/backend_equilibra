package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.BaseRepositoryTest;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UsuarioRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    public void deveSalvarUsuarioComSucessoENomearAtivoPorPadrao() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome("Joao Silva");
        usuario.setEmail("joao.teste@email.com");
        usuario.setSenha("senha123");

        UsuarioEntity salvo = usuarioRepository.saveAndFlush(usuario);

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getNome()).isEqualTo("Joao Silva");
        assertThat(salvo.isAtivo()).isTrue(); // Testando default constraint do SQL e Java
        assertThat(salvo.getDataCriacao()).isNotNull();
    }

    @Test
    public void naoDevePermitirEmailDuplicado() {
        UsuarioEntity u1 = new UsuarioEntity();
        u1.setNome("User 1");
        u1.setEmail("unico@email.com");
        u1.setSenha("senha");
        usuarioRepository.saveAndFlush(u1);

        UsuarioEntity u2 = new UsuarioEntity();
        u2.setNome("User 2");
        u2.setEmail("unico@email.com");
        u2.setSenha("senha");

        // O SQL V1 tem a constraint UNIQUE em email, isso deve lançar um erro
        assertThrows(DataIntegrityViolationException.class, () -> {
            usuarioRepository.saveAndFlush(u2);
        });
    }
}
