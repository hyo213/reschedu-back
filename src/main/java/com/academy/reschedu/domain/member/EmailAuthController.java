package com.academy.reschedu.domain.member;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/members/email-auth")
@RequiredArgsConstructor
public class EmailAuthController {

    private final EmailAuthService emailAuthService;

    /**
     * 인증 코드 발송 API (POST /api/members/email-auth/send)
     * Body: { "email": "test@naver.com" }
     */
    @PostMapping("/send")
    public ResponseEntity<String> sendEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        emailAuthService.sendAuthCode(email);
        return ResponseEntity.ok("인증 코드가 이메일로 발송되었습니다.");
    }

    /**
     * 인증 코드 검증 API (POST /api/members/email-auth/verify)
     * Body: { "email": "test@naver.com", "code": "123456" }
     */
    @PostMapping("/verify")
    public ResponseEntity<String> verifyCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");

        boolean isVerified = emailAuthService.verifyAuthCode(email, code);

        if (isVerified) {
            return ResponseEntity.ok("이메일 인증에 성공했습니다.");
        } else {
            return ResponseEntity.status(400).body("인증 코드가 일치하지 않습니다.");
        }
    }
}