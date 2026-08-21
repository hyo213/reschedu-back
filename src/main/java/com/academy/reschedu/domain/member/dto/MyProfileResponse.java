package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import java.util.UUID;

/**
 * 마이페이지(회원 정보 수정) 화면 진입 시 본인 정보를 미리 채워 넣기(Pre-filled) 위한 조회 응답.
 */
public record MyProfileResponse(
        UUID uuid,
        String email,
        String name,
        String phone,
        MemberRole role,
        Long academyId
) {
    public static MyProfileResponse from(Member member) {
        return new MyProfileResponse(
                member.getUuid(),
                member.getEmail(),
                member.getName(),
                member.getPhone(),
                member.getRole(),
                member.getAcademy() != null ? member.getAcademy().getId() : null
        );
    }
}
