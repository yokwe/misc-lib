package yokwe.util.http;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MyFutureCallback implements FutureCallback<Message<HttpResponse, byte[]>> {
	static final Logger logger = LoggerFactory.getLogger(MyFutureCallback.class);

	private final AsyncClientEndpoint clientEndpoint;
	private final Task                task;

	public MyFutureCallback(AsyncClientEndpoint clientEndpoint, Task task) {
		this.clientEndpoint = clientEndpoint;
		this.task           = task;
	}
	
    @Override
    public void completed(final Message<HttpResponse, byte[]> message) {
        clientEndpoint.releaseAndReuse();
        
        Result result = new Result(task, message);
        task.beforeProdess(task);
        task.process(result);
        task.afterProcess(task);
    }

    @Override
    public void failed(final Exception e) {
        clientEndpoint.releaseAndDiscard();
        logger.warn("failed {}", task.uri);
		String exceptionName = e.getClass().getSimpleName();
		logger.warn("{} {}", exceptionName, e);
    }

    @Override
    public void cancelled() {
        clientEndpoint.releaseAndDiscard();
        logger.warn("cancelled {}", task.uri);
    }
}