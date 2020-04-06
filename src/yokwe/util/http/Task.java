package yokwe.util.http;

import java.net.URI;
import java.util.function.Consumer;

public abstract class Task {
	public final URI uri;
	public final Consumer<Result> consumer;
	
	public Task(URI uri, Consumer<Result> consumer) {
		this.uri      = uri;
		this.consumer = consumer;
	}
	
	public abstract void beforeProdess(Task task);
	public          void process(Result result) {
		consumer.accept(result);
	}
	public abstract void afterProcess(Task task);
}