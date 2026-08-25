package com.academy.reschedu.global.config;

import com.academy.reschedu.global.security.jwt.JwtAuthenticationFilter;
import com.academy.reschedu.global.security.jwt.JwtCookieProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtCookieProvider jwtCookieProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors.configurationSource(request -> {
                    var config = new org.springframework.web.cors.CorsConfiguration();
                    config.setAllowedOrigins(List.of("http://localhost:3000"));
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
                    config.setAllowedHeaders(List.of("*"));
                    // 🚨 httpOnly 쿠키(JWT)를 브라우저가 크로스 오리진(3000 -> 8080)으로 실어 보내려면 필수.
                    // allowCredentials=true일 때는 allowedOrigins에 "*"를 쓸 수 없으므로 위처럼 출처를 명시해야 한다.
                    config.setAllowCredentials(true);
                    return config;
                }))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 1. 비인증 오픈 엔드포인트 매핑
                        .requestMatchers(
                                "/api/members/signup",
                                "/api/members/login",
                                "/api/members/check-email",
                                "/api/members/email-auth/**",
                                "/api/members/logout",
                                "/api/academies/search",
                                "/api/academies/register"
                        ).permitAll()

                        // 🚨 로컬/포트폴리오 데모 전제의 개방이다 — 실제 운영 환경이라면 이 경로는 별도 관리
                        // 네트워크로 격리하거나 인증을 걸어야 한다(Prometheus 스크레이핑 편의를 위해 열어둠).
                        .requestMatchers("/actuator/**").permitAll()

                        // 2. 학부모 전용: 본인 자녀 목록 조회/추가/수정 (구체적인 규칙을 먼저 선언)
                        .requestMatchers("/api/members/my-children", "/api/members/my-children/**", "/api/members/my-children-academies").hasRole("PARENT")

                        // 3. 강사 목록 조회 — 원장/강사 모두 가능(강사도 미배정 학생에게 담당 강사를 지정할 때 필요)
                        .requestMatchers(HttpMethod.GET, "/api/members/teachers/**").hasAnyRole("ADMIN", "TEACHER")

                        // 4. 수강생 관리 및 회원 승인 엔드포인트
                        .requestMatchers(
                                "/api/members/students/**",
                                "/api/members/*/approve"
                        ).hasAnyRole("ADMIN", "TEACHER")

                        // 5. 정규 수업(시간표) 엔드포인트
                        //    - 학부모: 본인 자녀가 편성된 시간표 "조회"만 가능 (구체적인 규칙을 먼저 선언)
                        //    - 원장/강사: 시간표 추가·전체 조회 등 관리 가능
                        .requestMatchers(HttpMethod.GET, "/api/regular-classes/my-children", "/api/regular-classes/my-children/next").hasRole("PARENT")
                        .requestMatchers("/api/regular-classes/**").hasAnyRole("ADMIN", "TEACHER")

                        // 6. 학원 휴무일 엔드포인트
                        //    - 조회(GET): 원장/강사/학부모 모두 가능 — 시간표 화면에서 휴무 사유를 함께 봐야 하므로
                        //    - 등록/수정/삭제: 원장/강사 모두 가능 (서비스 계층에서도 동일하게 검증)
                        .requestMatchers(HttpMethod.GET, "/api/academy-holidays/**").hasAnyRole("ADMIN", "TEACHER", "PARENT")
                        .requestMatchers("/api/academy-holidays/**").hasAnyRole("ADMIN", "TEACHER")

                        // 7. 보강권 엔드포인트
                        //    - 결석 처리/취소: 학부모(본인 자녀) + 원장/강사(소속 학원 수강생) 모두 가능 — 서비스 계층에서 세부 검증
                        //    - 본인 자녀 잔여 보강권 개수 조회(보강 신청 화면): 학부모 전용
                        //    - 잔여 개수 조회(보강 매칭 센터): 원장/강사 전용
                        .requestMatchers(HttpMethod.POST, "/api/makeup-tickets/absence").hasAnyRole("PARENT", "ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/makeup-tickets/absence").hasAnyRole("PARENT", "ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/makeup-tickets/my-children-counts").hasRole("PARENT")
                        .requestMatchers(HttpMethod.GET, "/api/makeup-tickets/my-children/*/details").hasRole("PARENT")
                        .requestMatchers("/api/makeup-tickets/**").hasAnyRole("ADMIN", "TEACHER")

                        // 7-1. 보강권 전체 정책([보강권 관리] 화면 원장 전용 설정) — 조회는 원장/강사, 변경은 원장 전용(서비스 계층에서도 재검증)
                        .requestMatchers(HttpMethod.GET, "/api/makeup-ticket-policy").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.PUT, "/api/makeup-ticket-policy").hasRole("ADMIN")

                        // 8. 보강 신청(makeup-requests) 엔드포인트
                        //    - 여석 조회 + 신청 + 본인 신청 내역: 학부모(+원장/강사)도 가능 — 서비스 계층에서 소유권 검증
                        //    - 대기 목록 조회 + 수락/거절: 원장/강사 전용
                        .requestMatchers(HttpMethod.GET, "/api/makeup-requests/open-slots", "/api/makeup-requests/my").hasAnyRole("PARENT", "ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.POST, "/api/makeup-requests").hasAnyRole("PARENT", "ADMIN", "TEACHER")
                        .requestMatchers("/api/makeup-requests/**").hasAnyRole("ADMIN", "TEACHER")

                        // 9. 공지사항 엔드포인트
                        //    - 조회(GET): 원장/강사/학부모 모두 가능 — 소속 학원 회원 전원이 읽을 수 있어야 하므로
                        //    - 작성/수정/삭제: 원장/강사만 가능 (서비스 계층에서도 동일하게 검증)
                        .requestMatchers(HttpMethod.GET, "/api/notices/**").hasAnyRole("ADMIN", "TEACHER", "PARENT")
                        .requestMatchers("/api/notices/**").hasAnyRole("ADMIN", "TEACHER")

                        // 10. 나머지 모든 자원은 보안 인증 필수
                        .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/members/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieProvider.createExpiredAccessTokenCookie().toString());
                            response.setStatus(HttpServletResponse.SC_OK);
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}