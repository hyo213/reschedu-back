package com.academy.reschedu.domain.member;

import com.academy.reschedu.domain.academy.Academy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    private boolean isEmailVerified = false;

    @Column(nullable = false)
    private boolean isApproved = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id")
    private Academy academy;

    public Member(String email, String password, String name, String phone, MemberRole role, Academy academy) {
        this.uuid = UUID.randomUUID();
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.academy = academy;
        this.isEmailVerified = false;
        this.isApproved = false;
    }

    public static Member createPreRegisteredParent(String name, String phone, String encodedTemporaryPassword) {
        Member member = new Member();
        member.uuid = UUID.randomUUID();
        member.name = name;
        member.phone = phone;
        member.password = encodedTemporaryPassword;
        member.role = MemberRole.PARENT;
        member.isEmailVerified = false;
        member.isApproved = true;
        return member;
    }

    public void approve() {
        this.isApproved = true;
    }

    public void updatePhone(String phone) {
        this.phone = phone;
    }

    public void updateBasicInfo(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void verifyAndVerifyEmail(String email) {
        this.email = email;
        this.isEmailVerified = true;
    }
}