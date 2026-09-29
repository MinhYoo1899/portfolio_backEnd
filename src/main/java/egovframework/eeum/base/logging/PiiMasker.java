package egovframework.eeum.base.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그 메시지 내 PII/민감값 마스킹 순수함수(Log4j2 의존 없음 — 단위테스트 용이).
 *
 * <p>설계 원칙(codex 교차검토 MUST): <b>과마스킹 금지</b>. 키-값 기반 민감값·구조가 명확한 PII(이메일/휴대폰/주민번호/
 * 카드/Bearer)만 좁게 마스킹한다. 한글 이름 자유탐지, 무차별 숫자/hex/UUID/base64 매칭은 절대 하지 않는다
 * (특히 corrId(UUID)·주문번호·productName/fileName/roleName 등이 훼손되면 안 됨).</p>
 */
public final class PiiMasker {

	private PiiMasker() {
	}

	private static final String MASK = "***";

	/**
	 * key 기반 민감값 — JSON("key":"val") / kv(key=val) 둘 다. 값 경계는 따옴표/공백/구분자 전까지.
	 * 키 뒤 닫는 따옴표(JSON) 허용("?\s*[:=]). authorization 키 뒤가 bearer 면 BEARER 패턴이 처리하므로 제외(?!bearer).
	 */
	private static final Pattern SENSITIVE_KEY = Pattern.compile(
			"(?i)\\b(password|passwd|pwd|secret|accessToken|refreshToken|authorization|cookie|set-cookie"
					+ "|paymentKey|approvalNo|accountNo|acctNo|ci|di|rrn)\\b\"?\\s*[:=]\\s*\"?(?!bearer\\b)([^\"\\s,;}]+)");

	/** Bearer 토큰. */
	private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._\\-+/=]+");

	/** 주민번호 — 대시 있는 것만(무차별 13자리 금지). */
	private static final Pattern RRN = Pattern.compile("(?<!\\d)\\d{6}-\\d{7}(?!\\d)");

	/** 카드번호 후보 — 13~19자리(공백/대시 구분 허용). Luhn 통과한 것만 마스킹. */
	private static final Pattern CARD_CANDIDATE = Pattern.compile(
			"(?<![\\d])(\\d[ -]?){12,18}\\d(?![\\d])");

	/** 이메일 — 첫 글자 + 도메인 유지. */
	private static final Pattern EMAIL = Pattern.compile(
			"(?i)([\\w.%+\\-])[\\w.%+\\-]*@([\\w.\\-]+\\.[A-Za-z]{2,})");

	/** 한국 휴대폰(국내 표기). */
	private static final Pattern PHONE_KR = Pattern.compile(
			"(?<!\\d)(01[016789])[-.\\s]?\\d{3,4}[-.\\s]?(\\d{4})(?!\\d)");

	/** +82 변형. */
	private static final Pattern PHONE_KR_INTL = Pattern.compile(
			"(?i)\\+?82[-.\\s]?10[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");

	/**
	 * 입력 문자열에서 민감값을 마스킹한다. null/빈 입력은 그대로 반환.
	 * 순서 주의: 충돌 적은 순서로 적용(키-값 → Bearer → 주민번호 → 카드(Luhn) → 이메일 → 휴대폰).
	 */
	public static String mask(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		String s = input;
		s = SENSITIVE_KEY.matcher(s).replaceAll(matchResult -> {
			// group(1)=키, group(2)=값. 키 + 구분자 부분은 보존하고 값만 ***.
			String full = matchResult.group();
			String value = matchResult.group(2);
			int valueStart = full.lastIndexOf(value);
			return Matcher.quoteReplacement(full.substring(0, valueStart) + MASK);
		});
		s = BEARER.matcher(s).replaceAll("$1" + MASK);
		s = RRN.matcher(s).replaceAll("******-*******");
		s = maskCards(s);
		s = EMAIL.matcher(s).replaceAll("$1" + MASK + "@$2");
		s = PHONE_KR.matcher(s).replaceAll("$1-****-$2");
		s = PHONE_KR_INTL.matcher(s).replaceAll(MASK);
		return s;
	}

	/** 카드 후보 중 Luhn 통과한 것만 마지막 4자리만 남기고 마스킹. 비-Luhn(주문번호 등)은 그대로 둔다. */
	private static String maskCards(String input) {
		Matcher m = CARD_CANDIDATE.matcher(input);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String candidate = m.group();
			String digits = candidate.replaceAll("[ -]", "");
			if (digits.length() >= 13 && digits.length() <= 19 && luhnValid(digits)) {
				String last4 = digits.substring(digits.length() - 4);
				m.appendReplacement(sb, Matcher.quoteReplacement(MASK + last4));
			} else {
				m.appendReplacement(sb, Matcher.quoteReplacement(candidate));
			}
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/** Luhn 체크섬 검증. */
	private static boolean luhnValid(String digits) {
		int sum = 0;
		boolean doubleIt = false;
		for (int i = digits.length() - 1; i >= 0; i--) {
			int d = digits.charAt(i) - '0';
			if (doubleIt) {
				d *= 2;
				if (d > 9) {
					d -= 9;
				}
			}
			sum += d;
			doubleIt = !doubleIt;
		}
		return sum % 10 == 0;
	}
}
