package egovframework.eeum.base.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.slf4j.MDC;

/**
 * 감사 기록기({@link AuditRecorder})의 트랜잭션 정합 분기 회귀 검증 (B1, codex 교차검토 권고).
 *
 * <p>검증 범위(1차 — DB/Docker 불요): 업무 트랜잭션 동기화 활성/비활성에 따른 기록 시점 분기,
 * 커밋→SUCCESS / 롤백→FAIL 상태 매핑, 실패 경로의 즉시 기록, MDC corrId 포착, 기록 실패의 무해 삼킴.
 *
 * <p><b>B3 이관(Testcontainers 이후)</b>: {@link AuditLogWriter}의 {@code @Transactional(REQUIRES_NEW)}가
 * 실제 별도 트랜잭션으로 커밋되어 외부 업무 롤백과 무관하게 행이 보존되는지, MyBatis insert·스키마·dialect 정합은
 * 실제 MySQL 컨테이너에서 추가 검증한다(여기서는 Spring 프록시·DB 없이 분기 로직만 충실히 검증).
 *
 * <p>분기 기준 명시(codex 권고): {@code AuditRecorder}는 {@code isActualTransactionActive()}가 아니라
 * {@code TransactionSynchronizationManager.isSynchronizationActive()}로 판단한다. 따라서 이 테스트는
 * "실제 업무 트랜잭션 존재"가 아니라 "동기화 활성 분기"를 검증한다(일반 Spring 트랜잭션 흐름에서 둘은 함께 켜진다).
 */
class AuditRecorderVerification {

	/** 기록된 VO 를 포착하는 테스트용 writer — 실제 DB 접근 없이 호출 인자만 캡처. */
	private static final class CapturingWriter extends AuditLogWriter {
		private final List<AuditLogInfoVO> written = new ArrayList<>();
		private boolean throwOnWrite;

		private CapturingWriter() {
			super(null); // mapper 미사용 — write() 를 오버라이드해 DB 접근을 차단
		}

		@Override
		public void write(AuditLogInfoVO auditLog) {
			if (throwOnWrite) {
				throw new IllegalStateException("simulated write failure");
			}
			written.add(auditLog);
		}
	}

