package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ReenviarCodigoRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsável pelo fluxo de verificação de e-mail.
 *
 * Fluxo:
 * 1. Após registro, gerar código de 6 dígitos e salvar em CodigoVerificacaoEntity
 * 2. Enviar código por e-mail (quando o JavaMailSender for configurado)
 * 3. Usuário envia o código recebido para validação
 * 4. Se válido e não expirado, marca emailVerificado = true no UsuarioEntity
 */
@Service
public class EmailVerificacaoService {

    private final CodigoVerificacaoRepository codigoVerificacaoRepository;
    private final UsuarioRepository usuarioRepository;

    public EmailVerificacaoService(CodigoVerificacaoRepository codigoVerificacaoRepository,
                                   UsuarioRepository usuarioRepository) {
        this.codigoVerificacaoRepository = codigoVerificacaoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Gera um código de 6 dígitos aleatório e salva no banco vinculado ao e-mail.
     * O código expira em 15 minutos.
     *
     * REGRAS que você deve implementar:
     * - Gerar código numérico aleatório de 6 dígitos (ex: "482917")
     * - Criar CodigoVerificacaoEntity com dataExpiracao = agora + 15 minutos
     * - Salvar no repository
     * - Retornar o código gerado (para uso futuro com envio de e-mail)
     *
     * DICA: Use java.util.Random ou SecureRandom para gerar. String.format("%06d", numero)
     */
    @Transactional
    public String gerarCodigo(String email) {
        // TODO: Implementar - gerar código de 6 dígitos, salvar com expiração de 15min
        return null;
    }

    /**
     * Valida o código de verificação enviado pelo usuário.
     *
     * REGRAS que você deve implementar:
     * - Buscar código no repository por email + codigo + não utilizado
     * - Se não encontrar: lançar CodigoVerificacaoInvalidoException("Código inválido ou já utilizado")
     * - Se expirado (dataExpiracao < agora): lançar CodigoVerificacaoInvalidoException("Código expirado...")
     * - Marcar código como utilizado = true
     * - Buscar o UsuarioEntity pelo e-mail
     * - Setar emailVerificado = true no usuário
     * - Salvar ambos (código e usuário)
     */
    @Transactional
    public void verificarEmail(VerificarEmailRequestDTO dto) {
        // TODO: Implementar - validar código, marcar como usado, verificar e-mail do usuário
    }

    /**
     * Reenvia um novo código de verificação para o e-mail informado.
     *
     * REGRAS que você deve implementar:
     * - Verificar se o e-mail existe no sistema (lançar RecursoNaoEncontradoException se não)
     * - Verificar se o e-mail já está verificado (lançar RegraDeNegocioException se já está)
     * - Invalidar códigos anteriores (marcar como utilizado = true) - OPCIONAL
     * - Gerar novo código (chamar gerarCodigo)
     * - Futuramente: enviar por e-mail
     */
    @Transactional
    public void reenviarCodigo(ReenviarCodigoRequestDTO dto) {
        // TODO: Implementar - validar, invalidar antigos, gerar novo código
    }
}
