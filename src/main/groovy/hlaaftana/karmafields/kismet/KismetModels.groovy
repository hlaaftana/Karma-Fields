package hlaaftana.karmafields.kismet

import hlaaftana.discordg.objects.DiscordObject
import hlaaftana.discordg.util.JSONPath
import hlaaftana.karmafields.Util

class KismetModels {
	static Map<Class, KismetClass> defaultConversions = [
		(Macro): 'Macro', (Function): 'Function', (char): 'Character',
		(byte): 'Int8', (short): 'Int16', (int): 'Int32', (long): 'Int64',
		(BigInteger): 'Integer', (BigDecimal): 'Decimal', (float): 'Dec32',
		(double): 'Dec64', (Expression): 'Expression', (String): 'String',
		(JSONPath): 'Path', (List): 'List', (Map): 'Map', (KismetClass): 'Class'
	].collectEntries { k, v -> [(k): KismetInner.defaultContext[v]] }

	static KismetObject model(Class c){ defaultConversions[c] ?:
		KismetInner.defaultContext.Native }

	static KismetObject model(KismetObject obj){ obj }

	static KismetObject model(Closure c){ model(new GroovyFunction(x: c)) }

	static KismetObject model(DiscordObject obj){
		def x = new KismetObject(obj, Util.discordKismetContext.DiscordObject)
		x.forbidden().addAll(['client', 'object', 'rawObject', 'patchableObject'])
		x
	}

	static KismetObject model(obj){
		null == obj ? new KismetObject(null, KismetInner.defaultContext.Null) :
			new KismetObject(obj, defaultConversions.find { k, v -> obj in k }?.value ?:
				KismetInner.defaultContext.Native)
	}
}
