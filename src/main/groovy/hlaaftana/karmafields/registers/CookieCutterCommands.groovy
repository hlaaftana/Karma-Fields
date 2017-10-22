package hlaaftana.karmafields.registers

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import hlaaftana.karmafields.relics.CasingType
import hlaaftana.karmafields.relics.JSONSimpleHTTP
import hlaaftana.karmafields.relics.JSONUtil
import hlaaftana.karmafields.relics.MiscUtil

import hlaaftana.karmafields.CommandRegister
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.relics.StringEscaper

@CompileStatic
class CookieCutterCommands extends CommandRegister {
	{ group = 'Cookie-cutter' }

	def register(){
		command('cleverbot',
			id: '11',
			description: 'Talks to Cleverbot (cleverbot.io).',
			usages: [
				' (text)': 'Talks to Cleverbot.'
			]){
			formatted(KarmaFields.cleverbot.ask(arguments))
		}

		command(['word',
			~/word<(\d+)>/],
			id: '12',
			description: 'Looks up a word in the Longman Contemporary English Dictionary.',
			usages: [
				' (word)': 'Looks up the given word.',
				'<(n)> (word)': 'Looks up the nth entry for the word.'
			]){
			Map x = (Map<String, Object>) JSONSimpleHTTP.get('http://api.pearson.com/v2/dictionaries/' +
				'ldoce5/entries?headword=' + URLEncoder.encode(arguments, 'UTF-8'))
			def n = captures[0] ? Math.min(Math.max((int) x.offset + (int) x.count - 1, 0),
				Math.max((int) captures[0].toInteger() - 1, 0)) : 0
			Map r = (Map) ((List) x.results)[n]
			if (!r) return formatted('No entry found.')
			def (word, pos, definition, example) = [r.headword,
				r.part_of_speech ? ' (' + r.part_of_speech.toString() + ')' : '',
	            ((List) ((List<Map>) r.senses)[0].definition).join('; '),
	            ((List<Map>) ((List<Map>) r.senses)[0].examples).collect { StringEscaper.escapeSoda((String) it.text) }
                    ?.join('\r') ?: '']
			def ipas = ((List<Map>) r.pronunciations).collect { it.lang ? "$it.lang: /$it.ipa/" :
				"/$it.ipa/" }.join(", ")
			if (ipas) ipas = " [$ipas]"
			formatted("$word$pos$ipas:\r$definition\r$example")
		}

		command('urlencode',
			id: '13',
			description: 'Appropriates a string for URL parameters.',
			usages: [
				' (text)': 'Converts the text.',
				' (file)': 'Converts the text in the file and uploads a file.'
			]){
			def text = message.attachments ? message.attachments[0].url.toURL().newInputStream().text : arguments
			text = URLEncoder.encode(text, 'UTF-8')
			boolean file = text.size() > 1000 || message.attachments
			if (file) sendFile('', new ByteArrayInputStream(text.getBytes('UTF-8')), "$alias-${message.stringID}.txt")
			else formatted(StringEscaper.escapeSoda(text))
		}

		command('urldecode',
			id: '14',
			description: 'Reverts a string from URL parameters to a normal one.',
			usages: [
				' (text)': 'Converts the text.',
				' (file)': 'Converts the text in the file and uploads a file.'
			]){
			def text = message.attachments ? message.attachments[0].url.toURL().newInputStream().text : arguments
			text = URLDecoder.decode(text, 'UTF-8')
			boolean file = text.size() > 1000 || message.attachments
			if (file) sendFile('', new ByteArrayInputStream(text.getBytes('UTF-8')), "$alias-${message.stringID}.txt")
			else formatted(StringEscaper.escapeSoda(text))
		}

		command(['encodejson', 'jsonize', 'jsonify'],
			id: '15',
			description: 'Converts a string to JSON, filling in \\u, \\r, \\n, \\t and whatnot.',
			usages: [
				' (text)': 'Converts the text.',
				' (file)': 'Converts the text in the file and uploads a file.'
			]){
			def text = message.attachments ? message.attachments[0].url.toURL().newInputStream().text : arguments
			text = JSONUtil.json(text)
			boolean file = text.size() > 1000 || message.attachments
			if (file) sendFile('', new ByteArrayInputStream(text.getBytes('UTF-8')),
					"$alias-${message.stringID}.txt")
			else formatted(text)
		}

		command(['prettyjson', 'ppjson'],
			id: '16',
			description: 'Pretty prints JSON text.',
			usages: [
				' (text)': 'Converts the text.',
				' (file)': 'Converts the text in the file and uploads a file.'
			]){
			def text = message.attachments ? message.attachments[0].url.toURL().newInputStream().text : arguments
			text = JsonOutput.prettyPrint(text)
			boolean file = text.size() > 1000 || message.attachments
			if (file) sendFile('', new ByteArrayInputStream(text.getBytes('UTF-8')), "$alias-${message.stringID}.txt")
			else sendMessage(MiscUtil.block(text, 'json'))
		}

		command(['convertcase',
			~/convertcase<(\w+)(?:\s*,\s*|\s+)(\w+)>/],
			id: '17',
			description: 'Converts text to a given case type. Case types are: ' +
				CasingType.defaultCases.keySet().join(', '),
			usages: [
				'<casefrom[,] caseto> ...': 'Converts the text to caseto assuming the text is casefrom.',
				'<...> (text)': 'Converts the text.',
				'<...> (file)': 'Converts the text in the file and uploads a file.'
			]){
			if (captures.size() != 2) return formatted('Invalid cases. Case types: ' +
				CasingType.defaultCases.keySet().join(', '))
			CasingType from = CasingType.defaultCases[captures[0]], to = CasingType.defaultCases[captures[1]]
			if (null == from || null == to) return formatted('Invalid cases.')
			def text = message.attachments ? message.attachments[0].url.toURL().newInputStream().text : arguments
			text = from.convert(text, to)
			if (text == null) return formatted('Invalid cases.')
			boolean file = text.size() > 1000 || message.attachments
			if (file) sendFile('', new ByteArrayInputStream(text.getBytes('UTF-8')),
					"convertcase-$from-$to-${message.stringID}.txt")
			else formatted(text)
		}

		command('say',
			id: '18',
			description: 'Repeats text.',
			usages: [
				' (text)': 'Guess.'
			]){ formatted(arguments) }

		command('ping',
			id: '19',
			description: 'Testing response times.',
			usages: [
				'': 'Starts the response time difference sequence.'
			]){
			def x = System.currentTimeMillis()
			def text = 'Man i love dicsord4j.'
			def m = formatted(text)
			def y = System.currentTimeMillis()
			def b = y - x
			text += "\nSent after $b ms."
			m.edit(MiscUtil.block(KarmaFields.format(text), 'accesslog'))
			def z = System.currentTimeMillis()
			def c = z - y
			text += "\nEdited after $c ms."
			m.edit(MiscUtil.block(KarmaFields.format(text), 'accesslog'))
		}
	}
}
