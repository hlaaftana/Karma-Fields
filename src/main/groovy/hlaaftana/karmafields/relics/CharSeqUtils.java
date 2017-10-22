package hlaaftana.karmafields.relics;

public class CharSeqUtils {
	public static String repeat(char c, int times) {
		if (times <= 0) return "";
		if (times == 1) return String.valueOf(c);
		return String.valueOf(repeatCharToArray(c, times));
	}

	public static char[] repeatCharToArray(char c, int times) {
		if (times <= 0) return new char[0];
		char[] arr = new char[times];
		for (int i = 0; i < times; ++i)
			arr[i] = c;
		return arr;
	}

	public static String repeat(CharSequence seq, int times) {
		if (times <= 0) return "";
		if (times == 1) return seq.toString();
		StringBuilder builder = new StringBuilder(seq.length() * times);
		for (int i = 0; i < times; ++i)
			builder.append(seq);
		return builder.toString();
	}

	public static String concat(Object obj) {
		return obj.toString();
	}

	public static String concat(Object... objects) {
		StringBuilder builder = new StringBuilder();
		for (Object obj : objects)
			builder.append(obj);
		return builder.toString();
	}

	public static String lpad(CharSequence seq, int num, char c) {
		if (num <= seq.length()) return seq.toString();
		return String.valueOf(repeatCharToArray(c, seq.length() - num)) + seq;
	}

	public static String lpad(CharSequence seq, int num, CharSequence r) {
		if (num <= seq.length()) return seq.toString();
		StringBuilder builder = new StringBuilder(num);
		int a = seq.length() - num;
		for (int i = 0; i < a / r.length(); ++i)
			builder.append(r);
		if (a % r.length() != 0)
			builder.append(r.subSequence(0, a % r.length()));
		builder.append(seq);
		return builder.toString();
	}

	public static String rpad(CharSequence seq, int num, char c) {
		if (num <= seq.length()) return seq.toString();
		return seq.toString() + String.valueOf(repeatCharToArray(c, seq.length() - num));
	}

	public static String rpad(CharSequence seq, int num, CharSequence r) {
		if (num <= seq.length()) return seq.toString();
		StringBuilder builder = new StringBuilder(num);
		builder.append(seq);
		int a = seq.length() - num;
		for (int i = 0; i < a / r.length(); ++i)
			builder.append(r);
		if (a % r.length() != 0)
			builder.append(r.subSequence(0, a % r.length()));
		return builder.toString();
	}
}
