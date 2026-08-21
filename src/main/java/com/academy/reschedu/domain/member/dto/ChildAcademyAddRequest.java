package com.academy.reschedu.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 학부모 본인 계정 설정 화면에서, 이미 등록된 자녀를 "다른 학원에도" 다니게 할 때 쓰는 요청 DTO.
 * 자녀(Student)는 그대로 두고 새 AcademyStudent 등록만 하나 추가한다.
 */
public record ChildAcademyAddRequest(
        @NotNull(message = "추가할 학원을 선택해주세요.")
        Long academyId,

        @NotBlank(message = "학교 이름은 필수입니다.")
        String schoolName
) {
}
