package yokwe.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class HttpUtil {
	private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);
	
	private static final int CONNECTION_POOLING_MAX_TOTAL = 5;
	
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private static CloseableHttpClient httpClient;
	static {
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setDefaultMaxPerRoute(1); // Single Thread
		connectionManager.setMaxTotal(CONNECTION_POOLING_MAX_TOTAL);
		
		HttpClientBuilder httpClientBuilder = HttpClients.custom();
		httpClientBuilder.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build());
		httpClientBuilder.setConnectionManager(connectionManager);
		
		httpClient = httpClientBuilder.build();
	}
	
	private static final boolean DEFAULT_TRACE      = false;
	private static final String  DEFAULT_TRACE_DIR  = "tmp/http";
	private static final String  DEFAULT_CHARSET    = "UTF-8";
	private static final String  DEFAULT_REFERER    = null;
	private static final String  DEFAULT_USER_AGENT = "Mozilla";

	private static class Context {
		boolean trace;
		String  traceDir;
		String  charset;
		String  referer;
		String  userAgent;
		
		private Context() {
			trace     = DEFAULT_TRACE;
			traceDir  = DEFAULT_TRACE_DIR;
			charset   = DEFAULT_CHARSET;
			referer   = DEFAULT_REFERER;
			userAgent = DEFAULT_USER_AGENT;
		}
	}
	
	public static class Result {
		public final String              url;
		public final String              result;
		public final Map<String, String> headerMap;
		public final String              timestamp;
		public final String              path;
		
		private Result (Context context, String url, String result, Map<String, String> headerMap) {
			this.url       = url;
			this.result    = result;
			this.headerMap = headerMap;
			this.timestamp = LocalDateTime.now(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
			
			if (context.trace) {
				this.path = String.format("%s/%s", context.traceDir, timestamp);
				
				FileUtil.write().file(this.path, result);
			} else {
				this.path = null;
			}
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
		context.charset = newValue;
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

	public Result download(String url) {
		HttpGet httpGet = new HttpGet(url);
		httpGet.setHeader("User-Agent", context.userAgent);
		if (context.referer != null) {
			httpGet.setHeader("Referer",    context.referer);
		}

		int retryCount = 0;
		for(;;) {
			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				final int code = response.getStatusLine().getStatusCode();
				final String reasonPhrase = response.getStatusLine().getReasonPhrase();
				
				if (code == 429) { // 429 Too Many Requests
					if (retryCount < 10) {
						retryCount++;
						logger.warn("retry {} {} {}  {}", retryCount, code, reasonPhrase, url);
						Thread.sleep(1000 * retryCount * retryCount); // sleep 1 * retryCount * retryCount sec
						continue;
					}
				}
				if (code == HttpStatus.SC_INTERNAL_SERVER_ERROR) { // 500
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
					Map<String, String> headerMap = new TreeMap<>();
					for(Header header: response.getAllHeaders()) {
						String key   = header.getName();
						String value = header.getValue();
						headerMap.put(key, value);
					}

					String result = getContent(response.getEntity());
					Result ret = new Result(context, url, result, headerMap);
					
					if (ret.path != null) {
						logger.info(String.format("%s %7d %s", ret.timestamp, ret.result.length(), ret.url));
					}
					return ret; 
				}
				
				// Other code
				logger.error("statusLine = {}", response.getStatusLine().toString());
				logger.error("url {}", url);
				logger.error("code {}", code);
				HttpEntity entity = response.getEntity();
				if (entity != null) {
			    	logger.error("entity {}", getContent(entity));
				}
				throw new UnexpectedException("download");
			} catch (IOException | InterruptedException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
	
	private String getContent(HttpEntity entity) {
		if (entity == null) {
			logger.error("entity is null");
			throw new UnexpectedException("entity is null");
		}
    	try (InputStreamReader isr = new InputStreamReader(entity.getContent(), context.charset)) {
     		char[]        cbuf = new char[1024 * 64];
       		StringBuilder ret  = new StringBuilder();
       		for(;;) {
    			int len = isr.read(cbuf);
    			if (len == -1) break;
    			ret.append(cbuf, 0, len);
    		}
    	   	return ret.toString();
    	} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
 	}
}
