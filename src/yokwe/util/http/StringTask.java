package yokwe.util.http;

import java.net.URI;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class StringTask extends Task {
	static final Logger logger = LoggerFactory.getLogger(StringTask.class);

	private static class MyConsumer implements Consumer<Result> {		
		private Consumer<String> consumer;
		
		public MyConsumer(Consumer<String> consumer) {
			this.consumer = consumer;
		}
		
		@Override
		public void accept(Result result) {
			if (result.charset == null) {
				logger.error("charset is null");
				logger.error("  uri         {}", result.task.uri);
				logger.error("  contentType {}", result.contentType);
				throw new UnexpectedException("charset is null");
			}
			String page = new String(result.body, result.charset);
			consumer.accept(page);
		}
	}
	
	private StringTask(URI uri, Consumer<Result> consumer) {
		super(uri, consumer);
	}
	private StringTask(String uriString, Consumer<Result> consumer) {
		super(uriString, consumer);
	}

	public static StringTask text(URI uri, Consumer<String> consumer) {
		return new StringTask(uri, new MyConsumer(consumer));
	}
	public static StringTask text(String uriString, Consumer<String> consumer) {
		return new StringTask(uriString, new MyConsumer(consumer));
	}
}