package com.academy.reschedu.domain.makeup.dto;

import java.util.UUID;

/**
 * 보강 매칭 센터 화면에서 학생별 잔여(미사용) 보강권 개수를 보여주기 위한 응답 DTO.
 * 상세 매칭/소모 로직은 이 범위에 포함하지 않고, 잔여 개수 집계만 제공한다.
 */
public record StudentTicketCountResponse(
        UUID studentUuid,
        String name,
        String managementName,
        long remainingTicketCount
) {
}
