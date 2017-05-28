package hlaaftana.karmafields.kismet

class KismetObject<T> {
	KismetObject<KismetClass> class_
	private forbidden = ['class', 'metaClass']
	private T inner

	KismetObject(i, c){ this.@inner = i; this.@class_ = c }
	KismetObject(i){ this.@inner = i }

	def inner(){ this.@inner }
	def forbidden(){ this.@forbidden }
	def kclass(){ this.@class_ }

	def getProperty(String name){
		if (name in forbidden) throw new MissingPropertyException(name, inner().class)
		else Kismet.model(kclass().inner().getter.call(this, Kismet.model(name)))
	}

	void setProperty(String name, value){
		if (name in forbidden) throw new MissingPropertyException(name, inner().class)
		else Kismet.model(kclass().inner().setter.call(this, Kismet.model(name), Kismet.model(value)))
	}

	def methodMissing(String name, ...args){
		if (args.every { it instanceof KismetObject }) args = args*.inner()
		Kismet.model(args ? inner()."$name"(*args) : inner()."$name"())
	}

	def methodMissing(String name, Collection args){ methodMissing(name, args as Object[]) }
	def methodMissing(String name, args){ methodMissing(name, args instanceof Object[] ? args : [args] as Object[]) }
	def methodMissing(String name, KismetObject args){ methodMissing(name, args.inner()) }
	def methodMissing(String name, KismetObject... args){ methodMissing(args*.inner()) }

	def call(...args){
		Kismet.model(kclass().inner().caller.call(this, *(args.collect(Kismet.&model))))
	}

	def "as"(Class c){
		def k = KismetModels.defaultConversions[c]?.inner()
		if (k && kclass().inner().converters.containsKey(k)) kclass().inner().converters[k](this)
		else inner().asType(c)
	}

	def "as"(KismetClass c){
		if (c && kclass().inner().converters.containsKey(c)) kclass().inner().converters[c](this)
		else throw new ClassCastException('Can\'t cast object with class ' +
			kclass() + ' to class ' + c)
	}

	boolean asBoolean(){
		inner() as boolean
	}

	String toString() {
		inner().toString()
	}
}
