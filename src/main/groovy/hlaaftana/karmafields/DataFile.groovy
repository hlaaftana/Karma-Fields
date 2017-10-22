package hlaaftana.karmafields

import groovy.transform.CompileStatic
import hlaaftana.karmafields.relics.JSONUtil

@CompileStatic
class DataFile {
	File file
	Map<String, Object> cache
	DataFile(x, load = true){ file = x as File; if (load) refresh() }

	Map refresh(){ cache = new HashMap<>((Map) JSONUtil.parse(file)) }
	void dump(){ if (cache) JSONUtil.dump(file, cache) }

	Modifier<Map<String, Object>> modifier(){
		new Modifier<Map>(value: cache)
	}

	void modify(upd = true, Closure c){
		cache = modifier().modify(c)
		if (upd) dump()
	}

	void modify(upd = true, Map c){
		cache = modifier().modify(c)
		if (upd) dump()
	}

	def remove(String name, boolean upd = true){
		cache.remove(name)
		if (upd) dump()
	}

	def <T> T get(String name) { (T) cache[name] }
	def set(String name, value, boolean upd = true) { cache[name] = value; if (upd) dump() }

	def propertyMissing(String name){ get(name) }
	def propertyMissing(String name, value){ set(name, value) }
}

class Modifier<T> {
	T value

	Modifier(T val) { value = val }

	def propertyMissing(String name){ value[name] }
	def propertyMissing(String name, val){ modifyProperty(name, val) }
	def methodMissing(String name, arguments){ value.invokeMethod(name, arguments) }

	def modifyProperty(String name, val){
		def x = value[name]
		if (!x)
			value[name] = val
		else if (x instanceof Map)
			if (val instanceof Map<String, Object>) {
				Modifier m = new Modifier(value[name])
				for (e in val)
					m.modifyProperty(e.key, e.value)
				value[name] = m.value
			} else value[name] = val
		else if (x instanceof Collection)
			value[name] = ((Collection) x) + val
		else value[name] = val
	}

	T modify(@DelegatesTo(Modifier) Closure c){
		c = (Closure) c.clone()
		c.delegate = this
		c()
		value
	}

	def modify(String pname, @DelegatesTo(Modifier) Closure c){
		Modifier m = new Modifier(value[pname])
		c = (Closure) c.clone()
		c.delegate = this
		c()
		value[pname] = m.value
		m.value
	}

	T modify(Map<String, Object> c){
		for (e in c)
			modifyProperty(e.key, e.value)
		value
	}

	def modify(Map<String, Object> c, String pname){
		Modifier m = new Modifier(value[pname])
		for (e in c)
			m.modifyProperty(e.key, e.value)
		value[pname] = m.value
		m.value
	}
}
