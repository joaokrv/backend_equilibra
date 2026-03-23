package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.mapper.UsuarioMapper;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CredenciaisInvalidasException;
import org.app_financeiro.backend.exception.EmailJaCadastradoException;
import org.app_financeiro.backend.exception.EmailNaoVerificadoException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço responsável por gerenciar a lógica de negócios relacionada aos Usuários.
 * Lida com registro, autenticação e validações de estado do usuário no sistema.
 */
@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, UsuarioMapper usuarioMapper) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioMapper = usuarioMapper;
    }

    /**
     * Registra um novo usuário no sistema.
     * Regras:
     * - Email deve ser único (inclui e-mails de contas inativas).
     * - Senha é submetida a hash via Argon2 + Pepper antes de salvar no banco.
     * - emailVerificado começa como false (precisa ser verificado via código de e-mail).
     *
     * @param dto DTO contendo dados de registro do usuário
     * @return DTO com os dados do usuário recém-criado
     * @throws EmailJaCadastradoException se o e-mail já existir no banco
     */
    @Transactional
    public UsuarioResponseDTO registrarUsuario(UsuarioRegistroRequestDTO dto) {

        if (usuarioRepository.existsByEmailIncludingInactive(dto.email())) {
            log.warn("Tentativa de registro com e-mail já cadastrado: {}", dto.email());
            throw new EmailJaCadastradoException();
        }

        String senhaCriptografada = passwordEncoder.encode(dto.senha());

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome(dto.nome());
        usuario.setEmail(dto.email());
        usuario.setSenha(senhaCriptografada);

        UsuarioEntity savedUser = usuarioRepository.save(usuario);

        log.info("Usuário registrado com sucesso: id={}, email={}", savedUser.getId(), savedUser.getEmail());
        return usuarioMapper.toResponse(savedUser);
    }

    /**
     * Autentica o usuário por e-mail e senha.
     * Regras:
     * - Email deve existir no banco de dados.
     * - Senha fornecida deve corresponder ao hash salvo.
     * - E-mail do usuário deve estar verificado (emailVerificado == true).
     * - A conta do usuário não pode estar desativada.
     *
     * @param email E-mail de login
     * @param senha Senha em texto plano a ser validada
     * @return DTO com os dados do usuário logado
     * @throws CredenciaisInvalidasException se o e-mail não existir, senha estiver errada ou usuário inativo
     * @throws EmailNaoVerificadoException se o e-mail ainda não tiver sido verificado com o código OTP
     */
    public UsuarioResponseDTO loginUsuario(String email, String senha) {
        UsuarioEntity usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
            log.warn("Tentativa de login com senha inválida para e-mail: {}", email);
            throw new CredenciaisInvalidasException();
        }

        if (!usuario.isEmailVerificado()) {
            log.warn("Tentativa de login com e-mail não verificado: {}", email);
            throw new EmailNaoVerificadoException();
        }

        return usuarioMapper.toResponse(usuario);
    }

    /**
     * Busca um usuário ativo no banco de dados pelo e-mail.
     * Método utilitário para uso interno entre os Services.
     *
     * @param email E-mail exato do usuário
     * @return A Entidade do Usuário
     * @throws RecursoNaoEncontradoException se não encontrar o e-mail
     */
    public UsuarioEntity buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email).orElseThrow(() -> new RecursoNaoEncontradoException("Email não encontrado"));
    }

    /**
     * Busca um usuário ativo por ID e garante sua existência antes de prosseguir.
     * Muito utilizado pelos outros Services (Conta, Cartão, Transação) para garantir que
     * a operação está sendo feita por um usuário válido e com sessão ativa.
     *
     * @param usuarioId ID do usuário a ser buscado no banco de dados
     * @return A Entidade do Usuário
     * @throws RecursoNaoEncontradoException se o usuário não existir ou se a conta estiver inativa
     */
    public UsuarioEntity buscarPorIdOuFalhar(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    /**
     * Desativa a conta do usuário através do processo de Soft Delete.
     * O registro continua no banco de dados, mas o campo 'ativo' é setado para false.
     *
     * @param usuarioId ID do usuário cuja conta será desativada.
     * @throws RecursoNaoEncontradoException se o usuário não existir ou já estiver inativo
     */
    @Transactional
    public void desativarConta(Long usuarioId) {
        UsuarioEntity usuario = buscarPorIdOuFalhar(usuarioId);

        usuario.setAtivo(false);

        usuarioRepository.save(usuario);
        log.info("Conta desativada (soft delete): usuarioId={}", usuarioId);
    }

    /**
     * Reativa uma conta previamente desativada (soft delete).
     * Valida a senha do usuário antes de reativar para confirmar identidade.
     *
     * @param email E-mail da conta a reativar
     * @param senha Senha para confirmar identidade
     * @throws RecursoNaoEncontradoException se não existir conta inativa com esse e-mail
     * @throws CredenciaisInvalidasException se a senha estiver incorreta
     */
    @Transactional
    public void reativarConta(String email, String senha) {
        UsuarioEntity usuario = usuarioRepository.findInactiveByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nenhuma conta inativa encontrada para este e-mail"));

        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
            throw new CredenciaisInvalidasException();
        }

        usuario.setAtivo(true);
        usuarioRepository.save(usuario);
        log.info("Conta reativada: usuarioId={}, email={}", usuario.getId(), email);
    }
}
