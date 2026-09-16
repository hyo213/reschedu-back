package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.MemberRole;
import jakarta.validation.constraints.NotNull;

/**
 * 데모 사이트 체험 로그인 요청. 아이디/비밀번호는 프론트에 절대 내려가지 않고,
 * 역할만 받아 서버가 미리 정해둔 데모 계정으로 로그인시킨다.
 */
public record DemoLoginRequest(
        @NotNull(message = "역할을 선택해 주세요.")
        MemberRole role
) {
}
