package egovframework.eeum.base.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 감사 기록 어노테이션 — 서비스 구현 메서드에 부착(AOP).
 *
 * <p>⚠ AOP 한계: 같은 클래스 내부 호출(self-invocation)에는 동작하지 않음 — 외부(컨트롤러)에서
 * 진입하는 공개 서비스 메서드에만 부착한다. 목록 조회처럼 검색조건·건수를 남겨야 하는 경우는
 * {@link AuditRecorder} 명시 호출을 사용(하이브리드 — codex 권고).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

	/** 행위 코드 (예: ACCOUNT_INSERT, PARTNER_APPROVE) */
	String actn();

	/** 대상 유형 (예: USER_ACNT, PRTNR) */
	String trgtType();

	/**
	 * 대상 식별자 SpEL — 파라미터는 #파라미터명, 반환값은 #result (예: "#prtnrSn", "#result.userSn").
	 * 평가 실패 시 기록은 계속하되 대상 식별자만 비움(본 작업 불간섭).
	 */
	String trgtId() default "";
}
