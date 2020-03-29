package yokwe.util;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class EnumUtil {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(EnumUtil.class);

	private static class EnumInfo {
		private static Map<String, EnumInfo> map = new TreeMap<>();
		
		static EnumInfo get(Class<?> clazz) {
			String key = clazz.getName();
			if (map.containsKey(key)) {
				return map.get(key);
			} else {
				EnumInfo enumInfo = new EnumInfo(clazz);
				map.put(key, enumInfo);
				return enumInfo;
			}
		}
		
		public Map<String, Enum<?>> enumMap;
		EnumInfo(Class<?> clazz) {
			enumMap = new TreeMap<>();
			
			@SuppressWarnings("unchecked")
			Class<Enum<?>> enumClazz = (Class<Enum<?>>)clazz;
			for(Enum<?> e: enumClazz.getEnumConstants()) {
				enumMap.put(e.toString(), e);
			}
		}
	}
	
	public static <E extends Enum<?>> E getInstance(Class<E> clazz, String value) {
		EnumInfo classInfo = EnumInfo.get(clazz);
		if (classInfo.enumMap.containsKey(value)) {
			Object o = classInfo.enumMap.get(value);
			@SuppressWarnings("unchecked")
			E e = (E)o;
			return e;
		} else {
			logger.error("Unexpected value");
			logger.error("  value {}", value);
			throw new UnexpectedException("Unexpected value");
		}
	}
}
