package yokwe.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class CSVUtil {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(CSVUtil.class);
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public static @interface ColumnName {
		String value();
	}

	public static final int BUFFER_SIZE = 64 * 1024;
	public static final LocalDate     NULL_LOCAL_DATE      = LocalDate.of(1900, 1, 1);
	public static final LocalTime     NULL_LOCAL_TIME      = LocalTime.of(0, 0, 0);
	public static final LocalDateTime NULL_LOCAL_DATE_TIME = LocalDateTime.of(NULL_LOCAL_DATE, NULL_LOCAL_TIME);

	private static class ClassInfo {
		private static Map<String, ClassInfo> map = new TreeMap<>();
		
		static ClassInfo get(Class<?> clazz) {
			String key = clazz.getName();
			if (map.containsKey(key)) {
				return map.get(key);
			} else {
				ClassInfo value = new ClassInfo(clazz);
				map.put(key, value);
				return value;
			}
		}

		final Class<?>    clazz;
		final FieldInfo[] fieldInfos;
		final String[]    names;
		ClassInfo(Class<?> value) {
			clazz = value;
			
			List<FieldInfo> list = new ArrayList<>();
			for(Field field: clazz.getDeclaredFields()) {
				// Skip static field
				if (Modifier.isStatic(field.getModifiers())) continue;

				list.add(new FieldInfo(field));
			}
			fieldInfos = list.toArray(new FieldInfo[0]);
			
			names = new String[fieldInfos.length];
			for(int i = 0; i < names.length; i++) {
				names[i] = fieldInfos[i].name;
			}
		}
	}
	private static class FieldInfo {
		final Field    field;
		final String   name;
		final Class<?> clazz;
		final String   clazzName;
		
		final Map<String, Enum<?>> enumMap;

		
		FieldInfo(Field value) {
			field      = value;
			
			ColumnName columnName = field.getDeclaredAnnotation(ColumnName.class);
			name = (columnName == null) ? field.getName() : columnName.value();

			clazz      = field.getType();
			clazzName  = clazz.getName();
			
			if (clazz.isEnum()) {
				enumMap = new TreeMap<>();
				
				@SuppressWarnings("unchecked")
				Class<Enum<?>> enumClazz = (Class<Enum<?>>)clazz;
				for(Enum<?> e: enumClazz.getEnumConstants()) {
					enumMap.put(e.toString(), e);
				}
			} else {
				enumMap = null;
			}
		}
	}
	
	public static String[] parseLine(BufferedReader br) {
		try {			
			// Peek one char to check end of stream
			{
				br.mark(1);
				int firstChar = br.read();
				br.reset();
				if (firstChar == -1) return null;
			}
			
			List<String> list  = new ArrayList<>();
			StringBuffer field = new StringBuffer();
			boolean endOfRecord = false;
			for(;;) {
				if (endOfRecord) break;
				
				int fieldFirstChar = br.read();
				if (fieldFirstChar == -1) {
					// end of stream
					logger.error("Unexpected end of stream");
					logger.error("  list   !{}!", list);
					logger.error("  field  !{}!", field.toString());
					throw new UnexpectedException("Unexpected end of stream");
				} else if (fieldFirstChar == '\n') {
					// end of record
					field.setLength(0);
					list.add("");
//					logger.debug("nl  field  \"\"");
					break;
				} else if (fieldFirstChar == ',') {
					// end of field -- empty field
					field.setLength(0);
					list.add("");
//					logger.debug("emp field  \"\"");
				} else if (fieldFirstChar == '"') {
					// quoted field
					field.setLength(0);
					
					for(;;) {
						int c = br.read();
						if (c == -1) {
							logger.error("Unexpected end of stream");
							logger.error("  list   !{}!", list);
							logger.error("  field  !{}!", field.toString());
							throw new UnexpectedException("Unexpected end of stream");
						} else if (c == '"') {
							// end of field, end of record or double quote
							int c2 = br.read();
							if (c2 == -1) {
								// Special handling of last record with no \n
								endOfRecord = true;
								break;
							} else if (c2 == ',') {
								// end of field
								break;
							} else if (c2 == '\r') {
								// end of record
								int c3 = br.read();
								if (c3 == -1) {
									logger.error("Unexpected end of stream");
									logger.error("  list   !{}!", list);
									logger.error("  field  !{}!", field.toString());
									throw new UnexpectedException("Unexpected end of stream");
								} else if (c3 == '\n') {
									endOfRecord = true;
									break;
								} else {
									logger.error("Unexpected char {}", String.format("%X", c3));
									throw new UnexpectedException("Unexpected char");
								}
							} else if (c2 == '\n') {
								// end of record
								endOfRecord = true;
								break;
							} else if (c2 == '"') {
								// double quote
								field.append('"');
							} else {
								logger.error("Unexpected back slash escape  {}", c2);
								logger.error("  list   !{}!", list);
								logger.error("  field  !{}!", field.toString());
								throw new UnexpectedException("Unexpected back slash escape");
							}
						} else if (c == '\\') {
							// back slash escape
							int c2 = br.read();
							if (c2 == -1) {
								logger.error("Unexpected end of stream");
								logger.error("  list   !{}!", list);
								logger.error("  field  !{}!", field.toString());
								throw new UnexpectedException("Unexpected end of stream");
							} else if (c2 == 'n') {
								// \n
								field.append('\n');
							} else if (c2 == 'r') {
								// \r
								field.append('\r');
							} else {
								logger.error("Unexpected back slash escape  {}", c2);
								logger.error("  list   !{}!", list);
								logger.error("  field  !{}!", field.toString());
								throw new UnexpectedException("Unexpected back slash escape");
							}
						} else {
							field.append((char)c);
						}
					}
					// append field to list
					list.add(field.toString());
//					logger.debug("quo field  {}!", field.toString());
				} else {
					// ordinary field
					field.setLength(0);
					
					field.append((char)fieldFirstChar);
					for(;;) {
						int c = br.read();
						if (c == -1) {
							// Special handling of last record with no \n
							endOfRecord = true;
							break;
						} else if (c == ',') {
							// end of field
							break;
						} else if (c == '\r') {
							// end of record
							int c2 = br.read();
							if (c2 == -1) {
								logger.error("Unexpected end of stream");
								logger.error("  list   !{}!", list);
								logger.error("  field  !{}!", field.toString());
								throw new UnexpectedException("Unexpected end of stream");
							} else if (c2 == '\n') {
								endOfRecord = true;
								break;
							} else {
								logger.error("Unexpected char {}", String.format("%X", c2));
								throw new UnexpectedException("Unexpected char");
							}
						} else if (c == '\n') {
							// end of record
							endOfRecord = true;
							break;
						} else {
							field.append((char)c);
						}
					}
					// append field to list
					list.add(field.toString());
//					logger.debug("ord field  {}!", field.toString());
				}
			}
			
			// Remove byte order mark from list element
			for(String string: list) {
				string = StringUtil.removeBOM(string);
			}
//			logger.debug("list  {}", list);
			return list.toArray(new String[0]);
		} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	
	private static class Context {
		private boolean withHeader = true;
	}
	
	public static <E> Read<E> read(Class<E> clazz) {
		return new Read<E>(clazz);
	}
	public static class Read<E> {
		private final Context   context;
		private final ClassInfo classInfo;
				
		private Read(Class<E> clazz) {
			context   = new Context();
			classInfo = ClassInfo.get(clazz);
		}
		public Read<E> withHeader(boolean newValue) {
			context.withHeader = newValue;
			return this;
		}
		
		public static String toStringAsHexChar(String string) {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("(%d)", string.length()));
			for(int i = 0; i < string.length(); i++) {
				char c = string.charAt(i);
				sb.append(String.format("%04X", (int)c));
			}
			return sb.toString();
		}


		private void readHeader(BufferedReader br) {
			String[] names = parseLine(br);
			if (names == null) {
				logger.error("Unexpected EOF");
				throw new UnexpectedException("Unexpected EOF");
			}
			
			// Sanity check
			if (classInfo.names.length != names.length) {
				logger.error("Unexpected length  {}  {}  {}", classInfo.names.length, names.length, Arrays.asList(names));
				logger.error("classInfo  {}", classInfo.clazz.getName());
				logger.error("====");
				for(int j = 0; j < classInfo.fieldInfos.length; j++) {
					logger.info("  clasInfo   {}  {}", j, classInfo.names[j]);
				}
				logger.error("====");
				for(int j = 0; j < names.length; j++) {
					logger.info("  names      {}  {}", j, names[j]);
				}
				throw new UnexpectedException("Unexpected length");
			}
			for(int i = 0; i < names.length; i++) {
				if (names[i].equals(classInfo.names[i])) continue;
				logger.error("Unexpected name  {}  {}  {}", i, names[i], classInfo.names[i]);
				logger.error("classInfo  {}", classInfo.clazz.getName());
				logger.error("====");
				for(int j = 0; j < classInfo.fieldInfos.length; j++) {
					logger.info("  clasInfo   {}  {}  {}", j, classInfo.names[j], toStringAsHexChar(classInfo.names[j]));
				}
				logger.error("====");
				for(int j = 0; j < names.length; j++) {
					logger.info("  names      {}  {}  {}", j, names[j], toStringAsHexChar(names[j]));
				}
				throw new UnexpectedException("Unexpected name");
			}
		}

		private E read(BufferedReader br) {
			try {
				String[] values = parseLine(br);
				if (values == null) return null;

				@SuppressWarnings("unchecked")
				Class<E> clazz = (Class<E>)classInfo.clazz;
				
				E data = clazz.newInstance();
				
				FieldInfo[] fieldInfos = classInfo.fieldInfos;
				for(int i = 0; i < fieldInfos.length; i++) {
					FieldInfo fieldInfo = fieldInfos[i];

					String value = values[i];

					switch(fieldInfo.clazzName) {
					case "int":
					{
						int intValue = value.isEmpty() ? 0 : Integer.parseInt(value);
						fieldInfo.field.setInt(data, intValue);
					}
						break;
					case "long":
					{
						long longValue = value.isEmpty() ? 0 : Long.parseLong(value);
						fieldInfo.field.setLong(data, longValue);
					}
						break;
					case "double":
					{
						double doubleValue = value.isEmpty() ? 0 : Double.parseDouble(value);
						fieldInfo.field.setDouble(data, doubleValue);
					}
						break;
					case "boolean":
					{
						boolean booleanValue = value.isEmpty() ? false : Boolean.parseBoolean(value);
						fieldInfo.field.setBoolean(data, booleanValue);
					}
						break;
					case "java.lang.String":
						fieldInfo.field.set(data, value);
						break;
					case "java.time.LocalDateTime":
					{
						final LocalDateTime localDateTime;
						
						if (value.isEmpty() || value.equals("0")) {
							localDateTime = NULL_LOCAL_DATE_TIME;
						} else {
							localDateTime = LocalDateTime.parse(value);
						}
						
						fieldInfo.field.set(data, localDateTime);
					}
						break;
					case "java.time.LocalDate":
					{
						final LocalDate localDate;
						
						if (value.isEmpty() || value.equals("0")) {
							localDate = NULL_LOCAL_DATE;
						} else {
							localDate = LocalDate.parse(value);
						}
						
						fieldInfo.field.set(data, localDate);
					}
						break;
					default:
						if (fieldInfo.enumMap != null) {
							if (fieldInfo.enumMap.containsKey(value)) {
								fieldInfo.field.set(data, fieldInfo.enumMap.get(value));
							} else {
								logger.error("Unknow enum value  {}  {}", fieldInfo.clazzName, value);
								throw new UnexpectedException("Unknow enum value");
							}
						} else {
							logger.error("Unexptected fieldInfo.clazzName {}", fieldInfo.clazzName);
							throw new UnexpectedException("Unexptected fieldInfo.clazzName");
						}
					}
				}
				
				return data;
			} catch (InstantiationException | IllegalAccessException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}

		public List<E> file(Reader reader) {
			try (BufferedReader br = new BufferedReader(reader, BUFFER_SIZE)) {
				if (context.withHeader) {
					readHeader(br);
				}
				
				List<E> ret = new ArrayList<>();
				for(;;) {
					E e = (E)read(br);
					if (e == null) break;
					ret.add(e);
				}
				return ret;
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public List<E> file(File file) {
			try {
				return file(new FileReader(file));
			} catch (FileNotFoundException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public List<E> file(String path) {
			return file(new File(path));
		}
	}
	
	public static <E> Write<E> write(Class<E> clazz) {
		return new Write<E>(clazz);
	}
	public static class Write<E> {
		private final Context   context;
		private final ClassInfo classInfo;
				
		private Write(Class<E> clazz) {
			context   = new Context();
			classInfo = ClassInfo.get(clazz);
		}
		public Write<E> withHeader(boolean newValue) {
			context.withHeader = newValue;
			return this;
		}
		
		private void writeHeader(BufferedWriter bw) {
			try {
				FieldInfo[] fieldInfos = classInfo.fieldInfos;
				
				bw.write(fieldInfos[0].name);
				for(int i = 1; i < fieldInfos.length; i++) {
					bw.write(",");
					bw.write(fieldInfos[i].name);
				}
				bw.newLine();
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		private void writeField(BufferedWriter bw, String value) throws IOException {
			if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
				bw.write("\"");
				
				char[] charArray = value.toCharArray();
				for(int i = 0; i < charArray.length; i++) {
					char c = charArray[i];
					switch(c) {
					case '"':
						bw.write('"');
						bw.write('"');
						break;
					case '\n':
						bw.write('\\');
						bw.write('n');
						break;
					case '\r':
						bw.write('\\');
						bw.write('r');
						break;
					default:
						bw.write(c);
						break;
					}
				}
				
				bw.write("\"");						
			} else {
				bw.write(value);
			}
		}
		private void write(BufferedWriter bw, E value) {
			FieldInfo[] fieldInfos = classInfo.fieldInfos;
			
			try {
				writeField(bw, fieldInfos[0].field.get(value).toString());
				for(int i = 1; i < fieldInfos.length; i++) {
					bw.write(",");
					writeField(bw, fieldInfos[i].field.get(value).toString());
				}
				bw.newLine();
			} catch (IllegalArgumentException | IllegalAccessException | IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}

		public void file(Writer writer, Collection<E> collection) {
			try (BufferedWriter bw = new BufferedWriter(writer, BUFFER_SIZE)) {
				if (context.withHeader) {
					writeHeader(bw);
				}
				for(E e: collection) {
					write(bw, e);
				}
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public void file(File file, Collection<E> collection) {
			// Create parent folder if not exists
			{
				File parent = file.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
			}
			
			try {
				file(new FileWriter(file), collection);
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public void file(String path, Collection<E> collection) {
			file(new File(path), collection);
		}
		
		
		private BufferedWriter bwStart = null;
		public void start(String path) {
			start(new File(path));
		}
		public void start(File file) {
			// Create parent folder if not exists
			{
				File parent = file.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
			}
			
			try {
				start(new FileWriter(file));
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public void start(Writer writer) {
			// Sanity check
			if (bwStart != null) {
				logger.error("bwStart != null");
				throw new UnexpectedException("bwStart != null");
			}
			bwStart = new BufferedWriter(writer, BUFFER_SIZE);
			if (context.withHeader) {
				writeHeader(bwStart);
			}
		}
		public void write(E e) {
			// Sanity check
			if (bwStart == null) {
				logger.error("bwStart == null");
				throw new UnexpectedException("bwStart == null");
			}
			write(bwStart, e);
		}
		public void stop() {
			// Sanity check
			if (bwStart == null) {
				logger.error("bwStart == null");
				throw new UnexpectedException("bwStart == null");
			}
			try {
				bwStart.close();
				bwStart = null;
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
}
