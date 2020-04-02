package yokwe.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
import org.apache.http.entity.ContentType;
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
		
		private Result (Context context, String url, String result, Map<String, String> headerMap, byte[] rawData) {
			this.url       = url;
			this.result    = result;
			this.headerMap = headerMap;
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

	public Result download(String url) {
		HttpGet httpGet = new HttpGet(url);

		if (context.userAgent != null) {
			httpGet.setHeader("User-Agent", context.userAgent);
		}
		if (context.referer != null) {
			httpGet.setHeader("Referer", context.referer);
		}
		if (context.cookie != null) {
			httpGet.setHeader("Cookie", context.cookie);
		}
		if (context.connection != null) {
			httpGet.setHeader("Connection", context.connection);
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

					byte[] rawData = getRawData(response.getEntity());
					String result;
					if (context.rawData) {
						result = null;
					} else {
						Charset charset = null;
						{
							// Take charset from mime header "Content-Type"
							if (headerMap.containsKey("Content-Type")) {
								String contentTypeString = headerMap.get("Content-Type");
								ContentType contentType = ContentType.parse(contentTypeString);
								charset = contentType.getCharset();
							}
							if (charset == null) {
								charset = context.charset;
							}
						}
						result = new String(rawData, charset);
					}
					Result ret = new Result(context, url, result, headerMap, rawData);
					
					if (ret.path != null) {
						logger.info(String.format("%s %7d %s", ret.timestamp, ret.rawData.length, ret.url));
					}
					return ret;
				}
				
				// Other code
				logger.error("statusLine = {}", response.getStatusLine().toString());
				logger.error("url {}", url);
				logger.error("code {}", code);
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					if (context.rawData) {
						logger.error("entity RAW_DATA");
					} else {
						byte[] rawData = getRawData(response.getEntity());
				    	logger.error("entity {}", new String(rawData, context.charset));
					}
				}
				throw new UnexpectedException("download");
			} catch (IOException | InterruptedException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
	
	private byte[] getRawData(HttpEntity entity) {
		if (entity == null) {
			logger.error("entity is null");
			throw new UnexpectedException("entity is null");
		}
 		byte[] buf = new byte[1024 * 64];
    	try (BufferedInputStream bis = new BufferedInputStream(entity.getContent(), buf.length)) {
       		ByteArrayOutputStream baos = new ByteArrayOutputStream();
       		for(;;) {
    			int len = bis.read(buf);
    			if (len == -1) break;
    			baos.write(buf, 0, len);
    		}
    	   	return baos.toByteArray();
    	} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
}
