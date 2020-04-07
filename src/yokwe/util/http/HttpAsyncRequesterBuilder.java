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
    	private final H2RequesterBootstrap bootstrap;
    	
    	private Context() {
            H2Config h2Config = H2Config.custom()
                    .setPushEnabled(false)
                    .build();
            
            bootstrap = H2RequesterBootstrap.bootstrap()
                    .setH2Config(h2Config);
    	}
		
    	public Context setVersionPolicy(HttpVersionPolicy newValue) {
    		bootstrap.setVersionPolicy(newValue);
    		return this;
    	}
    	public Context setMaxTotal(int newValue) {
    		bootstrap.setMaxTotal(newValue);
    		return this;
    	}
    	public Context setDefaultMaxPerRoute(int newValue) {
    		bootstrap.setDefaultMaxPerRoute(newValue);
    		return this;
    	}
    	
    	public HttpAsyncRequester get() {            
    		HttpAsyncRequester requester = bootstrap.create();
    		
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