package yokwe.util.http;

import java.net.URI;
import java.util.function.Consumer;

public class Task {
	public final URI uri;
	public final Consumer<Result> consumer;
	
	public Task(URI uri, Consumer<Result> consumer) {
		this.uri      = uri;
		this.consumer = consumer;
	}
	public Task(String uriString, Consumer<Result> consumer) {
		this(URI.create(uriString), consumer);
	}
	
	public void process(Result result) {
		consumer.accept(result);
	}
}