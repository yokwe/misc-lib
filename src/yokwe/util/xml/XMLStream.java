package yokwe.util.xml;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import yokwe.UnexpectedException;

public final class XMLStream {
	static final org.slf4j.Logger logger = LoggerFactory.getLogger(XMLStream.class);
	
	private static class SAXHandler extends DefaultHandler {
		final Stream.Builder<Element> builder;
		final Stack<Element>          elementStack;
		final Stack<String>           nameStack;
		      Map<String, String>     prefixMap;
		
		SAXHandler(Stream.Builder<Element> builder) {
			this.builder         = builder;
			this.elementStack = new Stack<>();
			this.nameStack       = new Stack<>();
			this.prefixMap       = new TreeMap<>();
		}
		
		@Override
	    public void startPrefixMapping (String prefix, String uri) {
			// Change instance of prefixMap	
			prefixMap.put(prefix, uri);
		}
		@Override
	    public void endPrefixMapping (String prefix) {
			// Change instance of prefixMap
			prefixMap = new TreeMap<>(prefixMap);
			prefixMap.remove(prefix);
		}
		
		@Override
		public void startElement (String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
			nameStack.push(qName);
			
			String  path    = String.join("/", nameStack);
			QValue  name    = new QValue(uri, localName);
			Element element = Element.getInstance(path, name, attributes, prefixMap);
			
			elementStack.push(element);
		}
		@Override
		public void endElement (String uri, String localName, String qName) {
			Element element = elementStack.pop();
			nameStack.pop();
			
			// Append child content to parent
			if (!element.content.isEmpty()) {
				if (!elementStack.isEmpty()) {
					Element parent = elementStack.peek();
					parent.characters(element.content);
				}
			}
			
			builder.accept(element);
		}
		@Override
		public void characters (char ch[], int start, int length) {
			Element element = elementStack.peek();
			element.characters(ch, start, length);
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
	
	public static Stream<Element> buildStream(InputStream is) {
		try {
			// Build handler
			Stream.Builder<Element> builder = Stream.builder();
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

	public static Stream<Element> buildStream(File file) {
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

	public static Stream<Element> buildStream(byte[] data) {
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		return buildStream(bais);
	}

	// string can contains encoding information of self.
	// XML file can have Shift_JIS encoding attribute in xml tag
	@Deprecated
	public static Stream<Element> buildStream(String string) {
		return buildStream(StandardCharsets.UTF_8.encode(string).array());
	}
}
