package com.academy.reschedu.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 마이페이지(회원 정보 수정) 저장 요청.
 * currentPassword/newPassword는 비밀번호를 바꿀 때만 채워 보내면 되는 선택 항목이다
 * (둘 다 비어 있으면 이름/연락처만 갱신하고 비밀번호는 그대로 유지한다).
 */
public record MyProfileUpdateRequest(
        @NotBlank(message = "이름은 필수 입력 값입니다.")
        String name,

        @NotBlank(message = "연락처는 필수 입력 값입니다.")
        String phone,

        String currentPassword,

        String newPassword
) {
}
