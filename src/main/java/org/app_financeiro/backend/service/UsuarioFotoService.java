package org.app_financeiro.backend.service;

import org.app_financeiro.backend.entity.UsuarioFotoEntity;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.UsuarioFotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** CRUD da foto de perfil (tabela usuario_foto), isolada do UsuarioEntity. */
@Service
public class UsuarioFotoService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioFotoService.class);
    private static final long TAMANHO_MAXIMO_BYTES = 2L * 1024 * 1024;

    private final UsuarioFotoRepository usuarioFotoRepository;

    public UsuarioFotoService(UsuarioFotoRepository usuarioFotoRepository) {
        this.usuarioFotoRepository = usuarioFotoRepository;
    }

    @Transactional
    public void atualizar(Long usuarioId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RegraDeNegocioException("error.imagem.corrompida", "Arquivo de imagem corrompido ou muito curto.");
        }
        if (file.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new RegraDeNegocioException("error.imagem.muito_grande", "A imagem é muito grande. Máximo de 2MB permitido.");
        }

        byte[] dados = lerBytes(usuarioId, file);
        String contentType = detectarTipoImagem(dados);

        UsuarioFotoEntity entidade = usuarioFotoRepository.findById(usuarioId)
                .orElseGet(UsuarioFotoEntity::new);
        entidade.setUsuarioId(usuarioId);
        entidade.setFoto(dados);
        entidade.setContentType(contentType);
        usuarioFotoRepository.save(entidade);
        log.info("Foto de perfil atualizada: usuarioId={}, size={} bytes, type={}", usuarioId, dados.length, contentType);
    }

    private byte[] lerBytes(Long usuarioId, MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("Erro ao processar upload de foto para usuário {}: {}", usuarioId, e.getMessage());
            throw new RegraDeNegocioException("error.imagem.processamento", "Erro ao processar o arquivo de imagem");
        }
    }

    @Transactional(readOnly = true)
    public UsuarioFotoEntity obter(Long usuarioId) {
        return usuarioFotoRepository.findById(usuarioId).orElse(null);
    }

    @Transactional
    public void remover(Long usuarioId) {
        if (usuarioFotoRepository.existsById(usuarioId)) {
            usuarioFotoRepository.deleteById(usuarioId);
            log.info("Foto de perfil removida: usuarioId={}", usuarioId);
        }
    }

    /**
     * Valida a assinatura binária (magic bytes) e retorna o content-type detectado.
     * Protege contra spoofing de extensão — apenas JPEG e PNG são aceitos.
     */
    private String detectarTipoImagem(byte[] dados) {
        if (dados.length < 3) {
            throw new RegraDeNegocioException("error.imagem.corrompida", "Arquivo de imagem corrompido ou muito curto.");
        }

        boolean isJpeg = (dados[0] & 0xFF) == 0xFF && (dados[1] & 0xFF) == 0xD8 && (dados[2] & 0xFF) == 0xFF;
        boolean isPng = dados.length >= 4 && (dados[0] & 0xFF) == 0x89 && (dados[1] & 0xFF) == 0x50
                && (dados[2] & 0xFF) == 0x4E && (dados[3] & 0xFF) == 0x47;

        if (isJpeg) return "image/jpeg";
        if (isPng) return "image/png";

        log.warn("Upload de imagem com formato inválido interceptado (magic bytes não conferem).");
        throw new RegraDeNegocioException("error.imagem.formato_invalido", "Formato de arquivo inválido. Apenas JPEG e PNG são permitidos.");
    }
}
