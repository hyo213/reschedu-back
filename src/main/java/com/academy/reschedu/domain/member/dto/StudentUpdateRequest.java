package com.academy.reschedu.domain.member.dto;

import java.time.LocalDate;
import java.util.UUID;

public record StudentUpdateRequest(
        String managementName,
        String birthDate,
        String gender,
        String phone,
        String childPhone,
        String schoolName,
        String shuttlePickupLocation,
        String shuttleDropoffLocation,
        String discountType,
        String memo,
        Integer weeklyFrequency
) {}