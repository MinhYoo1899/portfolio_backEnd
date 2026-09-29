package egovframework.com.security;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.util.unit.DataSize;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CharacterEncodingFilter;

import egovframework.com.cmm.filter.HTMLTagFilter;
import egovframework.com.jwt.JwtAuthenticationFilter;
import egovframework.eeum.idempotency.IdempotencyFilter;
import egovframework.eeum.base.security.ApiAccessDeniedHandler;
import egovframework.eeum.base.security.ApiAuthenticationEntryPoint;
import jakarta.servlet.MultipartConfigElement;

/**
 * fileName : SecurityConfig
 * author : crlee
 * date : 2023/06/10
 * description :
 * ===========================================================
 * DATE AUTHOR NOTE
 * -----------------------------------------------------------
 * 2023/06/10 crlee 최초 생성
 * 2026/05/13 보안취약점 대응
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize("hasAuthority('권한코드')") — 데이터 주도 RBAC 보호 기준
public class SecurityConfig {

    // 인증 예외 — 항상 공개(데모 화이트리스트 제거, auth 공개 엔드포인트 + 정적 리소스)
    private static final String[] AUTH_WHITELIST = {
            "/",
            "/error", // 에러 페이지
            "/auth/login", // 로그인
            "/auth/refresh", // 토큰 재발급 (refresh 자체 검증)
            "/auth/logout", // 로그아웃 (멱등 — 쿠키/바디 refresh 만으로 폐기 가능)
            "/auth/social-login", // 소셜 로그인 (id_token 검증 — 사전 인증 불요)
            "/auth/social-signup", // 소셜 가입 완료 (가입 티켓 — 사전 인증 불요)
            "/auth/oauth/*/authorize", // 소셜 authorize 시작 (웹 Auth Code + PKCE — 사전 인증 불요)
            "/auth/oauth/*/callback", // 소셜 콜백 (provider→서버, state/PKCE 검증)
            "/auth/oauth/result", // result 토큰 교환 (1회용 토큰 — 사전 인증 불요)
            "/accounts/insert", // 일반회원 가입
            "/partners/insert", // 파트너 가입 신청
            "/operators/setup-password", // 운영자 비밀번호 설정 (생성/초기화 시 발급된 1회성 설정토큰만으로 — 사전 인증 불요)
            "/menus/list", // SPA 동적 메뉴 (비로그인=공개 메뉴, 토큰 있으면 권한 반영)
            "/codes/list", // 공통코드 조회 (비민감 라벨 — 가입 등 비로그인 화면에서도 필요)
            "/terms/list", // 약관 목록 조회 (시행 중 약관 — 가입 등 비로그인 화면에서도 필요)
            "/terms/detail", // 약관 상세 조회 (시행 중 약관 본문 — 비로그인 공개)
            "/files/detail", // 공개 이미지 메타 조회 (업로드/삭제는 인증 — 화이트리스트 제외)
            "/files/download", // 공개 이미지 다운로드 (백엔드 경유 스트리밍)
            "/inquiries/list", // 문의 목록 (공개 — 비밀글은 SQL 마스킹, 토큰 있으면 본인/운영자 반영)
            "/inquiries/detail", // 문의 상세 (공개글; 비밀글/블라인드는 작성자·운영자만)
            "/inquiries/files/download", // 문의 첨부 다운로드 (PUBLIC=누구나; PRIVATE 은 서비스가 권한판정 후 404 — 업로드는 인증)
            "/public/houses/list", // 손님 공개 고택 목록 (ACTIVE 만, 비로그인 브라우즈)
            "/public/houses/detail", // 손님 공개 고택 상세 (ACTIVE+승인 파트너만, 그 외 404 은닉)
            "/public/houses/availability", // 손님 공개 가용검색 (날짜/인원 — DB 재고 기준, 비공개 404)
            "/actuator/health", // 배포 헬스체크 (deploy.sh — health 외 actuator 는 비노출 설정)
            "/actuator/health/deploy", // 배포 판정 그룹(DB·Redis·디스크, 상태만 노출). deploy.sh 가 사용

            /* 정적 리소스 */
            "/css/**",
            "/js/**",
            "/images/**",
            "/static/**",
            "/favicon.ico",
            "/index.html",
    };

    // Swagger/OpenAPI 경로 — springdoc 이 켜진 프로파일(dev)에서만 공개(secure by default, codex 교차검토 필수수정).
    // 명세가 안 떠도(404) 화이트리스트가 남으면 향후 설정 오버라이드·새 컨트롤러로 즉시 공개될 위험 → 토글에 묶어 닫는다.
    // /swagger-resources 는 Springfox(구버전) 잔재라 springdoc 2.x 에선 불필요 → 제거.
    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs", // 명세 루트(슬래시 없음) — /** 가 못 잡는 정확 매칭 명시 (codex 권고)
            "/v3/api-docs.yaml", // YAML 직접 접근도 고정
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/webjars/**", // Swagger UI 정적 리소스
    };
    // application.properties의 Globals.Allow.Origin 값을 사용하며,
    // 환경별로 콤마로 구분된 복수 Origin 지정 가능 (예: "https://a.com,https://b.com")
    @Value("${Globals.Allow.Origin:http://localhost:3000}")
    private String allowedOrigins;

    // Swagger 공개 여부 — 단일 토글 eeum.swagger.enabled(dev=true, 기본=false)에서 springdoc·화이트리스트가 모두 파생(단일 진실원).
    @Value("${eeum.swagger.enabled:false}")
    private boolean swaggerEnabled;

    // 인증/인가 실패(401/403)를 공통 envelope 로 응답하는 핸들러 (교차검토 보완)
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    // 멱등키(Idempotency-Key) 필터 — JWT 인증 뒤에 등록해 SecurityContext 의 principal(AuthUser)을 사용한다.
    private final IdempotencyFilter idempotencyFilter;

    public SecurityConfig(ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            ApiAccessDeniedHandler apiAccessDeniedHandler,
            IdempotencyFilter idempotencyFilter) {
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.apiAccessDeniedHandler = apiAccessDeniedHandler;
        this.idempotencyFilter = idempotencyFilter;
    }

    @Bean
    public JwtAuthenticationFilter authenticationTokenFilterBean() throws Exception {
        return new JwtAuthenticationFilter();
    }

    /** 비밀번호 해시 — BCrypt (USER_ACNT.ENPSWD) */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    protected CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 명시적 Origin 목록만 허용 — setAllowedOriginPatterns("*") + credentials 동시 사용 금지
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        // Idempotency-Key: 생성 API 멱등(opt-in) 헤더 — 미허용이면 SPA 의 cross-origin preflight 가 거부돼 브라우저에서 멱등을 못 쓴다.
        // 명시 목록 유지(allowCredentials(true)와 와일드카드 병용 금지).
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Idempotency-Key"));
        // X-Correlation-Id: SPA 가 오류 신고 시 첨부하는 추적 ID — 노출 안 하면 JS 에서 못 읽음
        configuration.setExposedHeaders(
                List.of("Authorization", egovframework.eeum.base.audit.CorrelationIdFilter.RESPONSE_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CharacterEncodingFilter characterEncodingFilter() {
        CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
        characterEncodingFilter.setEncoding("UTF-8");
        characterEncodingFilter.setForceEncoding(true);
        return characterEncodingFilter;
    }

    @Bean
    public HTMLTagFilter htmlTagFilter() {
        return new HTMLTagFilter();
    }

    // 서블릿 멀티파트 구성 — 한도는 공개 이미지 정책(서비스 50MB) 바깥 방어선(파일 55MB / 요청 60MB).
    // ※ MultipartFilter(시큐리티 체인 등록)는 제거함: 인증/인가 전에 본문을 파싱하던 구조라
    //   무권한 요청도 파싱·임시저장을 유발(codex 교차검토 High). 멀티파트 파싱은 DispatcherServlet(시큐리티 통과 후)에서 처리.
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(55L));
        factory.setMaxRequestSize(DataSize.ofMegabytes(60L));
        return factory.createMultipartConfig();
    }

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // Swagger 경로는 토글(eeum.swagger.enabled)이 켜진 프로파일(dev)에서만 공개로 추가 — 기본(운영)은 닫힘(secure by default)
        String[] permitAllPaths = swaggerEnabled
                ? Stream.concat(Arrays.stream(AUTH_WHITELIST), Arrays.stream(SWAGGER_WHITELIST)).toArray(String[]::new)
                : AUTH_WHITELIST;

        return http
                .csrf(AbstractHttpConfigurer::disable)
                // 이음: URL 패턴 hasRole 매처 미사용 — 보호는 데이터 주도 RBAC 권한코드(@PreAuthorize hasAuthority) 기준 (D 표준)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(permitAllPaths).permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(securityContext ->
                        securityContext.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(requestCache -> requestCache.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 보안 헤더 — Spring Security 기본(nosniff·X-Frame DENY) 유지(defaultsDisabled 미사용) + Referrer-Policy 추가.
                // strict-origin-when-cross-origin: cross-site 요청에 전체 URL referrer 누출 차단(프론트 무해). HSTS 는 HTTP 라 보류(HTTPS 전환 후).
                .headers(headers -> headers.referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .addFilterBefore(characterEncodingFilter(), DisableEncodeUrlFilter.class)
                .addFilterBefore(authenticationTokenFilterBean(), UsernamePasswordAuthenticationFilter.class)
                // 멱등키 필터는 JWT 인증 필터 뒤 — principal(AuthUser) 이 채워진 상태로 실행되어야 한다.
                .addFilterAfter(idempotencyFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .build();
    }

}