package yokwe.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;


public class FileUtil {
	private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

	private static final String  DEFAULT_CHARSET = "UTF-8";

	private static class Context {
		private String charset = DEFAULT_CHARSET;
	}
	
	public static Read read() {
		return new Read();
	}
	public static class Read {
		private final Context context;
		
		private Read() {
			context = new Context();
		}
		public Read withCharset(String newValue) {
			context.charset = newValue;
			return this;
		}
		
		public String file(File file) {
			char[] buffer = new char[65536];		
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), context.charset), buffer.length)) {
				StringBuilder ret = new StringBuilder();
				
				for(;;) {
					int len = br.read(buffer);
					if (len == -1) break;
					
					ret.append(buffer, 0, len);
				}
				return ret.toString();
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public String file(String path) {
			return file(new File(path));
		}
	}
	
	public static RawRead rawRead() {
		return new RawRead();
	}
	public static class RawRead {
		private RawRead() {
		}
		
		public byte[] file(File file) {			
			byte[] buffer = new byte[65536];
			try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), buffer.length)) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				for(;;) {
					int len = bis.read(buffer);
					if (len == -1) break;
					
					baos.write(buffer, 0, len);
				}
				return baos.toByteArray();
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public byte[] file(String path) {
			return file(new File(path));
		}
	}
	
	public static Write write() {
		return new Write();
	}
	public static class Write {
		private final Context context;
		
		private Write() {
			context = new Context();
		}
		public Write withCharset(String newValue) {
			context.charset = newValue;
			return this;
		}
		
		public void file(File file, String content) {
			char[] buffer = new char[65536];
			
			// Make parent directory if necessary.
			{
				File parent = file.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
			}
			try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), context.charset), buffer.length)) {
				bw.append(content);
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public void file(String path, String content) {
			file (new File(path), content);
		}
	}
	
	public static RawWrite rawWrite() {
		return new RawWrite();
	}
	public static class RawWrite {
		private RawWrite() {
		}
		public void file(File file, byte[] content) {
			char[] buffer = new char[65536];
			
			// Make parent directory if necessary.
			{
				File parent = file.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
			}
			try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file), buffer.length)) {
				bos.write(content);
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public void file(String path, byte[] content) {
			file (new File(path), content);
		}
	}
	
	public static List<File> listFile(File dir) {
		List<File> ret = new ArrayList<>();
		
		if (dir.isDirectory()) {
			for(File file: dir.listFiles()) {
				final String name = file.getName();
				// Skip special files -- assume we run on Unix
				if (name.equals("."))  continue;
				if (name.equals("..")) continue;

				if (file.isDirectory()) {
					ret.addAll(listFile(file));
				}
				if (file.isFile()) {
					ret.add(file);
				}
			}
		}
		
		return ret;
	}
	public static List<File> listFile(String dirPath) {
		return listFile(new File(dirPath));
	}
	
	public static Map<File, String> md5FileMap(List<File> list) {
		Map<File, String> ret = new TreeMap<>();
		for(File file: list) {
			String md5 = StringUtil.toHexString(HashCode.getHashCode(file));
			ret.put(file, md5);
		}
		return ret;
	}
	public static Set<String> md5Set(List<File> list) {
		Map<File, String> map = md5FileMap(list);
		Set<String> ret = new TreeSet<>(map.values());
		return ret;
	}

}
