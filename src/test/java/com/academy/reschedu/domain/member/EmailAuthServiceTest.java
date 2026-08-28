package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.member.event.AuthCodeRequestedEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailAuthServiceTest {

    @Mock
    private EmailAuthRepository emailAuthRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EmailAuthService emailAuthService;

    @Nested
    class SendAuthCode {

        @Test
        void 인증코드를_저장하고_이벤트를_발행한다() {
            emailAuthService.sendAuthCode("user@test.com");

            ArgumentCaptor<EmailAuth> savedCaptor = ArgumentCaptor.forClass(EmailAuth.class);
            verify(emailAuthRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getEmail()).isEqualTo("user@test.com");
            assertThat(savedCaptor.getValue().getAuthCode()).matches("\\d{6}");

            ArgumentCaptor<AuthCodeRequestedEvent> eventCaptor = ArgumentCaptor.forClass(AuthCodeRequestedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().email()).isEqualTo("user@test.com");
            assertThat(eventCaptor.getValue().authCode()).isEqualTo(savedCaptor.getValue().getAuthCode());
        }
    }

    @Nested
    class VerifyAuthCode {

        @Test
        void 코드가_일치하면_true를_반환한다() {
            EmailAuth emailAuth = new EmailAuth("user@test.com", "123456", 3);
            when(emailAuthRepository.findFirstByEmailOrderByExpiredAtDesc("user@test.com"))
                    .thenReturn(Optional.of(emailAuth));

            boolean result = emailAuthService.verifyAuthCode("user@test.com", "123456");

            assertThat(result).isTrue();
        }

        @Test
        void 코드가_일치하지_않으면_false를_반환한다() {
            EmailAuth emailAuth = new EmailAuth("user@test.com", "123456", 3);
            when(emailAuthRepository.findFirstByEmailOrderByExpiredAtDesc("user@test.com"))
                    .thenReturn(Optional.of(emailAuth));

            boolean result = emailAuthService.verifyAuthCode("user@test.com", "000000");

            assertThat(result).isFalse();
        }

        @Test
        void 인증_요청_내역이_없으면_예외() {
            when(emailAuthRepository.findFirstByEmailOrderByExpiredAtDesc("nobody@test.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> emailAuthService.verifyAuthCode("nobody@test.com", "123456"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("인증 요청 내역이 존재하지 않습니다");
        }

        @Test
        void 만료된_코드면_예외() {
            EmailAuth emailAuth = new EmailAuth("user@test.com", "123456", 3);
            ReflectionTestUtils.setField(emailAuth, "expiredAt", LocalDateTime.now().minusMinutes(1));
            when(emailAuthRepository.findFirstByEmailOrderByExpiredAtDesc("user@test.com"))
                    .thenReturn(Optional.of(emailAuth));

            assertThatThrownBy(() -> emailAuthService.verifyAuthCode("user@test.com", "123456"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("인증 시간이 만료");
        }
    }
}
