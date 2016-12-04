package hlaaftana.karmafields

import static hlaaftana.discordg.util.WhatIs.whatis

class BrainfuckInterpreter {
	Closure inputMethod = {
		new Scanner(System.in).next().charAt(0) as int
	}

	int steps = 0
	InfiniteList stack = new InfiniteList(0)
	int stackPosition = 0

	def interpret(String code, Modes mode = Modes.CHAR){
		interpret(toLists(code), mode)
	}

	def interpret(List code, Modes mode = Modes.CHAR){
		List output = []
		code.each { c ->
			whatis(c){
				when(List){
					while (stack[stackPosition]){
						++steps
						output += interpret(c)
					}
				}
				when ">": {
					++stackPosition
					++steps
				}
				when "<": {
					--stackPosition
					++steps
				}
				when "+": {
					++stack[stackPosition]
					++steps
				}
				when "-": {
					--stack[stackPosition]
					++steps
				}
				when ".": {
					output += stack[stackPosition]
					++steps
				}
				when ",": {
					stack[stackPosition] = inputMethod(this)
					++steps
				}
			}
		}
		mode.run(output)
	}

	private static toLists(String bf){
		List a = []
		int howDeep = 0
		bf.each {
			def var = a
			howDeep.times { var = var.last() }
			if (it == "["){
				++howDeep
				var.add([])
			}else if (it == "]"){
				--howDeep
			}else{
				var.add it
			}
		}
		a
	}

	enum Modes {
		CHAR({ l -> l.collect { (it + 32) as char }.join("") }),
		NUM({ l -> l }),
		UNICODE({ l -> l.collect { it as char }.join("") })

		Closure closure
		Closure<String> joiner
		Modes(Closure c){
			closure = c
		}

		def run(o){
			closure(o)
		}
	}
}