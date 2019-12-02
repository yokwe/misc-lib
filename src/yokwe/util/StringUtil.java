package yokwe.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class StringUtil {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(StringUtil.class);

	public interface MatcherFunction<T> {
		public T apply(Matcher matcher);
	}
	
	public static String replace(String string, Pattern pattern, MatcherFunction<String> operator) {
		StringBuilder ret = new StringBuilder();
		
		Matcher m = pattern.matcher(string);
		int lastEnd = 0;
		while(m.find()) {
			int start = m.start();
			int end   = m.end();

			// preamble
			if (lastEnd != start) {
				ret.append(string.substring(lastEnd, start));
			}
			
			// replace
			ret.append(operator.apply(m));
			
			lastEnd = end;
		}
		// postamble
		ret.append(string.substring(lastEnd));
		
		return ret.toString();
	}

	private static final Pattern PAT_UNESCAPE_HTML_CHAR = Pattern.compile("\\&\\#(?<code>[0-9]+)\\;");
	private static final MatcherFunction<String> OP_UNESCAPE_HTML_CHAR = (m) -> Character.toString((char)Integer.parseInt(m.group("code")));
	public static String unescapceHTMLChar(String string) {
		String ret = replace(string, PAT_UNESCAPE_HTML_CHAR, OP_UNESCAPE_HTML_CHAR);
		
		// unescape common char entity
		ret = ret.replace("&amp;",   "&");
		ret = ret.replace("&gt;",    ">");
		ret = ret.replace("&rsquo;", "'");
		ret = ret.replace("&nbsp;",  " ");
		ret = ret.replace("&#39;",   "'");

		return ret;
	}

	public static <T> Stream<T> find(String string, Pattern pattern, MatcherFunction<T> operator) {
		Stream.Builder<T> builder = Stream.builder();
		
		Matcher m = pattern.matcher(string);
		while(m.find()) {
			T value = operator.apply(m);
			builder.add(value);
		}
		
		return builder.build();
	}
	
	public static String removeBOM(String string) {
		String ret = new String(string);
		
		// Remove BOM
		if (ret.startsWith("\uFEFF")) ret = ret.substring(1);
		if (ret.startsWith("\uFFFE")) ret = ret.substring(1);
		
		return ret;
	}
	
	public static String toJavaConstName(String name) {
		StringBuilder ret = new StringBuilder();
		boolean lastCharIsUpper = false;
		for(int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);

			if (Character.isLowerCase(c)) {
				ret.append(Character.toUpperCase(c));
				lastCharIsUpper = false;
			} else if (Character.isDigit(c)) {
				ret.append(c);
				lastCharIsUpper = false;
			} else if (Character.isUpperCase(c)) {
				if (lastCharIsUpper) {
					ret.append(c);
				} else {
					if (ret.length() == 0) {
						ret.append(c);
					} else {
						ret.append('_').append(c);
					}
				}
				lastCharIsUpper = true;
			} else {
				throw new UnexpectedException(String.format("Unknown character type = %c - %04X", c, (int)c));
			}
		}
		return ret.toString();
	}

}
