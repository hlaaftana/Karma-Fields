package hlaaftana.kf;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Arguments implements Iterator {
	public Arguments(CharSequence r) {
		raw = r.toString();
	}

	public static Object run(CharSequence arguments, @DelegatesTo(Arguments.class) Closure closure) {
		Arguments a = new Arguments(arguments);
		Closure c = (Closure) closure.clone();
		c.setDelegate(a);
		return c.call(a);
	}

	public String getRest() {
		return raw.substring(Math.min(index, raw.length()));
	}

	public String next() {
		if (index >= raw.length()) {
			index = raw.length();
			return "";
		}

		StringBuilder x = new StringBuilder();
		int currentQuote = -1;
		boolean done = false;
		boolean escaped = false;
		for (int ch : getRest().codePoints().toArray()) {
			if (done) break;
			else if (!escaped && ch == '\\')
				escaped = true;
			else if (!escaped && currentQuote != -1 && ch == currentQuote) {
				currentQuote = -1;
				done = true;
			} else if (!escaped && ch == '"' || ch == '\'')
				currentQuote = ch;
			else if (currentQuote == -1 && Character.isWhitespace(ch)) {
				escaped = false;
				done = true;
			} else {
				escaped = false;
				x.appendCodePoint(ch);
			}
			++index;
		}

		String result = x.toString();
		return !result.isEmpty() ? result : next();
	}

	public boolean hasNext() {
		return index < raw.length();
	}

	public Integer goBack(int i) {
		return index -= i;
	}

	public Integer goBack(CharSequence str) {
		raw = str.toString().concat(raw);
		return index -= str.length();
	}

	public static String[] splitArgs(String arguments, int max, boolean keepQuotes) {
		List<StringBuilder> list = new ArrayList<>();
		list.add(new StringBuilder());
		int currentQuote = -1;
		boolean escaped = false;
		for (int ch : arguments.codePoints().toArray())
			if (max != 0 && list.size() >= max) list.get(list.size() - 1).appendCodePoint(ch);
			else if (currentQuote != -1)
				if (!escaped && ch == currentQuote) {
					currentQuote = -1;
					if (keepQuotes) list.get(list.size() - 1).appendCodePoint(ch);
				} else if (!escaped && ch == '\\') escaped = true;
			else {
				escaped = false;
				list.get(list.size() - 1).appendCodePoint(ch);
			}
			else if (!escaped && ch == '\\') escaped = true;
			else if (!escaped && ch == '"' || ch == '\'') {
				currentQuote = ch;
				if (keepQuotes) list.get(list.size() - 1).appendCodePoint(ch);
			} else {
				escaped = false;
				if (Character.isWhitespace(ch))
					list.add(new StringBuilder());
				else list.get(list.size() - 1).appendCodePoint(ch);
			}

		String[] result = new String[list.size()];
		for (int i = 0; i < result.length; ++i)
			result[i] = list.get(i).toString();
		return result;
	}

	public static String[] splitArgs(String arguments, int max) {
		return Arguments.splitArgs(arguments, max, false);
	}

	public static String[] splitArgs(String arguments) {
		return Arguments.splitArgs(arguments, 0, false);
	}

	public String getRaw() {
		return raw;
	}

	public void setRaw(String raw) {
		this.raw = raw;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	private String raw;
	private int index = 0;
}
