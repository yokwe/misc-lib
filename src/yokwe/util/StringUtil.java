package yokwe.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

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
	
}
