package com.academy.reschedu.domain.member;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class EmailAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 6)
    private String authCode;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    public EmailAuth(String email, String authCode, int validityMinutes) {
        this.email = email;
        this.authCode = authCode;
        this.expiredAt = LocalDateTime.now().plusMinutes(validityMinutes);
    }

    // 만료 여부 확인 메서드
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiredAt);
    }
}