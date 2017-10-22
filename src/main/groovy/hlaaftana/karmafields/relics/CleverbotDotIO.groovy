package hlaaftana.karmafields.relics

import com.mashape.unirest.http.Unirest
import groovy.transform.InheritConstructors

/// A wrapper for https://cleverbot.io/
/// API documentation: https://docs.cleverbot.io/docs/querying-cleverbot
class CleverbotDotIO {
	String baseUrl = 'https://cleverbot.io/1.0/'
	String user
	String key
	String nick

	CleverbotDotIO(String u, String k, String n = null){
		user = u
		key = k
		nick = n
	}

	def startSession(){
		nick = request('post', 'create', [:]).nick
	}

	String ask(text){
		if (!nick) startSession()
		request('post', 'ask', [nick: nick, text: text]).response
	}

	Map request(String method, String path, Map body = null){
		Map response
		if (body) {
			if (!user || !key)
				throw new IllegalArgumentException('User or key not set. ' +
						'Go to https://cleverbot.io/keys to get your user and key')
			Map a = [
				user: user,
				key: key
			]
			if (nick) a.nick = nick
			a << body
			response = (Map) JSONUtil.parse((String)
				Unirest."$method"(baseUrl + path)
					.fields(a)
					.asString().body)
		}
		else
			response = (Map) JSONUtil.parse((String)
				Unirest."$method"(baseUrl + path)
					.asString().body)
		if (response.status != 'success')
			throw new APIException("Something messed up: $response.status")
		else response
	}
}

@InheritConstructors
class APIException extends Exception {}