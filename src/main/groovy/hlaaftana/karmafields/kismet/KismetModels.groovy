package hlaaftana.karmafields.kismet

import hlaaftana.discordg.objects.DiscordObject
import hlaaftana.discordg.util.JSONPath
import hlaaftana.karmafields.Util

class KismetModels {
	static Map<Class, KismetClass> defaultConversions = [
		(Macro): 'Macro', (Function): 'Function', (Character): 'Character',
		(Byte): 'Int8', (Short): 'Int16', (Integer): 'Int32', (Long): 'Int64',
		(BigInteger): 'Integer', (BigDecimal): 'Decimal', (Float): 'Dec32',
		(Double): 'Dec64', (Expression): 'Expression', (String): 'String',
		(JSONPath): 'Path', (List): 'List', (Map): 'Map',
		(Boolean): 'Boolean', (KismetClass): 'Class'
	].collectEntries { k, v -> [(k): KismetInner.defaultContext[v]] }

	static KismetObject model(Class c){ defaultConversions[c] ?:
		KismetInner.defaultContext.Native }

	static KismetObject model(KismetObject obj){ obj }

	static KismetObject model(Closure c){ model(new GroovyFunction(x: c)) }

	static KismetObject model(File f){
		model(new Expando(name: f.name) )
	}

	static KismetObject model(DiscordObject obj){
		def x = new KismetObject(obj, Util.discordKismetContext.DiscordObject)
		x.forbidden().add('client')
		x
	}

	static KismetObject model(obj){
		null == obj ? new KismetObject(null, KismetInner.defaultContext.Null) :
			new KismetObject(obj, defaultConversions.find { k, v -> obj in k }?.value ?:
				KismetInner.defaultContext.Native)
	}
}
