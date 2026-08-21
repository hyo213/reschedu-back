package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.Student;
import com.academy.reschedu.domain.regularclass.dto.ScheduleSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StudentListResponse(
        UUID uuid,
        String email,                 // 학부모 로그인 계정 이메일 (null 일 수 있음)
        String name,                  // 아이 진짜 실명
        String managementName,        // 내 학원 전용 관리용 이름
        LocalDate birthDate,          // 생년월일
        String gender,                // 성별
        String parentPhone,           // 학부모 연락처
        String childPhone,            // 아이 본인 연락처
        String schoolName,            // 학교명
        String shuttlePickupLocation, // 등원 승차 위치
        String shuttleDropoffLocation,// 하원 하차 위치
        String discountType,          // 할인 종류
        String memo,                  // 내 학원 전용 비공개 메모
        Integer weeklyFrequency,      // 주당 희망 수강 횟수 — 아래 schedules(실제 배정)와 비교해 볼 수 있다
        boolean isApproved,
        // 🎯 수강 기간(수강료 납부 기간) — [수강생 관리] 화면에서 직접 관리
        LocalDate enrollmentStartDate,
        LocalDate enrollmentEndDate,
        // 매달 20일 기준으로 판단한 "다가오는 청구월" 수강료 납부 여부
        boolean paidForUpcomingMonth,
        // 수강 기간이 완전히 지났는지 여부 (지났다면 목록에서 회색으로 표시)
        boolean expired,
        // 🎯 [수강생 목록] 오늘 기준 활성 반 배정 요약 — 화면에서 "월3수4금5" 형태로 압축 표시한다
        List<ScheduleSummary> schedules
) {

    public static StudentListResponse from(AcademyStudent academyStudent, LocalDate today, List<ScheduleSummary> schedules) {
        Student student = academyStudent.getStudent();

        return new StudentListResponse(
                student.getUuid(), // 콕 집어서 수강생 UUID 연동 (화면에서 이 UUID로 이동)
                student.getParent().getEmail(), // 부모 계정 이메일
                student.getName(), // 아이 실명
                academyStudent.getManagementName(), // 학원 커스텀 관리명
                student.getBirthDate(),
                student.getGender(),
                student.getParent().getPhone(), // 부모 계정 연락처
                student.getChildPhone(),
                academyStudent.getSchoolName(),
                academyStudent.getShuttlePickupLocation(),
                academyStudent.getShuttleDropoffLocation(),
                academyStudent.getDiscountType(),
                academyStudent.getMemo(), // 내 학원 전용 프라이빗 메모
                academyStudent.getWeeklyFrequency(),
                academyStudent.isApproved(), // 💡 외부 가입 신청서 수락 여부 반영
                academyStudent.getEnrollmentStartDate(),
                academyStudent.getEnrollmentEndDate(),
                academyStudent.isPaidForUpcomingBillingMonth(today),
                academyStudent.isExpired(today),
                schedules
        );
    }
}
