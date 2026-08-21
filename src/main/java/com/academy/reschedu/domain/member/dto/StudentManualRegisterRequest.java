package com.academy.reschedu.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 원장/강사가 관리자 화면에서 수강생을 수기로 직접 등록할 때 사용하는 요청 DTO
 */
public record StudentManualRegisterRequest(
        // 🔑 계정 생성을 위한 학부모 기본 정보 및 임시 비밀번호
        @NotBlank(message = "학부모 이름은 필수 입력 값입니다.")
        String parentName,               // 학부모 실명

        @NotBlank(message = "학부모 연락처는 필수 입력 값입니다.")
        String parentPhone,              // 학부모 연락처 (향후 학부모 로그인 ID로 활용)

        @NotBlank(message = "임시 비밀번호는 필수 입력 값입니다.")
        @Size(min = 8, max = 100, message = "임시 비밀번호는 8자 이상이어야 합니다.")
        String temporaryPassword,        // 학원에서 지정해 준 임시 비밀번호

        // 수강생(자녀) 데이터
        @NotBlank(message = "수강생 이름은 필수 입력 값입니다.")
        String name,                     // 아이 실명

        @NotNull(message = "수강생 생년월일은 필수 입력 값입니다.")
        LocalDate birthDate,             // 생년월일 (YYYY-MM-DD)

        @NotBlank(message = "수강생 성별은 필수 입력 값입니다.")
        String gender,                   // 성별 (MALE, FEMALE)

        String childPhone,               // 아이 본인 연락처 (선택사항)

        // 🏫 내 학원 전용 관리 데이터 장부 항목
        String managementName,           // 원내 관리용 이름 (동명이인 구분용)
        UUID teacherUuid,                // 담당 배정 강사 UUID (선택사항)

        @NotBlank(message = "소속 학교명은 필수 입력 값입니다.")
        String schoolName,               // 소속 학교명

        String shuttlePickupLocation,    // 등원 승차 위치 (선택사항)
        String shuttleDropoffLocation,   // 하원 하차 위치 (선택사항)
        String discountType,             // 수강 할인 종류 (선택사항)
        String memo,                     // 학원 전용 비공개 특이사항 메모 (선택사항)

        // 🎯 수강 기간(수강료 납부 기간) — 필수 입력. [수강생 관리] 화면에서 이후에도 계속 연장/변경 가능
        @NotNull(message = "수강 시작일은 필수 입력 값입니다.")
        LocalDate enrollmentStartDate,

        @NotNull(message = "수강 종료일은 필수 입력 값입니다.")
        LocalDate enrollmentEndDate
) {
}