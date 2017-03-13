package hlaaftana.karmafields

import hlaaftana.karmafields.kismet.Kismet

class SandboxedObject {
	private inner
	private List forbidden

	SandboxedObject(i, f = ['class', 'metaClass']){ inner = i; forbidden = f }
	
	def inner(){ inner } // method so kismet cant access it
	
	def getProperty(String name){
		if (name in forbidden)
			throw new MissingPropertyException(name, inner.class)
		else {
			Kismet.model(inner.getProperty(name))
		}
	}

	def toMap(){
		null == inner ? null : (inner.metaClass.properties*.name - forbidden).collectEntries {
			try{
				def x = getProperty(it)
				[(it): x instanceof SandboxedObject ? x.toMap() : x]
			}catch (MissingPropertyException ex){}
		}
	}
}
