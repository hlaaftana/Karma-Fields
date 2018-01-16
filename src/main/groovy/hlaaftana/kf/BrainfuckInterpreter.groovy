package hlaaftana.kf

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
		interpret bfTokens(code), mode
	}

	def interpret(List code, Modes mode = Modes.CHAR) {
		mode.convert interpretRaw(code)
	}

	List<Integer> interpretRaw(List code) {
		List<Integer> output = []
		for (c in code) {
			if (c instanceof List) {
				while (stack[stackPosition]) {
					output.addAll(interpretRaw((List) c))
					++steps
				}
				continue
			}
			else if (c == 0)
				++stackPosition
			else if (c == 1)
				--stackPosition
			else if (c == 2)
				++stack[stackPosition > max ? (max = stackPosition) : stackPosition]
			else if (c == 3)
				--stack[stackPosition > max ? (max = stackPosition) : stackPosition]
			else if (c == 4)
				output += stack[stackPosition]
			else if (c == 5)
				stack[stackPosition] = inputMethod(this)
			++steps
		}
		output
	}

	static int OPEN_BRACKET = (char) '['
	static int CLOSE_BRACKET = (char) ']'
	static String TOKENS = '><+-.,'
	static List bfTokens(String bf){
		List a = []
		int howDeep = 0
		for (int it : bf.toCharArray()) {
			List var = a
			for (int i = 0; i < howDeep; ++i) var = (List) var.last()
			if (it == OPEN_BRACKET) {
				++howDeep
				var.add([])
			} else if (it == CLOSE_BRACKET) --howDeep
			else {
				int i = TOKENS.indexOf(it)
				if (i >= 0) var.add i
			}
		}
		a
	}

	enum Modes {
		CHAR { @CompileStatic def convert(List<Integer> l) {
			StringBuilder b = new StringBuilder()
			for (c in l)
				b.appendCodePoint(c + 32)
			b.toString()
		} },
		NUM { @CompileStatic def convert(List<Integer> l) { l } },
		UNICODE { @CompileStatic def convert(List<Integer> l) {
			StringBuilder b = new StringBuilder()
			for (c in l)
				b.appendCodePoint(c)
			b.toString()
		} }

		abstract convert(List<Integer> l)
	}
}