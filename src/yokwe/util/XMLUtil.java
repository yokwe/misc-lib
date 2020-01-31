package yokwe.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import yokwe.UnexpectedException;

public final class XMLUtil {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(XMLUtil.class);
	
	public static class QValue implements Comparable<QValue> {
		public final String namespace;
		public final String value;
		
		public QValue(String uri, String value) {
			this.namespace = uri;
			this.value     = value;
		}
		public QValue(XMLElement xmlElement) {
			this.namespace = xmlElement.name.namespace;
			this.value     = xmlElement.name.value;
		}
		public QValue(XMLAttribute xmlAttribute) {
			this.namespace = xmlAttribute.name.namespace;
			this.value     = xmlAttribute.name.value;
		}
		
		@Override
		public String toString() {
			return String.format("{%s %s}", namespace, value);
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == null) {
				return false;
			} else {
				if (o instanceof QValue) {
					QValue that = (QValue)o;
					return this.namespace.equals(that.namespace) && this.value.equals(that.value);
				} else {
					return false;
				}
			}
		}
		@Override
		public int compareTo(QValue that) {
			int ret = this.namespace.compareTo(that.namespace);
			if (ret == 0) ret = this.value.compareTo(that.value);
			return ret;
		}
	}
	
	public static class XMLElement {
		public static XMLElement getInstance(String path, QValue name, Attributes attributes, Map<String, String> prefixMap) {
			return new XMLElement(path, name, attributes, prefixMap);
		}
		
		public final String path;
		public final QValue name;
		
		public final List<XMLAttribute> attributeList;
		
		private final StringBuilder contentBuffer;
		public        String        content;
		
		public        Map<String, String> prefixMap;
		
		private XMLElement(String path, QValue name, Attributes attributes, Map<String, String> prefixMap) {
			this.path          = path;
			this.name          = name;
			this.attributeList = XMLAttribute.getInstance(attributes);
			this.contentBuffer = new StringBuilder();
			this.content       = "";
			this.prefixMap     = prefixMap;
		}
		
		public void characters (char ch[], int start, int length) {
			String chars = new String(ch);
			contentBuffer.append(chars.substring(start, start + length));
			content = contentBuffer.toString();
		}
		
		@Override
		public String toString() {
//			return String.format("{%s %s %s}", uri, localName, attributeList);
			return String.format("{%s \"%s\" %s}", path, content, attributeList);
		}
		
		public String getAttribute(QValue qValue) {
			for(XMLAttribute e: attributeList) {
				if (e.name.equals(qValue)) return e.value;
			}
			logger.error("Unexpected qValue {}", qValue);
			throw new UnexpectedException("Unexpected qValue");
		}
		public String getAttributeOrNull(QValue qValue) {
			for(XMLAttribute e: attributeList) {
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
	
	public static class XMLAttribute {
		public static List<XMLAttribute> getInstance(Attributes attributes) {
			int length = attributes.getLength();
			
			List<XMLAttribute> ret = new ArrayList<>(length);
			for(int i = 0; i < length; i++) {
				QValue name      = new QValue(attributes.getURI(i), attributes.getLocalName(i));
				String type      = attributes.getType(i);
				String value     = attributes.getValue(i);
				XMLAttribute xmlAttribute = new XMLAttribute(name, type, value);
				
				ret.add(xmlAttribute);
			}
			return ret;
		}
		public final QValue name;
		public final String type;
		public final String value;
		
		private XMLAttribute(QValue name, String type, String value) {
			this.name  = name;
			this.type  = type;
			this.value = value;
		}
		
		@Override
		public String toString() {
			return String.format("{%s = \"%s\"}", name, value);
//			return String.format("\"%s\"", value);
		}
	}
	
	private static class SAXHandler extends DefaultHandler {
		final Stream.Builder<XMLElement> builder;
		final Stack<XMLElement>          xmlElementStack;
		final Stack<String>              nameStack;
		      Map<String, String>        prefixMap;
		
		SAXHandler(Stream.Builder<XMLElement> builder) {
			this.builder         = builder;
			this.xmlElementStack = new Stack<>();
			this.nameStack       = new Stack<>();
			this.prefixMap       = new TreeMap<>();
		}
		
		@Override
	    public void startPrefixMapping (String prefix, String uri) {
			// Change instance of prefixMap
			prefixMap = new TreeMap<>(prefixMap);
			prefixMap.put(prefix, uri);
		}
		@Override
	    public void endPrefixMapping (String prefix) {
			// Change instance of prefixMap
			prefixMap = new TreeMap<>(prefixMap);
			prefixMap.remove(prefix);
		}
		
		@Override
		public void startElement (String uri, String localName, String qName, Attributes attributes) {
			nameStack.push(qName);
			
			String path = String.join("/", nameStack);
			QValue name = new QValue(uri, localName);
			XMLElement xmlElement = XMLElement.getInstance(path, name, attributes, prefixMap);
			
			xmlElementStack.push(xmlElement);
		}
		@Override
		public void endElement (String uri, String localName, String qName) {
			XMLElement xmlElement = xmlElementStack.pop();
			nameStack.pop();
			
			builder.accept(xmlElement);
		}
		@Override
		public void characters (char ch[], int start, int length) {
			XMLElement xmlElement = xmlElementStack.peek();
			xmlElement.characters(ch, start, length);
		}
		@Override
		public void warning (SAXParseException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
		@Override
		public void error (SAXParseException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
		@Override
		public void fatalError (SAXParseException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}
	
	public static Stream<XMLElement> buildStream(InputStream is) {
		try {
			// Build handler
			Stream.Builder<XMLElement> builder = Stream.builder();
			SAXHandler handler = new SAXHandler(builder);
			
			// Build parser
			SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
			saxParserFactory.setNamespaceAware(true);
			SAXParser parser = saxParserFactory.newSAXParser();

			// parse
			parser.parse(is, handler);
			
			// return Stream<XMLElement>
			return builder.build();
		} catch (ParserConfigurationException | SAXException | IOException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				String exceptionName = e.getClass().getSimpleName();
				logger.error("{} {}", exceptionName, e);
				throw new UnexpectedException(exceptionName, e);
			}
		}
	}

	public static Stream<XMLElement> buildStream(File file) {
		try {
			InputStream         is  = new FileInputStream(file);
			BufferedInputStream bis = new BufferedInputStream(is, 64 * 1024);
			return buildStream(bis);
		} catch (FileNotFoundException e) {
			String exceptionName = e.getClass().getSimpleName();
			logger.error("{} {}", exceptionName, e);
			throw new UnexpectedException(exceptionName, e);
		}
	}

	public static Stream<XMLElement> buildStream(byte[] data) {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		return buildStream(bais);
	}

	public static Stream<XMLElement> buildStream(String string) {
		return buildStream(StandardCharsets.UTF_8.encode(string).array());
	}
}
