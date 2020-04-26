package yokwe.util.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public final class ClassicTaskProcessor implements Runnable {
	static final Logger logger = LoggerFactory.getLogger(ClassicTaskProcessor.class);

	private static HttpRequester requester = null;
	public static void setRequesterBuilder(RequesterBuilder requesterBuilder) {
		SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(requesterBuilder.soTimeout, TimeUnit.SECONDS)
                .build();
		
		requester = RequesterBootstrap.bootstrap()
                .setSocketConfig(socketConfig)
                .setMaxTotal(requesterBuilder.maxTotal)
                .setDefaultMaxPerRoute(requesterBuilder.defaultMaxPerRoute)
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	logger.info("{}", "HTTP requester shutting down");
                requester.close(CloseMode.GRACEFUL);
           }
        });
	}
	
	private static final LinkedList<Task> taskQueue = new LinkedList<Task>();
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
	
	private static ExecutorService        executor      = null;
	private static int 		              taskQueueSize = 0;
	private static ClassicTaskProcessor[] taskProcessorArray = null;
	public static void startTask() {
		if (requester == null) {
			logger.error("Need to call TaskProcessor.setHttpAsyncRequester()");
			throw new UnexpectedException("Need to call TaskProcessor.setHttpAsyncRequester()");
		}
		taskQueueSize = taskQueue.size();
		
		logger.info("threadCount {}", threadCount);
		executor = Executors.newFixedThreadPool(threadCount);
		
		taskProcessorArray = new ClassicTaskProcessor[threadCount];
		for(int i = 0; i < threadCount; i++) {
			ClassicTaskProcessor taskProcessor = new ClassicTaskProcessor(i);
			taskProcessorArray[i] = taskProcessor;
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
		
		for(int i = 0; i < threadCount;) {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("TASK-%02d ", i));
			for(int j = 0; j < 10; j++) {
				if (i < threadCount) {
					sb.append(String.format("%4d", taskProcessorArray[i++].taskCount));
				}
			}
			logger.info("{}", sb.toString());
		}
	}
	public static void startAndWaitTask() {
		startTask();
		waitTask();
	}
	
	
	private final int no;
	private       int taskCount;
	public ClassicTaskProcessor(int no) {
		this.no = no;
		this.taskCount = 0;
	}
	
	@Override
	public void run() {
		if (requester == null) {
			logger.error("Need to call TaskProcessor.startRequester()");
			throw new UnexpectedException("Need to call TaskProcessor.startRequester()");
		}
		
		Thread.currentThread().setName(String.format("TASK-%02d", no));

        final HttpCoreContext coreContext = HttpCoreContext.create();
        
		for(;;) {
			final int  count;
			final Task task;
			synchronized (taskQueue) {
				count = taskQueueSize - taskQueue.size();
				task  = taskQueue.poll();
			}
			if (task == null) break;
			
			if ((count % 1000) == 0) {
				logger.info("{}", String.format("%4d / %4d", count, taskQueueSize));
			}
			taskCount++;
			
            try {
				HttpHost target = HttpHost.create(task.uri);
				
	            ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, task.uri);
	            headerList.forEach(o -> request.addHeader(o));
	            
	            HttpClientResponseHandler<Result> responseHandler = new HttpClientResponseHandler<Result>() {
	        		@Override
	        		public Result handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
	        			return new Result(task, response);
	        		}
	            };

	            Result result = requester.execute(target, request, Timeout.ofSeconds(5), coreContext, responseHandler);
	            
	            task.process(result);

			} catch (HttpException | IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.warn("{} {}", exceptionName, e);
			}
		}
	}
}