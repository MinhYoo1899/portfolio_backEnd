package egovframework.eeum;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.time.LocalDateTime;

/**
 * 패키지 의존 규칙 (architecture D15 / conventions §0 / 킥오프 팀 규칙).
 * 위반 시 mvn test 에서 빌드 실패 — 문서가 아니라 기계가 경계를 지킨다.
 */
@AnalyzeClasses(packages = "egovframework.eeum", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureVerification {

	/**
	 * base 는 egovframework 내에서 base 자신 외 어떤 패키지에도 의존 금지 — 타 프로젝트 토대 재사용/SSO 분리 보장 (D15, 매우 중요).
	 * (도메인 나열이 아닌 일반화 규칙 — 신규 도메인·eGov com/let 의존도 자동 차단. codex 권고 반영)
	 */
	@ArchTest
	static final ArchRule BASE_MUST_NOT_DEPEND_ON_ANY_PROJECT_PACKAGE =
			noClasses().that().resideInAPackage("egovframework.eeum.base..")
					.should().dependOnClassesThat(
							resideInAPackage("egovframework..")
									.and(resideOutsideOfPackages("egovframework.eeum.base..")))
					.because("base는 프로젝트 내 어떤 패키지에도 의존하지 않아야 다른 프로젝트의 토대로 재사용할 수 있다 (D15)");

	/** 웹 계층(컨트롤러·DTO)은 DAO(매퍼) 직접 호출 금지 — 반드시 Service 경유. (구조 개편 2026-06-12: controller→web) */
	@ArchTest
	static final ArchRule WEB_LAYER_MUST_NOT_USE_DAO =
			noClasses().that().resideInAPackage("..web..")
					.should().dependOnClassesThat().resideInAPackage("..dao..")
					.because("웹 계층은 Service를 경유해야 한다 (계층 규칙)");

	/** 컨트롤러(@RestController)는 반드시 web 패키지에 — 구조 표준 강제 (codex 권고 2026-06-12). */
	@ArchTest
	static final ArchRule REST_CONTROLLERS_MUST_RESIDE_IN_WEB =
			classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
					.should().resideInAPackage("egovframework.eeum..web")
					.because("컨트롤러 위치 표준 = {domain}.web (conventions §0)");

	/** 웹 계층은 서비스 구현(impl)에 직접 의존 금지 — 인터페이스만. (codex 권고) */
	@ArchTest
	static final ArchRule WEB_LAYER_MUST_NOT_USE_SERVICE_IMPL =
			noClasses().that().resideInAPackage("..web..")
					.should().dependOnClassesThat().resideInAPackage("..service..impl..")
					.because("웹 계층은 서비스 인터페이스에만 의존한다 (구현 교체 가능성 보존)");

	/** VO(내부 데이터 그릇)는 웹 계층에 의존 금지 — 계층 방향 유지. (codex 권고) */
	@ArchTest
	static final ArchRule VO_MUST_NOT_DEPEND_ON_WEB =
			noClasses().that().resideInAPackage("egovframework.eeum..vo..")
					.should().dependOnClassesThat().resideInAPackage("..web..")
					.because("VO는 DB·서비스 내부용 — 웹(DTO·컨트롤러)에 의존하면 계층이 역전된다");

	/**
	 * 도메인 간 DAO 직접 접근 금지 — 모든 도메인의 DAO 는 자기 도메인 안에서만 접근 가능.
	 * (외부 검토 반영: 도메인 나열식 → 일반화 규칙 — 신규 도메인도 자동 적용)
	 */
	@ArchTest
	static final ArchRule DAO_MUST_ONLY_BE_ACCESSED_WITHIN_OWN_DOMAIN =
			classes().that().resideInAPackage("egovframework.eeum..dao..")
					.should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaClass>(
							"자기 도메인 안에서만 접근되어야 한다") {
						@Override
						public void check(com.tngtech.archunit.core.domain.JavaClass daoClass,
								com.tngtech.archunit.lang.ConditionEvents events) {
							String daoDomain = domainOf(daoClass.getPackageName());
							daoClass.getAccessesToSelf().forEach(access -> {
								String originDomain = domainOf(access.getOriginOwner().getPackageName());
								if (!daoDomain.equals(originDomain)) {
									events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(
											access, access.getDescription()));
								}
							});
						}
					})
					.because("도메인 간 매퍼 직접 접근 금지 — 서비스 인터페이스를 경유한다 (팀 규칙)");

	/**
	 * 응답 DTO(..web.dto..*Response)에 {@code LocalDateTime} 신규 유입 금지 — 발생 순간은 {@code Instant}(UTC, ISO-8601 Z)로 노출.
	 * (타임존 표준 §2 — record 컴포넌트는 동명의 필드로 잡힌다. 날짜성 LocalDate/LocalTime 은 허용.)
	 *
	 * <p>표준 predicate 기반(fields().should().notHaveRawType(...)) — ArchUnit 1.3.0 에서 custom ArchCondition 을
	 * noClasses().should(condition) 로 감싸면 NeverCondition 으로 invert 되어 위반을 잡지 못하는 결함을 회피한다.
	 *
	 * <p>범위 결정: 발생시각 노출은 <b>응답 DTO</b>의 책임이므로 클래스명 {@code *Response} 로 한정한다(문서화된 의도 = 응답만).
	 * Request DTO 는 의도적으로 제외 — 요청 파라미터의 시간 타입 정책은 별개(범위 밖).
	 *
	 * <p>백로그: 이 규칙은 raw 타입 {@code LocalDateTime} 필드만 잡는다. {@code List<LocalDateTime>} 같은
	 * 제네릭 파라미터 내부의 LocalDateTime 은 표준 predicate 로 잡기 어렵다(과한 구현 지양 — 현재 응답 DTO 에 해당 사례 없음).
	 */
	@ArchTest
	static final ArchRule WEB_DTO_MUST_NOT_EXPOSE_LOCAL_DATE_TIME =
			fields().that().areDeclaredInClassesThat().resideInAPackage("egovframework.eeum..web.dto..")
					.and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Response")
					.should().notHaveRawType(LocalDateTime.class)
					.because("응답 DTO 발생시각은 Instant 로 노출(UTC, ISO-8601 Z) — LocalDateTime 금지 (timezone-standard §2)");

	/** egovframework.eeum.{도메인}.… 패키지명에서 도메인 세그먼트 추출. */
	private static String domainOf(String packageName) {
		String prefix = "egovframework.eeum.";
		if (!packageName.startsWith(prefix)) {
			return packageName;
		}
		String rest = packageName.substring(prefix.length());
		int dot = rest.indexOf('.');
		return dot < 0 ? rest : rest.substring(0, dot);
	}
}
