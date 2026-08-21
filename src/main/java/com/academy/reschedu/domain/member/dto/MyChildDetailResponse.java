package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.Student;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 학부모 본인 계정 설정 화면에서 자녀 정보를 추가/수정할 때 쓰는 상세 조회용.
 * 원내 관리 필드(담당강사/셔틀/할인/메모/수강기간)는 노출하지 않는다 — 그건 [수강생 관리] 화면의 몫이다.
 * 🎯 [다학원 자녀 지원] academies에 이 자녀가 등록된 모든 학원과 각각의 승인 여부를 담아, 자녀 수정
 * 화면에서 "다른 학원도 추가" 액션과 학원별 승인 상태를 함께 보여줄 수 있게 한다.
 */
public record MyChildDetailResponse(
        UUID uuid,
        String name,
        LocalDate birthDate,
        String gender,
        String childPhone,
        String schoolName,
        boolean isApproved,
        List<ChildAcademyRegistration> academies
) {
    public static MyChildDetailResponse of(Student student, String schoolName, boolean isApproved,
                                            List<ChildAcademyRegistration> academies) {
        return new MyChildDetailResponse(
                student.getUuid(), student.getName(), student.getBirthDate(),
                student.getGender(), student.getChildPhone(), schoolName, isApproved, academies
        );
    }
}
