package org.app_financeiro.backend.dto.response;

public record JobResultDTO(
        int processed,
        int sent,
        int skipped,
        int errors
) {}
