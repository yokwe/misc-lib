package yokwe.util;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
	
	private String stripString(String string) {
		StringBuffer ret = new StringBuffer();
		
		boolean insideDoubleQuote = false;
		
		int length = string.length();
		for(int i = 0; i < length; i++) {
			char c1 = string.charAt(i);
			char c2 = (i == length - 1) ? '\0' : string.charAt(i + 1);
			
			// line comment
			if ((!insideDoubleQuote) && c1 == '/' && c2 == '/') {
				break;
			}
			// ordinary comment
			if ((!insideDoubleQuote) && c1 == '/' && c2 == '*') {
				logger.error("Unexpected java comment");
				logger.error("  string {}!", string);
				throw new UnexpectedException("level < 0");
			}
			
			if (insideDoubleQuote) {
				//
			} else {
				// line comment
				if (c1 == '/' && c2 == '/') {
					break;
				}
				// ordinary comment
				if (c1 == '/' && c2 == '*') {
					logger.error("Unexpected java comment");
					logger.error("  string {}!", string);
					throw new UnexpectedException("level < 0");
				}
			}
			
			// \" can be appeared inside double quote
			if (c1 == '\\' && c2 == '"') {
				i++; // advance i to skip backslash
				continue;
			}
			
			if (c1 == '"') {
				insideDoubleQuote = !insideDoubleQuote;
				continue;
			}
			
			if (!insideDoubleQuote) {
				switch(c1) {
				case '{':
				case '}':
				case '(':
				case ')':
					ret.append(c1);
					break;
				default:
					break;
				}
			}
		}
		
		return ret.toString();
	}
	
	private boolean layout = false;
	private List<String> layoutLineList = new ArrayList<>();

	public void println(String string) {
		if (layout) {
			layoutLineList.add(string);
		} else {
			printlnInternal(string);
		}
	}
	private void printlnInternal(String string) {
		String strippedString = stripString(string);
		
		// adjust level
		for(char c: strippedString.toCharArray()) {
			switch (c) {
			case '}':
				level--;
				if (level < 0) {
					logger.error("level < 0");
					logger.error("  string {}!", string);
					logger.error("  strippedString {}!", strippedString);
					throw new UnexpectedException("level < 0");
				}
				break;
			default:
				break;
			}
		}

		// special handling for case and default
		int adjustLevel = 0;
		{
			String s = string.trim();
			if (s.startsWith("case " ))   adjustLevel = -1;
			if (s.startsWith("default:")) adjustLevel = -1;
		}
		
		for(int i = 0; i < (level + adjustLevel); i++) out.print(INDENT); 
		out.println(string);
		
		// adjust level
		for(char c: strippedString.toCharArray()) {
			switch (c) {
			case ')':
				level--;
				if (level < 0) {
					logger.error("level < 0");
					logger.error("  string {}!", string);
					logger.error("  strippedString {}!", strippedString);
					throw new UnexpectedException("level < 0");
				}
				break;
			case '{':
			case '(':
				level++;
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
	
	public enum Layout {
		LEFT, RIGHT
	}
	public void prepareLayout() {
		if (layout) {
			logger.error("Unexpected state");
			throw new UnexpectedException("Unexpected state");
		}
		
		layout = true;
		layoutLineList.clear();
	}
	public void layout(Layout... layouts) {
		if (!layout) {
			logger.error("Unexpected state");
			throw new UnexpectedException("Unexpected state");
		}

		int count = layouts.length;
		String[][] tokens = new String[layoutLineList.size()][count];
		{
			for(int i = 0; i < layoutLineList.size(); i++) {
				String line = layoutLineList.get(i);
				String[] token = line.split("[ ]+");
				if (token.length != count) {
					logger.error("Unecpected line");
					logger.error("  count {}!", count);
					logger.error("  token {}!", Arrays.asList(token));
					logger.error("  line  {}!", line);
					throw new UnexpectedException("Unecpected line");
				}
				for(int j = 0; j < token.length; j++) {
					tokens[i][j] = token[j];
				}
			}	
		}
		
		int width[] = new int[count];
		{
			for(int i = 0; i < width.length; i++) width[i] = 0;
			for(int i = 0; i < tokens.length; i++) {
				for(int j = 0; j < width.length; j++) {
					width[j] = Math.max(width[j], tokens[i][j].length());
				}
			}
		}
		
		final String format;
		{
			List<String> list = new ArrayList<>();
			for(int i = 0; i < count; i++) {
				list.add(String.format("%%%s%ds", layouts[i] == Layout.LEFT ? "-" : "", width[i]));
			}
			format = String.join(" ", list);
		}
		
		for(int i = 0; i < tokens.length; i++) {
			String string = String.format(format, (Object[])tokens[i]);
			logger.info("string  {}", string);
			printlnInternal(string);
		}
		
		//
		layout = false;
		layoutLineList.clear();
	}
	
}