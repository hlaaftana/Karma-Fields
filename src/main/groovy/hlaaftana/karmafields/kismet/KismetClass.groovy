package hlaaftana.karmafields.kismet

import groovy.transform.AutoClone
import groovy.transform.Memoized

@AutoClone
class KismetClass {
	private static List<String> names = []
	private int index = names.size()
	Class orig
	String name = "anonymous_$index"
	Function has = func { o, k -> o.inner().hasProperty(k) as boolean }
	Function getter = func { o, k -> o.inner()[k.inner()] }
	Function setter = func { o, k, v -> o.inner()[k.inner()] = v }
	Function caller = func { o, ...a -> a ? o.inner()(*a) : o.inner()() }
	Function constructor = func { ...a -> new KismetObject(new Expando(), this.object) }
	Function iterator = func { o -> o.inner().iterator() };

	{
		names[index] = name
	}

	void setName(String n){
		if (n in names) throw new IllegalArgumentException("Class with name $n already exists")
		else { name = n; names[index] = n }
	}

	@Memoized
	KismetObject<KismetClass> getObject() {
		new KismetObject<KismetClass>(this, meta.object)
	}

	@Memoized
	static KismetClass getMeta() {
		new KismetClass() {
			KismetObject<KismetClass> getObject() {
				def x = new KismetObject<KismetClass>(this)
				x.@class_ = x
				x
			}
		}
	}

	def call(...args){ constructor(*args) }

	String toString(){ "class($name)" }

	private static func(Closure a){ new GroovyFunction(x: a, convert: false) }
}