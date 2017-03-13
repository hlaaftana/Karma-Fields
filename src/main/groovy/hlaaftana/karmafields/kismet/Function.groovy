package hlaaftana.karmafields.kismet

abstract class Function {
	abstract KismetObject call(KismetObject... args)
}

class KismetFunction extends Function {
	KismetObject<Block> b

	KismetObject call(KismetObject... args){
		def c = b.inner().anonymousClone()
		args.length.times {
			c.context.directSet("\$$it", args[it])
		}
		c.context.directSet('$all', Kismet.model(args.toList()))
		Block.changeBlock(c.expressions, c)
		c()
	}
}

class GroovyFunction extends Function {
	boolean convert = true
	Closure x

	KismetObject call(KismetObject... args){
		Kismet.model(args ? x(*(convert ? args*.inner() : args)) : x())
	}
}
