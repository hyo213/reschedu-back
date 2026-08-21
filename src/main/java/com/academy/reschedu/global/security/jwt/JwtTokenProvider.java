package com.academy.reschedu.global.security.jwt;

import com.academy.reschedu.domain.member.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final long accessTokenValidityMs;
    private final SecretKey secretKey;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKeyRaw,
            @Value("${jwt.access-token-validity-ms}") long accessTokenValidityMs
    ) {
        this.accessTokenValidityMs = accessTokenValidityMs;
        byte[] keyBytes = Decoders.BASE64.decode(secretKeyRaw);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(String email, MemberRole role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenValidityMs);

        return Jwts.builder()
                .subject(email)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public MemberRole getRole(String token) {
        String role = parseClaims(token).get("role", String.class);
        return MemberRole.valueOf(role);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}