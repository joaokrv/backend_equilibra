package org.app_financeiro.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
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

    private static final long CLEANUP_INTERVAL_REQUESTS = 100;

    private final Map<String, BucketEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final boolean enabled;
    private final boolean trustForwardedFor;
    private final int maxBuckets;
    private final long idleTtlMillis;

    public RateLimitInterceptor(
            @Value("${security.rate-limit.enabled:true}") boolean enabled,
            @Value("${security.trust-forwarded-for:false}") boolean trustForwardedFor,
            @Value("${security.rate-limit.max-buckets:10000}") int maxBuckets,
            @Value("${security.rate-limit.bucket-idle-ttl-minutes:120}") long bucketIdleTtlMinutes) {
        this.enabled = enabled;
        this.trustForwardedFor = trustForwardedFor;
        this.maxBuckets = maxBuckets;
        this.idleTtlMillis = Duration.ofMinutes(bucketIdleTtlMinutes).toMillis();
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled) {
            return true;
        }

        String ip = extractClientIp(request);

        Bucket bucket = resolveBucket(ip);

        if (bucket.tryConsume(1)) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"erro\":\"Muitas tentativas simultâneas. O bloqueio temporário ativo na rede para sua segurança. Aguarde um minuto.\"}");
        
        return false;
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
                || remoteAddr.startsWith("172.16.")
                || remoteAddr.startsWith("172.17.")
                || remoteAddr.startsWith("172.18.")
                || remoteAddr.startsWith("172.19.")
                || remoteAddr.startsWith("172.2")
                || remoteAddr.startsWith("172.30.")
                || remoteAddr.startsWith("172.31.")
                || remoteAddr.startsWith("fc")
                || remoteAddr.startsWith("fd");
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // Aceita IPv4 e IPv6 textuais simples. Evita valores malformados no header.
        return ip.matches("^[0-9a-fA-F:.]{2,45}$");
    }

    private void periodicCleanup(long now) {
        long count = requestCounter.incrementAndGet();
        if (count % CLEANUP_INTERVAL_REQUESTS != 0) {
            return;
        }

        long minAccess = now - idleTtlMillis;
        cache.entrySet().removeIf(entry -> entry.getValue().lastAccessAt() < minAccess);

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
