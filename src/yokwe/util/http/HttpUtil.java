package yokwe.util.http;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;
import yokwe.util.FileUtil;

public class HttpUtil {
	private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);
	
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private static final HttpRequester requester;
	static {
		SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(30, TimeUnit.SECONDS)
                .build();
		
		requester = RequesterBootstrap.bootstrap()
                .setSocketConfig(socketConfig)
                .setMaxTotal(100)
                .setDefaultMaxPerRoute(50)
                .create();
		
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
            	logger.info("{}", "HTTP requester shutting down");
                requester.close(CloseMode.GRACEFUL);
           }
        });
	}
	
    private static final HttpCoreContext httpContext = HttpCoreContext.create();

	
	private static final boolean DEFAULT_TRACE      = false;
	private static final String  DEFAULT_TRACE_DIR  = "tmp/http";
	private static final Charset DEFAULT_CHARSET    = StandardCharsets.UTF_8;
	private static final String  DEFAULT_REFERER    = null;
	private static final String  DEFAULT_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit";
	private static final String  DEFAULT_COOKIE     = null;
	private static final String  DEFAULT_CONNECTION = "keep-alive";
	private static final boolean DEFAULT_RAW_DATA   = false;

	private static class Context {
		boolean trace;
		String  traceDir;
		Charset charset;
		String  referer;
		String  userAgent;
		String  cookie;
		String  connection;
		boolean rawData;
		
		private Context() {
			trace      = DEFAULT_TRACE;
			traceDir   = DEFAULT_TRACE_DIR;
			charset    = DEFAULT_CHARSET;
			referer    = DEFAULT_REFERER;
			userAgent  = DEFAULT_USER_AGENT;
			cookie     = DEFAULT_COOKIE;
			connection = DEFAULT_CONNECTION;
			rawData    = DEFAULT_RAW_DATA;
		}
	}
	
	public static class Result {
		public final String              url;
		public final String              result;
		public final Map<String, String> headerMap;
		public final String              timestamp;
		public final String              path;
		public final byte[]              rawData;
		
		public final ClassicHttpResponse response;
		public final int                 code;
		public final String              reasonPhrase;
		public final ProtocolVersion     version;
		
		public Result (Context context, String url, String result, byte[] rawData,
				ClassicHttpResponse response) {
			this.url       = url;
			this.result    = result;
			this.timestamp = LocalDateTime.now(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
			
			if (context.trace) {
				this.path = String.format("%s/%s", context.traceDir, timestamp);
				
				if (result != null) {
					FileUtil.write().file(this.path, result);
				} else {
					FileUtil.rawWrite().file(this.path, rawData);
				}
			} else {
				this.path = null;
			}
			
			this.rawData = rawData;
			
			this.response     = response;
			this.code         = response.getCode();
			this.reasonPhrase = response.getReasonPhrase();
			this.version      = response.getVersion();
			this.headerMap    = new TreeMap<>();
			Arrays.asList(response.getHeaders()).stream().forEach(o -> this.headerMap.put(o.getName(), o.getValue()));
		}
	}
	
	public static HttpUtil getInstance() {
		return new HttpUtil();
	}
	
	private final Context context;
	private HttpUtil() {
		this.context = new Context();
	}
	
	public HttpUtil withTrace(boolean newValue) {
		context.trace = newValue;
		return this;
	}
	public HttpUtil withTraceDir(String newValue) {
		context.traceDir = newValue;
		return this;
	}
	public HttpUtil withCharset(String newValue) {
		context.charset = Charset.forName(newValue);
		return this;
	}
	public HttpUtil withReferer(String newValue) {
		context.referer = newValue;
		return this;
	}
	public HttpUtil withUserAgent(String newValue) {
		context.userAgent = newValue;
		return this;
	}
	public HttpUtil withCookie(String newValue) {
		context.cookie = newValue;
		return this;
	}
	public HttpUtil withConnection(String newValue) {
		context.connection = newValue;
		return this;
	}
	public HttpUtil withRawData(boolean newValue) {
		context.rawData = newValue;
		return this;
	}
	
	private static Charset getCharset(ClassicHttpResponse response, Charset defaultCharset) {
		Charset ret = null;
		{
			// Take charset from mime header "Content-Type"
			if (response.containsHeader("Content-Type")) {
				String contentTypeString = response.getFirstHeader("Content-Type").getValue();
				ContentType contentType = ContentType.parse(contentTypeString);
				ret = contentType.getCharset();
			}
			// If no charset in Content-Type, use charset in context
			if (ret == null) {
				ret = defaultCharset;
			}
		}
		
		return ret;
	}

	public Result download(String url) {
		URI                uri     = URI.create(url);
		HttpHost           target  = HttpHost.create(uri);
        ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, uri);

		if (context.userAgent != null) {
			request.setHeader("User-Agent", context.userAgent);
		}
		if (context.referer != null) {
			request.setHeader("Referer", context.referer);
		}
		if (context.cookie != null) {
			request.setHeader("Cookie", context.cookie);
		}
		if (context.connection != null) {
			request.setHeader("Connection", context.connection);
		}

        HttpClientResponseHandler<ClassicHttpResponse> responseHandler = new HttpClientResponseHandler<ClassicHttpResponse>() {
    		@Override
    		public ClassicHttpResponse handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
    			return response;
    		}
        };
        
		int retryCount = 0;
		for(;;) {
			try (ClassicHttpResponse response = requester.execute(target, request, Timeout.ofSeconds(5), httpContext, responseHandler)) {
		        final int    code         = response.getCode();
		        final String reasonPhrase = response.getReasonPhrase();
		        
				if (code == 429) { // 429 Too Many Requests
					if (retryCount < 10) {
						retryCount++;
						logger.warn("retry {} {} {}  {}", retryCount, code, reasonPhrase, url);
						Thread.sleep(1000 * retryCount * retryCount); // sleep 1 * retryCount * retryCount sec
						continue;
					}
				}
				
				retryCount = 0;
				if (code == HttpStatus.SC_NOT_FOUND) { // 404
					logger.warn("{} {}  {}", code, reasonPhrase, url);
					return null;
				}
				if (code == HttpStatus.SC_BAD_REQUEST) { // 400
					logger.warn("{} {}  {}", code, reasonPhrase, url);
					return null;
				}
		        if (code == HttpStatus.SC_OK) {
	    			byte[] rawData;
	    			{
						HttpEntity entity = response.getEntity();
						if (entity == null) {
							rawData = null;
						} else {
							try {
								rawData = EntityUtils.toByteArray(entity);
							} catch (IOException e) {
								rawData = null;
							}
						}
					}
					
	    			final String result;
					if (context.rawData) {
						result = null;
					} else {
						if (rawData == null) {
							result = null;
						} else {
							Charset charset = getCharset(response, context.charset);
							result = new String(rawData, charset);
						}
					}

	    			Result ret = new Result(context, url, result, rawData, response);
					
					if (ret.path != null) {
						logger.info(String.format("%s %7d %s", ret.timestamp, ret.rawData.length, ret.url));
					}
					return ret;
		        }
		        
				// Other code
				logger.error("statusLine = {} {} {}", code, reasonPhrase, response.getVersion());
				logger.error("url {}", url);
				logger.error("code {}", code);
				{
					HttpEntity entity = response.getEntity();
					if (entity != null) {
						if (context.rawData) {
							logger.error("entity RAW_DATA");
						} else {
							byte[]  rawData = EntityUtils.toByteArray(entity);
							Charset charset = getCharset(response, context.charset);
					    	logger.error("entity  {}", new String(rawData, charset));
						}
					}
				}
				throw new UnexpectedException("download");
			} catch (IOException | HttpException | InterruptedException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
}
