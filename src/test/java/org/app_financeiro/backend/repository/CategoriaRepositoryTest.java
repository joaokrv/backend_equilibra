package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.BaseRepositoryTest;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class CategoriaRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UsuarioEntity usuarioPadrao;

    @BeforeEach
    public void setup() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome("Cat User");
        usuario.setEmail("cat.user@email.com");
        usuario.setSenha("123");
        this.usuarioPadrao = usuarioRepository.saveAndFlush(usuario);
    }

    @Test
    public void deveSalvarCategoriaERespeitarChaveEstrangeiraDeUsuario() {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setNome("Lazer");
        categoria.setTipo(TipoTransacao.DESPESA);
        categoria.setUsuario(usuarioPadrao);

        CategoriaEntity salva = categoriaRepository.saveAndFlush(categoria);

        assertThat(salva.getId()).isNotNull();
        assertThat(salva.getNome()).isEqualTo("Lazer");
        assertThat(salva.getTipo()).isEqualTo(TipoTransacao.DESPESA);
        assertThat(salva.getUsuario().getId()).isEqualTo(usuarioPadrao.getId());
        assertThat(salva.isAtivo()).isTrue();
    }
}
