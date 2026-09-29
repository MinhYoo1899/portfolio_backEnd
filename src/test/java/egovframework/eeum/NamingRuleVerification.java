package egovframework.eeum;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 검증 클래스 명명 규칙 — 팀 규칙: test 단어 금지, 접미사는 *Verification.
 * surefire 가 *Verification.java 만 실행하므로 다른 이름이면 조용히 미실행됨 — 그 함정을 빌드 실패로 차단.
 * (외부 검토 반영: 음성 규칙(*Test 금지)만으로는 FooIT/FooSpec 등을 못 잡아 양성 규칙 추가)
 */
@AnalyzeClasses(packages = "egovframework.eeum", importOptions = ImportOption.OnlyIncludeTests.class)
class NamingRuleVerification {

	/** JUnit @Test 메서드 또는 ArchUnit @ArchTest 멤버를 가진 클래스 = 실행돼야 할 검증 클래스 */
	private static final DescribedPredicate<JavaClass> CONTAIN_VERIFICATION_MEMBERS =
			new DescribedPredicate<>("JUnit @Test 메서드 또는 @ArchTest 멤버를 가진") {
				@Override
				public boolean test(JavaClass javaClass) {
					boolean hasTestMethod = javaClass.getMethods().stream()
							.anyMatch(m -> m.isAnnotatedWith("org.junit.jupiter.api.Test"));
					boolean hasArchTestField = javaClass.getFields().stream()
							.anyMatch(f -> f.isAnnotatedWith("com.tngtech.archunit.junit.ArchTest"));
					return hasTestMethod || hasArchTestField;
				}
			};

	/** 양성 규칙: 검증 멤버를 가진 클래스는 이름이 무엇이든 *Verification 으로 끝나야 한다 (조용한 미실행 차단). */
	@ArchTest
	static final ArchRule VERIFICATION_CLASSES_MUST_END_WITH_VERIFICATION =
			classes().that(CONTAIN_VERIFICATION_MEMBERS)
					.should().haveSimpleNameEndingWith("Verification")
					.because("surefire는 *Verification.java만 실행한다 — 다른 접미사는 조용히 미실행 (팀 명명 규칙)");

	/** 음성 규칙(보조): *Test/*Tests 접미사 금지 — test 단어 금지 규칙 명시. */
	@ArchTest
	static final ArchRule CLASSES_MUST_NOT_END_WITH_TEST =
			noClasses().should().haveSimpleNameEndingWith("Test")
					.orShould().haveSimpleNameEndingWith("Tests")
					.because("검증 클래스는 *Verification 으로 명명한다 (test 단어 금지 — 팀 명명 규칙)");
}
