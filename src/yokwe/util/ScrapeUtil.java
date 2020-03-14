package yokwe.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class ScrapeUtil {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(StringUtil.class);

	private static class ClassInfo {
		final Constructor<?> constructor;
		final FieldInfo[]    fieldInfos;
		
		ClassInfo(Constructor<?> constructor, FieldInfo[] fieldInfos) {
			this.constructor = constructor;
			this.fieldInfos  = fieldInfos;
		}
	}
	private static class FieldInfo {
		final String name;
		
		FieldInfo(Field field) {
			this.name  = field.getName();
		}
	}
	private static Map<String, ClassInfo> classInfoMap = new TreeMap<>();
	private static ClassInfo getClassInfo(Class<?> clazz) {
		String clazzName = clazz.getName();
		if (classInfoMap.containsKey(clazzName)) {
			return classInfoMap.get(clazzName);
		} else {
			try {
				Constructor<?> constructor = null;
				int            paramCount  = -1;
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
					constructor = constructors[0];
					paramCount  = constructor.getParameterCount();
					
					// Sanity check
					{
						int modifiers = constructor.getModifiers();
						if (!Modifier.isPublic(modifiers)) {
							logger.error("constructor is not public");
							logger.error("  clazz       {}", clazz.getName());
							logger.error("  constructor {}", constructor.toString());
							throw new UnexpectedException("method is not public");
						}
						
						Parameter[] parameters = constructor.getParameters();
						for(Parameter parameter: parameters) {
							if (parameter.getType().equals(String.class)) continue;
							logger.error("parameter is not String");
							logger.error("  clazz       {}", clazz.getName());
							logger.error("  constructor {}", constructor.toString());
							logger.error("  parameter   {}", parameter.toString());
							throw new UnexpectedException("parameter is not String");
						}
					}
				}

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
					// Sanity check
					{
						if (paramCount != fieldInfos.length) {
							logger.error("Unexpected number of fields");
							logger.error("  clazz  {}", clazz.getName());
							logger.error("  param  {}", paramCount);
							logger.error("  field  {}", fieldInfos.length);
							throw new UnexpectedException("Unexpected number of fields");
						}
					}
				}
				
				ClassInfo classInfo = new ClassInfo(constructor, fieldInfos);
				classInfoMap.put(clazzName, classInfo);
				
				return classInfo;
			} catch (IllegalArgumentException | SecurityException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}
	
	public static <E> E getInstance(Class<E> clazz, Pattern pat, String string) {
		try {
			ClassInfo classInfo = getClassInfo(clazz);
			String[] args = new String[classInfo.fieldInfos.length];
			
			Matcher m = pat.matcher(string);
			if (m.find()) {
				for(int i = 0; i < classInfo.fieldInfos.length; i++) {
					String name  = classInfo.fieldInfos[i].name;
					args[i] = m.group(name);
					if (args[i] == null) {
						logger.error("value is null");
						logger.error("  clazz  {}", clazz.getName());
						logger.error("  name   {}", name);
						throw new UnexpectedException("value is null");
					}
				}
				@SuppressWarnings("unchecked")
				E ret = (E)classInfo.constructor.newInstance((Object[])args);
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
			String[] args = new String[classInfo.fieldInfos.length];

			Matcher m = pat.matcher(string);
			while(m.find()) {
				for(int i = 0; i < classInfo.fieldInfos.length; i++) {
					String name  = classInfo.fieldInfos[i].name;
					args[i] = m.group(name);
					if (args[i] == null) {
						logger.warn("value is null {} {}", clazz.getName(), name);
					}
				}
				@SuppressWarnings("unchecked")
				E value = (E)classInfo.constructor.newInstance((Object[])args);
				
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
