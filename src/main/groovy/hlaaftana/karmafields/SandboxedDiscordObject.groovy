package hlaaftana.karmafields

import hlaaftana.discordg.objects.DiscordObject

class SandboxedDiscordObject {
	private DiscordObject inner
	private List forbidden = ['client', 'object', 'rawObject', 'patchableObject']

	SandboxedDiscordObject(DiscordObject i){ inner = i }
	
	def inner(){ inner } // method so kismet cant access it
	
	def getProperty(String name){
		if (name in forbidden)
			throw new MissingPropertyException(name, inner.class)
		else {
			def x = inner.getProperty(name)
			x instanceof DiscordObject ? new SandboxedDiscordObject(x) : x
		}
	}

	def toMap(){
		inner.metaClass.properties*.name - forbidden
	}
}
