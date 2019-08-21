package yokwe.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
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

public class SimpleCSV {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(SimpleCSV.class);
	
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
	
	public static class Save<E> implements Closeable {
		private final ClassInfo      classInfo;
		private final BufferedWriter bw;
		
		private void writeHeader() {
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
		private Save(Writer w, Class<E> clazz) {
			classInfo = ClassInfo.get(clazz);
			bw        = new BufferedWriter(w, BUFFER_SIZE);
			
			writeHeader();
		}
		private void writeField(String value) throws IOException {
			if (value.contains(",") || value.contains("\"")) {
				bw.write("\"");
				bw.write(value.replaceAll("\"", "\"\"")); // " => ""
				bw.write("\"");						
			} else {
				bw.write(value);
			}
		}
		public void write(E value) {
			FieldInfo[] fieldInfos = classInfo.fieldInfos;
			
			try {
				writeField(fieldInfos[0].field.get(value).toString());
				for(int i = 1; i < fieldInfos.length; i++) {
					bw.write(",");
					writeField(fieldInfos[i].field.get(value).toString());
				}
				bw.newLine();
			} catch (IllegalArgumentException | IllegalAccessException | IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}

		@Override
		public void close() throws IOException {
			bw.close();
		}
		
		public static <E> Save<E> getInstance(Writer w, Class<E> clazz) {
			Save<E> ret = new Save<E>(w, clazz);
			return ret;
		}
		public static <E> Save<E> getInstance(File file, Class<E> clazz) {
			try {
				File parent = file.getParentFile();
				if (!parent.exists()) {
					parent.mkdirs();
				}
				
				Writer w = new FileWriter(file);
				return getInstance(w, clazz);
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		public static <E> Save<E> getInstance(String path, Class<E> clazz) {
			File file = new File(path);
			return getInstance(file, clazz);
		}
	}
	
	public static <E> void save(Writer w, Class<E> clazz, Collection<E> collection) {
		try (Save<E> save = Save.getInstance(w, clazz)) {
			for(E e: collection) {
				save.write(e);
			}
		} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	public static <E> void save(File file, Class<E> clazz, Collection<E> collection) {
		try (Save<E> save = Save.getInstance(file, clazz)) {
			for(E e: collection) {
				save.write(e);
			}
		} catch (IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	public static <E> void save(String path, Class<E> clazz, Collection<E> collection) {
		File file = new File(path);
		save(file, clazz, collection);
	}	
	
	public static class Load<E> implements Closeable {
		final ClassInfo      classInfo;
		final BufferedReader br;
		
		public static String[] parseLine(String line) {
			List<String> list = new ArrayList<>();
			
			String lineComma = line + ","; // Add ',' to end of line
			int lineLength = lineComma.length();
			int pos = 0;
			
			StringBuilder sb = new StringBuilder();
			for(;;) {
				// pos reach to very last ','
				if (pos == lineLength) break;
				
				sb.setLength(0);
				char firstChar = lineComma.charAt(pos++);
				if (firstChar == '"') {
					for(;;) {
						char c = lineComma.charAt(pos++);
						
						if (c == '"') {
							c = lineComma.charAt(pos++);
							if (c == ',') {
								break;
							} else if (c == '"') {
								sb.append('"');
							} else {
								logger.error("Unexpected c '{}'  {}  '{}'", c, pos, line);
								throw new UnexpectedException("Unexpected nextChar");
							}
						} else {
							sb.append(c);
						}
					}
				} else if (firstChar == ',') {
					// empty column, Do nothing
				} else {
					sb.append(firstChar);
					for(;;) {
						char c = lineComma.charAt(pos++);
						if (c == ',') break;
						sb.append(c);
					}
				}
				
				list.add(sb.toString());
			}
			
			return list.toArray(new String[0]);
		}
		
		private void readHeader() {
			try {
				String line = br.readLine();
				if (line == null) {
					logger.error("Unexpected EOF");
					throw new UnexpectedException("Unexpected EOF");
				}
				String[] names = parseLine(line);
				
				// Sanity check
				if (classInfo.names.length != names.length) {
					logger.error("Unexpected line  {}  {}  {}", classInfo.names.length, names.length, Arrays.asList(names));
					throw new UnexpectedException("Unexpected line");
				}
				for(int i = 0; i < names.length; i++) {
					if (names[i].equals(classInfo.names[i])) continue;
					logger.error("Unexpected name  {}  {}  {}", i, names[i], classInfo.names[i]);
					throw new UnexpectedException("Unexpected name");
				}
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		private Load(Reader r, Class<E> clazz) {
			classInfo = ClassInfo.get(clazz);
			br        = new BufferedReader(r, BUFFER_SIZE);
	
			readHeader();
		}
		
		E read() {
			try {
				String line = br.readLine();
				if (line == null) {
					return null;
				}
				String[] values = parseLine(line);

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
			} catch (IOException | InstantiationException | IllegalAccessException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
		
		@Override
		public void close() throws IOException {
			br.close();
		}
		
		public static <E> Load<E> getInstance(Reader r, Class<E> clazz) {
			Load<E> ret = new Load<E>(r, clazz);
			return ret;
		}
		public static <E> Load<E> getInstance(File f, Class<E> clazz) {
			try {
				Load<E> ret = new Load<E>(new FileReader(f), clazz);
				return ret;
			} catch (FileNotFoundException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}


	public static <E> List<E> load(Reader reader, Class<E> clazz) {
		try (Load<E> load = Load.getInstance(reader, clazz)) {
			List<E> ret = new ArrayList<>();
			for(;;) {
				E e = (E)load.read();
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
	public static <E> List<E> load(File file, Class<E> clazz) {
		try {
			List<E> ret = load(new FileReader(file), clazz);
			return ret;
		} catch (FileNotFoundException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	public static <E> List<E> load(String path, Class<E> clazz) {
		File file = new File(path);
		return load(file, clazz);
	}
}
