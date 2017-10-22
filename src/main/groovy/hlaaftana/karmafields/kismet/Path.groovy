package hlaaftana.karmafields.kismet

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

@CompileStatic
class Path {
	String raw
	List<PathExpr> parsedExpressions = []

	Path(String aaa){
		raw = aaa
		StringBuilder latest = new StringBuilder()
		int last = 0
		boolean type = true
		for (int it in aaa.codePoints().toArray()) {
			int len = latest.length()
			if (it == 46 || it == 91) {
				String x = latest.toString()
				latest = new StringBuilder()
				parsedExpressions.add(type ? new PropertyPathExpr(x) : new SubscriptPathExpr(x))
				type = it == 46
			} else {
				if (last == 94) latest.deleteCharAt(len - 1)
				if (0 != len || it != 93) latest.appendCodePoint it
			}
			last = it
		}
		String x = latest.toString()
		parsedExpressions.add(type ? new PropertyPathExpr(x) : new SubscriptPathExpr(x))
	}

	static Path parse(String aaa){ new Path(aaa) }

	String toString() { raw }

	def apply(thing){
		for (it in parsedExpressions) thing = it.act(thing)
		thing
	}

	static abstract class PathExpr {
		String raw

		PathExpr(String raw) {
			this.raw = raw
		}

		abstract act(thing)
	}

	@InheritConstructors
	static class PropertyPathExpr extends PathExpr {
		@CompileDynamic
		def act(thing) {
			raw ? (raw.startsWith('*') ?
				thing*."${raw.substring(1)}" :
				thing."$raw") :
				thing
		}
	}

	@InheritConstructors
	static class SubscriptPathExpr extends PathExpr {
		def act(thing) {
			thing.invokeMethod('getAt', raw as int)
		}
	}
}

