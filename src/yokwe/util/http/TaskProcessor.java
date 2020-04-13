package yokwe.util.http;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public final class TaskProcessor implements Runnable {
	static final Logger logger = LoggerFactory.getLogger(TaskProcessor.class);

	private static HttpAsyncRequester requester = null;
	
	public static void setHttpAsyncRequester(HttpAsyncRequester newValue) {
		requester = newValue;
	}
	
	private static final ConcurrentLinkedQueue<Task> taskQueue = new ConcurrentLinkedQueue<Task>();
	public static void addTask(Task task) {
		taskQueue.add(task);
	}
	
	private static final List<Header> headerList = new ArrayList<>();
	public static void addHeader(String name, String value) {
		headerList.add(new BasicHeader(name, value));
	}
	public static void setReferer(String value) {
		addHeader("Referer", value);
	}
	public static void setUserAgent(String value) {
		addHeader("User-Agent", value);
	}
	
	private static int threadCount = 1;
	public static void setThreadCount(int newValue) {
		threadCount = newValue;
	}
	
	private static ExecutorService executor = null;
	public static void startTask() {
		if (requester == null) {
			logger.error("Need to call TaskProcessor.setHttpAsyncRequester()");
			throw new UnexpectedException("Need to call TaskProcessor.setHttpAsyncRequester()");
		}
		
		logger.info("threadCount {}", threadCount);
		executor = Executors.newFixedThreadPool(threadCount);
		
		for(int i = 0; i < threadCount; i++) {
			TaskProcessor taskProcessor = new TaskProcessor();
			executor.execute(taskProcessor);
		}

		executor.shutdown();
	}
	public static void waitTask() {
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.warn("{} {}", exceptionName, e);
		}
		executor = null;
	}
	public static void startAndWaitTask() {
		startTask();
		waitTask();
	}
	
	@Override
	public void run() {
		if (requester == null) {
			logger.error("Need to call TaskProcessor.startRequester()");
			throw new UnexpectedException("Need to call TaskProcessor.startRequester()");
		}

		for(;;) {
			Task task = taskQueue.poll();
			if (task == null) break;
			
            try {
				URI      uri = task.uri;
				HttpHost target = new HttpHost(uri.getHost());
				
				AsyncClientEndpoint clientEndpoint = requester.connect(target, Timeout.ofSeconds(30)).get();
				
				String pathString;
				{
					String path  = uri.getPath();
					String query = uri.getQuery();
					
					if (query != null) {
						pathString = String.format("%s?%s", path, query);
					} else {
						pathString = path;
					}
				}
				
//					logger.info("pathString {}", pathString);
				
	            HttpRequest request = new BasicHttpRequest(Method.GET, target, pathString);
	            headerList.forEach(o -> request.addHeader(o));
	            
	            AsyncRequestProducer                                 requestProducer  = new BasicRequestProducer(request, null);
	            AsyncResponseConsumer<Message<HttpResponse, byte[]>> responseConsumer = new BasicResponseConsumer<>(new BasicAsyncEntityConsumer());
	            FutureCallback<Message<HttpResponse, byte[]>>        futureCallback   = new MyFutureCallback(clientEndpoint, task);

	            clientEndpoint.execute(requestProducer, responseConsumer, futureCallback);
			} catch (InterruptedException | ExecutionException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.warn("{} {}", exceptionName, e);
			}
		}
	}
}