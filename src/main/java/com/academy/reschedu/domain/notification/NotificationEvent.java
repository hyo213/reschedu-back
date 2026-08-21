package com.academy.reschedu.domain.notification;

import com.academy.reschedu.domain.member.MemberRole;

import java.util.List;

/**
 * 실시간 알림 페이로드. Spring ApplicationEvent와 Kafka 메시지 양쪽에서 같은 타입을 그대로 쓴다.
 * targetMemberId가 있으면 그 회원 한 명에게, targetAcademyId+targetRoles가 있으면 그 학원 소속의
 * 해당 역할 전원에게 전달한다(둘 중 하나만 채운다).
 */
public record NotificationEvent(
        NotificationType type,
        String message,
        String linkPath,
        Long targetMemberId,
        Long targetAcademyId,
        List<MemberRole> targetRoles
) {
    public static NotificationEvent toMember(NotificationType type, String message, String linkPath, Long targetMemberId) {
        return new NotificationEvent(type, message, linkPath, targetMemberId, null, null);
    }

    public static NotificationEvent toAcademyRoles(NotificationType type, String message, String linkPath,
                                                     Long targetAcademyId, List<MemberRole> targetRoles) {
        return new NotificationEvent(type, message, linkPath, null, targetAcademyId, targetRoles);
    }
}
