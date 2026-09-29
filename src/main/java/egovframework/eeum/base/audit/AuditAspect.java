package egovframework.eeum.base.audit;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link Audit} 어노테이션 처리 Aspect — 성공/실패 결과와 함께 감사 기록.
 *
 * <p>알려진 한계(설계 명시 — codex 권고): self-invocation 미동작(외부 진입 메서드에만 부착),
 * 컨트롤러 검증/보안 단계에서 막힌 요청은 서비스까지 오지 않아 기록되지 않음(그 영역은 LOGIN_LOG_INFO·보안 핸들러 담당).
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

	private final AuditRecorder auditRecorder;

	private final ExpressionParser spelParser = new SpelExpressionParser();
	private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	@Around("@annotation(audit)")
	public Object record(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
		try {
			Object result = joinPoint.proceed();
			auditRecorder.recordSuccess(audit.actn(), audit.trgtType(),
					evaluateTrgtId(audit.trgtId(), joinPoint, result));
			return result;
		} catch (Throwable e) {
			auditRecorder.recordFail(audit.actn(), audit.trgtType(),
					evaluateTrgtId(audit.trgtId(), joinPoint, null));
			throw e;
		}
	}

	/** SpEL 평가 — 실패해도 기록은 계속(대상 식별자만 비움, 본 업무 불간섭). */
	private String evaluateTrgtId(String expression, ProceedingJoinPoint joinPoint, Object result) {
		if (expression == null || expression.isBlank()) {
			return null;
		}
		try {
			StandardEvaluationContext context = new StandardEvaluationContext();
			Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
			String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
			Object[] args = joinPoint.getArgs();
			if (parameterNames != null) {
				for (int i = 0; i < parameterNames.length && i < args.length; i++) {
					context.setVariable(parameterNames[i], args[i]);
				}
			}
			context.setVariable("result", result);
			Object value = spelParser.parseExpression(expression).getValue(context);
			return value == null ? null : String.valueOf(value);
		} catch (RuntimeException e) {
			log.warn("[AUDIT] trgtId SpEL evaluation failed. expression={}", expression, e);
			return null;
		}
	}
}
