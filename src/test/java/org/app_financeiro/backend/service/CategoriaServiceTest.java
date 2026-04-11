package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.CategoriaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CategoriaResponseDTO;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.OperacaoNaoPermitidaException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.mapper.CategoriaMapper;
import org.app_financeiro.backend.repository.CategoriaRepository;
import org.app_financeiro.backend.repository.TransacaoRecorrenteRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private TransacaoRecorrenteRepository transacaoRecorrenteRepository;

    @Mock
    private CategoriaMapper categoriaMapper;

    @InjectMocks
    private CategoriaService categoriaService;

    private UsuarioEntity usuarioPadrao;
    private CategoriaEntity categoriaDespesa;

    @BeforeEach
    void setUp() {
        usuarioPadrao = new UsuarioEntity();
        usuarioPadrao.setId(1L);
        usuarioPadrao.setNome("Joao");

        categoriaDespesa = new CategoriaEntity();
        categoriaDespesa.setId(10L);
        categoriaDespesa.setNome("Alimentação");
        categoriaDespesa.setTipo(TipoTransacao.DESPESA);
        categoriaDespesa.setUsuario(usuarioPadrao);
        categoriaDespesa.setAtivo(true);
    }

    @Test
    void deveCriarCategoriaComSucesso() {
        // Arrange
        CategoriaRegistroRequestDTO request = new CategoriaRegistroRequestDTO("Salário", TipoTransacao.RECEITA);
        CategoriaResponseDTO responseEsperada = new CategoriaResponseDTO(11L, "Salário", TipoTransacao.RECEITA);

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(categoriaRepository.findByUsuarioIdAndTipo(1L, TipoTransacao.RECEITA)).thenReturn(Collections.emptyList());

        when(categoriaRepository.save(any(CategoriaEntity.class))).thenAnswer(invocation -> {
            CategoriaEntity c = invocation.getArgument(0);
            c.setId(11L);
            return c;
        });

        when(categoriaMapper.toResponse(any(CategoriaEntity.class))).thenReturn(responseEsperada);

        // Act
        CategoriaResponseDTO result = categoriaService.criarCategoria(request, 1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.nome()).isEqualTo("Salário");
        assertThat(result.tipo()).isEqualTo(TipoTransacao.RECEITA);

        ArgumentCaptor<CategoriaEntity> captor = ArgumentCaptor.forClass(CategoriaEntity.class);
        verify(categoriaRepository).save(captor.capture());
        CategoriaEntity captorEntity = captor.getValue();
        assertThat(captorEntity.getNome()).isEqualTo("Salário");
        assertThat(captorEntity.getUsuario().getId()).isEqualTo(1L);
    }

    @Test
    void deveLancarOperacaoNaoPermitidaExceptionAoCriarCategoriaDuplicada() {
        // Arrange
        CategoriaRegistroRequestDTO request = new CategoriaRegistroRequestDTO("ALImentaÇÃO", TipoTransacao.DESPESA);

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(categoriaRepository.findByUsuarioIdAndTipo(1L, TipoTransacao.DESPESA))
                .thenReturn(List.of(categoriaDespesa)); // Categoria "Alimentação" já existe

        // Act & Assert
        assertThatThrownBy(() -> categoriaService.criarCategoria(request, 1L))
                .isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessageContaining("Já existe uma categoria com este nome");

        verify(categoriaRepository, never()).save(any(CategoriaEntity.class));
    }

    @Test
    void deveBuscarPorIdOuFalharComSucesso() {
        // Arrange
        when(categoriaRepository.findById(10L)).thenReturn(Optional.of(categoriaDespesa));

        // Act
        CategoriaEntity result = categoriaService.buscarPorIdOuFalhar(10L, 1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getNome()).isEqualTo("Alimentação");
    }

    @Test
    void deveLancarRecursoNaoEncontradoExceptionAoBuscarCategoriaInexistente() {
        // Arrange
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> categoriaService.buscarPorIdOuFalhar(99L, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Categoria não encontrada");
    }

    @Test
    void deveLancarRecursoNaoEncontradoExceptionAoBuscarCategoriaDeOutroUsuario() {
        // Arrange
        when(categoriaRepository.findById(10L)).thenReturn(Optional.of(categoriaDespesa));

        // Act & Assert
        assertThatThrownBy(() -> categoriaService.buscarPorIdOuFalhar(10L, 2L)) // Usuario 2 tenta ler do Usuario 1
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Categoria não pertence ao usuário");
    }

    @Test
    void deveDeletarCategoriaComSucesso() {
        // Arrange
        when(categoriaRepository.findById(10L)).thenReturn(Optional.of(categoriaDespesa));
        when(transacaoRepository.desassociarCategoria(1L, 10L)).thenReturn(4);
        when(transacaoRecorrenteRepository.desassociarCategoria(1L, 10L)).thenReturn(2);
        when(categoriaRepository.save(any(CategoriaEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        categoriaService.deletarCategoria(10L, 1L);

        // Assert
        assertThat(categoriaDespesa.isAtivo()).isFalse(); // Testa o soft delete
        verify(categoriaRepository).save(categoriaDespesa);
        verify(transacaoRepository).desassociarCategoria(1L, 10L);
        verify(transacaoRecorrenteRepository).desassociarCategoria(1L, 10L);
    }

    @Test
    void deveRetornarTodasCategoriasDoUsuario() {
        // Arrange
        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(categoriaRepository.findByUsuarioId(1L)).thenReturn(List.of(categoriaDespesa));

        CategoriaResponseDTO response = new CategoriaResponseDTO(10L, "Alimentação", TipoTransacao.DESPESA);
        when(categoriaMapper.toResponse(categoriaDespesa)).thenReturn(response);

        // Act
        List<CategoriaResponseDTO> result = categoriaService.buscarTodasDoUsuario(1L);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Alimentação");
    }

    @Test
    void deveBuscarCategoriasPorTipo() {
        // Arrange
        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(categoriaRepository.findByUsuarioIdAndTipo(1L, TipoTransacao.DESPESA)).thenReturn(List.of(categoriaDespesa));

        CategoriaResponseDTO response = new CategoriaResponseDTO(10L, "Alimentação", TipoTransacao.DESPESA);
        when(categoriaMapper.toResponse(categoriaDespesa)).thenReturn(response);

        // Act
        List<CategoriaResponseDTO> result = categoriaService.buscarPorTipo(1L, TipoTransacao.DESPESA);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).tipo()).isEqualTo(TipoTransacao.DESPESA);
    }
}
