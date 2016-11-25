package hlaaftana.karmafields

import static hlaaftana.discordg.util.WhatIs.whatis

class BrainfuckInterpreter {
	Closure inputMethod = {
		new Scanner(System.in).next().charAt(0) as int
	}

	int steps = 0
	InfiniteList stack = new InfiniteList(0)
	int stackPosition = 0

	String interpret(String code){
		interpret(toLists(code))
	}

	String interpret(List code){
		String output = ""
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
					output += stack[stackPosition] as char
					++steps
				}
				when ",": {
					stack[stackPosition] = inputMethod(this)
					++steps
				}
			}
		}
		output
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
}