package yokwe.util.xml;

public class QValue implements Comparable<QValue> {
	public final String namespace;
	public final String value;
	
	public QValue(String uri, String value) {
		this.namespace = uri;
		this.value     = value;
	}
	public QValue(Element element) {
		this.namespace = element.name.namespace;
		this.value     = element.name.value;
	}
	public QValue(Attribute attribute) {
		this.namespace = attribute.name.namespace;
		this.value     = attribute.name.value;
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