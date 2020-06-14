package yokwe.util;

import java.util.Deque;
import java.util.LinkedList;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import yokwe.UnexpectedException;

public class LoggingUtil {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(LoggingUtil.class);
	
	private static final Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
	private static Deque<Level> stack = new LinkedList<>();
	
	public static void turnOff() {
		push(Level.OFF);
	}
	public static void turnOn() {
		pop();
	}
	
	public static void push(Level newValue) {
		stack.push(root.getLevel());
		root.setLevel(newValue);
	}
	public static void pop() {
		if (stack.isEmpty()) {
			if (root.getLevel() == Level.OFF) {
				root.setLevel(Level.ALL);
			}
			logger.error("stack.isEmpty()");
			throw new UnexpectedException("stack.isEmpty()");
		}
		root.setLevel(stack.pop());
	}
}
