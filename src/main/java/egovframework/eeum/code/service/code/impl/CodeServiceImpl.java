package egovframework.eeum.code.service.code.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import egovframework.eeum.code.service.code.CodeService;
import egovframework.eeum.code.service.code.dao.CodeMapper;
import egovframework.eeum.code.web.dto.CodeItemResponse;
import lombok.RequiredArgsConstructor;

/**
 * 공통코드 서비스 구현 — 조회 전용(읽기). 캐싱은 추후 트래픽 증가 시(redis-strategy cache:comcd).
 */
@Service
@RequiredArgsConstructor
public class CodeServiceImpl implements CodeService {

	private final CodeMapper codeMapper;

	@Override
	public List<CodeItemResponse> list(String groupCd) {
		return codeMapper.selectActiveCodesByGroup(groupCd).stream()
				.map(CodeItemResponse::from)
				.toList();
	}
}
