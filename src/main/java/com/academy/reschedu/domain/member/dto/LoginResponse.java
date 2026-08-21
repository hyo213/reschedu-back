package com.academy.reschedu.domain.member.dto;

import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import java.util.UUID;

public record LoginResponse(
        UUID uuid,
        String email,
        String name,
        MemberRole role,
        Long academyId
) {
    public static LoginResponse of(Member member) {
        return new LoginResponse(
                member.getUuid(),
                member.getEmail(),
                member.getName(),
                member.getRole(),
                member.getAcademy() != null ? member.getAcademy().getId() : null
        );
    }
}
