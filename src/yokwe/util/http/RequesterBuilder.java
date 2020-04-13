package yokwe.util.http;

import org.apache.hc.core5.http2.HttpVersionPolicy;

// To hide difference, provide ReuestBuilder
public class RequesterBuilder {
	int maxTotal                    = 50;
	int defaultMaxPerRoute          = 20;
	int soTimeout                   = 30; // 30 seconds
	HttpVersionPolicy versionPolicy = HttpVersionPolicy.NEGOTIATE;
	
    private RequesterBuilder() {
    }
    public static RequesterBuilder custom() {
    	return new RequesterBuilder();
    }
	public RequesterBuilder setVersionPolicy(HttpVersionPolicy newValue) {
		this.versionPolicy = newValue;
		return this;
	}
	public RequesterBuilder setMaxTotal(int newValue) {
		this.maxTotal = newValue;
		return this;
	}
	public RequesterBuilder setDefaultMaxPerRoute(int newValue) {
		this.defaultMaxPerRoute = newValue;
		return this;
	}
	public RequesterBuilder setSoTimeout(int newValue) {
		this.soTimeout = newValue;
		return this;
	}
}