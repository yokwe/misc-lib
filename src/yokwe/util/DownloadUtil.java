package yokwe.util;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;
import yokwe.util.CSVUtil;

public class DownloadUtil {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(DownloadUtil.class);

	public static class Target {
		public List<Target> load(String path) {
			return CSVUtil.read(Target.class).file(path);
		}
		public void save(String path, Collection<Target> collection) {
			CSVUtil.write(Target.class).file(file, collection);
		}
		
		final String url;
		final File   file;
		
		public Target(String url, File file) {
			this.url  = url;
			this.file = file;
		}
		public Target(String url, String path) {
			this.url  = url;
			this.file = new File(path);
		}
	}
	
	private static class Context {
		private List<Header>  headers;
		private Queue<Target> targets;
		private int           maxThread;
		
		private Context() {
			this.headers   = new ArrayList<>();
			this.targets   = new ArrayDeque<>();
			this.maxThread = 1;
		}
	}
	
	public static class Instance {
		private Context context;
		
		public Instance() {
			this.context = new Context();
		}
		
		public Instance clearTarget() {
			this.context.targets.clear();
			return this;
		}
		
		public Instance withHeader(List<Header> headers) {
			this.context.headers.addAll(headers);
			return this;
		}
		public Instance withHeader(Header header) {
			this.context.headers.add(header);
			return this;
		}
		public Instance withHeader(String name, String value) {
			Header header = new BasicHeader(name, value);
			this.context.headers.add(header);
			return this;
		}
		public Instance withTarget(Collection<Target> collecton) {
			this.context.targets.addAll(collecton);
			return this;
		}
		public Instance withTarget(Target target) {
			this.context.targets.add(target);
			return this;
		}
		public Instance withTarget(String url, File file) {
			this.context.targets.add(new Target(url, file));
			return this;
		}
		public Instance withTarget(String url, String path) {
			this.context.targets.add(new Target(url, path));
			return this;
		}
		public Instance withMaxThread(int maxThread) {
			this.context.maxThread = maxThread;
			return this;
		}
		
		public void download() {
			DownloadThreadGroup downloadThreadGroup = new DownloadThreadGroup(context.headers, context.targets);
			downloadThreadGroup.start(this.context.maxThread);
		}
	}
	public static Instance getInstance() {
		return new Instance();
	}
	
	public static CloseableHttpClient getCloseableHttpClient(int maxTotal, int maxPerRoute, Collection<Header> headers) {
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setDefaultMaxPerRoute(maxPerRoute);
		connectionManager.setMaxTotal(maxTotal);
		
		HttpClientBuilder httpClientBuilder = HttpClients.custom();
		httpClientBuilder.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build());
		httpClientBuilder.setConnectionManager(connectionManager);
		httpClientBuilder.setDefaultHeaders(headers);
		
		return httpClientBuilder.build();
	}

	public static class ResponseHandlerFile implements ResponseHandler<Void> {
		private final OutputStream os;
		public ResponseHandlerFile(OutputStream os) {
			this.os = os;
		}
		public ResponseHandlerFile(File file) {
			{
				File parent = file.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
			}
			try {
				this.os = new FileOutputStream(file);
			} catch (FileNotFoundException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public ResponseHandlerFile(String path) {
			this(new File(path));
		}
		@Override
		public Void handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
			StatusLine statusLine = response.getStatusLine();
			
	        switch(statusLine.getStatusCode()) {
	        case HttpStatus.SC_OK:
	        {
	            HttpEntity entity = response.getEntity();
	            if (entity == null) {
	            	return null;
	            } else {
	            	ContentType contentType = ContentType.getOrDefault(entity);
	            	Charset charset = contentType.getCharset();
	            	
	            	InputStream is = entity.getContent();
	                if (is == null) {
	                    return null;
	                }

	            	if (charset == null) {
	            		BufferedOutputStream bos = new BufferedOutputStream(os, 64 * 1024);
		                try {
		                    byte[] buffer = new byte[4096];
		                    for(;;) {
		                    	int len = is.read(buffer);
		                    	if (len == -1) break;
		                    	bos.write(buffer, 0, len);
		                    }
		                    return null;
		                } finally {
		                    is.close();
		                    bos.close();
		                }
	            	} else {
                		Reader r = new InputStreamReader(is, charset);
                		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), 64 * 1024);

		                try {
		                    char[] buffer = new char[4096];
		                    for(;;) {
		                    	int len = r.read(buffer);
		                    	if (len == -1) break;
		                    	bw.write(buffer, 0, len);
		                    }
		                } finally {
		                	bw.close();
		                	r.close();
		                }
	            	}
	            	return null;
	            }
	        }
	        default:
	        	logger.error("Unexpected status");
	        	logger.error("  {}", statusLine);
	        	throw new UnexpectedException("Unexpected status");
	        }
		}
	}

	public static class DownloadThread extends Thread {		
		private CloseableHttpClient closeableHttpClient;
		private Queue<Target>  targets;
		private int            targetSize;
		
		public DownloadThread(String name, CloseableHttpClient closeableHttpClient, Queue<Target> targets) {
			super(name);
			this.closeableHttpClient = closeableHttpClient;
			this.targets             = targets;
			this.targetSize          = targets.size();
		}
		
		@Override
		public void run() {
			for(;;) {
				int count;
				Target target;
				synchronized(targets) {
					count  = targetSize - targets.size();
					target = targets.poll();					
				}
				if (target == null) break;
				
				if ((count % 100) == 0) {
					logger.info("{}", String.format("%4d / %4d  %s", count, targetSize, target.file.getName()));
				}
				download(target);
			}
		}
		
		private void download(Target target) {			
			HttpGet httpGet = new HttpGet(target.url);
			try {
				ResponseHandler<Void> responseHandler = new ResponseHandlerFile(target.file);
				closeableHttpClient.execute(httpGet, responseHandler);
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}

	public static class DownloadThreadGroup {
		final List<Header>  headers;
		final Queue<Target> queue;
		
		private DownloadThread[] threads = null;
		
		public DownloadThreadGroup(List<Header> headers, Queue<Target> queue) {
			this.headers = headers;
			this.queue   = queue;
		}

		public void start(int maxThread) {
			start(maxThread, maxThread, maxThread);
		}

		public void start(int maxTotal, int maxPerRoute, int maxThread) {
			logger.info("maxThread {}", maxThread);
			CloseableHttpClient closeableHttpClient = getCloseableHttpClient(maxTotal, maxPerRoute, headers);
			threads = new DownloadThread[maxThread];
			
			try {
				// prepare thread
				for(int i = 0; i < threads.length; i++) {
					threads[i] = new DownloadThread(String.format("DT-%03d", i), closeableHttpClient, queue);
				}
				
				// start thread
				Thread.sleep(500);
				for(int i = 0; i < threads.length; i++) {
					threads[i].start();
				}

				// wait thread
				for(int i = 0; i < threads.length; i++) {
					threads[i].join();
				}
			} catch (InterruptedException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
}
