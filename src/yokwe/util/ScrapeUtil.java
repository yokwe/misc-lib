package yokwe.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class ScrapeUtil {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(StringUtil.class);

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public static @interface AsNumber {}

	private static final String NBSP = "&nbsp;";
	
	private static Double toClassDouble(String string) {
		if (string == null) return null;
		
		String value = string.replace(",", "");
		while(value.contains(NBSP)) {
			value = value.replace(NBSP, "");
		}
		if (value.isEmpty()) return null;
		try {
			return Double.valueOf(value);
		} catch (NumberFormatException e) {
			logger.error("Unexpected number format");
			logger.error("  string {}!", string);
			logger.error("  value  {}!", value);
			throw new UnexpectedException("Unexpected number format", e);
		}
	}
	private static Long toClassLong(String string) {
		if (string == null) return null;
		
		String value = string.replace(",", "");
		while(value.contains(NBSP)) {
			value = value.replace(NBSP, "");
		}
		if (value.isEmpty()) return null;
		try {
			return Long.valueOf(value);
		} catch (NumberFormatException e) {
			logger.error("Unexpected number format");
			logger.error("  string {}!", string);
			logger.error("  value  {}!", value);
			throw new UnexpectedException("Unexpected number format", e);
		}
	}
	private static Integer toIntegerValue(String string) {
		if (string == null) return null;
		
		String value = string.replace(",", "");
		while(value.contains(NBSP)) {
			value = value.replace(NBSP, "");
		}
		if (value.isEmpty()) return null;
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			logger.error("Unexpected number format");
			logger.error("  string {}!", string);
			logger.error("  value  {}!", value);
			throw new UnexpectedException("Unexpected number format", e);
		}
	}
	
	private static double toPrimitiveDouble(String string) {
		Double value = toClassDouble(string);
		if (value == null) {
			logger.error("Unexpected value");
			logger.error("  string {}!", string);
			throw new UnexpectedException("Unexpected value");
		}
		return value.doubleValue();
	}
	private static long toPrimitiveLong(String string) {
		Long value = toClassLong(string);
		if (value == null) {
			logger.error("Unexpected value");
			logger.error("  string {}!", string);
			throw new UnexpectedException("Unexpected value");
		}
		return value.longValue();
	}
	private static int toPrimitiveInt(String string) {
		Integer value = toIntegerValue(string);
		if (value == null) {
			logger.error("Unexpected value");
			logger.error("  string {}!", string);
			throw new UnexpectedException("Unexpected value");
		}
		return value.intValue();
	}

	private static OptionalDouble toOptionalDouble(String string) {
		Double value = toClassDouble(string);
		return value == null ? OptionalDouble.empty() : OptionalDouble.of(value.doubleValue());
	}
	private static OptionalLong toOptionalLong(String string) {
		Long value = toClassLong(string);
		return value == null ? OptionalLong.empty() : OptionalLong.of(value.longValue());
	}
	private static OptionalInt toOptionalInt(String string) {
		Integer value = toIntegerValue(string);
		return value == null ? OptionalInt.empty() : OptionalInt.of(value.intValue());
	}

	private static Object toOptional(FieldInfo fieldInfo, String string) {
		GenericInfo info = new GenericInfo(fieldInfo.field);
		
		Class<?> type     = info.classArguments[0];
		String   typeName = type.getName();
		
		switch(typeName) {
		case CLASS_STRING:
		{
			String value = toStringValue(string);
			return value.isEmpty() ? Optional.empty() : Optional.of(value);
		}
		default:
			if (type.isEnum()) {
				Map<String, Enum<?>> enumMap = getEnumMap(type);
				
				if (enumMap.containsKey(string)) {
					Enum<?> value = enumMap.get(string);
					return Optional.of(value);
				} else {
					logger.error("Unknow enum value");
					logger.error("  name  {}", fieldInfo.name);
					logger.error("  type  {}", typeName);
					logger.error("  value {}", string);
					throw new UnexpectedException("Unknow enum value");
				}
			}
			logger.error("Unexpected type");
			logger.error("  name  {}", fieldInfo.name);
			logger.error("  type  {}", typeName);
			logger.error("  value {}", string);
			throw new UnexpectedException("Unexpected type");
		}
	}
	
	private static String toStringValue(String string) {
		if (string == null) return "";
		
		String value = string;
		while(value.contains(NBSP)) {
			value = value.replace(NBSP, "");
		}
		return value;
	}
	
	private static Map<String, Map<String, Enum<?>>> enumMapMap = new TreeMap<>();
	private static Map<String, Enum<?>> getEnumMap(Class<?> clazz) {
		String typeName = clazz.getTypeName();
		if (!clazz.isEnum()) {
			logger.error("Unexpected type");
			logger.error("  type  {}", typeName);
			throw new UnexpectedException("Unexpected type");
		}
		if (enumMapMap.containsKey(typeName)) {
			return enumMapMap.get(typeName);
		} else {
			Map<String, Enum<?>> enumMap = new TreeMap<>();
			@SuppressWarnings("unchecked")
			Class<Enum<?>> enumClazz = (Class<Enum<?>>)clazz;
			for(Enum<?> e: enumClazz.getEnumConstants()) {
				String key = e.toString();
				if (enumMap.containsKey(key)) {
					Enum<?> old = enumMap.get(key);
					logger.error("Duplicate enum value");
					logger.error("  enum {}", e.getClass().getName());
					logger.error("  old  {} {}!", old.name(), old.toString());
					logger.error("  new  {} {}!", e.name(), e.toString());
					throw new UnexpectedException("Duplicate enum key");
				} else {
					enumMap.put(e.toString(), e);
				}
			}
			enumMapMap.put(typeName, enumMap);
			return enumMap;
		}
	}
	
	private static class ClassInfo {
		final String          name;
		final Constructor<?>  constructor;
		final FieldInfo[]     fieldInfos;

		
		ClassInfo(String name, Constructor<?> constructor, FieldInfo[] fieldInfos) {
			this.name        = name;
			this.constructor = constructor;
			this.fieldInfos  = fieldInfos;
		}
	}
	private static class FieldInfo {
		final Field    field;
		final String   name;
		final Class<?> type;
		final String   typeName;
		final boolean  asNubmer;

		FieldInfo(Field field) {
			this.field    = field;
			this.name     = field.getName();
			this.type     = field.getType();
			this.typeName = field.getType().getName();
			this.asNubmer = field.isAnnotationPresent(AsNumber.class);
		}
	}
	private static Map<String, ClassInfo> classInfoMap = new TreeMap<>();
	private static ClassInfo getClassInfo(Class<?> clazz) {
		String clazzName = clazz.getName();
		if (classInfoMap.containsKey(clazzName)) {
			return classInfoMap.get(clazzName);
		} else {
			try {
				FieldInfo[] fieldInfos;
				{
					List<FieldInfo> list = new ArrayList<>();
					Field[] fields = clazz.getDeclaredFields();
					for(int i = 0; i < fields.length; i++) {
						Field field   = fields[i];
						int modifiers = field.getModifiers();
						
						// Skip static
						if (Modifier.isStatic(modifiers)) continue;

						// Sanity check
						if (!Modifier.isPublic(modifiers)) {
							logger.error("field is not public");
							logger.error("  clazz  {}", clazz.getName());
							logger.error("  field  {}", field.toString());
							throw new UnexpectedException("not public field");
						}
						list.add(new FieldInfo(field));
					}
					fieldInfos = list.toArray(new FieldInfo[0]);
				}

				Constructor<?> constructor = null;
				{
					Constructor<?>[] constructors = clazz.getDeclaredConstructors();
					
					// Sanity check
					{
						if (constructors.length == 0) {
							logger.error("no constructor");
							logger.error("  clazz       {}", clazz.getName());
							throw new UnexpectedException("no constructor");
						}
						if (1 < constructors.length) {
							logger.error("more than one constructor");
							logger.error("  clazz       {}", clazz.getName());
							for(Constructor<?> e: constructors) {
								logger.error("  constructor {}", e.toString());
							}
							throw new UnexpectedException("more than one constructor");
						}
					}
					
					// Find constructor by param type
					for(Constructor<?> myConstructor: constructors) {						
						Parameter[] myParameters = myConstructor.getParameters();
						if (myParameters.length == fieldInfos.length) {
							boolean hasSameType = true;
							for(int i = 0; i < myParameters.length; i++) {
								Class<?> paramType = myParameters[i].getType();
								Class<?> fieldType = fieldInfos[i].type;
								if (paramType.equals(fieldType)) continue;
								hasSameType = false;
							}
							if (hasSameType) {
								constructor = myConstructor;
							}
						}
					}
					if (constructor == null) {
						logger.error("no suitable constructor");
						logger.error("  clazz       {}", clazz.getName());
						logger.error("    expect {}", Arrays.stream(fieldInfos).map(o -> o.typeName).collect(Collectors.toList()));
						throw new UnexpectedException("no suitable constructor");
					}
					
					// Sanity check
					{
						int modifiers = constructor.getModifiers();
						if (!Modifier.isPublic(modifiers)) {
							logger.error("constructor is not public");
							logger.error("  clazz       {}", clazz.getName());
							logger.error("  constructor {}", constructor.toString());
							throw new UnexpectedException("method is not public");
						}
					}
				}
				
				ClassInfo classInfo = new ClassInfo(clazzName, constructor, fieldInfos);
				classInfoMap.put(clazzName, classInfo);
				
				return classInfo;
			} catch (IllegalArgumentException | SecurityException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
	
	private static final String CLASS_STRING  = "java.lang.String";
	
	private static final String CLASS_DOUBLE  = "java.lang.Double";
	private static final String CLASS_LONG    = "java.lang.Long";
	private static final String CLASS_INTEGER = "java.lang.Integer";
	
	private static final String PRIMITIVE_DOUBLE = "double";
	private static final String PRIMITIVE_LONG   = "long";
	private static final String PRIMITIVE_INT    = "int";
	
	private static final String OPTIONAL_DOUBLE = "java.util.OptionalDouble";
	private static final String OPTIONAL_LONG   = "java.util.OptionalLong";
	private static final String OPTIONAL_INT    = "java.util.OptionalInt";
	
	private static final String CLASS_OPTIONAL = "java.util.Optional";

	
	private static Object getArg(ClassInfo classInfo, FieldInfo fieldInfo, String stringValue) {
		if (stringValue == null) {
			logger.error("stringValue is null");
			logger.error("  clazz  {}", classInfo.name);
			logger.error("  name   {}", fieldInfo.name);
			throw new UnexpectedException("stringValue is null");
		}

		String name      = fieldInfo.name;
		String typeName  = fieldInfo.typeName;

		Object arg;
		
		switch(typeName) {
		case CLASS_STRING:
			if (fieldInfo.asNubmer) {
				// Remove comma
				stringValue = stringValue.replace(",", "");
			}
			arg = toStringValue(stringValue);
			break;
		case CLASS_DOUBLE:
			arg = toClassDouble(stringValue);
			break;
		case CLASS_LONG:
			arg = toClassLong(stringValue);
			break;
		case CLASS_INTEGER:
			arg = toIntegerValue(stringValue);
			break;
		case PRIMITIVE_DOUBLE:
			arg = toPrimitiveDouble(stringValue);
			break;
		case PRIMITIVE_LONG:
			arg = toPrimitiveLong(stringValue);
			break;
		case PRIMITIVE_INT:
			arg = toPrimitiveInt(stringValue);
			break;
		case OPTIONAL_DOUBLE:
			arg = toOptionalDouble(stringValue);
			break;
		case OPTIONAL_LONG:
			arg = toOptionalLong(stringValue);
			break;
		case OPTIONAL_INT:
			arg = toOptionalInt(stringValue);
			break;
		case CLASS_OPTIONAL:
			arg = toOptional(fieldInfo, stringValue);
			break;
		default:
			if (fieldInfo.type.isEnum()) {
				Map<String, Enum<?>> enumMap = getEnumMap(fieldInfo.type);
				
				if (enumMap.containsKey(stringValue)) {
					arg = enumMap.get(stringValue);
					break;
				} else {
					logger.error("Unknow enum value");
					logger.error("  clazz {}", classInfo.name);
					logger.error("  name  {}", name);
					logger.error("  type  {}", typeName);
					logger.error("  value {}", stringValue);
					throw new UnexpectedException("Unknow enum value");
				}
			}
			logger.error("Unexpected type");
			logger.error("  clazz {}", classInfo.name);
			logger.error("  name  {}", name);
			logger.error("  type  {}", typeName);
			throw new UnexpectedException("Unexpected type");
		}
		
		return arg;
	}
	
	public static <E> E get(Class<E> clazz, Pattern pat, String string) {
		try {
			ClassInfo classInfo = getClassInfo(clazz);
			Object[] args = new Object[classInfo.fieldInfos.length];
			
			Matcher m = pat.matcher(string);
			if (m.find()) {
				for(int i = 0; i < classInfo.fieldInfos.length; i++) {
					FieldInfo fieldInfo = classInfo.fieldInfos[i];
					
					String stringValue = m.group(fieldInfo.name);
					args[i] = getArg(classInfo, fieldInfo, stringValue);
				}
				@SuppressWarnings("unchecked")
				E ret = (E)classInfo.constructor.newInstance(args);
				return ret;
			} else {
				return null;
			}
		} catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	public static <E> List<E> getList(Class<E> clazz, Pattern pat, String string) {
		try {
			List<E> ret = new ArrayList<>();
			
			ClassInfo classInfo = getClassInfo(clazz);
			Object[] args = new Object[classInfo.fieldInfos.length];

			Matcher m = pat.matcher(string);
			while(m.find()) {
				for(int i = 0; i < classInfo.fieldInfos.length; i++) {
					FieldInfo fieldInfo = classInfo.fieldInfos[i];
					
					String stringValue = m.group(fieldInfo.name);
					args[i] = getArg(classInfo, fieldInfo, stringValue);
				}
				@SuppressWarnings("unchecked")
				E value = (E)classInfo.constructor.newInstance(args);
				ret.add(value);
			}
			
			return ret;
		} catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
}
