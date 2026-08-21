package com.academy.reschedu.domain.member.dto;

/**
 * 로그인 처리 결과: 응답 바디로 내려갈 프로필 정보와, 컨트롤러가 httpOnly 쿠키로 담아 보낼 토큰을 분리해서 전달한다.
 * accessToken은 JSON 바디에 절대 포함되어서는 안 된다(XSS로 JS가 읽을 수 없게 하기 위함).
 */
public record LoginResult(LoginResponse profile, String accessToken) {
}
