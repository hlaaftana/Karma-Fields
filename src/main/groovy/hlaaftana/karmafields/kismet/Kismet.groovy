package hlaaftana.karmafields.kismet

import hlaaftana.discordg.objects.DiscordObject;
import hlaaftana.karmafields.SandboxedDiscordObject;

class Kismet {
	static Block parse(String code, Map ctxt = KismetInner.defaultContext){
		Block block = new Block()
		def exprs = KismetInner.separateLines(code).collect { it ->
			KismetInner.parseExpression(block, (String) it) }
		block.expressions = (LinkedList<Expression>) exprs
		block.context = new Context(data: ctxt, block: block)
		block
	}
	
	static eval(String code, Map ctxt = KismetInner.defaultContext){
		parse(code, ctxt).evaluate()
	}
	
	static SandboxedDiscordObject model(DiscordObject obj){
		new SandboxedDiscordObject(obj)
	}
	
	static model(obj){
		obj
	}
}
