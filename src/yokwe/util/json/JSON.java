package yokwe.util.json;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
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
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.stream.JsonGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;
import yokwe.util.GenericInfo;

public final class JSON {
	static final Logger logger = LoggerFactory.getLogger(JSON.class);
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Name {
		String value();
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Ignore {
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
		public final DateTimeFormatter    dateTimeFormatter;
		
		FieldInfo(Field field) {
			this.field = field;
			
			this.name  = field.getName();
			this.clazz = field.getType();

			// Use json name if exists.
			Name jsonName = field.getDeclaredAnnotation(Name.class);
			this.jsonName = (jsonName == null) ? field.getName() : jsonName.value();
			
			Class<?> type = field.getType();
			this.type     = type.getName();
			this.isArray  = type.isArray();
			
			this.ignoreField = field.getDeclaredAnnotation(Ignore.class) != null;
			
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
				
				{
					Constructor<?> construcor = null;
					
					if (!clazz.isInterface()) {
						try {
							construcor = clazz.getDeclaredConstructor();
						} catch(NoSuchMethodException | SecurityException e) {
							logger.warn("Failed to get constructor  {}", clazzName);
						}
					}
					
					this.construcor = construcor;
				}
				
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
			} catch (SecurityException e) {
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

			// Assume jsonReader has only one object
			JsonObject jsonObject = jsonReader.readObject();
			
			setValue(ret, jsonObject);
			
			return ret;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	public static <E> List<E> getList(Class<E> clazz, String jsonString) {
		ClassInfo classInfo = ClassInfo.get(clazz);
		try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
			// Assume result is array
			JsonArray jsonArray = reader.readArray();
			
			int jsonArraySize = jsonArray.size();
			List<E> ret = new ArrayList<>(jsonArraySize);
			
			for(int i = 0; i < jsonArraySize; i++) {
				JsonValue jsonValue = jsonArray.get(i);
				ValueType valueType = jsonValue.getValueType();
				switch (valueType) {
				case OBJECT:
				{
					@SuppressWarnings("unchecked")
					E e = (E)classInfo.construcor.newInstance();
					
					JsonObject jsonObject = jsonValue.asJsonObject();

					setValue(e, jsonObject);
					ret.add(e);
				}
					break;
				case NULL:
					// Skip NULL
					break;
				default:
					logger.info("Unexpected valueType  {}  {}  {}", i, valueType, jsonValue);
					throw new UnexpectedException("Unexpected valueType");
				}
			}
			
			return ret;
		} catch (IllegalAccessException | InstantiationException | IllegalArgumentException | InvocationTargetException | SecurityException | NoSuchMethodException e) {
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
		case "java.math.BigDecimal":
			fieldInfo.field.set(object, new BigDecimal(jsonString.getString()));
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
		ClassInfo classInfo = ClassInfo.get(fieldInfo.clazz);
		
		if (classInfo.clazzName.equals("java.util.Map")) {
			GenericInfo genericInfo = new GenericInfo(fieldInfo.field);
			if (genericInfo.classArguments.length != 2) {
				logger.error("Unexptected genericInfo.classArguments.length {}", genericInfo.classArguments.length);
				throw new UnexpectedException("Unexptected genericInfo.classArguments.length");
			}
			Class<?> mapKeyClass   = genericInfo.classArguments[0];
			Class<?> mapValueClass = genericInfo.classArguments[1];

			String mapKeyClassName   = mapKeyClass.getTypeName();
			String mapValueClassName = mapValueClass.getTypeName();

			if (!mapKeyClassName.equals("java.lang.String")) {
				logger.error("Unexptected keyTypeName {}", mapKeyClassName);
				throw new UnexpectedException("Unexptected keyTypeName");
			}
			
			switch(mapValueClassName) {
			case "java.lang.Long":
			{
				Map<String, Long> map = new TreeMap<>();
				
				for(String childKey: jsonObject.keySet()) {
					JsonValue childValue = jsonObject.get(childKey);
					ValueType childValueType = childValue.getValueType();
					
					switch(childValueType) {
					case STRING:
					{
						JsonString jsonStringtValue = jsonObject.getJsonString(childKey);

						Long value = Long.parseLong(jsonStringtValue.getString());
						
						map.put(childKey, value);
					}
						break;
					default:
						logger.error("Unexptected childValueType {}", childValueType);
						logger.error(" {}", classInfo.clazz.getTypeName());
						logger.error(" {}", fieldInfo.field.toString());
						throw new UnexpectedException("Unexptected childValueType");
					}
				}
				
				fieldInfo.field.set(object, map);
			}
				break;
			case "java.lang.String":
			{
				Map<String, String> map = new TreeMap<>();
				
				for(String childKey: jsonObject.keySet()) {
					JsonValue childValue = jsonObject.get(childKey);
					ValueType childValueType = childValue.getValueType();
					
					switch(childValueType) {
					case STRING:
					{
						JsonString jsonStringtValue = jsonObject.getJsonString(childKey);

						String value = jsonStringtValue.getString();
						
						map.put(childKey, value);
					}
						break;
					default:
						logger.error("Unexptected childValueType {}", childValueType);
						logger.error(" {}", classInfo.clazz.getTypeName());
						logger.error(" {}", fieldInfo.field.toString());
						throw new UnexpectedException("Unexptected childValueType");
					}
				}
				
				fieldInfo.field.set(object, map);
			}
				break;
			default:
				if (mapValueClass.isPrimitive()) {
					//
					logger.error("Unexptected mapValueClass {}", mapValueClassName);
					throw new UnexpectedException("Unexptected mapValueClass");
				} else {
					Map<String, Object> map = new TreeMap<>();
					
					for(String childKey: jsonObject.keySet()) {
						JsonValue childValue = jsonObject.get(childKey);
						ValueType childValueType = childValue.getValueType();
						
						switch(childValueType) {
						case OBJECT:
						{
							JsonObject jsonObjectValue = jsonObject.getJsonObject(childKey);
							
							ClassInfo valueClassInfo = ClassInfo.get(mapValueClass);
							Object value = valueClassInfo.construcor.newInstance();
							
							setValue(value, jsonObjectValue);

							map.put(childKey, value);
						}
							break;
						default:
							logger.error("Unexptected childValueType {}", childValueType);
							logger.error(" {}", classInfo.clazz.getTypeName());
							logger.error(" {}", fieldInfo.field.toString());
							throw new UnexpectedException("Unexptected childValueType");
						}
					}
					
					fieldInfo.field.set(object, map);
				}
				break;
			}

		} else {
			Object fieldObject = classInfo.construcor.newInstance();
			setValue(fieldObject, jsonObject);
			
			fieldInfo.field.set(object, fieldObject);
		}
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
			
			// special case of null array
			if (jsonArraySize == 1) {
				JsonValue jsonValue = jsonArray.get(0);
				if (jsonValue.getValueType() == JsonValue.ValueType.NULL) {
					fieldInfo.field.set(object, null);
					return;
				}
			}
			
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

	
	//
	// toJSONString
	//
	private static void toJSONStringArray(JsonGenerator gen, Object[] array, String name) throws IllegalArgumentException, IllegalAccessException {
		if (name == null) {
			gen.writeStartArray();
		} else {
			gen.writeStartArray(name);
		}
		
		if (array.length == 0) {
			// Do nothing
		} else {
			for(Object o: array) {
				toJSONStringVariable(gen, o, null);
			}
		}
		
		gen.writeEnd();
	}
	private static void toJSONStringObject(JsonGenerator gen, Object o, String name) throws IllegalArgumentException, IllegalAccessException {
		if (name == null) {
			gen.writeStartObject();
		} else {
			gen.writeStartObject(name);
		}

		Class<?>  clazz = o.getClass();
		ClassInfo classInfo = ClassInfo.get(clazz);

		for(FieldInfo fieldInfo: classInfo.fieldInfos) {
			if (fieldInfo.ignoreField) continue;
			
			String fieldName  = fieldInfo.jsonName != null ? fieldInfo.jsonName : fieldInfo.name;
			Object fieldValue = fieldInfo.field.get(o);
			
			if (fieldInfo.clazz.equals(Map.class)) {
				GenericInfo genericInfo = new GenericInfo(fieldInfo.field);
				if (genericInfo.classArguments.length != 2) {
					logger.error("Unexptected genericInfo.classArguments.length {}", genericInfo.classArguments.length);
					throw new UnexpectedException("Unexptected genericInfo.classArguments.length");
				}
				Class<?> mapKeyClass   = genericInfo.classArguments[0];
											
				String mapKeyClassName   = mapKeyClass.getTypeName();
				
//				logger.info("mapKeyClassName   {}", mapKeyClassName);
//				logger.info("mapValueClassName {}", mapValueClassName);
				
				if (!mapKeyClassName.equals("java.lang.String")) {
					logger.error("Unexptected keyTypeName {}", mapKeyClassName);
					throw new UnexpectedException("Unexptected keyTypeName");
				}
				
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>)fieldValue;
				
				// output map
				gen.writeStartObject(fieldName);
				for(Map.Entry<String, Object> e: map.entrySet()) {
					String key   = e.getKey();
					Object value = e.getValue();
					
					toJSONStringVariable(gen, value, key);
				}
				gen.writeEnd();

			} else {
				toJSONStringVariable(gen, fieldValue, fieldName);
			}
		}
		
		gen.writeEnd();
	}
	
	/*

	private static void toJSONStringList(JsonGenerator gen, Object o, String name) throws IllegalArgumentException, IllegalAccessException {
		if (o == null) {
			if (name == null) {
				gen.writeNull();
			} else {
				gen.writeNull(name);
			}
		} else {
			List<?> list = (List<?>)o;
			
			if (name == null) {
				gen.writeStartArray();
			} else {
				gen.writeStartArray(name);
			}

			if (list.size() == 0) {
				gen.writeEnd();
			} else {
				for(Object e: list) {
					toJSONStringVariable(gen, e);
				}
			}
			gen.writeEnd();
		}
	}

	 */
	private static void toJSONStringVariable(JsonGenerator gen, Object o, String name) throws IllegalArgumentException, IllegalAccessException {
		if (o == null) {
			if (name == null) {
				gen.writeNull();
			} else {
				gen.writeNull(name);
			}
		} else {
			Class<?> clazz = o.getClass();
			
			if (clazz.isArray()) {
				toJSONStringArray(gen, (Object[])o, name);
			} else if (simpleTypeSet.contains(clazz.getName())) {
				toJSONStringSimpleType(gen, o, name);
			} else if (clazz.equals(String.class)) {
				if (name == null) {
					gen.write((String)o);
				} else {
					gen.write(name, (String)o);
				}
			} else if (clazz.isEnum()) {
				if (name == null) {
					gen.write(o.toString());
				} else {
					gen.write(name, o.toString());
				}
//			} else if (List.class.isAssignableFrom(clazz)) {
//				toJSONStringList(gen, o, name);
			} else {
				toJSONStringObject(gen, o, name);
			}
		}
	}
	
	private static Set<String> simpleTypeSet = new TreeSet<>();
	static {
		simpleTypeSet.add(Boolean.TYPE.getName());
		simpleTypeSet.add(Boolean.class.getName());
		simpleTypeSet.add(Integer.TYPE.getName());
		simpleTypeSet.add(Integer.class.getName());
		simpleTypeSet.add(Long.TYPE.getName());
		simpleTypeSet.add(Long.class.getName());
		simpleTypeSet.add(Double.TYPE.getName());
		simpleTypeSet.add(Double.class.getName());
		simpleTypeSet.add(Float.TYPE.getName());
		simpleTypeSet.add(Float.class.getName());
	}
	private static void toJSONStringSimpleType(JsonGenerator gen, Object o, String name) throws IllegalArgumentException, IllegalAccessException {
		String clazzName = o.getClass().getName();

		switch(clazzName) {
		case "java.lang.Boolean":
		case "boolean":
			if (name == null) {
				gen.write((boolean)o);
			} else {
				gen.write(name, (boolean)o);
			}
			break;
		case "java.lang.Integer":
		case "int":
			if (name == null) {
				gen.write((int)o);
			} else {
				gen.write(name, (int)o);
			}
			break;
		case "java.lang.Long":
		case "long":
			if (name == null) {
				gen.write((long)o);
			} else {
				gen.write(name, (long)o);
			}
			break;
		case "java.lang.Double":
		case "double":
			if (name == null) {
				gen.write((double)o);
			} else {
				gen.write(name, (double)o);
			}
			break;
		case "java.lang.Float":
		case "float":
			if (name == null) {
				gen.write((float)o);
			} else {
				gen.write(name, (float)o);
			}
			break;
		default:
			logger.error("Unexpected type");
			logger.error("  clazzName {}", clazzName);
			logger.error("  name      {}", name);
			throw new UnexpectedException("Unexpected type");
		}
	}

	private static void toJSONStringVariable(JsonGenerator gen, Object o) throws IllegalArgumentException, IllegalAccessException {
		toJSONStringVariable(gen, o, null);
	}
	
	public static String toJSONString(Object object) {
		StringWriter writer = new StringWriter();
		
		try (JsonGenerator gen = Json.createGenerator(writer)) {
			toJSONStringVariable(gen, object);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	
		return writer.toString();
	}

}
