package egovframework.eeum.idempotency.impl;

import java.time.Duration;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.idempotency.IdempotencyInfraException;
import egovframework.eeum.idempotency.IdempotencyRecord;
import egovframework.eeum.idempotency.IdempotencyStore;

/**
 * Redis 기반 멱등 저장소. 키=String, 값=레코드 JSON(StringRedisTemplate + ObjectMapper).
 *
 * <p>{@link RedisIdempotencyStore#claim(String, String, Duration)}은 setIfAbsent(원자 선점),
 * promote 는 set(덮어쓰기+TTL 재설정), release 는 delete. 연결 불가/직렬화 실패는 모두
 * {@link IdempotencyInfraException} 로 감싸 던져 필터가 fail-closed(503) 처리하게 한다.
 */
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public RedisIdempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
		this.redis = redis;
		this.objectMapper = objectMapper;
	}

	@Override
	public boolean claim(String key, String bodyHash, Duration pendingTtl) {
		try {
			String json = objectMapper.writeValueAsString(IdempotencyRecord.pending(bodyHash));
			Boolean acquired = redis.opsForValue().setIfAbsent(key, json, pendingTtl);
			return Boolean.TRUE.equals(acquired);
		} catch (JsonProcessingException e) {
			throw new IdempotencyInfraException("멱등 레코드(claim) 직렬화 실패", e);
		} catch (DataAccessException e) {
			throw new IdempotencyInfraException("멱등 저장소(claim) 접근 실패", e);
		}
	}

	@Override
	public Optional<IdempotencyRecord> find(String key) {
		try {
			String json = redis.opsForValue().get(key);
			if (json == null) {
				return Optional.empty();
			}
			return Optional.of(objectMapper.readValue(json, IdempotencyRecord.class));
		} catch (JsonProcessingException e) {
			throw new IdempotencyInfraException("멱등 레코드(find) 역직렬화 실패", e);
		} catch (DataAccessException e) {
			throw new IdempotencyInfraException("멱등 저장소(find) 접근 실패", e);
		}
	}

	@Override
	public void promote(String key, IdempotencyRecord doneRecord, Duration doneTtl) {
		try {
			redis.opsForValue().set(key, objectMapper.writeValueAsString(doneRecord), doneTtl);
		} catch (JsonProcessingException e) {
			throw new IdempotencyInfraException("멱등 레코드(promote) 직렬화 실패", e);
		} catch (DataAccessException e) {
			throw new IdempotencyInfraException("멱등 저장소(promote) 접근 실패", e);
		}
	}

	@Override
	public void release(String key) {
		try {
			redis.delete(key);
		} catch (DataAccessException e) {
			throw new IdempotencyInfraException("멱등 저장소(release) 접근 실패", e);
		}
	}
}
