package hlaaftana.kf.registers

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import hlaaftana.kf.CommandRegister
import hlaaftana.kf.KarmaFields
import hlaaftana.discordg.util.CasingType
import hlaaftana.discordg.util.JSONSimpleHTTP
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.kf.Util
import hlaaftana.kismet.parser.StringEscaper

@CompileStatic
class QuickCommands extends CommandRegister {
	{ group = 'Quick' }

	@CompileStatic
	static class LongmanResult {
		String headword
		String partOfSpeech
		List<String> examples = []
		List<String> definitions = []
		List<Pronunciation> pronunciations = []
	}

	@CompileStatic
	static class Pronunciation { String lang, ipa }

	def register(){
		command('cleverbot',
			id: '11',
			description: 'Talks to Cleverbot (cleverbot.io).',
			usages: [
				' (text)': 'Talks to Cleverbot.'
			]){
			respond(KarmaFields.cleverbot.ask(arguments))
		}

		command(['word',
			~/word<(\d+)>/],
			id: '12',
			description: 'Looks up a word in the Longman Contemporary English Dictionary.',
			usages: [
				' (word)': 'Looks up the given word.',
				'<(n)> (word)': 'Looks up the nth entry for the word.'
			]){
			final request = (Map<String, Object>) JSONSimpleHTTP.get(
					'http://api.pearson.com/v2/dictionaries/ldoce5/entries?headword=' +
							URLEncoder.encode(arguments, 'UTF-8'))
			final resultNumber = captures ? Math.min(Math.max((int) request.offset + (int) request.count - 1, 0),
					Math.max((int) captures[0].toInteger() - 1, 0)) : 0
			final result = ((List<Map<String, Object>>) (request.results ?: []))[resultNumber]
			if (null == result) return respond('no entry found')
			final r = new LongmanResult()
			r.headword = (String) result.headword
			r.partOfSpeech = (String) result.part_of_speech
			if (result.senses) {
				final s = ((List<Map<String, Object>>) result.senses)[0]
				if (s.definition) {
					for (a in s.definition) r.definitions.add((String) a)
				}
				if (s.examples) {
					for (e in s.examples)
						r.examples.add(((Map<String, String>) e).text)
				}
			}
			if (result.pronunciations) {
				for (p in result.pronunciations) {
					final pr = (Map<String, String>) p
					r.pronunciations.add(new Pronunciation(lang: pr.lang, ipa: pr.ipa))
				}
			}
			final word = r.headword
			final pos = r.partOfSpeech ? " ($r.partOfSpeech)" : ''
			final de1 = r.definitions.join('\n')
			final de = de1 ? '\n' + de1 : de1
	        final ex1 = r.examples.collect(Util.&doubleQuote).join('\n')
			final ex = ex1 ? '\n' + ex1 : ex1
			final ipa = r.pronunciations.collect { it.lang ? "$it.lang: /$it.ipa/" :
				"/$it.ipa/" }.join(", ")
			final ipas = ipa ? " [$ipa]" : ipa
			respond("$word$pos:$ipas$de$ex")
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
			if (file) sendFile('', new ByteArrayInputStream(text.getBytes('UTF-8')), "$alias-${message.id}.txt")
			else respond(StringEscaper.escape(text))
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
			if (file) sendFile('', new ByteArrayInputStream(text.getBytes('UTF-8')), "$alias-${message.id}.txt")
			else respond(StringEscaper.escape(text))
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
					"$alias-${message.id}.txt")
			else respond(text)
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
			if (file) sendFile('', new ByteArrayInputStream(text.getBytes('UTF-8')), "$alias-${message.id}.txt")
			else sendMessage(MiscUtil.block(text, 'json'))
		}

		command(['convertcase',
			~/convertcase<(\w+)(?:\s*,\s*|\s+)(\w+)>/],
			id: '17',
			description: 'Converts text to a given case type. Case types are: ' +
				CasingType.declaredFields.findAll { it.type == CasingType }*.name*.toLowerCase().join(', '),
			usages: [
				'<casefrom[,] caseto> ...': 'Converts the text to caseto assuming the text is casefrom.',
				'<...> (text)': 'Converts the text.',
				'<...> (file)': 'Converts the text in the file and uploads a file.'
			]){
			if (captures?.size() != 2) return respond('Invalid cases. Case types: ' +
					CasingType.declaredFields.findAll { it.type == CasingType }*.name*.toLowerCase().join(', '),)
			CasingType from = (CasingType) CasingType[captures[0]], to = (CasingType) CasingType[captures[1]]
			if (null == from || null == to) return respond('Invalid cases.')
			def text = null != message.attachment ? message.attachment.url.toURL().newInputStream().text : arguments
			text = from.to(to, text)
			if (text == null) return respond('Invalid cases.')
			boolean file = text.size() > 1000 || null != message.attachment
			if (file) sendFile('', new ByteArrayInputStream(text.getBytes('UTF-8')),
					"convertcase-$from-$to-${message.id}.txt")
			else respond(text)
		}

		command('say',
			id: '18',
			description: 'Repeats text.',
			usages: [
				' (text)': 'Guess.'
			]) { respond(arguments) }

		command('ping',
			id: '19',
			description: 'Testing response times.',
			usages: [
				'': 'Starts the response time difference sequence.'
			]) {
			def x = System.currentTimeMillis()
			def text = new StringBuilder('Ha hah.aa. ah.a.')
			def m = respond(text.toString())
			def y = System.currentTimeMillis()
			def b = y - x
			text.append "\nSent after $b ms."
			m.edit(KarmaFields.format(text.toString()))
			def z = System.currentTimeMillis()
			def c = z - y
			text.append "\nEdited after $c ms."
			m.edit(KarmaFields.format(text.toString()))
		}
	}
}
