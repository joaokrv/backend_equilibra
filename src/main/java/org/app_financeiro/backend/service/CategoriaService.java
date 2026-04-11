package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.CategoriaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CategoriaResponseDTO;
import org.app_financeiro.backend.mapper.CategoriaMapper;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.OperacaoNaoPermitidaException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.repository.CategoriaRepository;
import org.app_financeiro.backend.repository.TransacaoRecorrenteRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serviço responsável pelo gerenciamento de categorias de transações.
 * Permite criar, listar, filtrar por tipo e desativar categorias vinculadas a um usuário.
 */
@Service
public class CategoriaService {

    private static final Logger log = LoggerFactory.getLogger(CategoriaService.class);

    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoRecorrenteRepository transacaoRecorrenteRepository;
    private final UsuarioService usuarioService;
    private final CategoriaMapper categoriaMapper;

    public CategoriaService(CategoriaRepository categoriaRepository,
                            TransacaoRepository transacaoRepository,
                            TransacaoRecorrenteRepository transacaoRecorrenteRepository,
                            UsuarioService usuarioService,
                            CategoriaMapper categoriaMapper) {
        this.categoriaRepository = categoriaRepository;
        this.transacaoRepository = transacaoRepository;
        this.transacaoRecorrenteRepository = transacaoRecorrenteRepository;
        this.usuarioService = usuarioService;
        this.categoriaMapper = categoriaMapper;
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
                usuarioService.buscarPorIdOuFalhar(usuarioId);

        List<CategoriaEntity> categoriasExistentes =
                categoriaRepository.findByUsuarioIdAndTipo(usuarioId, dto.tipo());

        boolean nomeDuplicado = categoriasExistentes.stream()
                .anyMatch(c -> c.getNome().equalsIgnoreCase(dto.nome()));

        if (nomeDuplicado) {
            log.warn("Tentativa de criar categoria duplicada '{}' do tipo {} para usuário {}", dto.nome(), dto.tipo(), usuarioId);
            throw new OperacaoNaoPermitidaException("Já existe uma categoria com este nome para o " +
                    "tipo " + dto.tipo());
        }

        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setNome(dto.nome());
        categoria.setTipo(dto.tipo());
        categoria.setUsuario(usuario);
        categoria.setAtivo(true);

        categoriaRepository.save(categoria);
        log.info("Categoria '{}' do tipo {} criada para usuário {}", categoria.getNome(), categoria.getTipo(), usuarioId);

        return categoriaMapper.toResponse(categoria);
    }

    /**
     * Lista todas as categorias ativas do usuário.
     *
     * @param usuarioId ID do usuário autenticado
     * @return Lista de CategoriaResponseDTO (pode ser vazia)
     */
    public List<CategoriaResponseDTO> buscarTodasDoUsuario(Long usuarioId) {
        usuarioService.buscarPorIdOuFalhar(usuarioId);
        List<CategoriaEntity> categorias = categoriaRepository.findByUsuarioId(usuarioId);

        return categorias.stream()
                    .map(categoriaMapper::toResponse)
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
        List<CategoriaEntity> categorias = categoriaRepository.findByUsuarioIdAndTipo(usuarioId, tipo);
        return categorias.stream()
                .map(categoriaMapper::toResponse)
                .toList();
    }

    /**
     * Atualiza o nome de uma categoria existente.
     * Valida duplicidade de nome (case-insensitive) para o mesmo tipo antes de salvar.
     *
     * @param categoriaId ID da categoria a atualizar
     * @param dto         Dados com o novo nome (tipo é ignorado, não se muda o tipo de uma categoria)
     * @param usuarioId   ID do usuário autenticado
     * @return CategoriaResponseDTO com os dados atualizados
     */
    @Transactional
    public CategoriaResponseDTO atualizarCategoria(Long categoriaId, CategoriaRegistroRequestDTO dto, Long usuarioId) {
        CategoriaEntity categoria = buscarPorIdOuFalhar(categoriaId, usuarioId);

        List<CategoriaEntity> categoriasExistentes =
                categoriaRepository.findByUsuarioIdAndTipo(usuarioId, categoria.getTipo());

        boolean nomeDuplicado = categoriasExistentes.stream()
                .filter(c -> !c.getId().equals(categoriaId))
                .anyMatch(c -> c.getNome().equalsIgnoreCase(dto.nome()));

        if (nomeDuplicado) {
            throw new OperacaoNaoPermitidaException("Já existe uma categoria com este nome para o tipo " + categoria.getTipo());
        }

        categoria.setNome(dto.nome());
        categoriaRepository.save(categoria);
        log.info("Categoria {} renomeada para '{}' pelo usuário {}", categoriaId, dto.nome(), usuarioId);
        return categoriaMapper.toResponse(categoria);
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

        int transacoesAtualizadas = transacaoRepository.desassociarCategoria(usuarioId, categoriaId);
        int recorrenciasAtualizadas = transacaoRecorrenteRepository.desassociarCategoria(usuarioId, categoriaId);

        categoria.setAtivo(false);
        categoriaRepository.save(categoria);
        log.info("Categoria {} '{}' desativada para usuário {} (transacoesDesassociadas={}, recorrenciasDesassociadas={})",
                categoriaId, categoria.getNome(), usuarioId, transacoesAtualizadas, recorrenciasAtualizadas);
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

        if (!categoria.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Categoria não pertence ao usuário");
        }

        return categoria;
    }
}
