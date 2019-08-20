package yokwe.util.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DoubleUtil {
	public static final double ALMOST_ZERO = 0.000001;
	public static boolean isAlmostZero(double value) {
		return -ALMOST_ZERO < value && value < ALMOST_ZERO;
	}
	public static boolean isAlmostEqual(double a, double b) {
		return isAlmostZero(a - b);
	}
	
	public static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    return bd.setScale(places, RoundingMode.HALF_UP).doubleValue();
	}
	
	public static double round(String value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    return bd.setScale(places, RoundingMode.HALF_UP).doubleValue();
	}
	
	public static double roundPrice(double value) {
		return round(String.format("%.4f", value), 2);
	}
	public static double roundQuantity(double value) {
		return round(String.format("%.7f", value), 5);
	}
}
