package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.TransacaoRecorrenteRequestDTO;
import org.app_financeiro.backend.dto.response.TransacaoRecorrenteResponseDTO;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.RecorrenciaCanceladaEntity;
import org.app_financeiro.backend.entity.TransacaoRecorrenteEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.mapper.TransacaoRecorrenteMapper;
import org.app_financeiro.backend.repository.RecorrenciaCanceladaRepository;
import org.app_financeiro.backend.repository.TransacaoRecorrenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TransacaoRecorrenteService {

    private static final Logger log = LoggerFactory.getLogger(TransacaoRecorrenteService.class);

    private final TransacaoRecorrenteRepository recorrenteRepository;
    private final RecorrenciaCanceladaRepository canceladaRepository;
    private final ContaService contaService;
    private final CartaoService cartaoService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;
    private final TransacaoRecorrenteMapper mapper;

    public TransacaoRecorrenteService(TransacaoRecorrenteRepository recorrenteRepository,
                                       RecorrenciaCanceladaRepository canceladaRepository,
                                       ContaService contaService,
                                       CartaoService cartaoService,
                                       CategoriaService categoriaService,
                                       UsuarioService usuarioService,
                                       TransacaoRecorrenteMapper mapper) {
        this.recorrenteRepository = recorrenteRepository;
        this.canceladaRepository = canceladaRepository;
        this.contaService = contaService;
        this.cartaoService = cartaoService;
        this.categoriaService = categoriaService;
        this.usuarioService = usuarioService;
        this.mapper = mapper;
    }

    @Transactional
    public TransacaoRecorrenteResponseDTO criar(TransacaoRecorrenteRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        ContaEntity conta = contaService.buscarContaValidada(dto.contaId(), usuarioId);

        TransacaoRecorrenteEntity entity = new TransacaoRecorrenteEntity();
        entity.setDescricao(dto.descricao());
        entity.setValor(dto.valor());
        entity.setTipo(dto.tipo());
        entity.setMetodoPagamento(dto.metodoPagamento());
        entity.setConta(conta);
        entity.setDiaLancamento(dto.diaLancamento());
        entity.setDataInicio(dto.dataInicio() != null ? dto.dataInicio() : LocalDate.now());
        entity.setDataFim(dto.dataFim());
        entity.setUsuario(usuario);
        entity.setAtivo(true);

        if (dto.cartaoId() != null) {
            CartaoEntity cartao = cartaoService.buscarCartaoValidado(dto.cartaoId(), usuarioId);
            entity.setCartao(cartao);
        }

        if (dto.categoriaId() != null) {
            CategoriaEntity categoria = categoriaService.buscarPorIdOuFalhar(dto.categoriaId(), usuarioId);
            entity.setCategoria(categoria);
        }

        if (dto.dataFim() != null && dto.dataInicio() != null && dto.dataFim().isBefore(dto.dataInicio())) {
            throw new RegraDeNegocioException("A data fim não pode ser anterior à data início.");
        }

        entity = recorrenteRepository.save(entity);
        log.info("Recorrência {} '{}' criada para usuário {}", entity.getId(), dto.descricao(), usuarioId);
        return mapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<TransacaoRecorrenteResponseDTO> listar(Long usuarioId) {
        return recorrenteRepository.findByUsuarioId(usuarioId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public TransacaoRecorrenteResponseDTO atualizar(Long id, TransacaoRecorrenteRequestDTO dto, Long usuarioId) {
        TransacaoRecorrenteEntity entity = buscarValidada(id, usuarioId);
        ContaEntity conta = contaService.buscarContaValidada(dto.contaId(), usuarioId);

        entity.setDescricao(dto.descricao());
        entity.setValor(dto.valor());
        entity.setTipo(dto.tipo());
        entity.setMetodoPagamento(dto.metodoPagamento());
        entity.setConta(conta);
        entity.setDiaLancamento(dto.diaLancamento());

        if (dto.dataInicio() != null) entity.setDataInicio(dto.dataInicio());
        entity.setDataFim(dto.dataFim());

        if (dto.cartaoId() != null) {
            entity.setCartao(cartaoService.buscarCartaoValidado(dto.cartaoId(), usuarioId));
        } else {
            entity.setCartao(null);
        }

        if (dto.categoriaId() != null) {
            entity.setCategoria(categoriaService.buscarPorIdOuFalhar(dto.categoriaId(), usuarioId));
        } else {
            entity.setCategoria(null);
        }

        entity = recorrenteRepository.save(entity);
        log.info("Recorrência {} atualizada", id);
        return mapper.toResponse(entity);
    }

    @Transactional
    public void deletar(Long id, Long usuarioId) {
        TransacaoRecorrenteEntity entity = buscarValidada(id, usuarioId);
        entity.setAtivo(false);
        recorrenteRepository.save(entity);
        log.info("Recorrência {} desativada", id);
    }

    @Transactional
    public void cancelarMes(Long id, int ano, int mes, Long usuarioId) {
        TransacaoRecorrenteEntity entity = buscarValidada(id, usuarioId);

        if (canceladaRepository.existsByRecorrenteIdAndAnoAndMes(entity.getId(), ano, mes)) {
            throw new RegraDeNegocioException("Este mês já está cancelado.");
        }

        RecorrenciaCanceladaEntity cancelada = new RecorrenciaCanceladaEntity();
        cancelada.setRecorrente(entity);
        cancelada.setAno(ano);
        cancelada.setMes(mes);
        canceladaRepository.save(cancelada);
        log.info("Recorrência {} cancelada para {}/{}", id, mes, ano);
    }

    @Transactional
    public void reativarMes(Long id, int ano, int mes, Long usuarioId) {
        buscarValidada(id, usuarioId);
        canceladaRepository.deleteByRecorrenteIdAndAnoAndMes(id, ano, mes);
        log.info("Recorrência {} reativada para {}/{}", id, mes, ano);
    }

    private TransacaoRecorrenteEntity buscarValidada(Long id, Long usuarioId) {
        TransacaoRecorrenteEntity entity = recorrenteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recorrência não encontrada"));
        if (!entity.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Recorrência não pertence ao usuário");
        }
        return entity;
    }
}
