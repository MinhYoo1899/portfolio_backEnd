package egovframework.eeum.base.logging;

import java.util.List;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Log4j2 커스텀 converter — {@code %mask{내부패턴}} 으로 감싼 부분(메시지+예외)만 {@link PiiMasker} 로 마스킹.
 *
 * <p>corrId(%X{corrId})·레벨·타임스탬프는 %mask 밖에 두어 절대 마스킹하지 않는다(corrId 보존 MUST).</p>
 */
@Plugin(name = "PiiMaskingConverter", category = "Converter")
@ConverterKeys({ "mask" })
public final class PiiMaskingConverter extends LogEventPatternConverter {

	private final List<PatternFormatter> formatters;

	private PiiMaskingConverter(List<PatternFormatter> formatters) {
		super("mask", "mask");
		this.formatters = formatters;
	}

	/** options[0] = 내부 패턴. PatternLayout 의 파서로 내부 PatternFormatter 리스트로 파싱한다. */
	public static PiiMaskingConverter newInstance(Configuration config, String[] options) {
		String innerPattern = (options != null && options.length > 0 && options[0] != null) ? options[0] : "%m";
		PatternParser parser = PatternLayout.createPatternParser(config);
		List<PatternFormatter> formatters = parser.parse(innerPattern);
		return new PiiMaskingConverter(formatters);
	}

	@Override
	public void format(LogEvent event, StringBuilder toAppendTo) {
		StringBuilder buffer = new StringBuilder();
		for (PatternFormatter formatter : formatters) {
			formatter.format(event, buffer);
		}
		toAppendTo.append(PiiMasker.mask(buffer.toString()));
	}
}
