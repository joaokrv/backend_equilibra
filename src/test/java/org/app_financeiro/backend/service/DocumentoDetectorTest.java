package org.app_financeiro.backend.service;

import org.app_financeiro.backend.enums.FormatoDetectado;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Detecção de formato via magic bytes — nunca pela extensão declarada.
 * Casos de segurança: spoofing de extensão e limite de tamanho antes de ler o conteúdo.
 */
class DocumentoDetectorTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 conteudo qualquer".getBytes(StandardCharsets.UTF_8);

    private DocumentoDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DocumentoDetector();
    }

    @Test
    @DisplayName("PDF com 'fatura' no nome → PDF_FATURA")
    void devDetectarPdfFatura() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "fatura_marco.pdf",
                "application/pdf", PDF_BYTES);

        assertThat(detector.detectar(arquivo)).isEqualTo(FormatoDetectado.PDF_FATURA);
    }

    @Test
    @DisplayName("PDF sem 'fatura' no nome → PDF_EXTRATO")
    void deveDetectarPdfExtrato() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato_marco.pdf",
                "application/pdf", PDF_BYTES);

        assertThat(detector.detectar(arquivo)).isEqualTo(FormatoDetectado.PDF_EXTRATO);
    }

    @Test
    @DisplayName("Nome com 'FATURA' em maiúsculas ainda é detectado (case-insensitive)")
    void deveDetectarFaturaCaseInsensitive() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "FATURA_MARCO.PDF",
                "application/pdf", PDF_BYTES);

        assertThat(detector.detectar(arquivo)).isEqualTo(FormatoDetectado.PDF_FATURA);
    }

    @Test
    @DisplayName("SEGURANÇA: arquivo .txt cujo conteúdo real é PDF é detectado pelos magic bytes, não pela extensão")
    void deveIgnorarExtensaoESeguirMagicBytes() {
        MockMultipartFile arquivoSpoofed = new MockMultipartFile("arquivo", "Extrato Sicredi.txt",
                "text/plain", PDF_BYTES);

        assertThat(detector.detectar(arquivoSpoofed)).isEqualTo(FormatoDetectado.PDF_EXTRATO);
    }

    @Test
    @DisplayName("Nome de arquivo nulo com bytes PDF não quebra e cai em PDF_EXTRATO")
    void develidarComNomeDeArquivoNulo() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", null, "application/pdf", PDF_BYTES);

        assertThat(detector.detectar(arquivo)).isEqualTo(FormatoDetectado.PDF_EXTRATO);
    }

    @Test
    @DisplayName("CSV identificado por content-type text/csv")
    void deveDetectarCsvPorContentType() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato",
                "text/csv", "linha;linha;linha".getBytes(StandardCharsets.UTF_8));

        assertThat(detector.detectar(arquivo)).isEqualTo(FormatoDetectado.CSV);
    }

    @Test
    @DisplayName("CSV identificado pela extensão .csv quando content-type é genérico")
    void deveDetectarCsvPorExtensaoQuandoContentTypeGenerico() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato.csv",
                "application/octet-stream", "linha;linha;linha".getBytes(StandardCharsets.UTF_8));

        assertThat(detector.detectar(arquivo)).isEqualTo(FormatoDetectado.CSV);
    }

    @Test
    @DisplayName("Formato não reconhecido (sem magic bytes PDF, sem indício de CSV) → RegraDeNegocioException")
    void deveRejeitarFormatoNaoSuportado() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "imagem.png",
                "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        assertThatThrownBy(() -> detector.detectar(arquivo))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("SEGURANÇA: arquivo acima de 10MB é rejeitado antes de ler o conteúdo")
    void deveRejeitarArquivoAcimaDoTamanhoMaximo() {
        byte[] grande = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato.csv",
                "text/csv", grande);

        assertThatThrownBy(() -> detector.detectar(arquivo))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    @DisplayName("Arquivo vazio não quebra o detector — cai em formato não suportado")
    void deveTratarArquivoVazioSemQuebrar() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "vazio.csv", "text/csv", new byte[0]);

        assertThat(detector.detectar(arquivo)).isEqualTo(FormatoDetectado.CSV);
    }
}
