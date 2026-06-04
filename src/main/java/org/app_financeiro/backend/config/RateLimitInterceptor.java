package org.app_financeiro.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.app_financeiro.backend.exception.RateLimitExcedidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Token Bucket por IP (ConcurrentHashMap in-memory). Proteção cumulativa força-bruta + picos. */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final long CLEANUP_INTERVAL_REQUESTS = 100;

    static final String ESCOPO_GERAL = "geral";
    static final String ESCOPO_RELATORIO = "relatorio";
    static final String ESCOPO_AUTH = "auth";
    static final String ESCOPO_HEALTH = "health";
    static final String ESCOPO_ESCRITA = "escrita";

    /** Prefixos de endpoints CRUD autenticados — escritas (POST/PUT/DELETE) têm bucket dedicado. */
    private static final String[] PREFIXOS_CRUD = {
            "/api/transacoes", "/api/contas", "/api/cartoes",
            "/api/investimentos", "/api/faturas", "/api/categorias",
            "/api/transacoes-recorrentes"
    };

    private final Map<String, BucketEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> cacheRelatorio = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> cacheAuth = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> cacheHealth = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> cacheEscrita = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final RateLimitProperties properties;
    private final boolean trustForwardedFor;
    private final long idleTtlMillis;
    private final MeterRegistry meterRegistry;

    public RateLimitInterceptor(
            RateLimitProperties properties,
            MeterRegistry meterRegistry,
            @Value("${security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.trustForwardedFor = trustForwardedFor;
        this.idleTtlMillis = Duration.ofMinutes(properties.bucketIdleTtlMinutes()).toMillis();
    }

    private record BucketEntry(Bucket bucket, long lastAccessAt) {
        private BucketEntry touch(long now) {
            return new BucketEntry(bucket, now);
        }
    }

    private Bucket resolveBucket(String clientIp) {
        long now = System.currentTimeMillis();
        periodicCleanup(now);

        BucketEntry entry = cache.compute(clientIp, (ip, existing) -> {
            if (existing == null) {
                return new BucketEntry(newBucket(ip), now);
            }
            return existing.touch(now);
        });

        return entry.bucket();
    }

    private Bucket newBucket(String clientIp) {
        Bandwidth limit1 = Bandwidth.builder()
                .capacity(5)
                .refillIntervally(5, Duration.ofMinutes(1))
                .build();

        Bandwidth limit2 = Bandwidth.builder()
                .capacity(15)
                .refillIntervally(15, Duration.ofHours(1))
                .build();

        Bandwidth limit3 = Bandwidth.builder()
                .capacity(30)
                .refillIntervally(30, Duration.ofDays(1))
                .build();

        return Bucket.builder()
                .addLimit(limit1)
                .addLimit(limit2)
                .addLimit(limit3)
                .build();
    }

    private Bucket resolveBucketRelatorio(String clientIp) {
        long now = System.currentTimeMillis();

        BucketEntry entry = cacheRelatorio.compute(clientIp, (ip, existing) -> {
            if (existing == null) {
                return new BucketEntry(newBucketRelatorio(ip), now);
            }
            return existing.touch(now);
        });

        return entry.bucket();
    }

    private Bucket newBucketRelatorio(String clientIp) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(2)
                .refillIntervally(2, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket resolveBucketAuth(String clientIp) {
        long now = System.currentTimeMillis();
        BucketEntry entry = cacheAuth.compute(clientIp, (ip, existing) -> {
            if (existing == null) {
                return new BucketEntry(newBucketAuth(ip), now);
            }
            return existing.touch(now);
        });
        return entry.bucket();
    }

    private Bucket newBucketAuth(String clientIp) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(3)
                .refillIntervally(3, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket resolveBucketHealth(String clientIp) {
        long now = System.currentTimeMillis();
        BucketEntry entry = cacheHealth.compute(clientIp, (ip, existing) -> {
            if (existing == null) {
                return new BucketEntry(newBucketHealth(ip), now);
            }
            return existing.touch(now);
        });
        return entry.bucket();
    }

    private Bucket newBucketHealth(String clientIp) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillIntervally(10, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket resolveBucketEscrita(String clientIp) {
        long now = System.currentTimeMillis();
        BucketEntry entry = cacheEscrita.compute(clientIp, (ip, existing) -> {
            if (existing == null) {
                return new BucketEntry(newBucketEscrita(ip), now);
            }
            return existing.touch(now);
        });
        return entry.bucket();
    }

    /** Escritas CRUD: 40/min — suficiente para uso legítimo, bloqueia flood. */
    private Bucket newBucketEscrita(String clientIp) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(40)
                .refillIntervally(40, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean isCrudEscrita(String uri) {
        for (String prefixo : PREFIXOS_CRUD) {
            if (uri.startsWith(prefixo)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMetodoEscrita(String metodo) {
        return "POST".equals(metodo) || "PUT".equals(metodo)
                || "DELETE".equals(metodo) || "PATCH".equals(metodo);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.enabled()) {
            return true;
        }

        String ip = extractClientIp(request);
        String uri = request.getRequestURI();

        String escopo;
        Bucket bucket;
        if (uri.contains("/relatorios/exportar")) {
            bucket = resolveBucketRelatorio(ip);
            escopo = ESCOPO_RELATORIO;
        } else if (uri.equals("/actuator/health")) {
            bucket = resolveBucketHealth(ip);
            escopo = ESCOPO_HEALTH;
        } else if (uri.startsWith("/api/auth/")) {
            bucket = resolveBucketAuth(ip);
            escopo = ESCOPO_AUTH;
        } else if (isCrudEscrita(uri)) {
            // Endpoint CRUD: só escritas (POST/PUT/DELETE/PATCH) têm bucket dedicado.
            // Leituras (GET) não são limitadas aqui — navegação legítima seria penalizada.
            if (!isMetodoEscrita(request.getMethod())) {
                return true;
            }
            bucket = resolveBucketEscrita(ip);
            escopo = ESCOPO_ESCRITA;
        } else {
            bucket = resolveBucket(ip);
            escopo = ESCOPO_GERAL;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return true;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        incrementarMetricaBloqueio(escopo);
        log.warn("Rate limit excedido: escopo={}, uri={}, ip={}, retryAfterSeconds={}",
                escopo, uri, mascararIp(ip), retryAfterSeconds);

        throw new RateLimitExcedidoException(escopo, retryAfterSeconds);
    }

    private void incrementarMetricaBloqueio(String escopo) {
        Counter.builder("equilibra.ratelimit.blocked")
                .description("Total de requisições bloqueadas pelo rate limit, agrupado por escopo.")
                .tag("escopo", escopo)
                .register(meterRegistry)
                .increment();
    }

    /** Mascaramento simples para logs (LGPD): preserva primeiro e último octeto. */
    private String mascararIp(String ip) {
        if (ip == null || ip.length() < 4) {
            return "***";
        }
        int firstDot = ip.indexOf('.');
        int lastDot = ip.lastIndexOf('.');
        if (firstDot > 0 && lastDot > firstDot) {
            return ip.substring(0, firstDot) + ".***.***" + ip.substring(lastDot);
        }
        // IPv6 ou formato inesperado: mantém primeiros 4 chars
        return ip.substring(0, 4) + "***";
    }

    private String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustForwardedFor || !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || "unknown".equalsIgnoreCase(forwarded)) {
            return remoteAddr;
        }

        String candidate = forwarded.split(",")[0].trim();
        return isValidIp(candidate) ? candidate : remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }

        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || remoteAddr.startsWith("10.")
                || remoteAddr.startsWith("192.168.")
                || isPrivate172Range(remoteAddr)
                || remoteAddr.startsWith("fc")
                || remoteAddr.startsWith("fd");
    }

    private boolean isPrivate172Range(String remoteAddr) {
        if (!remoteAddr.startsWith("172.")) {
            return false;
        }
        int firstDot = 4;
        int secondDot = remoteAddr.indexOf('.', firstDot);
        if (secondDot < 0) {
            return false;
        }
        try {
            int second = Integer.parseInt(remoteAddr.substring(firstDot, secondDot));
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        return ip.matches("^[0-9a-fA-F:.]{2,45}$");
    }

    private void periodicCleanup(long now) {
        long count = requestCounter.incrementAndGet();
        if (count % CLEANUP_INTERVAL_REQUESTS != 0) {
            return;
        }

        long minAccess = now - idleTtlMillis;
        cache.entrySet().removeIf(entry -> entry.getValue().lastAccessAt() < minAccess);
        cacheRelatorio.entrySet().removeIf(entry -> entry.getValue().lastAccessAt() < minAccess);
        cacheAuth.entrySet().removeIf(entry -> entry.getValue().lastAccessAt() < minAccess);
        cacheHealth.entrySet().removeIf(entry -> entry.getValue().lastAccessAt() < minAccess);
        cacheEscrita.entrySet().removeIf(entry -> entry.getValue().lastAccessAt() < minAccess);

        int maxBuckets = properties.maxBuckets();
        if (cache.size() <= maxBuckets) {
            return;
        }

        int toRemove = cache.size() - maxBuckets;
        var oldest = new ArrayList<>(cache.entrySet());
        oldest.sort(Comparator.comparingLong(e -> e.getValue().lastAccessAt()));

        for (int i = 0; i < toRemove && i < oldest.size(); i++) {
            cache.remove(oldest.get(i).getKey());
        }
    }
}
