package com.academy.reschedu.domain.notification;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SseEmitter는 subscribe() 내부에서 직접 생성되어 외부에서 스텁할 수 없으므로, 실제 전송(send) 대신
 * connections 목록/매칭 로직(matches)을 리플렉션으로 직접 검증한다.
 */
class NotificationSseServiceTest {

    private NotificationSseService notificationSseService;
    private Academy academy;
    private Member adminMember;
    private Member teacherMember;

    @BeforeEach
    void setUp() {
        notificationSseService = new NotificationSseService();

        academy = Academy.builder().name("테스트 학원").build();
        ReflectionTestUtils.setField(academy, "id", 1L);

        adminMember = new Member("admin@test.com", "e", "원장", "010-0000-0001", MemberRole.ADMIN, academy);
        ReflectionTestUtils.setField(adminMember, "id", 10L);

        teacherMember = new Member("teacher@test.com", "e", "강사", "010-0000-0002", MemberRole.TEACHER, academy);
        ReflectionTestUtils.setField(teacherMember, "id", 20L);
    }

    @Nested
    class Subscribe {

        @Test
        void 구독하면_connected_이벤트를_보내고_연결목록에_등록된다() {
            SseEmitter emitter = notificationSseService.subscribe(adminMember);

            assertThat(emitter).isNotNull();
            List<?> connections = (List<?>) ReflectionTestUtils.getField(notificationSseService, "connections");
            assertThat(connections).hasSize(1);
        }

        @Test
        void 여러_회원이_구독하면_각각_연결목록에_쌓인다() {
            notificationSseService.subscribe(adminMember);
            notificationSseService.subscribe(teacherMember);

            List<?> connections = (List<?>) ReflectionTestUtils.getField(notificationSseService, "connections");
            assertThat(connections).hasSize(2);
        }
    }

    @Nested
    class Matches {

        private boolean matches(NotificationEvent event, Member member) throws Exception {
            Object connection = buildConnection(member);
            var method = NotificationSseService.class.getDeclaredMethod(
                    "matches", NotificationEvent.class, connection.getClass());
            method.setAccessible(true);
            return (boolean) method.invoke(notificationSseService, event, connection);
        }

        private Object buildConnection(Member member) throws Exception {
            Class<?> connectionClass = Class.forName(
                    "com.academy.reschedu.domain.notification.NotificationSseService$Connection");
            var constructor = connectionClass.getDeclaredConstructor(
                    Long.class, Long.class, MemberRole.class, SseEmitter.class);
            constructor.setAccessible(true);
            Long academyId = member.getAcademy() != null ? member.getAcademy().getId() : null;
            return constructor.newInstance(member.getId(), academyId, member.getRole(), new SseEmitter());
        }

        @Test
        void 특정_회원_대상_이벤트는_해당_회원에게만_매칭된다() throws Exception {
            NotificationEvent event = NotificationEvent.toMember(
                    NotificationType.MAKEUP_TICKET_ISSUED, "발급됨", "/dashboard", adminMember.getId());

            assertThat(matches(event, adminMember)).isTrue();
            assertThat(matches(event, teacherMember)).isFalse();
        }

        @Test
        void 학원_역할_대상_이벤트는_같은_학원_같은_역할에만_매칭된다() throws Exception {
            NotificationEvent event = NotificationEvent.toAcademyRoles(
                    NotificationType.TEACHER_SIGNUP_PENDING, "가입 요청", "/dashboard/teachers",
                    academy.getId(), List.of(MemberRole.ADMIN));

            assertThat(matches(event, adminMember)).isTrue();
            assertThat(matches(event, teacherMember)).isFalse();
        }

        @Test
        void 학원_역할_대상_이벤트는_다른_학원에는_매칭되지_않는다() throws Exception {
            Academy otherAcademy = Academy.builder().name("다른 학원").build();
            ReflectionTestUtils.setField(otherAcademy, "id", 2L);
            Member otherAdmin = new Member("other@test.com", "e", "원장", "010-0000-0003", MemberRole.ADMIN, otherAcademy);
            ReflectionTestUtils.setField(otherAdmin, "id", 30L);

            NotificationEvent event = NotificationEvent.toAcademyRoles(
                    NotificationType.STUDENT_ENROLLMENT_PENDING, "등록 요청", "/dashboard/students",
                    academy.getId(), List.of(MemberRole.ADMIN));

            assertThat(matches(event, otherAdmin)).isFalse();
        }
    }
}
