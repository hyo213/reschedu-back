package com.academy.reschedu.global.security;

import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRepository;
import com.academy.reschedu.global.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * [CurrentMemberProvider] JWT 인증 컨텍스트로부터 현재 요청자의 Member 엔티티를 조회한다.
 *
 * 여러 서비스에서 "이 요청을 보낸 사람이 실제로 누구인가"를 검증할 때 반복적으로 필요했던
 * SecurityContext → 로그인 ID → Member 조회 로직을 한 곳으로 모아 재사용한다.
 * (로그인 ID는 이메일 또는 휴대폰 번호일 수 있어 두 컬럼을 순차 조회한다.)
 */
@Component
@RequiredArgsConstructor
public class CurrentMemberProvider {

    private final MemberRepository memberRepository;

    public Member getCurrentMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal)) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }

        String loginId = jwtPrincipal.email();
        return memberRepository.findByEmail(loginId)
                .or(() -> memberRepository.findByPhone(loginId))
                .orElseThrow(() -> new IllegalStateException("인증된 사용자를 찾을 수 없습니다."));
    }
}
