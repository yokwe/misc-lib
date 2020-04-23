package yokwe.util;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

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

/**
 * @deprecate "Use http.Download instead"
 */
@Deprecated
public class DownloadUtil {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(DownloadUtil.class);

	public enum ContentMode {
		BINARY,
		TEXT,
		AUTO,
	}
	
	public static interface Target {
		public String       getURL();
		public ContentMode  getContentMode();
		
		public OutputStream getOutputStream();
		public Writer       getWriter();
		
		public void beforeProcess();
		public void afterProcess();
	}
	
	public static abstract class AbstractTarget implements Target {
		private final String       url;
		private final ContentMode  contentMode;
		
		protected AbstractTarget(String url, ContentMode contentMode) {
			this.url         = url;
			this.contentMode = contentMode;
		}
		
		@Override
		public String getURL() {
			return url;
		}
		@Override
		public ContentMode getContentMode() {
			return contentMode;
		}
		@Override
		public abstract OutputStream getOutputStream();
		@Override
		public abstract Writer getWriter();
		@Override
		public abstract void beforeProcess();
		@Override
		public abstract void afterProcess();
	}
	
	public static abstract class FileTarget extends AbstractTarget {
		private final Consumer<FileTarget>  action;
		private final File                  file;
		public FileTarget(String url, Consumer<FileTarget> action, ContentMode contentMode, File file) {
			super(url, contentMode);
			this.action      = action;
			this.file        = file;
		}
		
		public File getFile() {
			return file;
		}
		
		@Override
		public OutputStream getOutputStream() {
			try {
				OutputStream os = new FileOutputStream(file);
				
				return os;
			} catch (FileNotFoundException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		
		@Override
		public Writer getWriter() {
			try {
				OutputStream os = new FileOutputStream(file);
				Writer       w  = new OutputStreamWriter(os);
				return w;
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		
		@Override
		public void beforeProcess() {
			// create parent directories if necessary
			{
				File parent = file.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
			}
		}
		
		@Override
		public void afterProcess() {
			// No need to close this.os and this.w. They are closed by ResponseHandlerTarget.handleResponse()
			// call actions
			this.action.accept(this);
		}
	}
	public static class BinaryFileTarget extends FileTarget {
		public BinaryFileTarget(String url, Consumer<FileTarget> action, File file) {
			super(url, action, ContentMode.BINARY, file);
		}
	}
	public static class TextFileTarget extends FileTarget {
		public TextFileTarget(String url, Consumer<FileTarget> action, File file) {
			super(url, action, ContentMode.TEXT, file);
		}
	}
	public static class AutoFileTarget extends FileTarget {
		public AutoFileTarget(String url, Consumer<FileTarget> action, File file) {
			super(url, action, ContentMode.AUTO, file);
		}
	}
	@Deprecated
	public static class StringTarget extends AbstractTarget {
		private final Consumer<StringTarget> action;
		private final StringWriter           writer;
		
		public StringTarget(String url, Consumer<StringTarget> action) {
			super(url, ContentMode.TEXT);
			this.action = action;
			this.writer = new StringWriter();
		}
		
		public String getString() {
			return writer.toString();
		}
		
		@Override
		public OutputStream getOutputStream() {
			logger.error("Unexpected");
			throw new UnexpectedException("Unexpected");
		}
		@Override
		public Writer getWriter() {
			return writer;
		}
		@Override
		public void beforeProcess() {
			// clear buffer before process
			this.writer.getBuffer().setLength(0);
		}
		@Override
		public void afterProcess() {
			// No need to close this.os and this.w. They are closed by ResponseHandlerTarget.handleResponse()
			// call actions
			this.action.accept(this);
			// clear buffer after process
			this.writer.getBuffer().setLength(0);
		}
	}
	public static class ByteArrayTarget extends AbstractTarget {
		private final Consumer<ByteArrayTarget> action;
		private final ByteArrayOutputStream     outputStream;
		
		public ByteArrayTarget(String url, Consumer<ByteArrayTarget> action) {
			super(url, ContentMode.BINARY);
			this.action       = action;
			this.outputStream = new ByteArrayOutputStream();
		}
		
		public byte[] getByteArray() {
			return outputStream.toByteArray();
		}
		
		@Override
		public OutputStream getOutputStream() {
			return outputStream;
		}
		@Override
		public Writer getWriter() {
			logger.error("Unexpected");
			throw new UnexpectedException("Unexpected");
		}
		@Override
		public void beforeProcess() {
			// clear buffer before process
			this.outputStream.reset();
		}
		@Override
		public void afterProcess() {
			// No need to close this.os and this.w. They are closed by ResponseHandlerTarget.handleResponse()
			// call actions
			this.action.accept(this);
			// clear buffer after process
			this.outputStream.reset();
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

	public static class ResponseHandlerTarget implements ResponseHandler<Void> {
		private static final int BUFFER_SIZE = 64 * 1024;
		private final Target target;

		public ResponseHandlerTarget(Target target) {
			this.target = target;
		}
		private static void binaryContent(Target target, InputStream is) throws IOException {
    		target.beforeProcess();
    		BufferedOutputStream bos = new BufferedOutputStream(target.getOutputStream(), BUFFER_SIZE);
            try {
                byte[] buffer = new byte[4096];
                for(;;) {
                	int len = is.read(buffer);
                	if (len == -1) break;
                	bos.write(buffer, 0, len);
                }
            } finally {
                is.close();
                bos.close();
            	// after close os, call afterProcess
                target.afterProcess();
            }
		}
		private static void textContent(Target target, InputStream is, Charset charset) throws IOException {
    		Reader r = new InputStreamReader(is, charset);
    		
    		target.beforeProcess();
    		BufferedWriter bw = new BufferedWriter(target.getWriter(), BUFFER_SIZE);
            try {
                char[] buffer = new char[4096];
                for(;;) {
                	int len = r.read(buffer);
                	if (len == -1) break;
                	bw.write(buffer, 0, len);
                }
            } finally {
            	r.close();
            	bw.close();
            	// after close w, call afterProcess
                target.afterProcess();
            }
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

	                ContentMode contentMode = target.getContentMode();
	                switch(contentMode) {
	                case TEXT:
	                	if (charset == null) {
	                		logger.error("Unexpected contentMode");
	        	        	logger.error("  url         {}", target.getURL());
	        	        	logger.error("  contentType {}", contentType);
	        	        	throw new UnexpectedException("Unexpected contentMode");
	                	}
	                	textContent(target, is, charset);
	                	break;
	                case BINARY:
	                	binaryContent(target, is);
	                	break;
	                case AUTO:
	                	if (charset == null) {
		                	binaryContent(target, is);
	                	} else {
		                	textContent(target, is, charset);
	                	}
	                	break;
	                default:
	                	break;
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
					logger.info("{}", String.format("%4d / %4d", count, targetSize));
				}
				download(target);
			}
		}
		
		private void download(Target target) {			
			HttpGet httpGet = new HttpGet(target.getURL());
			try {
				ResponseHandler<Void> responseHandler = new ResponseHandlerTarget(target);
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
