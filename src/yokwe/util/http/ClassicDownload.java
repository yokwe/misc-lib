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

public final class ClassicDownload {
	static final Logger logger = LoggerFactory.getLogger(ClassicDownload.class);

	private HttpRequester requester = null;
	
	public void setRequesterBuilder(RequesterBuilder requesterBuilder) {
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
	
	private final LinkedList<Task> taskQueue = new LinkedList<Task>();
	public void addTask(Task task) {
		taskQueue.add(task);
	}
	
	private final List<Header> headerList = new ArrayList<>();
	public void addHeader(String name, String value) {
		headerList.add(new BasicHeader(name, value));
	}
	public void setReferer(String value) {
		addHeader("Referer", value);
	}
	public void setUserAgent(String value) {
		addHeader("User-Agent", value);
	}
	
	private int threadCount = 1;
	public void setThreadCount(int newValue) {
		threadCount = newValue;
	}
	
	private ExecutorService executor      = null;
	private int 		    taskQueueSize = 0;
	private Worker[]        workerArray   = null;
	public void startProcessTask() {
		if (requester == null) {
			logger.warn("Set requester using default value of RequestBuilder");
			// Set requester using default value of RequestBuilder
			setRequesterBuilder(RequesterBuilder.custom());
		}
		taskQueueSize = taskQueue.size();
		
		logger.info("threadCount {}", threadCount);
		executor = Executors.newFixedThreadPool(threadCount);
		
		workerArray = new Worker[threadCount];
		for(int i = 0; i < threadCount; i++) {
			Worker workder = new Worker(String.format("WORKER-%02d", i));
			workerArray[i] = workder;
		}
		
		for(Worker worker: workerArray) {
			executor.execute(worker);
		}
	}
	public void waitProcessTask() {
		try {
			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.warn("{} {}", exceptionName, e);
		} finally {
			executor      = null;
			taskQueueSize = 0;
		}
	}
	public void showRunCount() {
		logger.info("== Worker runCount");
		for(int i = 0; i < threadCount;) {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("%s ", workerArray[i].name));
			for(int j = 0; j < 10; j++) {
				if (i < threadCount) {
					sb.append(String.format("%4d", workerArray[i++].runCount));
				}
			}
			logger.info("{}", sb.toString());
		}
	}
	public void startAndWait() {
		startProcessTask();
		waitProcessTask();
	}
	
	private class Worker implements Runnable {
		private String name;
		private int    runCount;
		public Worker(String name) {
			this.name      = name;
			this.runCount  = 0;
		}
		
		@Override
		public void run() {
			if (requester == null) {
				throw new UnexpectedException("requester == null");
			}
			Thread.currentThread().setName(name);

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
					logger.info("{}", String.format("%4d / %4d  %s", count, taskQueueSize, task.uri));
				}
				runCount++;

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
}