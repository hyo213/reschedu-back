package com.academy.reschedu.global.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT를 httpOnly 쿠키로 발급/만료시키기 위한 컴포넌트.
 * 토큰을 JS에서 읽을 수 있는 sessionStorage 등에 두지 않고 브라우저가 자동으로만 전송하도록 하여
 * XSS를 통한 토큰 탈취를 막는다.
 */
@Component
public class JwtCookieProvider {

    public static final String COOKIE_NAME = "access_token";

    private final long accessTokenValidityMs;

    public JwtCookieProvider(@Value("${jwt.access-token-validity-ms}") long accessTokenValidityMs) {
        this.accessTokenValidityMs = accessTokenValidityMs;
    }

    public ResponseCookie createAccessTokenCookie(String token) {
        return baseCookie(token)
                .maxAge(Duration.ofMillis(accessTokenValidityMs))
                .build();
    }

    public ResponseCookie createExpiredAccessTokenCookie() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                // 🚨 운영 환경(HTTPS)에서는 반드시 true로 배포해야 한다. 로컬 개발은 http://localhost 이므로 false.
                .secure(false)
                .sameSite("Lax")
                .path("/");
    }
}
