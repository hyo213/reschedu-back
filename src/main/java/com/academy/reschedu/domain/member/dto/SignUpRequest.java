package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.member.AcademyStudent;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.member.Student;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record SignUpRequest(
        @NotBlank(message = "이메일은 필수 입력 값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수 입력 값입니다.")
        @Size(max = 50, message = "이름은 50자를 초과할 수 없습니다.")
        String name,

        @NotNull(message = "회원 역할은 필수 입력 값입니다.")
        MemberRole role,

        @NotNull(message = "소속 학원(센터) 선택은 필수입니다.")
        Long academyId,

        @NotBlank(message = "연락처는 필수 입력 값입니다.")
        String phone,

        @Valid
        List<ChildInfo> children
) {
    public Member toMemberEntity(String encodedPassword, Academy academy) {
        // 🚨 모든 주체의 본인 연락처(phone)를 공통 인자로 빌드 시 주입
        return new Member(this.email, encodedPassword, this.name, this.phone, this.role, academy);
    }

    public void validateParentFields() {
        if (this.role == MemberRole.PARENT && (this.children == null || this.children.isEmpty())) {
            throw new IllegalArgumentException("학부모 가입 시 최소 1명 이상의 자녀 정보를 입력해야 합니다.");
        }
    }

    public record ChildInfo(
            @NotBlank(message = "자녀 이름은 필수입니다.") String childName,
            @NotNull(message = "자녀 생년월일은 필수입니다.") LocalDate birthDate,
            @NotBlank(message = "자녀 성별은 필수입니다.") String gender,
            @NotBlank(message = "학교 이름은 필수입니다.") String schoolName,
            String childPhone // 🚨 학생 본인 연락처만 별도로 격리 수집
    ) {
        public Student toStudentEntity(Member parent) {
            return new Student(this.childName, this.birthDate, this.gender, this.childPhone, parent);
        }

        public AcademyStudent toAcademyStudentEntity(Academy academy, Student student) {
            return new AcademyStudent(
                    academy, student, student.getName(), null, false, this.schoolName,
                    null, null, null, null
            );
        }
    }
}