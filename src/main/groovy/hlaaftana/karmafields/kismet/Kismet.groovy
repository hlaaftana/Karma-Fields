package hlaaftana.karmafields.kismet

class Kismet {
	static Block parse(String code, Map ctxt = new HashMap(KismetInner.defaultContext)){
		def b = new Block(raw: code, context: new Context(data: ctxt))
		b.context.setBlock(b)
		def x = KismetInner.compile(code, b)
		if (!(x instanceof Block)) {
			b.expressions = [x]
			b
		}else x
	}
	
	static eval(String code, Map ctxt = new HashMap(KismetInner.defaultContext)){
		parse(code, ctxt).evaluate()
	}

	static KismetObject model(x){ KismetModels.model(x) }
}
