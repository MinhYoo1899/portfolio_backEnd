package egovframework.eeum.auth.service.auth.impl;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.ActorResolver;
import egovframework.eeum.base.audit.AuditActor;

/**
 * 감사 행위자 식별 구현 — SecurityContext 의 {@link AuthUser} 를 base 경계 타입으로 변환.
 * (base→도메인 의존 금지(D15)를 지키는 ActorResolver 패턴 — codex 권고)
 */
@Component
public class AuthUserActorResolver implements ActorResolver {

	@Override
	public AuditActor resolveActor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
			return new AuditActor(user.userSn(), user.userTypeCd());
		}
		return AuditActor.anonymous();
	}
}
