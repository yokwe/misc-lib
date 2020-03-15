package yokwe.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

//
// Generic
//
public class GenericInfo {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(GenericInfo.class);

	public final Class<?>   rawClass;
	public final Class<?>[] classArguments;
	
	public GenericInfo(Field field) {
		Type genericType = field.getGenericType();
		if (genericType instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType)genericType;
			Type rawType = parameterizedType.getRawType();
			Type[] typeArguments = parameterizedType.getActualTypeArguments();

			if (rawType instanceof Class) {
				rawClass = (Class<?>)rawType;
			} else {
				logger.error("Unexpected rawType  {}", rawType.getClass().getName());
				throw new UnexpectedException("Unexpected rawType");
			}

			classArguments = new Class<?>[typeArguments.length];
			for(int i = 0; i < typeArguments.length; i++) {
				Type typeArgument = typeArguments[i];
				if (typeArgument instanceof Class) {
					classArguments[i] = (Class<?>)typeArgument;
				} else {
					logger.error("Unexpected typeArgument  {}", typeArgument.getClass().getName());
					throw new UnexpectedException("Unexpected typeArgument");
				}
			}
		} else {
			logger.error("Unexpected fieldGenericType  {}", genericType.getClass().getName());
			throw new UnexpectedException("Unexpected genericType");
		}
	}
}