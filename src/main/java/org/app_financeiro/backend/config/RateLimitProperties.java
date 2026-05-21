package org.app_financeiro.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades tipadas do rate limiting (prefixo security.rate-limit).
 * Substitui múltiplos @Value no RateLimitInterceptor por bind validado no boot.
 *
 * Variáveis de ambiente mapeadas em application.properties:
 *   SECURITY_RATE_LIMIT_ENABLED                       → enabled
 *   SECURITY_RATE_LIMIT_MAX_BUCKETS                   → maxBuckets
 *   SECURITY_RATE_LIMIT_BUCKET_IDLE_TTL_MINUTES       → bucketIdleTtlMinutes
 */
@ConfigurationProperties(prefix = "security.rate-limit")
@Validated
public record RateLimitProperties(
        boolean enabled,
        @Min(1) int maxBuckets,
        @Min(1) long bucketIdleTtlMinutes
) {
    public RateLimitProperties {
        if (maxBuckets <= 0) {
            maxBuckets = 10_000;
        }
        if (bucketIdleTtlMinutes <= 0) {
            bucketIdleTtlMinutes = 120;
        }
    }
}
