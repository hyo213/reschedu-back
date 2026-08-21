package com.academy.reschedu.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "아이디(이메일 또는 연락처)를 입력해 주세요.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
        String password
) {
}