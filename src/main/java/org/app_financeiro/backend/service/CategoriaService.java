package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.CategoriaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CategoriaResponseDTO;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.repository.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
     *
     * REGRAS:
     * - Validar que o usuário existe (usuarioService.buscarPorIdOuFalhar)
     * - Validar duplicidade: não deve existir outra categoria ativa com mesmo nome E
     *   mesmo tipo para o mesmo usuário. Se existir, lançar
     *   OperacaoNaoPermitidaException("Já existe uma categoria com este nome para o tipo " + tipo)
     *   Use categoriaRepository.findByUsuarioIdAndTipoAndNomeContainingIgnoreCaseAndAtivoTrue()
     *   ou compare com equalsIgnoreCase() após buscar por usuário e tipo.
     * - Criar CategoriaEntity, setar nome, tipo, usuario, ativo=true
     * - Salvar e retornar CategoriaResponseDTO
     */
    public CategoriaResponseDTO criarCategoria(CategoriaRegistroRequestDTO dto, Long usuarioId) {
        // TODO: Implementar - validar usuário, validar duplicidade, criar, salvar, retornar DTO
        return null;
    }

    /**
     * Lista todas as categorias ativas do usuário.
     *
     * REGRAS:
     * - Usar categoriaRepository.findByUsuarioIdAndAtivoTrue(usuarioId)
     * - Converter cada CategoriaEntity para CategoriaResponseDTO com .stream().map(CategoriaResponseDTO::new).toList()
     * - Retornar a lista (pode ser vazia)
     */
    public List<CategoriaResponseDTO> buscarTodasDoUsuario(Long usuarioId) {
        // TODO: Implementar - buscar por usuário, converter para DTOs
        return null;
    }

    /**
     * Lista categorias do usuário filtradas por tipo (RECEITA ou DESPESA).
     *
     * REGRAS:
     * - Usar categoriaRepository.findByUsuarioIdAndTipoAndAtivoTrue(usuarioId, tipo)
     * - Converter para CategoriaResponseDTO e retornar a lista
     */
    public List<CategoriaResponseDTO> buscarPorTipo(Long usuarioId, TipoTransacao tipo) {
        // TODO: Implementar - buscar por tipo, converter para DTOs
        return null;
    }

    /**
     * Desativa (soft delete) uma categoria.
     *
     * REGRAS:
     * - Buscar categoria por ID no repository
     * - Validar que pertence ao usuário E está ativa
     * - Se não encontrar: lançar RecursoNaoEncontradoException("Categoria não encontrada")
     * - Setar ativo = false e salvar
     *
     * Transações já vinculadas a esta categoria mantêm a referência.
     * Apenas novas transações não poderão selecionar esta categoria.
     */
    @Transactional
    public void deletarCategoria(Long categoriaId, Long usuarioId) {
        // TODO: Implementar - soft delete da categoria
    }

    /**
     * Busca uma categoria ativa por ID validando que pertence ao usuário.
     * Método de uso interno (chamado pelo TransacaoService).
     *
     * REGRAS:
     * - Buscar por ID no repository
     * - Validar que pertence ao usuário E está ativa
     * - Se não encontrar: lançar RecursoNaoEncontradoException("Categoria não encontrada")
     * - Retornar a CategoriaEntity
     */
    public CategoriaEntity buscarPorIdOuFalhar(Long categoriaId, Long usuarioId) {
        // TODO: Implementar - buscar categoria, validar dono, retornar entity
        return null;
    }
}
