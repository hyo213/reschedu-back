package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.member.event.AuthCodeRequestedEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailAuthServiceTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<String> bucket;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EmailAuthService emailAuthService;

    @Nested
    class SendAuthCode {

        @Test
        void 인증코드를_Redis에_저장하고_이벤트를_발행한다() {
            when(redissonClient.<String>getBucket("email-auth:user@test.com")).thenReturn(bucket);

            emailAuthService.sendAuthCode("user@test.com");

            ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
            verify(bucket).set(codeCaptor.capture(), any(Duration.class));
            assertThat(codeCaptor.getValue()).matches("\\d{6}");

            ArgumentCaptor<AuthCodeRequestedEvent> eventCaptor = ArgumentCaptor.forClass(AuthCodeRequestedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().email()).isEqualTo("user@test.com");
            assertThat(eventCaptor.getValue().authCode()).isEqualTo(codeCaptor.getValue());
        }
    }

    @Nested
    class VerifyAuthCode {

        @Test
        void 코드가_일치하면_true를_반환한다() {
            when(redissonClient.<String>getBucket("email-auth:user@test.com")).thenReturn(bucket);
            when(bucket.get()).thenReturn("123456");

            boolean result = emailAuthService.verifyAuthCode("user@test.com", "123456");

            assertThat(result).isTrue();
        }

        @Test
        void 코드가_일치하지_않으면_false를_반환한다() {
            when(redissonClient.<String>getBucket("email-auth:user@test.com")).thenReturn(bucket);
            when(bucket.get()).thenReturn("123456");

            boolean result = emailAuthService.verifyAuthCode("user@test.com", "000000");

            assertThat(result).isFalse();
        }

        @Test
        void 인증_요청_내역이_없거나_만료됐으면_예외() {
            when(redissonClient.<String>getBucket("email-auth:nobody@test.com")).thenReturn(bucket);
            when(bucket.get()).thenReturn(null);

            assertThatThrownBy(() -> emailAuthService.verifyAuthCode("nobody@test.com", "123456"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("인증 요청 내역이 없거나 만료되었습니다");
        }
    }
}
