package hlaaftana.karmafields.kismet

import groovy.transform.Memoized

class KismetClass {
	static List<KismetClass> instances = []
	Class orig
	String name = "anonymous_${instances.size()}"
	def getter = func { ...a -> a[0].inner()[a[1].inner()] }
	def setter = func { ...a -> a[0].inner()[a[1].inner()] = a[2] }
	def caller = func { ...a -> a.size() > 1 ? a[0].inner()(*(a.toList().drop(1))) : a[0].inner()() }
	def constructor = func { ...a -> }
	Map<KismetClass, Object> converters = [:];

	{
		instances += this
	}

	void setName(String n){
		if (n in instances*.name) throw new IllegalArgumentException("Class with name $n already exists")
		this.@name = n
	}

	@Memoized
	KismetObject<KismetClass> getObject() {
		new KismetObject<KismetClass>(this, meta.object)
	}

	@Memoized
	static KismetClass getMeta() {
		new MetaKismetClass()
	}

	def call(...args){
		def a = new KismetObject(new Expando(), this.object)
		constructor(*(([a] + args.toList()) as KismetObject[]))
		a
	}

	String toString(){ "class($name)" }

	protected static func(Closure a){ new GroovyFunction(x: a, convert: false) }
}

class MetaKismetClass extends KismetClass {
	{
		name = 'Class'
		constructor = func { ...a -> a[0].@inner = new KismetClass() }
	}

	KismetObject<KismetClass> getObject() {
		def x = new KismetObject<KismetClass>(this)
		x.@class_ = x
		x
	}
}