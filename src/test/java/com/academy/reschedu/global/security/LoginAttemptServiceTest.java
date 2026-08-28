package com.academy.reschedu.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService();
    }

    @Test
    void 시도_기록이_없으면_잠기지_않는다() {
        assertThatCode(() -> loginAttemptService.checkNotLocked("user@test.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void 실패가_4회까지는_잠기지_않는다() {
        for (int i = 0; i < 4; i++) {
            loginAttemptService.recordFailure("user@test.com");
        }

        assertThatCode(() -> loginAttemptService.checkNotLocked("user@test.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void 실패가_5회_누적되면_잠긴다() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("user@test.com");
        }

        assertThatThrownBy(() -> loginAttemptService.checkNotLocked("user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("로그인 시도 횟수를 초과");
    }

    @Test
    void 성공_기록시_실패_카운트가_초기화된다() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("user@test.com");
        }

        loginAttemptService.recordSuccess("user@test.com");

        assertThatCode(() -> loginAttemptService.checkNotLocked("user@test.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void 서로_다른_아이디의_실패_횟수는_독립적으로_집계된다() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("locked@test.com");
        }

        assertThatCode(() -> loginAttemptService.checkNotLocked("other@test.com"))
                .doesNotThrowAnyException();
    }
}
