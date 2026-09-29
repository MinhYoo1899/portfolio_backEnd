package egovframework.eeum.auth.service.social;

import egovframework.eeum.auth.social.SocialUser;

/**
 * 소셜 신규 가입 대기(pending) 저장소 — 소셜 인증은 통과했으나 약관 동의 전(계정 미생성) 상태의
 * 검증된 신원을 1회용 토큰으로 보관(전략 §2-2: 약관 전 활성 계정 생성 금지). 짧은 TTL.
 */
public interface SocialSignupStore {

	/** 검증된 신원을 보관하고 1회용 가입 토큰을 발급. */
	String issue(SocialUser user);

	/** 가입 토큰으로 신원을 1회 소비(consume) — 없거나 만료/재사용이면 null. */
	SocialUser consume(String signupToken);
}
