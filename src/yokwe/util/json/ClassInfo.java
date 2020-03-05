package yokwe.util.json;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;
import yokwe.util.json.JSONBase.DateTimeFormat;
import yokwe.util.json.JSONBase.IgnoreField;
import yokwe.util.json.JSONBase.JSONName;


public class ClassInfo {
	static final Logger logger = LoggerFactory.getLogger(ClassInfo.class);

	private static Map<String, ClassInfo> map = new TreeMap<>();
	
	public static class FieldInfo {
		public final Field    field;
		public final String   name;
		public final String   jsonName;
		public final String   type;
		public final boolean  isArray;
		public final boolean  ignoreField;
		
		public final DateTimeFormatter dateTimeFormatter;
		
		FieldInfo(Field field) {
			this.field = field;
			
			this.name  = field.getName();

			// Use JSONName if exists.
			JSONName jsonName = field.getDeclaredAnnotation(JSONName.class);
			this.jsonName = (jsonName == null) ? field.getName() : jsonName.value();
			
			Class<?> type = field.getType();
			this.type     = type.getName();
			this.isArray  = type.isArray();
			
			this.ignoreField = field.getDeclaredAnnotation(IgnoreField.class) != null;
			
			DateTimeFormat dateTimeFormat = field.getDeclaredAnnotation(DateTimeFormat.class);
			this.dateTimeFormatter = (dateTimeFormat == null) ? null : DateTimeFormatter.ofPattern(dateTimeFormat.value());
		}
		
		@Override
		public String toString() {
			return String.format("{%s %s %s %s}", name, type, isArray, ignoreField);
		}
	}
	
	public static ClassInfo get(JSONBase o) {
		return get(o.getClass());
	}
	public static ClassInfo get(Class<?> clazz) {
		if (JSONBase.class.isAssignableFrom(clazz)) {
			@SuppressWarnings("unchecked")
			Class<JSONBase> clazzBase = (Class<JSONBase>)clazz;

			String key = clazzBase.getName();
			
			if (map.containsKey(key)) return map.get(key);
			
			ClassInfo value = new ClassInfo(clazzBase);
			map.put(key, value);
			return value;
		} else {
			logger.error("Unexpected clazz {}", clazz.getClass().getName());
			throw new UnexpectedException("Unexpected clazz");
		}
	}

	public final String            clazzName;
	public final String            method;
	public final FieldInfo[]       fieldInfos;
	public final int               fieldSize;
	public final Set<String>       fieldNameSet; // Set of fieldsInfos[].jsonName
	public final Constructor<JSONBase> construcor;
	
	private static String getStaticStringFieldValue(Class<?> clazz, String fieldName) {
		try {
			Field field = clazz.getDeclaredField(fieldName);
			if (field != null) {
				if (!Modifier.isStatic(field.getModifiers())) {
					logger.error("METHOD field is not static {}", clazz.getName());
					throw new UnexpectedException("METHOD field is not static");
				}
				String fieldTypeName = field.getType().getName();
				if (!fieldTypeName.equals("java.lang.String")) {
					logger.error("Unexpected fieldTypeName {}", fieldTypeName);
					throw new UnexpectedException("Unexpected fieldTypeName");
				}
				Object value = field.get(null);
				return (String)value;
			} else {
				return null;
			}
		} catch (NoSuchFieldException e) {
			return null;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	ClassInfo(Class<JSONBase> clazz) {
		try {
			List<Field> fieldList = new ArrayList<>();
			for(Field field: clazz.getDeclaredFields()) {
				// Skip static field
				if (Modifier.isStatic(field.getModifiers())) continue;
				fieldList.add(field);
			}
			FieldInfo[] fieldInfos = new FieldInfo[fieldList.size()];
			for(int i = 0; i < fieldInfos.length; i++) {
				fieldInfos[i] = new FieldInfo(fieldList.get(i));
			}
			
			int fieldSize = 0;
			{
				for(int i = 0; i < fieldInfos.length; i++) {
					if (fieldInfos[i].ignoreField) continue;
					fieldSize++;
				}
			}
			
			this.clazzName    = clazz.getName();
			this.method       = getStaticStringFieldValue(clazz, "METHOD");
			this.fieldInfos   = fieldInfos;
			this.fieldSize    = fieldSize;
			this.fieldNameSet = Arrays.stream(fieldInfos).map(o -> o.jsonName).collect(Collectors.toSet());
			this.construcor   = clazz.getDeclaredConstructor(JsonObject.class);
		} catch (SecurityException | IllegalArgumentException | NoSuchMethodException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s  %s  %s", clazzName, method, Arrays.asList(this.fieldInfos));
	}
}
