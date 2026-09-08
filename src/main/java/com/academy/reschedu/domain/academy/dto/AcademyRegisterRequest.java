package com.academy.reschedu.domain.academy.dto;

import jakarta.validation.constraints.NotBlank;

public record AcademyRegisterRequest(
        @NotBlank(message = "학원 이름은 필수 입력 값입니다.")
        String name,

        @NotBlank(message = "학원 주소는 필수 입력 값입니다.")
        String address
) {
}
