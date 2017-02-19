package hlaaftana.karmafields

import hlaaftana.discordg.util.JSONUtil

class DataFile {
	File file
	Map cache
	DataFile(x, load = true){ file = x as File; if (load) refresh() }

	def refresh(){ cache = JSONUtil.parse(file) }
	def dump(){ if (cache) JSONUtil.dump(file, cache) }

	def modifier(){
		new Modifier(value: cache)
	}

	def modify(upd = true, Closure c){
		cache = modifier().modify(c)
		if (upd) dump()
	}

	def modify(upd = true, Map c){
		cache = modifier().modify(c)
		if (upd) dump()
	}

	def remove(String name, boolean upd = true){
		cache.remove(name)
		if (upd) dump()
	}

	def propertyMissing(String name){
		cache[name]
	}

	def propertyMissing(String name, value, boolean upd = true){
		cache[name] = value
		if (upd) dump()
	}
}

class Modifier {
	def value

	def propertyMissing(String name){ value[name] }
	def propertyMissing(String name, val){ modifyProperty(name, val) }
	def methodMissing(String name, args){ value.invokeMethod(name, args) }

	def modifyProperty(name, val){
		def x = value[name]
		if (!x){
			value[name] = val
		}else if (x instanceof Map){
			if (val instanceof Map){
				def m = new Modifier(value: value[name])
				val.each { k, v ->
					m.modifyProperty(k, v)
				}
				value[name] = m.value
			}else value[name] = val
		}else if (x instanceof Collection){
			value[name] += val
		}else value[name] = val
	}

	def modify(Closure c){
		c.clone().with { it.delegate = this; it() }
		value
	}

	def modify(pname, Closure c){
		def m = new Modifier(value: value[pname])
		c.clone().with { it.delegate = m; it() }
		value[pname] = m.value
		m.value
	}

	def modify(Map c){
		c.each { k, v ->
			modifyProperty(k, v)
		}
		value
	}

	def modify(Map c, pname){
		def m = new Modifier(value: value[pname])
		c.each { k, v ->
			m.modifyProperty(k, v)
		}
		value[pname] = m.value
		m.value
	}
}
