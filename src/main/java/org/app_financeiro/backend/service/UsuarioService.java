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
     * - Email deve ser único
     * - Senha é hasheada com BCrypt antes de salvar
     * - emailVerificado começa como false (precisa verificar por código)
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
     * Autentica o usuário por email e senha.
     * Regras:
     * - Email deve existir no banco
     * - Senha deve conferir com o hash
     * - Email deve estar verificado (emailVerificado == true)
     * - Usuário deve estar ativo
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
     * Busca um usuário ativo por e-mail.
     * Retorna a Entity (uso interno entre Services).
     */
    public UsuarioEntity buscarPorEmail(String email) {
        return usuarioRepository.findByEmailAndAtivoTrue(email).orElseThrow(() -> new RecursoNaoEncontradoException("Email não encontrado"));
    }

    /**
     * Busca um usuário ativo por ID ou lança exceção.
     * Uso: chamado pelos outros Services para validar que o usuarioId existe.
     *
     * @param usuarioId ID do usuário a ser buscado.
     * @return O usuário ativo encontrado.
     * @throws RecursoNaoEncontradoException se o usuário não for encontrado ou estiver inativo.
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
     * Desativa (soft delete) a conta do usuário.
     *
     * @param usuarioId ID do usuário cuja conta será desativada.
     */
    public void desativarConta(Long usuarioId) {
        UsuarioEntity usuario = buscarPorIdOuFalhar(usuarioId);

        usuario.setAtivo(false);

        usuarioRepository.save(usuario);
    }
}
