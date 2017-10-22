package hlaaftana.karmafields

import groovy.transform.CompileStatic

@CompileStatic
class BrainfuckInterpreter {
	Closure<Integer> inputMethod = {
		(++new Scanner(System.in)).codePointAt(0)
	}

	int steps = 0
	int[] stack = new int[30000]
	int stackPosition = 0
	int max = 0

	def interpret(String code, Modes mode = Modes.CHAR){
		interpret(bfTokens(code), mode)
	}

	def interpret(List code, Modes mode = Modes.CHAR) {
		List output = []
		for (c in code) {
			if (c instanceof List) {
				while (stack[stackPosition]) {
					output.addAll(interpret((List) c, Modes.NUM))
					++steps
				}
				continue
			} else if (c == '>')
				++stackPosition
			else if (c == '<')
				--stackPosition
			else if (c == '+')
				++stack[stackPosition > max ? (max = stackPosition) : stackPosition]
			else if (c == '-')
				--stack[stackPosition > max ? (max = stackPosition) : stackPosition]
			else if (c == '.')
				output += stack[stackPosition]
			else if (c == ',')
				stack[stackPosition] = inputMethod(this)
			++steps
		}
		mode.convert(output)
	}

	static int OPEN_BRACKET = '['.codePointAt(0)
	static int CLOSE_BRACKET = ']'.codePointAt(0)
	static List bfTokens(String bf){
		List a = []
		int howDeep = 0
		PrimitiveIterator.OfInt x = bf.codePoints().iterator()
		while (x.hasNext()) {
			int it = x.nextInt()
			List var = a
			for (int i = 0; i < howDeep; ++i) var = (List) var.last()
			if (it == OPEN_BRACKET){
				++howDeep
				var.add([])
			}else if (it == CLOSE_BRACKET) --howDeep
			else var.add it
		}
		a
	}

	enum Modes {
		CHAR { def convert(List<Integer> l) {
			StringBuilder b = new StringBuilder()
			for (c in l)
				b.appendCodePoint(c + 32)
			b.toString()
		} },
		NUM { def convert(List<Integer> l) { l } },
		UNICODE { def convert(List<Integer> l) {
			StringBuilder b = new StringBuilder()
			for (c in l)
				b.appendCodePoint(c)
			b.toString()
		} }

		abstract convert(List<Integer> l)
	}
}