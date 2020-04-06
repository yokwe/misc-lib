package yokwe.util.http;

import java.net.URI;
import java.util.function.Consumer;

public class BasicTask extends Task {
	public BasicTask(URI uri, Consumer<Result> consumer) {
		super(uri, consumer);
	}
	public BasicTask(String uriString, Consumer<Result> consumer) {
		this(URI.create(uriString), consumer);
	}
	public void beforeProdess(Task task) {
//			logger.info("beforeProcess {}", task.uri);
	}
	public void afterProcess(Task task) {
//			logger.info("afterProcess  {}", task.uri);
	}
}