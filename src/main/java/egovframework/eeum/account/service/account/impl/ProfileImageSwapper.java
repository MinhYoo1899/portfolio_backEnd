package egovframework.eeum.account.service.account.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.FileService;
import lombok.RequiredArgsConstructor;

/**
 * 프로필 이미지 교체의 DB 트랜잭션 경계 — 행 잠금 SELECT FOR UPDATE → UPDATE → 이전파일 soft delete.
 *
 * <p>별도 빈으로 분리한 이유: {@code @Transactional} 은 Spring AOP 프록시를 거쳐야 적용되는데,
 * 저장(SFTP)을 수행하는 서비스 메서드에서 같은 클래스 내부 호출(self-invocation)로 트랜잭션 메서드를 부르면
 * 프록시를 우회해 트랜잭션이 걸리지 않는다(Audit 어노테이션 주석의 한계와 동일). 프록시 경계를 넘기 위해 분리한다.
 */
@Component
@RequiredArgsConstructor
public class ProfileImageSwapper {

	private final AccountMapper accountMapper;
	private final FileService fileService;

	/**
	 * 행 잠금으로 이전 프로필을 읽고 새 파일로 교체한 뒤, 이전 파일을 soft delete(같은 트랜잭션에서 일관 처리).
	 * 동시 교체 시 FOR UPDATE 로 직렬화되어 한쪽의 이전값이 다른쪽 결과로 덮이지 않는다.
	 */
	@Transactional
	public void swap(long userSn, long newAtchFileSn) {
		Long previous = accountMapper.selectPrflImgFileSnForUpdate(userSn); // 행 잠금(FOR UPDATE)
		int updated = accountMapper.updatePrflImgFileSn(userSn, newAtchFileSn);
		if (updated == 0) {
			throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
		}
		if (previous != null && !previous.equals(newAtchFileSn)) {
			// 이전 프로필 파일 soft delete(물리삭제 best-effort) — 교체 정합성 위해 같은 트랜잭션
			fileService.softDeleteByAtchFileSn(previous, userSn);
		}
	}
}
