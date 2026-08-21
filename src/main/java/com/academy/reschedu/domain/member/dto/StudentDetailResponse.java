package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.Student;
import java.time.LocalDate;
import java.util.UUID;

public record StudentDetailResponse(
        UUID uuid,
        String email,
        String name,
        String managementName,
        LocalDate birthDate,
        String gender,
        String parentPhone,
        String childPhone,
        String schoolName,
        String shuttlePickupLocation,
        String shuttleDropoffLocation,
        String discountType,
        String memo,
        Integer weeklyFrequency,
        UUID teacherUuid,        // 담당 강사 식별용 UUID
        String teacherName,      // 화면단 노출용 강사 실명
        UUID previousTeacherUuid,      // 담당 강사 인계(효력일 지정) 중일 때만 채워짐
        String previousTeacherName,
        LocalDate teacherHandoverEffectiveFrom,
        LocalDate enrollmentStartDate,
        LocalDate enrollmentEndDate,
        boolean paidForUpcomingMonth,
        boolean expired
) {
    public static StudentDetailResponse from(AcademyStudent academyStudent, LocalDate today) {
        Student student = academyStudent.getStudent(); // 장부에서 수강생 알맹이 꺼내기

        return new StudentDetailResponse(
                student.getUuid(),
                student.getParent().getEmail(), // 부모 이메일 계정
                student.getName(), // 아이 실명
                academyStudent.getManagementName(), // 학원별 관리용 이름
                student.getBirthDate(),
                student.getGender(),
                student.getParent().getPhone(), // 부모 연락처
                student.getChildPhone(),
                academyStudent.getSchoolName(),
                academyStudent.getShuttlePickupLocation(),
                academyStudent.getShuttleDropoffLocation(),
                academyStudent.getDiscountType(),
                academyStudent.getMemo(),
                academyStudent.getWeeklyFrequency(),
                academyStudent.getTeacher() != null ? academyStudent.getTeacher().getUuid() : null,
                academyStudent.getTeacher() != null ? academyStudent.getTeacher().getName() : "배정 없음",
                academyStudent.getPreviousTeacher() != null ? academyStudent.getPreviousTeacher().getUuid() : null,
                academyStudent.getPreviousTeacher() != null ? academyStudent.getPreviousTeacher().getName() : null,
                academyStudent.getTeacherHandoverEffectiveFrom(),
                academyStudent.getEnrollmentStartDate(),
                academyStudent.getEnrollmentEndDate(),
                academyStudent.isPaidForUpcomingBillingMonth(today),
                academyStudent.isExpired(today)
        );
    }
}
