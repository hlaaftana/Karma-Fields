package hlaaftana.karmafields

import java.util.List;

import groovy.transform.CompileStatic

@CompileStatic
class Arguments implements Iterator {
	String raw
	int index = 0
	Arguments(CharSequence r){ raw = r }

	static run(CharSequence args, Closure closure){
		Arguments a = new Arguments(args)
		Closure c = (Closure) closure.clone()
		c.delegate = a
		c(a)
	}

	String getRest(){ raw.substring(Math.min(index, raw.size())) }
	
	String next(){
		if (index >= raw.size()){ index = raw.size(); return '' }
		String x = ''
		String currentQuote
		boolean done
		boolean escaped
		for (ch in rest.toList()){
			if (done) break
			else if (!escaped && ch == '\\'){ escaped = true; ++index }
			else if (!escaped && currentQuote && ch == currentQuote){
				currentQuote = null; done = true; ++index }
			else if (!escaped && ch in ['"', '\'']){
				currentQuote = ch; ++index }
			else if (!currentQuote && Character.isSpaceChar(ch as char)){
				escaped = false; done = true; ++index }
			else {
				escaped = false
				x += ch
				++index
			}
		}
		x ?: next()
	}
	
	boolean hasNext(){
		index < raw.size()
	}

	def start(match, Closure closure){
		if (rest.startsWith(match.toString())){
			index += match.toString().length()
			Closure cl = (Closure) closure.clone()
			cl.delegate = this
			cl(match)
		}
	}

	def when(match, Closure closure){
		if (rest in match){
			Closure cl = (Closure) closure.clone()
			cl.delegate = this
			cl(match)
		}
	}

	def goBack(int i){ index -= i }
	def goBack(CharSequence str){ raw = str + raw; index -= str.length() }
	
	static List splitArgs(String arguments, int max = 0, boolean keepQuotes = false){
		List list = ['']
		String currentQuote
		boolean escaped
		arguments.toList().each { String ch ->
			if (max && list.size() >= max){
				list[list.size() - 1] += ch
			}else{
				if (currentQuote){
					if (!escaped && ch == currentQuote){
						currentQuote = null
						if (keepQuotes) list[-1] += ch
					}
					else if (!escaped && ch == '\\') escaped = true
					else {
						escaped = false
						list[-1] += ch
					}
				}else{
					if (!escaped && ch == '\\') escaped = true
					else if (!escaped && ch in ['"', "'"]){
						currentQuote = ch
						if (keepQuotes) list[-1] += ch
					}
					else {
						escaped = false
						if (Character.isSpaceChar(ch as char)) list += ''
						else list[-1] += ch
					}
				}
			}
		}
		list
	}
}
