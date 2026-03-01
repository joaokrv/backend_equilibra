package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CredenciaisInvalidasException;
import org.app_financeiro.backend.exception.EmailJaCadastradoException;
import org.app_financeiro.backend.exception.EmailNaoVerificadoException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável por gerenciar a lógica de negócios relacionada aos Usuários.
 * Lida com registro, autenticação e validações de estado do usuário no sistema.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registra um novo usuário no sistema.
     * Regras:
     * - Email deve ser único.
     * - Senha é submetida a hash via BCrypt antes de salvar no banco.
     * - emailVerificado começa como false (precisa ser verificado via código de e-mail).
     *
     * @param dto DTO contendo dados de registro do usuário
     * @return DTO com os dados do usuário recém-criado
     * @throws EmailJaCadastradoException se o e-mail já existir no banco
     */
    public UsuarioResponseDTO registrarUsuario(UsuarioRegistroRequestDTO dto) {

        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new EmailJaCadastradoException();
        }

        String senhaCriptografada = passwordEncoder.encode(dto.getSenha());

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setSenha(senhaCriptografada);

        UsuarioEntity savedUser = usuarioRepository.save(usuario);

        return new UsuarioResponseDTO(savedUser);
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
        UsuarioEntity usuario = usuarioRepository.findByEmailAndAtivoTrue(email)
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!passwordEncoder.matches(senha, usuario.getSenha())) {
            throw new CredenciaisInvalidasException();
        }

        if (!usuario.isEmailVerificado()) {
            throw new EmailNaoVerificadoException();
        }

        return new UsuarioResponseDTO(usuario);
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
        return usuarioRepository.findByEmailAndAtivoTrue(email).orElseThrow(() -> new RecursoNaoEncontradoException("Email não encontrado"));
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
        UsuarioEntity usuario =
                usuarioRepository.findById(usuarioId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        if (!usuario.isAtivo()) {
            throw new RecursoNaoEncontradoException("Usuário inativo.");
        }

        return usuario;
    }

    /**
     * Desativa a conta do usuário através do processo de Soft Delete.
     * O registro continua no banco de dados, mas o campo 'ativo' é setado para false.
     *
     * @param usuarioId ID do usuário cuja conta será desativada.
     */
    public void desativarConta(Long usuarioId) {
        UsuarioEntity usuario = buscarPorIdOuFalhar(usuarioId);

        usuario.setAtivo(false);

        usuarioRepository.save(usuario);
    }
}
