package hlaaftana.karmafields

class Arguments {
	String raw
	int index = 0
	Arguments(String r){ raw = r }

	static run(String args, Closure closure){
		def a = new Arguments(args)
		def c = closure.clone()
		c.delegate = a
		c()
	}

	String getRest(){ raw.substring(index) }

	String getAfterSpace(){
		String now = rest
		String ret = ""
		while (true){
			if (index == raw.size()){
				index--
				break
			}
			if (Character.isSpaceChar(now[index])){
				if (ret){
					index--
					break
				}
			}else{
				ret += now[index]
			}
			index++
		}
		ret
	}

	def start(match, Closure closure){
		if (rest.startsWith(match)){
			index += match.size()
			def cl = closure.clone()
			cl.delegate = this
			cl()
		}
	}

	def when(match, Closure closure){
		if (rest in match){
			def cl = closure.clone()
			cl.delegate = this
			cl()
		}
	}

	def goBack(int i){ index -= i }
	def goBack(String str){ raw = str + raw; index -= str.size() }
}
