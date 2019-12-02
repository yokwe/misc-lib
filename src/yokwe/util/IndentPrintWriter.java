package yokwe.util;

import java.io.PrintWriter;

import yokwe.UnexpectedException;

public class IndentPrintWriter implements AutoCloseable {
	private static final String INDENT = "    ";

	private final PrintWriter out;
	private int level = 0;

	public IndentPrintWriter(PrintWriter out) {
		this.out = out;
	}
	public void close() {
		out.close();
	}
	public void nest() {
		level++;
	}
	public void unnest() {
		level--;
		if (level < 0) throw new UnexpectedException("level < 0");
	}
	public PrintWriter indent() {
		for(int i = 0; i < level; i++) out.print(INDENT);
		return out;
	}
}