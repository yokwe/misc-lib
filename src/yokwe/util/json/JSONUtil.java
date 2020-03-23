package yokwe.util.json;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.TreeSet;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class JSONUtil {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(JSONUtil.class);
		
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
				toJSONStringObject(gen, o, null);
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

		for(ClassInfo.FieldInfo fieldInfo: classInfo.fieldInfos) {
			String fieldName  = fieldInfo.jsonName != null ? fieldInfo.jsonName : fieldInfo.name;
			Object fieldValue = fieldInfo.field.get(o);
			toJSONStringVariable(gen, fieldValue, fieldName);
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
//			} else if (List.class.isAssignableFrom(clazz)) {
//				toJSONStringList(gen, o, name);
			} else if (clazz.isEnum()) {
				if (name == null) {
					gen.write(o.toString());
				} else {
					gen.write(name, o.toString());
				}
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
	
	public static String toJSONString(Object o) {
		StringWriter writer = new StringWriter();
		
		try (JsonGenerator gen = Json.createGenerator(writer)) {
			toJSONStringVariable(gen, o);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	
		return writer.toString();
	}
	
	public static <E extends JSONBase> E fromJSONSTring(Class<E> clazz, String jsonString) {
		ClassInfo classInfo = ClassInfo.get(clazz);
		try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
			// Assume result is only one object
			JsonObject arg = reader.readObject();
			@SuppressWarnings("unchecked")
			E ret = (E)classInfo.construcor.newInstance(arg);
			return ret;
		} catch (IllegalAccessException | InstantiationException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
}
