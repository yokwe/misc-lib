package yokwe.util.http;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public final class DownloadAsync implements Download {
	static final Logger logger = LoggerFactory.getLogger(DownloadAsync.class);

	private HttpAsyncRequester requester = null;
	
	public void setRequesterBuilder(RequesterBuilder requesterBuilder) {
        H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();
        
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
        		.setSoTimeout(requesterBuilder.soTimeout, TimeUnit.SECONDS)
        		.build();
        
		requester = H2RequesterBootstrap.bootstrap()
				.setH2Config(h2Config)
                .setIOReactorConfig(ioReactorConfig)
                .setMaxTotal(requesterBuilder.maxTotal)
                .setDefaultMaxPerRoute(requesterBuilder.defaultMaxPerRoute)
                .setVersionPolicy(requesterBuilder.versionPolicy)
                .create();
		
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	logger.info("{}", "HTTP requester shutting down");
                requester.close(CloseMode.GRACEFUL);
           }
        });
        
        requester.start(); // Need to start
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
	private CountDownLatch  stopLatch     = null;
	
	public void startProcessTask() {
		if (requester == null) {
			logger.warn("Set requester using default value of RequestBuilder");
			// Set requester using default value of RequestBuilder
			setRequesterBuilder(RequesterBuilder.custom());
		}
		taskQueueSize = taskQueue.size();
		
		logger.info("threadCount {}", threadCount);
		executor = Executors.newFixedThreadPool(threadCount);
		
		stopLatch = new CountDownLatch(taskQueueSize);

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
			stopLatch.await();
			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.warn("{} {}", exceptionName, e);
		} finally {
			executor      = null;
			stopLatch     = null;
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
					AsyncClientEndpoint clientEndpoint = requester.connect(target, Timeout.ofSeconds(30)).get();
					
		            HttpRequest request = new BasicHttpRequest(Method.GET, task.uri);
		            headerList.forEach(o -> request.addHeader(o));
		            
		            AsyncRequestProducer                                 requestProducer  = new BasicRequestProducer(request, null);
		            AsyncResponseConsumer<Message<HttpResponse, byte[]>> responseConsumer = new BasicResponseConsumer<>(new BasicAsyncEntityConsumer());
		            FutureCallback<Message<HttpResponse, byte[]>>        futureCallback   = new FutureCallback<Message<HttpResponse, byte[]>>() {
		        	    @Override
		        	    public void completed(final Message<HttpResponse, byte[]> message) {
		        	        clientEndpoint.releaseAndReuse();
		        	        
		        	        Result result = new Result(task, message);
		        	        task.process(result);
		        	        stopLatch.countDown();
		        	    }

		        	    @Override
		        	    public void failed(final Exception e) {
		        	        clientEndpoint.releaseAndDiscard();
		        	        logger.warn("failed {}", task.uri);
		        			String exceptionName = e.getClass().getSimpleName();
		        			logger.warn("{} {}", exceptionName, e);
		        	        stopLatch.countDown();
		        	    }

		        	    @Override
		        	    public void cancelled() {
		        	        clientEndpoint.releaseAndDiscard();
		        	        logger.warn("cancelled {}", task.uri);
		        	        stopLatch.countDown();
		        	    }
		            };

		            clientEndpoint.execute(requestProducer, responseConsumer, futureCallback);
				} catch (InterruptedException | ExecutionException e) {
					String exceptionName = e.getClass().getSimpleName();
					logger.warn("{} {}", exceptionName, e);
				}
			}
		}
	}
}