	private final CapturingWriter writer = new CapturingWriter();
	/** 로그인 사용자 행위자 고정 — actor 해석 분기와 무관하게 결과 기록만 본다. */
	private final ActorResolver actorResolver = () -> new AuditActor(7L, "USER");
	private final AuditRecorder recorder = new AuditRecorder(writer, actorResolver);

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
		MDC.clear();
	}

	@Test
	@DisplayName("트랜잭션 밖 성공 기록 → 즉시 SUCCESS 기록 + corrId 포착")
	void noTransaction_writesSuccessImmediately() {
		MDC.put(CorrelationIdFilter.MDC_KEY, "corr-001");

		recorder.recordSuccess("PARTNER_LIST_VIEW", "PARTNER", "42");

		assertThat(writer.written).hasSize(1);
		AuditLogInfoVO log = writer.written.get(0);
		assertThat(log.getPrcsRslt()).isEqualTo(AuditRecorder.RESULT_SUCCESS);
		assertThat(log.getActnCn()).isEqualTo("PARTNER_LIST_VIEW");
		assertThat(log.getTrgtType()).isEqualTo("PARTNER");
		assertThat(log.getTrgtId()).isEqualTo("42");
		assertThat(log.getActrUserSn()).isEqualTo(7L);
		assertThat(log.getCorrId()).isEqualTo("corr-001");
	}

	@Test
	@DisplayName("트랜잭션 활성 시 성공 기록 → 커밋 전까지 지연(동기화만 등록)")
	void activeTransaction_defersUntilCompletion() {
		TransactionSynchronizationManager.initSynchronization();

		recorder.recordSuccess("ACCOUNT_UPDATE", "ACCOUNT", "7");

		assertThat(writer.written).as("커밋 전에는 기록되지 않아야 함").isEmpty();
		assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
	}

	@Test
	@DisplayName("트랜잭션 커밋 완료 → SUCCESS 로 기록")
	void afterCommit_writesSuccess() {
		TransactionSynchronizationManager.initSynchronization();
		recorder.recordSuccess("ACCOUNT_UPDATE", "ACCOUNT", "7");

		completeWith(TransactionSynchronization.STATUS_COMMITTED);

		assertThat(writer.written).hasSize(1);
		assertThat(writer.written.get(0).getPrcsRslt()).isEqualTo(AuditRecorder.RESULT_SUCCESS);
	}

	@Test
	@DisplayName("트랜잭션 롤백 → 성공 기록이라도 FAIL 로 전환")
	void afterRollback_marksFail() {
		TransactionSynchronizationManager.initSynchronization();
		recorder.recordSuccess("ACCOUNT_UPDATE", "ACCOUNT", "7");

		completeWith(TransactionSynchronization.STATUS_ROLLED_BACK);

		assertThat(writer.written).hasSize(1);
		assertThat(writer.written.get(0).getPrcsRslt()).isEqualTo(AuditRecorder.RESULT_FAIL);
	}

	@Test
	@DisplayName("완료 상태 불명(STATUS_UNKNOWN) → 안전하게 FAIL 로 기록")
	void unknownCompletion_marksFail() {
		TransactionSynchronizationManager.initSynchronization();
		recorder.recordSuccess("ACCOUNT_UPDATE", "ACCOUNT", "7");

		completeWith(TransactionSynchronization.STATUS_UNKNOWN);

		assertThat(writer.written).hasSize(1);
		assertThat(writer.written.get(0).getPrcsRslt()).isEqualTo(AuditRecorder.RESULT_FAIL);
	}

	@Test
	@DisplayName("실패 기록 → 트랜잭션 활성 여부와 무관하게 즉시 FAIL 기록(롤백과 함께 사라지지 않음)")
	void recordFail_writesImmediatelyEvenInTransaction() {
		TransactionSynchronizationManager.initSynchronization();

		recorder.recordFail("ACCOUNT_UPDATE", "ACCOUNT", "7");

		assertThat(writer.written).hasSize(1);
		assertThat(writer.written.get(0).getPrcsRslt()).isEqualTo(AuditRecorder.RESULT_FAIL);
		assertThat(TransactionSynchronizationManager.getSynchronizations())
				.as("실패 기록은 커밋 동기화에 의존하지 않아야 함").isEmpty();
	}

	@Test
	@DisplayName("기록 자체가 실패해도 본 업무로 예외가 전파되지 않음(무해 삼킴)")
	void writeFailure_isSwallowed() {
		writer.throwOnWrite = true;

		assertThatCode(() -> recorder.recordFail("ACCOUNT_UPDATE", "ACCOUNT", "7"))
				.doesNotThrowAnyException();
		assertThatCode(() -> recorder.recordSuccess("ACCOUNT_UPDATE", "ACCOUNT", "7"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("지연 기록 경로 → corrId 는 호출 시점 값으로 고정(이후 MDC 변경에 영향 없음)")
	void deferredPath_capturesCorrIdAtCallTime() {
		MDC.put(CorrelationIdFilter.MDC_KEY, "corr-at-call");
		TransactionSynchronizationManager.initSynchronization();
		recorder.recordSuccess("ACCOUNT_UPDATE", "ACCOUNT", "7");

		MDC.put(CorrelationIdFilter.MDC_KEY, "corr-changed-later"); // 커밋 콜백 시점에 MDC 가 바뀌어도

		completeWith(TransactionSynchronization.STATUS_COMMITTED);

		assertThat(writer.written).hasSize(1);
		assertThat(writer.written.get(0).getCorrId())
				.as("VO 는 등록(호출) 시점에 build 되므로 corrId 는 호출 시점 값이어야 함")
				.isEqualTo("corr-at-call");
	}

	@Test
	@DisplayName("지연 기록 경로에서 기록이 실패해도 커밋 콜백이 예외를 전파하지 않음(무해 삼킴)")
	void deferredPath_writeFailureSwallowedOnCommit() {
		writer.throwOnWrite = true;
		TransactionSynchronizationManager.initSynchronization();
		recorder.recordSuccess("ACCOUNT_UPDATE", "ACCOUNT", "7");

		assertThatCode(() -> completeWith(TransactionSynchronization.STATUS_COMMITTED))
				.doesNotThrowAnyException();
	}

	/** 등록된 트랜잭션 동기화들의 afterCompletion 을 주어진 상태로 호출(실제 커밋/롤백 시점 시뮬레이션). */
	private void completeWith(int completionStatus) {
		List<TransactionSynchronization> synchronizations =
				new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
		TransactionSynchronizationManager.clearSynchronization();
		for (TransactionSynchronization synchronization : synchronizations) {
			synchronization.afterCompletion(completionStatus);
		}
	}
}
