package yokwe.util.xml;

import java.util.ArrayList;
import java.util.List;

public class Attribute {
	public static List<Attribute> getInstance(org.xml.sax.Attributes attributes) {
		int length = attributes.getLength();
		
		List<Attribute> ret = new ArrayList<>(length);
		for(int i = 0; i < length; i++) {
			QValue    name      = new QValue(attributes.getURI(i), attributes.getLocalName(i));
			String    type      = attributes.getType(i);
			String    value     = attributes.getValue(i);
			Attribute attribute = new Attribute(name, type, value);
			
			ret.add(attribute);
		}
		return ret;
	}
	public final QValue name;
	public final String type;
	public final String value;
	
	private Attribute(QValue name, String type, String value) {
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