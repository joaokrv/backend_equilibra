package org.app_financeiro.backend.service;

import org.app_financeiro.backend.enums.FormatoDetectado;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Detecta o formato do documento via magic bytes, nunca pela extensão declarada.
 * Motivo: arquivos como "Extrato Sicredi.txt" são na verdade PDFs (%PDF-1.4).
 */
@Component
public class DocumentoDetector {

    private static final byte[] MAGIC_PDF  = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final int    CABECALHO  = 8;
    private static final int    TAMANHO_MAXIMO = 10 * 1024 * 1024; // 10MB

    public FormatoDetectado detectar(MultipartFile arquivo) {
        validarTamanho(arquivo);
        byte[] inicio = lerInicio(arquivo);
        return classificar(inicio, arquivo);
    }

    private void validarTamanho(MultipartFile arquivo) {
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException(
                    "error.importacao.arquivo_invalido",
                    "O arquivo excede o tamanho máximo de 10MB.");
        }
    }

    private byte[] lerInicio(MultipartFile arquivo) {
        try (InputStream is = arquivo.getInputStream()) {
            return is.readNBytes(CABECALHO);
        } catch (IOException e) {
            throw new RegraDeNegocioException(
                    "error.importacao.arquivo_invalido",
                    "Não foi possível ler o arquivo enviado.");
        }
    }

    private FormatoDetectado classificar(byte[] inicio, MultipartFile arquivo) {
        if (isPdf(inicio)) {
            return classificarPdf(arquivo);
        }
        if (isCsv(inicio, arquivo)) {
            return FormatoDetectado.CSV;
        }
        throw new RegraDeNegocioException(
                "error.importacao.formato_nao_suportado",
                "Formato de arquivo não suportado. Envie um PDF ou CSV.");
    }

    private boolean isPdf(byte[] inicio) {
        if (inicio.length < MAGIC_PDF.length) return false;
        for (int i = 0; i < MAGIC_PDF.length; i++) {
            if (inicio[i] != MAGIC_PDF[i]) return false;
        }
        return true;
    }

    /** CSV: magic bytes ausentes — valida pela ausência de PDF e pelo content type declarado ou extensão. */
    private boolean isCsv(byte[] inicio, MultipartFile arquivo) {
        String contentType = arquivo.getContentType();
        String nome = arquivo.getOriginalFilename() != null
                ? arquivo.getOriginalFilename().toLowerCase()
                : "";
        return (contentType != null && (contentType.contains("csv") || contentType.contains("text")))
                || nome.endsWith(".csv");
    }

    private FormatoDetectado classificarPdf(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename() != null
                ? arquivo.getOriginalFilename().toLowerCase()
                : "";
        if (nome.contains("fatura")) {
            return FormatoDetectado.PDF_FATURA;
        }
        return FormatoDetectado.PDF_EXTRATO;
    }
}
