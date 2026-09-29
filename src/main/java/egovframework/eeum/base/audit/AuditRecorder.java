package egovframework.eeum.base.audit;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 감사 기록 공개 API — @Audit(AOP)와 명시 호출(목록 조회 등) 양쪽이 공용 (하이브리드, codex 권고).
 *
 * <p>트랜잭션 정합(codex 권고): 업무 트랜잭션이 진행 중이면 SUCCESS 기록을 커밋 이후로 미루고
 * (커밋 실패/롤백 시 FAIL 로 전환), 트랜잭션 밖이면 즉시 기록한다. 기록 실패는 본 업무에 영향 없음(error 로그만).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRecorder {

	public static final String RESULT_SUCCESS = "SUCCESS";
	public static final String RESULT_FAIL = "FAIL";

	private final AuditLogWriter auditLogWriter;
	private final ActorResolver actorResolver;

	/**
	 * 성공 기록 — 업무 트랜잭션 커밋과 정합 보장.
	 * 트랜잭션 활성 시: 커밋 완료 후 SUCCESS, 롤백 시 FAIL 로 기록.
	 */
	public void recordSuccess(String actn, String trgtType, String trgtId) {
		AuditLogInfoVO auditLog = build(actn, trgtType, trgtId, RESULT_SUCCESS);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					String result = status == STATUS_COMMITTED ? RESULT_SUCCESS : RESULT_FAIL;
					writeSafely(withResult(auditLog, result));
				}
			});
		} else {
			writeSafely(auditLog);
		}
	}

	/** 실패 기록 — 즉시 별도 트랜잭션으로 기록(본 트랜잭션 롤백과 무관하게 남음). */
	public void recordFail(String actn, String trgtType, String trgtId) {
		writeSafely(build(actn, trgtType, trgtId, RESULT_FAIL));
	}

	// ------------------------------------------------------------------

	private AuditLogInfoVO build(String actn, String trgtType, String trgtId, String prcsRslt) {
		AuditActor actor = resolveActorSafely();
		HttpServletRequest request = currentRequest();
		return AuditLogInfoVO.builder()
				.actrUserSn(actor.actrUserSn())
				.actrType(truncate(actor.actrType(), 20))
				.actnCn(truncate(actn, 100))
				.trgtType(truncate(trgtType, 50))
				.trgtId(truncate(trgtId, 50))
				.prcsRslt(prcsRslt)
				.cntnIp(request == null ? null : request.getRemoteAddr())
				.userAgnt(request == null ? null : truncate(request.getHeader(HttpHeaders.USER_AGENT), 500))
				.corrId(MDC.get(CorrelationIdFilter.MDC_KEY))
				.build();
	}

	private static AuditLogInfoVO withResult(AuditLogInfoVO source, String prcsRslt) {
		return AuditLogInfoVO.builder()
				.actrUserSn(source.getActrUserSn())
				.actrType(source.getActrType())
				.actnCn(source.getActnCn())
				.trgtType(source.getTrgtType())
				.trgtId(source.getTrgtId())
				.prcsRslt(prcsRslt)
				.cntnIp(source.getCntnIp())
				.userAgnt(source.getUserAgnt())
				.corrId(source.getCorrId())
				.build();
	}

	private AuditActor resolveActorSafely() {
		try {
			AuditActor actor = actorResolver.resolveActor();
			return actor == null ? AuditActor.anonymous() : actor;
		} catch (RuntimeException e) {
			log.warn("[AUDIT] actor resolve failed", e);
			return AuditActor.anonymous();
		}
	}

	private void writeSafely(AuditLogInfoVO auditLog) {
		try {
			auditLogWriter.write(auditLog);
		} catch (RuntimeException e) {
			// 기록 실패가 본 업무를 막으면 안 됨 (LOGIN_LOG_INFO 패턴)
			log.error("[AUDIT] audit log write failed. actn={}", auditLog.getActnCn(), e);
		}
	}

	private static HttpServletRequest currentRequest() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			return attributes.getRequest();
		}
		return null;
	}

	private static String truncate(String value, int max) {
		return value != null && value.length() > max ? value.substring(0, max) : value;
	}
}
