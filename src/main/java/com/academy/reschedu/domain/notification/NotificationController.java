package com.academy.reschedu.domain.notification;

import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationSseService sseService;
    private final CurrentMemberProvider currentMemberProvider;

    /** 로그인한 회원이 자신에게 오는 실시간 알림을 구독하는 SSE 스트림. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Member member = currentMemberProvider.getCurrentMember();
        return sseService.subscribe(member);
    }
}
