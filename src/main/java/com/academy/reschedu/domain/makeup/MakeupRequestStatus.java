package com.academy.reschedu.domain.makeup;

public enum MakeupRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** 휴무일 지정 취소로 인해 수락된 보강 매칭이 자동으로 취소됨. */
    CANCELLED
}
