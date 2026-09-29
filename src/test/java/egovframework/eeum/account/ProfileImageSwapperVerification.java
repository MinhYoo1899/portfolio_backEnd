package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.service.account.dao.AccountMapper;
import egovframework.eeum.account.service.account.impl.ProfileImageSwapper;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.FileService;

/**
 * 프로필 이미지 교체 트랜잭션 경계 검증 — 이전 파일 soft delete / 최초 등록(이전 없음) / 대상 없음(0건) 분기.
 */
class ProfileImageSwapperVerification {

	private final AccountMapper accountMapper = mock(AccountMapper.class);
	private final FileService fileService = mock(FileService.class);
	private final ProfileImageSwapper swapper = new ProfileImageSwapper(accountMapper, fileService);

	@Test
	@DisplayName("이전 프로필이 있으면 교체 후 이전 파일을 soft delete")
	void replacesAndSoftDeletesPrevious() {
		when(accountMapper.selectPrflImgFileSnForUpdate(7L)).thenReturn(11L); // 행 잠금으로 읽은 이전 파일
		when(accountMapper.updatePrflImgFileSn(7L, 42L)).thenReturn(1);

		swapper.swap(7L, 42L);

		verify(accountMapper).updatePrflImgFileSn(7L, 42L);
		verify(fileService).softDeleteByAtchFileSn(11L, 7L); // 이전 파일 정리
	}

	@Test
	@DisplayName("이전 프로필이 없으면(최초 등록) soft delete 호출 안 함")
	void firstTimeNoPreviousDelete() {
		when(accountMapper.selectPrflImgFileSnForUpdate(7L)).thenReturn(null);
		when(accountMapper.updatePrflImgFileSn(7L, 42L)).thenReturn(1);

		swapper.swap(7L, 42L);

		verify(fileService, never()).softDeleteByAtchFileSn(anyLong(), anyLong());
	}

	@Test
	@DisplayName("대상 행이 없으면(0건) FILE_NOT_FOUND")
	void targetRowMissing() {
		when(accountMapper.selectPrflImgFileSnForUpdate(7L)).thenReturn(null);
		when(accountMapper.updatePrflImgFileSn(7L, 42L)).thenReturn(0);

		BusinessException e = assertThrows(BusinessException.class, () -> swapper.swap(7L, 42L));
		assertEquals(FileErrorCode.FILE_NOT_FOUND, e.getErrorCode());
	}
}
