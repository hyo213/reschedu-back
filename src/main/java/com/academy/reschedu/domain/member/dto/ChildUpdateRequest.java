package com.academy.reschedu.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 학부모 본인 계정 설정 화면에서 기존 자녀 정보를 수정할 때 쓰는 요청 DTO. */
public record ChildUpdateRequest(
        @NotBlank(message = "자녀 이름은 필수입니다.")
        String name,

        @NotNull(message = "자녀 생년월일은 필수입니다.")
        LocalDate birthDate,

        @NotBlank(message = "자녀 성별은 필수입니다.")
        String gender,

        @NotBlank(message = "학교 이름은 필수입니다.")
        String schoolName,

        String childPhone
) {
}
