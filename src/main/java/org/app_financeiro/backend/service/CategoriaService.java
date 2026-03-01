package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.CategoriaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CategoriaResponseDTO;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.OperacaoNaoPermitidaException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.repository.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serviço responsável pelo gerenciamento de categorias de transações.
 * Permite criar, listar, filtrar por tipo e desativar categorias vinculadas a um usuário.
 */
@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final UsuarioService usuarioService;

    public CategoriaService(CategoriaRepository categoriaRepository, UsuarioService usuarioService) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioService = usuarioService;
    }

/**
 * Cria uma nova categoria para o usuário.
 * Valida duplicidade de nome (case-insensitive) para o mesmo tipo antes de salvar.
 *
 * @param dto       Dados da nova categoria
 * @param usuarioId ID do usuário autenticado
 * @return CategoriaResponseDTO com os dados da categoria criada
 * @throws OperacaoNaoPermitidaException se já existir categoria com mesmo nome e tipo
 */
    @Transactional
    public CategoriaResponseDTO criarCategoria(CategoriaRegistroRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario =
                usuarioService.buscarPorIdOuFalhar(usuarioId); // Validar que usuário existe

         // Validar duplicidade de nome para o mesmo tipo
        List<CategoriaEntity> categoriasExistentes =
                categoriaRepository.findByUsuarioIdAndTipoAndAtivoTrue(usuarioId, dto.getTipo());

        boolean nomeDuplicado = categoriasExistentes.stream()
                .anyMatch(c -> c.getNome().equalsIgnoreCase(dto.getNome()));

        if (nomeDuplicado) {
            throw new OperacaoNaoPermitidaException("Já existe uma categoria com este nome para o " +
                    "tipo " + dto.getTipo());
        }

        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setNome(dto.getNome());
        categoria.setTipo(dto.getTipo());
        categoria.setUsuario(usuario);
        categoria.setAtivo(true);

        categoriaRepository.save(categoria);

        return new CategoriaResponseDTO(categoria);
    }

    /**
     * Lista todas as categorias ativas do usuário.
     *
     * @param usuarioId ID do usuário autenticado
     * @return Lista de CategoriaResponseDTO (pode ser vazia)
     */
    public List<CategoriaResponseDTO> buscarTodasDoUsuario(Long usuarioId) {
        usuarioService.buscarPorIdOuFalhar(usuarioId);
        List<CategoriaEntity> categorias = categoriaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        return categorias.stream()
                    .map(CategoriaResponseDTO::new)
                    .toList();
    }

    /**
     * Lista categorias ativas do usuário filtradas por tipo (RECEITA ou DESPESA).
     *
     * @param usuarioId ID do usuário autenticado
     * @param tipo      Tipo da transação a filtrar
     * @return Lista de CategoriaResponseDTO (pode ser vazia)
     */
    public List<CategoriaResponseDTO> buscarPorTipo(Long usuarioId, TipoTransacao tipo) {
        usuarioService.buscarPorIdOuFalhar(usuarioId);
        List<CategoriaEntity> categorias = categoriaRepository.findByUsuarioIdAndTipoAndAtivoTrue(usuarioId, tipo);
        return categorias.stream()
                .map(CategoriaResponseDTO::new)
                .toList();
    }

    /**
     * Desativa (soft delete) uma categoria.
     * Transações já vinculadas mantêm a referência; apenas novas transações
     * não poderão selecionar esta categoria.
     *
     * @param categoriaId ID da categoria a desativar
     * @param usuarioId   ID do usuário autenticado
     * @throws RecursoNaoEncontradoException se a categoria não existir ou não pertencer ao usuário
     */
    @Transactional
    public void deletarCategoria(Long categoriaId, Long usuarioId) {
        CategoriaEntity categoria = buscarPorIdOuFalhar(categoriaId, usuarioId);
        categoria.setAtivo(false);
        categoriaRepository.save(categoria);
    }

    /**
     * Busca uma categoria ativa por ID validando que pertence ao usuário.
     * Método de uso interno, chamado pelo TransacaoService.
     *
     * @param categoriaId ID da categoria
     * @param usuarioId   ID do usuário autenticado
     * @return CategoriaEntity correspondente
     * @throws RecursoNaoEncontradoException se a categoria não existir, não pertencer ao usuário ou estiver inativa
     */
    public CategoriaEntity buscarPorIdOuFalhar(Long categoriaId, Long usuarioId) {
        CategoriaEntity categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));

        if (!categoria.getUsuario().getId().equals(usuarioId) || !categoria.isAtivo()) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada");
        }

        return categoria;
    }
}
