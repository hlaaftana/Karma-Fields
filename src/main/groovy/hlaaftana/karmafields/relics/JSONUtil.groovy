package hlaaftana.karmafields.relics

import com.mashape.unirest.http.Unirest
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import groovy.json.internal.LazyMap
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

/**
 * Methods as utilities for JSON using the groovy.json package.
 * @author Hlaaftana
 */
class JSONUtil {
	static slurper = new JsonSlurperClassic()

	static parse(String string){
		slurper.parseText(string)
	}

	static parse(File file, charset = 'UTF-8'){ parse(file.getText(charset)) }

	static String json(thing){
		JsonOutput.toJson(thing instanceof JSONable ? thing.json() : thing)
	}

	static String pjson(thing){ JsonOutput.prettyPrint(json(thing)) }

	static File dump(String filename, thing, charset = 'UTF-8'){ dump(new File(filename), thing, charset) }

	static File dump(File file, thing, charset = 'UTF-8'){
		if (!file.exists()) file.createNewFile()
		file.write(pjson(thing), charset)
		file
	}

	static File modify(String filename, Map<String, Object> newData){ modify(new File(filename), newData) }

	static File modify(File file, Map<String, Object> newData){
		if (newData instanceof LazyMap) newData = new HashMap<>(newData)
		if (!file.exists()) return dump(file, newData)
		Map<String, Object> oldData = (Map<String, Object>) parse(file)
		if (oldData instanceof LazyMap) oldData = new HashMap<>(oldData)
		dump(file, modifyMaps(oldData, newData))
	}
	
	static Map<String, Object> modifyMaps(Map<String, Object> x, Map<String, Object> y){
		Map<String, Object> a = new HashMap<>(x)
		for (e in y) {
			String k = e.key
			def v = e.value
			if (a.containsKey(k))
				if (a[k] instanceof Collection)
					if (v instanceof Collection)
						((Collection) a[k]).addAll(v)
					else
						((Collection) a[k]).add(v)
				else if (a[k] instanceof Map)
					if (v instanceof Map)
						a[k] = (Map) a[k] + modifyMaps((Map<String, Object>) a[k], v)
					else a[k] = v
				else a[k] = v
			else a[k] = v
		}
		a
	}
}

interface JSONable {
	// this is NOT supposed to return raw JSON, just a Groovy representation of an object
	// that will regularly be translated to JSON
	def json()
}

@CompileStatic
class JSONPath {
	List<Expression> parsedExpressions = [new Expression('', Expression.AccessType.OBJECT)]

	JSONPath(String aaa){
		aaa.toList().each {
			if (parsedExpressions.last().lastChar() == '\\'){
				parsedExpressions.last().removeLastChar()
				parsedExpressions.last() << it
			}else if (it == '.'){
				parsedExpressions +=
					Expression.AccessType.OBJECT.express('')
			}else if (it == '['){
				parsedExpressions +=
					Expression.AccessType.ARRAY.express('')
			}else if (it != ']'){
				parsedExpressions.last() << it
			}
		}
	}

	static JSONPath parse(String aaa){ new JSONPath(aaa) }

	def parseAndApply(String json){ apply(JSONUtil.parse(json)) }
	def parseAndApply(File json){ apply(JSONUtil.parse(json)) }

	def apply(thing){
		def newValue = thing
		for (ex in parsedExpressions)
			newValue = ex.act(newValue)
		newValue
	}

	static class Expression {
		String raw
		AccessType type
		Expression(String ahh, AccessType typ){ raw = ahh; type = typ }

		Expression leftShift(other){
			raw += other
			this
		}

		Expression plus(other){
			new Expression(raw, type) << other
		}

		Expression removeLastChar(){
			raw = raw[0..-1]
			this
		}

		String lastChar(){
			raw.toList() ? raw.toList().last() : ''
		}

		@CompileDynamic
		Closure getAction(){
			{ thing -> raw == '' ? thing : raw == '*' ? thing.collect() : thing[raw.asType(type.accessor)] }
		}

		def act(thing){
			action.call(thing)
		}

		String toString(){ raw }

		static enum AccessType {
			OBJECT(String),
			ARRAY(int)

			Class accessor
			AccessType(Class ass){ accessor = ass }

			Expression express(ahde){
				new Expression(ahde.toString(), this)
			}
		}
	}
}

class JSONSimpleHTTP {
	static String userAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:57.0) Gecko/20100101 Firefox/57.0'
	static get(String url){
		JSONUtil.parse(Unirest.get(url).header('User-Agent', userAgent).asString().getBody())
	}

	static delete(String url){
		JSONUtil.parse(Unirest.delete(url).header('User-Agent', userAgent).asString().getBody())
	}

	static post(String url, Map body){
		JSONUtil.parse(Unirest.post(url).header('User-Agent', userAgent).body(JSONUtil.json(body)).asString().getBody())
	}

	static patch(String url, Map body){
		JSONUtil.parse(Unirest.patch(url).header('User-Agent', userAgent).body(JSONUtil.json(body)).asString().getBody())
	}

	static put(String url, Map body){
		JSONUtil.parse(Unirest.put(url).header('User-Agent', userAgent).body(JSONUtil.json(body)).asString().getBody())
	}
}
