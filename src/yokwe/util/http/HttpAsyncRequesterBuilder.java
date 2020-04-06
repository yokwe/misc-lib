package yokwe.util.http;

import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2RequesterBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpAsyncRequesterBuilder {
	static final Logger logger = LoggerFactory.getLogger(HttpAsyncRequesterBuilder.class);

	public static Context builder() {
		return new Context();
	}
	public static class Context {
    	private HttpVersionPolicy httpVersionPolicy;
    	private int               maxTotal;
    	private int               defaultMaxPerRoute;
    	
    	private Context() {
	    	httpVersionPolicy  = HttpVersionPolicy.NEGOTIATE;
	    	maxTotal           = 50;
	    	defaultMaxPerRoute = 20;
    	}
		
    	public Context setHttpVersionPolicy(HttpVersionPolicy newValue) {
    		httpVersionPolicy = newValue;
    		return this;
    	}
    	public Context setMaxTotal(int newValue) {
    		maxTotal = newValue;
    		return this;
    	}
    	public Context setDefaultMaxPerRoute(int newValue) {
    		defaultMaxPerRoute = newValue;
    		return this;
    	}
    	
    	public HttpAsyncRequester get() {
    		logger.info("httpVersionPolicy  {}", httpVersionPolicy);
    		logger.info("maxTotal           {}", maxTotal);
    		logger.info("defaultMaxPerRoute {}", defaultMaxPerRoute);
    		
            H2Config h2Config = H2Config.custom()
                    .setPushEnabled(false)
                    .build();
            
    		HttpAsyncRequester requester = H2RequesterBootstrap.bootstrap()
                    .setH2Config(h2Config)
                    .setVersionPolicy(httpVersionPolicy)
                    .setMaxTotal(maxTotal)
                    .setDefaultMaxPerRoute(defaultMaxPerRoute)
                    .create();
    		
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                	logger.info("{}", "HTTP requester shutting down");
                    requester.close(CloseMode.GRACEFUL);
               }
            });
            
            requester.start();
            return requester;
    	}
	}
}