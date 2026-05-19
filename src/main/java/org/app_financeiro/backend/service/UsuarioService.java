package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.AlterarSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioAtualizacaoRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.mapper.UsuarioMapper;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CredenciaisInvalidasException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.repository.CategoriaRepository;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.TokenRecuperacaoSenhaRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.app_financeiro.backend.entity.UsuarioPendenteEntity;

import java.util.List;
import org.app_financeiro.backend.dto.response.PerfilResumoResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

/** Gerencia usuários: registro, autenticação, perfil e ciclo de vida da conta. */
@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;
    private final TransacaoRepository transacaoRepository;
    private final ContaRepository contaRepository;
    private final InvestimentoRepository investimentoRepository;
    private final CategoriaRepository categoriaRepository;
    private final TokenRecuperacaoSenhaRepository tokenRecuperacaoSenhaRepository;
    private final CodigoVerificacaoRepository codigoVerificacaoRepository;
    private final UsuarioPendenteRepository usuarioPendenteRepository;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          UsuarioMapper usuarioMapper,
                          TransacaoRepository transacaoRepository,
                          ContaRepository contaRepository,
                          InvestimentoRepository investimentoRepository,
                          CategoriaRepository categoriaRepository,
                          TokenRecuperacaoSenhaRepository tokenRecuperacaoSenhaRepository,
                          CodigoVerificacaoRepository codigoVerificacaoRepository,
                          UsuarioPendenteRepository usuarioPendenteRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioMapper = usuarioMapper;
        this.transacaoRepository = transacaoRepository;
        this.contaRepository = contaRepository;
        this.categoriaRepository = categoriaRepository;
        this.investimentoRepository = investimentoRepository;
        this.tokenRecuperacaoSenhaRepository = tokenRecuperacaoSenhaRepository;
        this.codigoVerificacaoRepository = codigoVerificacaoRepository;
        this.usuarioPendenteRepository = usuarioPendenteRepository;
    }

    /** Anti-enumeração: retorna false silenciosamente se e-mail já cadastrado (B1-A2). */
    @Transactional
    public boolean registrarUsuario(UsuarioRegistroRequestDTO dto) {
        if (usuarioRepository.existsByEmailIncludingInactive(dto.email())) {
            log.warn("Tentativa de registro com e-mail já cadastrado (silenciado)");
            return false;
        }

        String senhaCriptografada = passwordEncoder.encode(dto.senha());

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setSenha(senhaCriptografada);

        UsuarioEntity savedUser = usuarioRepository.save(usuario);
        criarCategoriasPadrao(savedUser);

        log.info("Usuário registrado com sucesso: id={}, email={}", savedUser.getId(), org.app_financeiro.backend.util.EmailMasker.mascarar(savedUser.getEmail()));
        return true;
    }

    /**
     * Finaliza o registro de um usuário a partir dos dados do pré-cadastro validado.
     */
    @Transactional
    public UsuarioEntity finalizarRegistro(UsuarioPendenteEntity pendente) {
        if (usuarioRepository.existsByEmailIncludingInactive(pendente.getEmail())) {
            throw new RegraDeNegocioException("Este e-mail já possui uma conta ativa.");
        }

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome(pendente.getNome());
        usuario.setEmail(pendente.getEmail());
        usuario.setSenha(pendente.getSenhaHash());
        usuario.setEmailVerificado(true);

        UsuarioEntity savedUser = usuarioRepository.save(usuario);
        criarCategoriasPadrao(savedUser);

        log.info("Usuário finalizado via pré-registro: id={}, email={}", savedUser.getId(), org.app_financeiro.backend.util.EmailMasker.mascarar(savedUser.getEmail()));
        return savedUser;
    }

    public UsuarioResponseDTO loginUsuario(String email, String senha) {
        UsuarioEntity usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
            log.warn("Tentativa de login com senha inválida para e-mail: {}", org.app_financeiro.backend.util.EmailMasker.mascarar(email));
            throw new CredenciaisInvalidasException();
        }

        return usuarioMapper.toResponse(usuario);
    }

    public UsuarioEntity buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Email não encontrado"));
    }

    @Transactional(readOnly = true)
    public UsuarioEntity buscarPorIdOuFalhar(Long usuarioId) {
        if (usuarioId == null) {
            throw new RegraDeNegocioException("ID de usuário não pode ser nulo");
        }
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    @Transactional
    public void desativarConta(Long usuarioId) {
        UsuarioEntity usuario = buscarPorIdOuFalhar(usuarioId);
        usuario.setAtivo(false);
        usuario.setChaveSessao(null);

        usuarioRepository.save(usuario);
        log.info("Conta desativada (soft delete): usuarioId={}", usuarioId);
    }

    /**
     * Exclusao definitiva com limpeza de tabelas que nao possuem FK para usuarios.
     */
    @Transactional
    public void excluirConta(Long usuarioId, String email) {
        UsuarioEntity usuario = buscarPorIdOuFalhar(usuarioId);

        String emailNormalizado = (email != null ? email : usuario.getEmail());
        if (emailNormalizado != null && !emailNormalizado.isBlank()) {
            tokenRecuperacaoSenhaRepository.deleteByEmail(emailNormalizado);
            codigoVerificacaoRepository.deleteByEmail(emailNormalizado);
            usuarioPendenteRepository.deleteByEmail(emailNormalizado);
        }

        usuarioRepository.deleteById(usuarioId);
        log.info("Conta excluida (hard delete): usuarioId={}, email={}", usuarioId, org.app_financeiro.backend.util.EmailMasker.mascarar(emailNormalizado));
    }

    @Transactional
    public void reativarConta(String email, String senha) {
        UsuarioEntity usuario = usuarioRepository.findInactiveByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nenhuma conta inativa encontrada para este e-mail"));

        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
            throw new CredenciaisInvalidasException();
        }

        usuario.setAtivo(true);
        usuarioRepository.save(usuario);
        log.info("Conta reativada: usuarioId={}, email={}", usuario.getId(), org.app_financeiro.backend.util.EmailMasker.mascarar(email));
    }

    @Transactional
    public UsuarioResponseDTO atualizarPerfil(Long usuarioId, UsuarioAtualizacaoRequestDTO dto) {
        UsuarioEntity usuario = buscarPorIdOuFalhar(usuarioId);

        if (dto.celular() != null && !dto.celular().isBlank()) {
            usuarioRepository.findByCelular(dto.celular())
                    .filter(outro -> !outro.getId().equals(usuarioId))
                    .ifPresent(outro -> {
                        log.warn("Tentativa de atualização de perfil com celular já em uso: {}", dto.celular());
                        throw new RegraDeNegocioException("Este número de celular já está vinculado a outra conta");
                    });
            usuario.setCelular(dto.celular());
        }

        usuario.setNome(dto.nome());
        usuario.setMoeda(dto.moeda());

        UsuarioEntity salvo = usuarioRepository.save(usuario);
        log.info("Perfil atualizado com sucesso: usuarioId={}, moeda={}", usuarioId, dto.moeda());

        return usuarioMapper.toResponse(salvo);
    }

    @Transactional
    public void atualizarFoto(Long usuarioId, MultipartFile file) {
        UsuarioEntity usuario = buscarPorIdOuFalhar(usuarioId);

        if (file.getSize() > 2 * 1024 * 1024) {
            throw new RegraDeNegocioException("A imagem é muito grande. Máximo de 2MB permitido.");
        }

        validarAssinaturaImagem(file);

        try {
            usuario.setFoto(file.getBytes());
            usuarioRepository.save(usuario);
            log.info("Foto de perfil atualizada: usuarioId={}, size={} bytes", usuarioId, file.getSize());
        } catch (IOException e) {
            log.error("Erro ao processar upload de foto para usuário {}: {}", usuarioId, e.getMessage());
            throw new RegraDeNegocioException("Erro ao processar o arquivo de imagem");
        }
    }

    /**
     * Valida a assinatura binária (Magic Bytes) do arquivo para garantir que seja JPEG ou PNG.
     * Protege contra ataques de spoofing de extensão.
     */
    private void validarAssinaturaImagem(MultipartFile file) {
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            int bytesRead = is.read(header);

            if (bytesRead < 3) {
                throw new RegraDeNegocioException("Arquivo de imagem corrompido ou muito curto.");
            }

            boolean isJpeg = (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            boolean isPng = (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 && (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47;

            if (!isJpeg && !isPng) {
                log.warn("Tentativa de upload de arquivo com formato inválido interceptada (Magic Bytes não conferem).");
                throw new RegraDeNegocioException("Formato de arquivo inválido. Apenas JPEG e PNG são permitidos.");
            }
        } catch (IOException e) {
            throw new RegraDeNegocioException("Erro ao ler assinatura do arquivo.");
        }
    }

    /** Invalida todas as sessões ativas ao limpar a chaveSessao após troca de senha. */
    @Transactional
    public void alterarSenha(Long usuarioId, AlterarSenhaRequestDTO dto) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenha())) {
            throw new CredenciaisInvalidasException();
        }

        if (passwordEncoder.matches(dto.novaSenha(), usuario.getSenha())) {
            throw new RegraDeNegocioException("A nova senha deve ser diferente da atual.");
        }

        usuario.setSenha(passwordEncoder.encode(dto.novaSenha()));
        usuario.setChaveSessao(null);
        usuarioRepository.save(usuario);

        log.info("Senha alterada: usuarioId={}", usuarioId);
    }

    private void criarCategoriasPadrao(UsuarioEntity usuario) {
        List<String> despesas = List.of("Aluguel", "Água", "Luz", "Internet", "Gás", "Condomínio", "Transporte", "Alimentação", "Saúde", "Educação", "Investimento");
        List<String> receitas = List.of("Salário", "Vale Alimentação", "Vale Transporte", "Freelance", "Rendimentos");

        for (String nome : despesas) {
            CategoriaEntity cat = new CategoriaEntity();
            cat.setNome(nome);
            cat.setTipo(TipoTransacao.DESPESA);
            cat.setUsuario(usuario);
            cat.setPadrao(true);
            categoriaRepository.save(cat);
        }
        for (String nome : receitas) {
            CategoriaEntity cat = new CategoriaEntity();
            cat.setNome(nome);
            cat.setTipo(TipoTransacao.RECEITA);
            cat.setUsuario(usuario);
            cat.setPadrao(true);
            categoriaRepository.save(cat);
        }
        log.info("Categorias padrão criadas para usuário {}", usuario.getId());
    }

    public PerfilResumoResponseDTO obterResumoFinanceiro(Long usuarioId) {
        BigDecimal receitas = transacaoRepository.somarReceitasPorUsuario(usuarioId);
        BigDecimal despesas = transacaoRepository.somarDespesasPorUsuario(usuarioId);
        BigDecimal saldoContas = contaRepository.somarSaldoPorUsuario(usuarioId);
        BigDecimal investido = investimentoRepository.somarTotalInvestidoPorUsuario(usuarioId);

        return new PerfilResumoResponseDTO(
            receitas != null ? receitas : BigDecimal.ZERO,
            despesas != null ? despesas : BigDecimal.ZERO,
            saldoContas != null ? saldoContas : BigDecimal.ZERO,
            investido != null ? investido : BigDecimal.ZERO,
            0.0
        );
    }
}
