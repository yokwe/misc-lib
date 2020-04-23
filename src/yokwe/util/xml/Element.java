package yokwe.util.xml;

import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import yokwe.UnexpectedException;

public class Element {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(Element.class);

	public static Element getInstance(String path, QValue name, org.xml.sax.Attributes attributes, Map<String, String> prefixMap) {
		return new Element(path, name, attributes, prefixMap);
	}
	
	public final String path;
	public final QValue name;
	
	public final List<Attribute> attributeList;
	
	private final StringBuilder contentBuffer;
	public        String        content;
	
	public        Map<String, String> prefixMap;
	
	private Element(String path, QValue name, org.xml.sax.Attributes attributes, Map<String, String> prefixMap) {
		this.path          = path;
		this.name          = name;
		this.attributeList = Attribute.getInstance(attributes);
		this.contentBuffer = new StringBuilder();
		this.content       = "";
		this.prefixMap     = prefixMap;
	}
	
	public void characters (char ch[], int start, int length) {
		String chars = new String(ch);
		characters(chars.substring(start, start + length));
	}
	public void characters(String string) {
		contentBuffer.append(string);
		content = contentBuffer.toString();
	}
	
	@Override
	public String toString() {
//			return String.format("{%s %s %s}", uri, localName, attributeList);
		return String.format("{%s \"%s\" %s}", path, content, attributeList);
	}
	
	public String getAttribute(QValue qValue) {
		for(Attribute e: attributeList) {
			if (e.name.equals(qValue)) return e.value;
		}
		logger.error("Unexpected qValue {}", qValue);
		throw new UnexpectedException("Unexpected qValue");
	}
	public String getAttributeOrNull(QValue qValue) {
		for(Attribute e: attributeList) {
			if (e.name.equals(qValue)) return e.value;
		}
		return null;
	}
	public String getAttribute(String value) {
		return getAttribute(new QValue("", value));
	}
	public String getAttributeOrNull(String value) {
		return getAttributeOrNull(new QValue("", value));
	}
	
	public QValue expandNamespacePrefix(String value) {
		String[] names = value.split(":");
		if (names.length != 2) {
			logger.error("Unexpected value format {}", value);
			throw new UnexpectedException("Unexpected value format");
		}
		String prefix = names[0];
		String name   = names[1];
		if (prefixMap.containsKey(prefix)) {
			return new QValue(prefixMap.get(prefix), name);
		} else {
			logger.error("Unexpected prefix {}", prefix);
			logger.error("  prefixMap {}", prefixMap);
			throw new UnexpectedException("Unexpected prefix");
		}
	}
	public boolean canExpandNamespacePrefix(String value) {
		String[] names = value.split(":");
		if (names.length != 2) {
			logger.error("Unexpected value format {}", value);
			throw new UnexpectedException("Unexpected value format");
		}
		String prefix = names[0];
		return prefixMap.containsKey(prefix);
	}
}