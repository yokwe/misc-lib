package yokwe.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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

public class XMLUtil {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(XMLUtil.class);
	
	public static class XMLElement {
		public static XMLElement getInstance(String path, String uri, String localName, String qName, Attributes attributes) {
			return new XMLElement(path, uri, localName, qName, attributes);
		}
		
		public final String path;
		public final String uri;
		public final String localName;
		public final String qName;
		
		public final Map<String, XMLAttribute> attributeMap;
		
		public final StringBuilder content;
		
		private XMLElement(String path, String uri, String localName, String qName, Attributes attributes) {
			this.path          = path;
			this.uri           = uri;
			this.localName     = localName;
			this.qName         = qName;
			this.attributeMap  = new TreeMap<>();
			this.content       = new StringBuilder();
			
			List<XMLAttribute> attributeList = XMLAttribute.getInstance(attributes);
			for(XMLAttribute xmlAttribute: attributeList) {
				String name = xmlAttribute.qName;
				
				if (attributeMap.containsKey(name)) {
					logger.error("duplicate name {}", name);
					logger.error("old {}", attributeMap.get(name));
					logger.error("new {}", xmlAttribute);
					throw new UnexpectedException("duplicate name");
				} else {
					attributeMap.put(name, xmlAttribute);
				}
			}
		}
		
		public void characters (char ch[], int start, int length) {
			String chars = new String(ch);
			content.append(chars.substring(start, start + length));
		}
		
		@Override
		public String toString() {
//			return String.format("{%s %s %s}", uri, localName, attributeList);
			return String.format("{%s \"%s\" %s}", path, content.toString(), attributeMap);
		}
	}
	
	public static class XMLAttribute {
		public static List<XMLAttribute> getInstance(Attributes attributes) {
			int length = attributes.getLength();
			
			List<XMLAttribute> ret = new ArrayList<>(length);
			for(int i = 0; i < length; i++) {
				String localName = attributes.getLocalName(i);
				String qName     = attributes.getQName(i);
				String type      = attributes.getType(i);
				String uri       = attributes.getURI(i);
				String value     = attributes.getValue(i);
				XMLAttribute xmlAttribute = new XMLAttribute(localName, qName, type, uri, value);
				
				ret.add(xmlAttribute);
			}
			return ret;
		}
		public final String localName;
		public final String qName;
		public final String type;
		public final String uri;
		public final String value;
		
		private XMLAttribute(String localName, String qName, String type, String uri, String value) {
			this.localName = localName;
			this.qName     = qName;
			this.type      = type;
			this.uri       = uri;
			this.value     = value;
		}
		
		@Override
		public String toString() {
//			return String.format("{%s = \"%s\"}", qName, value);
			return String.format("\"%s\"", value);
		}
	}
	
	private static class SAXHandler extends DefaultHandler {
		final Stream.Builder<XMLElement> builder;
		final Stack<XMLElement>          xmlElementStack;
		final Stack<String>              nameStack;
		
		SAXHandler(Stream.Builder<XMLElement> builder) {
			this.builder         = builder;
			this.xmlElementStack = new Stack<>();
			this.nameStack       = new Stack<>();
		}
		
		@Override
		public void startElement (String uri, String localName, String qName, Attributes attributes) {
			nameStack.push(qName);
			
			String path = String.join("/", nameStack);
			XMLElement xmlElement = XMLElement.getInstance(path, uri, localName, qName, attributes);
			
			xmlElementStack.push(xmlElement);
		}
		@Override
		public void endElement (String uri, String localName, String qName) {
			XMLElement xmlElement = xmlElementStack.pop();
			nameStack.pop();
			
//			logger.info("{}", xmlElement);
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
}
