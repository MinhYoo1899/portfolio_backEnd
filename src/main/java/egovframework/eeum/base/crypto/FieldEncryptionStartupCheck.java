package egovframework.eeum.base.crypto;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

/**
 * 필드 암호화 키 부팅 fail-fast (crypto-standard.md §6 — Phase C 활성).
 *
 * <p>Phase A/B 까지는 암호화 필드가 실제 wiring 되지 않아 키 미설정으로도 부팅을 허용했다(boot-safe).
 * Phase C 부터 {@code PRTNR_MST.PIC_MBL_TELNO} 가 TypeHandler 로 암복호화되므로, <b>키가 없으면 조회/저장이
 * 런타임에 깨진다.</b> 따라서 부팅 시점에 {@link FieldEncryptionKeyProvider#requireConfigured()} 로 키 존재를
 * 강제 검증해, 미설정을 <b>부팅 실패</b>로 승격한다(런타임 부분 장애 < 즉시 부팅 실패).
 *
 * <p>키 미설정 시 {@link FieldCryptoException}(키값 미노출) 으로 컨텍스트 초기화가 실패한다.
 * 단위테스트는 Spring 컨텍스트를 띄우지 않으므로 영향받지 않는다(키 주입은 통합/부팅 경로에서만 필요).
 */
@Component
public class FieldEncryptionStartupCheck {

	private final FieldEncryptionKeyProvider keyProvider;

	public FieldEncryptionStartupCheck(FieldEncryptionKeyProvider keyProvider) {
		this.keyProvider = keyProvider;
	}

	@PostConstruct
	void verifyKeyConfigured() {
		keyProvider.requireConfigured();
	}
}
