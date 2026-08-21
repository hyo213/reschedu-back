package com.academy.reschedu.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 학부모 본인 계정 설정 화면에서 자녀를 새로 추가할 때 쓰는 요청 DTO. 회원가입 시 자녀 입력 폼과 동일한 필드다. */
public record ChildAddRequest(
        @NotNull(message = "등록할 학원을 선택해주세요.")
        Long academyId,

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
