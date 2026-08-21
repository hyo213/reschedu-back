package com.academy.reschedu.global.security.jwt;

import com.academy.reschedu.domain.member.MemberRole;

public record JwtPrincipal(String email, MemberRole role) {
}