package egovframework.eeum.file;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.impl.FileAttachmentCleanupJob;
import egovframework.eeum.file.vo.AtchFileVO;

/**
 * 미연결 만료 TEMP 정리잡 검증 — 전 scope를 건별 {@code cleanupExpiredTempAttachment}(프록시)로 정리,
 * 경합(0건) 미포함, 예외는 삼켜 다음 주기 재시도. (inquiry 전용 정리 테스트를 파일 도메인으로 이동·일반화.)
 */
class FileAttachmentCleanupJobVerification {

	private final FileService fileService = mock(FileService.class);
	private final FileAttachmentCleanupJob job = new FileAttachmentCleanupJob(fileService);

	private static AtchFileVO temp(long atchFileSn) {
		AtchFileVO vo = new AtchFileVO();
		vo.setAtchFileSn(atchFileSn);
		return vo;
	}

	@Test
	@DisplayName("전 scope 만료 TEMP 를 건별 cleanupExpiredTempAttachment 로 정리(건별 트랜잭션 보존)")
	void cleansAllExpiredPerItem() {
		when(fileService.selectExpiredTempAttachments(anyInt())).thenReturn(List.of(temp(10L), temp(11L)));
		when(fileService.cleanupExpiredTempAttachment(anyLong())).thenReturn(1);

		job.cleanupExpiredTemp();

		verify(fileService).cleanupExpiredTempAttachment(10L);
		verify(fileService).cleanupExpiredTempAttachment(11L);
	}

	@Test
	@DisplayName("그새 LINKED 되어 0건이면 경합 방어로 건너뜀(예외 없이 진행)")
	void skipsRelinkedZero() {
		when(fileService.selectExpiredTempAttachments(anyInt())).thenReturn(List.of(temp(10L)));
		when(fileService.cleanupExpiredTempAttachment(10L)).thenReturn(0);

		assertDoesNotThrow(job::cleanupExpiredTemp);
		verify(fileService).cleanupExpiredTempAttachment(10L);
	}

	@Test
	@DisplayName("목록 조회 실패는 삼켜 다음 주기 재시도(부팅/서비스 흐름 영향 없음)")
	void swallowsException() {
		when(fileService.selectExpiredTempAttachments(anyInt())).thenThrow(new RuntimeException("db down"));

		assertDoesNotThrow(job::cleanupExpiredTemp);
	}

	@Test
	@DisplayName("건별 실패는 건너뛰고 나머지 계속 정리(한 건이 뒤 건을 굶기지 않음, codex SHOULD)")
	void perItemFailureContinues() {
		when(fileService.selectExpiredTempAttachments(anyInt())).thenReturn(List.of(temp(10L), temp(11L)));
		when(fileService.cleanupExpiredTempAttachment(10L)).thenThrow(new RuntimeException("boom"));
		when(fileService.cleanupExpiredTempAttachment(11L)).thenReturn(1);

		assertDoesNotThrow(job::cleanupExpiredTemp);

		verify(fileService).cleanupExpiredTempAttachment(10L); // 실패해도
		verify(fileService).cleanupExpiredTempAttachment(11L); // 다음 건 처리됨
	}
}
