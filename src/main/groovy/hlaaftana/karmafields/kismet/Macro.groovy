package hlaaftana.karmafields.kismet

abstract class Macro {
	abstract KismetObject call(KismetObject<Expression>... expressions)
}

class KismetMacro extends Macro {
	KismetObject<Block> b

	KismetObject call(KismetObject<Expression>... args){
		def c = b.inner().anonymousClone()
		args.length.times {
			c.context.directSet("\$$it", args[it])
		}
		c.context.directSet('$all', Kismet.model(args.toList()))
		Block.changeBlock(c.expressions, c)
		c()
	}
}

class GroovyMacro extends Macro {
	boolean convert = true
	Closure x

	KismetObject call(KismetObject<Expression>... expressions){
		Kismet.model(expressions ? x(*(convert ? expressions*.inner() : expressions)) : x())
	}
}