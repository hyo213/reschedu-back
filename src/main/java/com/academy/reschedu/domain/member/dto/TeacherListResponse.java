package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.Member;
import java.util.UUID;

public record TeacherListResponse(
        UUID uuid,
        String email,
        String name,
        String role,
        boolean isApproved
) {
    public static TeacherListResponse from(Member member) {
        return new TeacherListResponse(
                member.getUuid(),
                member.getEmail(),
                member.getName(),
                member.getRole().name(),
                member.isApproved()
        );
    }
}