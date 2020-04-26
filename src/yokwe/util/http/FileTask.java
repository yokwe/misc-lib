package yokwe.util.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class FileTask extends Task {
	static final Logger logger = LoggerFactory.getLogger(FileTask.class);

	public static enum Mode {
		BINARY,
		TEXT
	}
	
	private static class MyConsumer implements Consumer<Result> {
		private final File file;
		private final Mode mode;
		
		public MyConsumer(File file, Mode mode) {
			this.file = file;
			this.mode = mode;
		}
		
		@Override
		public void accept(Result result) {
			switch (mode) {
			case BINARY:
				saveAsBinaryFile(result);
				break;
			case TEXT:
				saveAsTextFile(result);
				break;
			default:
				logger.error("Unexpected mode");
				logger.error("  mode {}", mode);
				throw new UnexpectedException("Unexpected mode");
			}
		}
		
		private void saveAsBinaryFile(Result result) {
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			
			try (FileOutputStream fos = new FileOutputStream(file)) {
				fos.write(result.body);
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		private void saveAsTextFile(Result result) {
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			
			if (result.charset == null) {
				logger.error("charset is null");
				logger.error("  uri         {}", result.task.uri);
				logger.error("  contentType {}", result.contentType);
				throw new UnexpectedException("charset is null");
			}
			try (FileWriter fw = new FileWriter(file)) {
				fw.write(new String(result.body, result.charset));
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
	
	private FileTask(URI uri, File file, Mode mode) {
		super(uri, new MyConsumer(file, mode));
	}
	private FileTask(String uriString, File file, Mode mode) {
		super(uriString, new MyConsumer(file, mode));
	}

	public static FileTask binary(URI uri, File file) {
		return new FileTask(uri, file, Mode.BINARY);
	}
	public static FileTask binary(String uriString, File file) {
		return new FileTask(uriString, file, Mode.BINARY);
	}

	public static FileTask text(URI uri, File file) {
		return new FileTask(uri, file, Mode.TEXT);
	}
	public static FileTask text(String uriString, File file) {
		return new FileTask(uriString, file, Mode.TEXT);
	}
}