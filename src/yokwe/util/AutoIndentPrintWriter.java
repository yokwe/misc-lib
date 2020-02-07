package yokwe.util;

import java.io.PrintWriter;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class AutoIndentPrintWriter implements AutoCloseable {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(AutoIndentPrintWriter.class);

	private static final String INDENT = "    ";

	private final PrintWriter out;
	private int level = 0;

	public AutoIndentPrintWriter(PrintWriter out) {
		this.out = out;
	}
	public void close() {
		out.close();
	}
	public void println() {
		out.println();
	}
	public void println(String string) {
		for(int i = 0; i < level; i++) out.print(INDENT);
		out.println(string);
		
		// adjust level
		for(int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			switch (c) {
			case '{':
			case '(':
				level++;
				break;
			case '}':
			case ')':
				level--;
				if (level < 0) {
					logger.error("level < 0");
					logger.error("  string {}!", string);
					throw new UnexpectedException("level < 0");
				}
				break;
			default:
				break;
			}
		}
	}
	public void println(String format, Object... args) {
		String string = String.format(format, args);
		println(string);
	}
}