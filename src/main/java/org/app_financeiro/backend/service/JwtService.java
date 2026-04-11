package org.app_financeiro.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Serviço responsável pela geração e validação de tokens JWT.
 *
 * Gera dois tipos de token:
 * - Access Token: curta duração (configurável via jwt.access-token-expiration)
 * - Refresh Token: longa duração (configurável via jwt.refresh-token-expiration)
 *
 * Ambos os tokens incluem o usuarioId como claim extra para fácil extração.
 */
@Service
public class JwtService {

    private static final Pattern HEX_64_PATTERN = Pattern.compile("^[0-9a-fA-F]{64,}$");

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUsuarioId(String token) {
        return extractClaim(token, claims -> claims.get("usuarioId", Long.class));
    }

    public String extractChaveSessao(String token) {
        return extractClaim(token, claims -> claims.get("chaveSessao", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Gera um Access Token (curta duração) para o usuário autenticado.
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (userDetails instanceof UsuarioEntity usuario) {
            extraClaims.put("usuarioId", usuario.getId());
            if (usuario.getChaveSessao() != null) {
                extraClaims.put("chaveSessao", usuario.getChaveSessao());
            }
        }
        return buildToken(extraClaims, userDetails, accessTokenExpiration);
    }

    /**
     * Gera um Refresh Token (longa duração) para o usuário autenticado.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (userDetails instanceof UsuarioEntity usuario) {
            extraClaims.put("usuarioId", usuario.getId());
            if (usuario.getChaveSessao() != null) {
                extraClaims.put("chaveSessao", usuario.getChaveSessao());
            }
        }
        return buildToken(extraClaims, userDetails, refreshTokenExpiration);
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equalsIgnoreCase(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes;

        // Aceita chave em HEX (preferencial no projeto) ou BASE64 por compatibilidade.
        if (secretKey != null && HEX_64_PATTERN.matcher(secretKey).matches()) {
            keyBytes = HexFormat.of().parseHex(secretKey);
        } else {
            keyBytes = Decoders.BASE64.decode(secretKey);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret deve ter pelo menos 256 bits (32 bytes)");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}
