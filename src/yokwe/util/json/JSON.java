package yokwe.util.json;

import java.io.Reader;
import java.io.StringReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public final class JSON {
	static final Logger logger = LoggerFactory.getLogger(JSON.class);
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface JSONName {
		String value();
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface IgnoreField {
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface DateTimeFormat {
		String value();
	}
	
	public static class FieldInfo {
		public final Field    field;
		public final Class<?> clazz;
		public final String   name;
		public final String   jsonName;
		public final String   type;
		public final boolean  isArray;
		public final boolean  ignoreField;
		
		public final Map<String, Enum<?>> enumMap;
		public final DateTimeFormatter dateTimeFormatter;
		
		FieldInfo(Field field) {
			this.field = field;
			
			this.name  = field.getName();
			this.clazz = field.getType();

			// Use JSONName if exists.
			JSONName jsonName = field.getDeclaredAnnotation(JSONName.class);
			this.jsonName = (jsonName == null) ? field.getName() : jsonName.value();
			
			Class<?> type = field.getType();
			this.type     = type.getName();
			this.isArray  = type.isArray();
			
			this.ignoreField = field.getDeclaredAnnotation(IgnoreField.class) != null;
			
			DateTimeFormat dateTimeFormat = field.getDeclaredAnnotation(DateTimeFormat.class);
			this.dateTimeFormatter = (dateTimeFormat == null) ? null : DateTimeFormatter.ofPattern(dateTimeFormat.value());
			
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
	
	public static class ClassInfo {
		private static Map<String, ClassInfo> map = new TreeMap<>();
		
		public static ClassInfo get(Class<?> clazz) {
			String clazzName = clazz.getName();
			if (map.containsKey(clazzName)) {
				return map.get(clazzName);
			} else {
				ClassInfo classInfo = new ClassInfo(clazz);
				map.put(clazzName, classInfo);
				return classInfo;
			}
		}
		
		public final Class<?>       clazz;
		public final String         clazzName;
		public final Constructor<?> construcor;

		public final FieldInfo[]    fieldInfos;
		public final Set<String>    fieldNameSet;
		
		private ClassInfo(Class<?> clazz) {
			try {
				this.clazz      = clazz;
				this.clazzName  = clazz.getName();
				this.construcor = clazz.getDeclaredConstructor();
				
				{
					List<Field> fieldList = new ArrayList<>();
					for(Field field: clazz.getDeclaredFields()) {
						// Skip static field
						if (Modifier.isStatic(field.getModifiers())) continue;
						fieldList.add(field);
					}
					this.fieldInfos = new FieldInfo[fieldList.size()];
					for(int i = 0; i < fieldInfos.length; i++) {
						fieldInfos[i] = new FieldInfo(fieldList.get(i));
					}
				}
				
				this.fieldNameSet = Arrays.stream(fieldInfos).map(o -> o.jsonName).collect(Collectors.toSet());
			} catch (NoSuchMethodException | SecurityException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}

	}
	
	private static final LocalDate     NULL_LOCAL_DATE      = LocalDate.of(0, 1, 1);
	private static final LocalTime     NULL_LOCAL_TIME      = LocalTime.of(0, 0, 0);
	private static final LocalDateTime NULL_LOCAL_DATE_TIME = LocalDateTime.of(NULL_LOCAL_DATE, NULL_LOCAL_TIME);

	public static <E> E unmarshal(Class<E> clazz, String jsonString) {
		return unmarshal(clazz, new StringReader(jsonString));
	}
	public static <E> E unmarshal(Class<E> clazz, Reader reader) {
		try (JsonReader jsonReader = Json.createReader(reader)) {
			ClassInfo classInfo = ClassInfo.get(clazz);
			
			// call default constructor of the class
			@SuppressWarnings("unchecked")
			E ret = (E)classInfo.construcor.newInstance();

			// Assume result has only one object
			JsonObject jsonObject = jsonReader.readObject();
			
			setValue(ret, jsonObject);
			
			return ret;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
		
	private static void setValue(Object object, JsonObject jsonObject) throws IllegalAccessException, IllegalArgumentException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
		ClassInfo classInfo = ClassInfo.get(object.getClass());
		
		// Sanity check
		for(FieldInfo fieldInfo: classInfo.fieldInfos) {
			if (fieldInfo.ignoreField)                      continue;
			if (jsonObject.containsKey(fieldInfo.jsonName)) continue;
			// jsonObject doesn't contains field named fieldInfo.jsonName
			logger.warn("Missing json field  {}  {}  {}", classInfo.clazzName, fieldInfo.jsonName, jsonObject.keySet());
		}
		for(String jsonKey: jsonObject.keySet()) {
			if (classInfo.fieldNameSet.contains(jsonKey)) continue;
			// this class doesn't contains field named jsonKey
			logger.warn("Unknown json field  {}  {}  {}", classInfo.clazzName, jsonKey, classInfo.fieldNameSet);
		}
		
		for(FieldInfo fieldInfo: classInfo.fieldInfos) {
			// Skip field if name is not exist in jsonObject
			if (!jsonObject.containsKey(fieldInfo.jsonName))continue;
			
			ValueType valueType = jsonObject.get(fieldInfo.jsonName).getValueType();
			
//			logger.debug("parse {} {} {}", fieldInfo.name, valueType.toString(), fieldInfo.type);
			
			switch(valueType) {
			case NUMBER:
				setValue(object, fieldInfo, jsonObject.getJsonNumber(fieldInfo.jsonName));
				break;
			case STRING:
				setValue(object, fieldInfo, jsonObject.getJsonString(fieldInfo.jsonName));
				break;
			case TRUE:
				setValue(object, fieldInfo, true);
				break;
			case FALSE:
				setValue(object, fieldInfo, false);
				break;
			case NULL:
				setValue(object, fieldInfo);
				break;
			case OBJECT:
				setValue(object, fieldInfo, jsonObject.getJsonObject(fieldInfo.jsonName));
				break;
			case ARRAY:
				setValue(object, fieldInfo, jsonObject.getJsonArray(fieldInfo.jsonName));
				break;
			default:
				logger.error("Unknown valueType {} {}", valueType.toString(), fieldInfo.toString());
				throw new UnexpectedException("Unknown valueType");
			}
		}

		// Assign default value for LocalDate and LocalDateTime, if field value is null and not appeared in jsonObject
		for(FieldInfo fieldInfo: classInfo.fieldInfos) {
			// Skip if name is exist in jsonObject
			if (jsonObject.containsKey(fieldInfo.jsonName)) continue;

			Object objectField = fieldInfo.field.get(object);
			// If field is null, assign default value
			if (objectField == null) {
				if (!fieldInfo.ignoreField) {
					logger.warn("Assign default value  {} {} {}", classInfo.clazzName, fieldInfo.name, fieldInfo.type);
				}
				setValue(object, fieldInfo);
			}
		}
	}
	
	//
	// JsonNumber
	//
	private static void setValue(Object object, FieldInfo fieldInfo, JsonNumber jsonNumber) throws IllegalAccessException {
		switch(fieldInfo.type) {
		case "double":
			fieldInfo.field.set(object, jsonNumber.doubleValue());
			break;
		case "long":
			fieldInfo.field.set(object, jsonNumber.longValue());
			break;
		case "int":
			fieldInfo.field.set(object, jsonNumber.intValue());
			break;
		case "java.math.BigDecimal":
			fieldInfo.field.set(object, jsonNumber.bigDecimalValue());
			break;
		case "java.lang.String":
			// To handle irregular case in Symbols, add this code. Value of iexId in Symbols can be number or String.
			fieldInfo.field.set(object, jsonNumber.toString());
			break;
		case "java.time.LocalDateTime":
			fieldInfo.field.set(object, LocalDateTime.ofInstant(Instant.ofEpochMilli(jsonNumber.longValue()), ZoneOffset.UTC));
			break;
		default:
			logger.error("Unexptected type {}", fieldInfo.field.toString());
			throw new UnexpectedException("Unexptected type");
		}
	}

	//
	// JsonString
	//
	private static void setValue(Object object, FieldInfo fieldInfo, JsonString jsonString) throws IllegalAccessException {
		switch(fieldInfo.type) {
		case "java.lang.String":
			fieldInfo.field.set(object, jsonString.getString());
			break;
		case "double":
			fieldInfo.field.set(object, Double.valueOf((jsonString.getString().length() == 0) ? "0" : jsonString.getString()));
			break;
		case "long":
			fieldInfo.field.set(object, Long.valueOf((jsonString.getString().length() == 0) ? "0" : jsonString.getString()));
			break;
		case "int":
			fieldInfo.field.set(object, Integer.valueOf((jsonString.getString().length() == 0) ? "0" : jsonString.getString()));
			break;
		case "java.time.LocalDate":
			if (fieldInfo.dateTimeFormatter != null) {
				fieldInfo.field.set(object, LocalDate.parse(jsonString.getString(), fieldInfo.dateTimeFormatter));
			} else {
				fieldInfo.field.set(object, LocalDate.parse(jsonString.getString()));
			}
			break;
		case "java.time.LocalDateTime":
			if (fieldInfo.dateTimeFormatter != null) {
				fieldInfo.field.set(object, LocalDateTime.parse(jsonString.getString(), fieldInfo.dateTimeFormatter));
			} else {
				fieldInfo.field.set(object, LocalDateTime.parse(jsonString.getString()));
			}
			break;
		default:
			if (fieldInfo.enumMap != null) {
				String value = jsonString.getString();
				if (fieldInfo.enumMap.containsKey(value)) {
					fieldInfo.field.set(object, fieldInfo.enumMap.get(value));
				} else {
					logger.error("Unknow enum value  {}  {}", fieldInfo.clazz.getName(), value);
					throw new UnexpectedException("Unknow enum value");
				}
			} else {
				logger.error("Unexptected type {}", fieldInfo.field.toString());
				throw new UnexpectedException("Unexptected type");
			}
		}
	}
	
	//
	// boolean
	//
	private static void setValue(Object object, FieldInfo fieldInfo, boolean value) throws IllegalAccessException {
		switch(fieldInfo.type) {
		case "boolean":
			fieldInfo.field.set(object, value);
			break;
		default:
			logger.error("Unexptected type {}", fieldInfo.field.toString());
			throw new UnexpectedException("Unexptected type");
		}
	}
	
	//
	// default value
	//
	private static void setValue(Object object, FieldInfo fieldInfo) throws IllegalAccessException {
		switch(fieldInfo.type) {
		case "double":
			fieldInfo.field.set(object, 0);
			break;
		case "long":
			fieldInfo.field.set(object, 0);
			break;
		case "int":
			fieldInfo.field.set(object, 0);
			break;
		case "java.time.LocalDateTime":
			fieldInfo.field.set(object, NULL_LOCAL_DATE_TIME);
			break;
		case "java.time.LocalDate":
			fieldInfo.field.set(object, NULL_LOCAL_DATE);
			break;
		case "java.lang.String":
			fieldInfo.field.set(object, "");
			break;
		default:
			if (fieldInfo.field.getType().isPrimitive()) {
				logger.error("Unexpected field type");
				logger.error("  field {}", fieldInfo.name);
				logger.error("  type  {}", fieldInfo.type);
				throw new UnexpectedException("Unexpected field type");
			} else {
				fieldInfo.field.set(object, null);
			}
			break;
		}
	}
	
	//
	// JsonObject
	//
	private static void setValue(Object object, FieldInfo fieldInfo, JsonObject jsonObject) throws IllegalArgumentException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
		Object fieldObject = fieldInfo.field.get(object);
		setValue(fieldObject, jsonObject);
	}

	//
	// JsonArray
	//
	private static void setValue(Object object, FieldInfo fieldInfo, JsonArray jsonArray) throws IllegalArgumentException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
		if (!fieldInfo.isArray) {
			logger.error("Field is not array  {}", fieldInfo.field.toString());
			throw new UnexpectedException("Field is not array");
		}
		
		Class<?> componentType = fieldInfo.field.getType().getComponentType();
		String componentTypeName = componentType.getName();
		switch(componentTypeName) {
		case "java.lang.String":
		{
			// array of String
			int jsonArraySize = jsonArray.size();
			String[] array = new String[jsonArray.size()];
			
			for(int i = 0; i < jsonArraySize; i++) {
				JsonValue jsonValue = jsonArray.get(i);
				
				switch(jsonValue.getValueType()) {
				case STRING:
					array[i] = jsonArray.getString(i);
					break;
				default:
					logger.error("Unexpected json array element type {} {}", jsonValue.getValueType().toString(), fieldInfo.field.toString());
					throw new UnexpectedException("Unexpected json array element type");
				}
			}
			fieldInfo.field.set(object, array);
		}
			break;
		default:
		{				
			int jsonArraySize = jsonArray.size();
			Object[] array = (Object[])Array.newInstance(componentType, jsonArraySize);

			ClassInfo classInfo = ClassInfo.get(componentType);

			for(int i = 0; i < jsonArraySize; i++) {
				JsonValue jsonValue = jsonArray.get(i);
				
				switch(jsonValue.getValueType()) {
				case OBJECT:
				{
					array[i] = classInfo.construcor.newInstance();
					setValue(array[i], jsonValue.asJsonObject());
				}
					break;
				default:
					logger.error("Unexpected json array element type {} {}", jsonValue.getValueType().toString(), classInfo.clazzName);
					throw new UnexpectedException("Unexpected json array element type");
				}
			}
			fieldInfo.field.set(object, array);
		}
			break;
		}
	}

}
