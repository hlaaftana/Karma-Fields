package hlaaftana.karmafields.registers

import groovy.json.JsonOutput
import hlaaftana.discordg.util.CasingType
import hlaaftana.discordg.util.JSONSimpleHTTP
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.karmafields.CommandRegister;
import hlaaftana.karmafields.KarmaFields

import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage

class CookieCutterCommands extends CommandRegister {
	{ group = 'Cookie-cutter' }

	def register(){
		command('cleverbot',
			id: '11',
			description: 'Talks to Cleverbot (cleverbot.io).',
			usages: [
				' (text)': 'Talks to Cleverbot.'
			]){
			formatted(KarmaFields.cleverbot.ask(args))
		}

		command(['word',
			~/word<(\d+)>/],
			id: '12',
			description: 'Looks up a word in the Longman Contemporary English Dictionary.',
			usages: [
				' (word)': 'Looks up the given word.',
				'<(n)> (word)': 'Looks up the nth entry for the word.'
			]){
			def x = JSONSimpleHTTP.get('http://api.pearson.com/v2/dictionaries/' +
				'ldoce5/entries?headword=' + URLEncoder.encode(args))
			def n = captures[0] ? Math.min(Math.max(x.offset + x.count - 1, 0),
				Math.max(captures[0].toInteger() - 1, 0)) : 0
			def r = x.results[n]
			if (!r){
				formatted('No entry found.')
				return
			}
			def (word, pos, definition, example) = [r.headword,
				r.part_of_speech ? ' (' + r.part_of_speech + ')' : '',
				r.senses[0].definition.join('; '),
				r.senses[0].examples*.text*.surround('"')?.join('\r') ?: '']
			def ipas = r.pronunciations.collect { it.lang ? "$it.lang: /$it.ipa/" :
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
			def text = json.attachments ? message.attachment.newInputStream().text : args
			text = URLEncoder.encode(text)
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes('UTF-8'), "$usedAlias-${json.id}.txt")
			else formatted(text.surround('"'))
		}

		command('urldecode',
			id: '14',
			description: 'Reverts a string from URL parameters to a normal one.',
			usages: [
				' (text)': 'Converts the text.',
				' (file)': 'Converts the text in the file and uploads a file.'
			]){
			def text = json.attachments ? message.attachment.newInputStream().text : args
			text = URLDecoder.decode(text)
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes('UTF-8'), "$usedAlias-${json.id}.txt")
			else formatted(text.surround('"'))
		}

		command(['encodejson', 'jsonize', 'jsonify'],
			id: '15',
			description: 'Converts a string to JSON, filling in \\u, \\r, \\n, \\t and whatnot.',
			usages: [
				' (text)': 'Converts the text.',
				' (file)': 'Converts the text in the file and uploads a file.'
			]){
			def text = json.attachments ? message.attachment.newInputStream().text : args
			text = JSONUtil.json(text)
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes('UTF-8'), "$usedAlias-${json.id}.txt")
			else formatted(text)
		}

		command(['prettyjson', 'ppjson'],
			id: '16',
			description: 'Pretty prints JSON text.',
			usages: [
				' (text)': 'Converts the text.',
				' (file)': 'Converts the text in the file and uploads a file.'
			]){
			def text = json.attachments ? message.attachment.newInputStream().text : args
			text = JsonOutput.prettyPrint(text)
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes('UTF-8'), "$usedAlias-${json.id}.txt")
			else sendMessage(text.block('json'))
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
			if (captures.size() != 2){ formatted('Invalid cases. Case types: ' +
				MiscUtil.caseConverters.keySet().join(', ')); return }
			def (from, to) = captures.collect { CasingType.defaultCases[it] }
			if (!(from && to)){ formatted('Invalid cases.'); return }
			def text = json.attachments ? message.attachment.newInputStream().text : args
			text = from.convert(text, to)
			if (text == null){ formatted('Invalid cases.'); return }
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes('UTF-8'), "convertcase-$from-$to-${json.id}.txt")
			else formatted(text)
		}

		command('say',
			id: '18',
			description: 'Repeats text.',
			usages: [
				' (text)': 'Guess.'
			]){ formatted(args) }

		command('ping',
			id: '19',
			description: 'Testing response times.',
			usages: [
				'': 'Starts the response time difference sequence.'
			]){
			def x = System.currentTimeMillis()
			def a = x - timeReceived
			def text = "Started after $a ms."
			def m = formatted(text)
			def y = System.currentTimeMillis()
			def b = y - x
			text += "\nSent after $b ms."
			m.edit(('> ' + text).replace('\n', '\n> ').block('accesslog'))
			def z = System.currentTimeMillis()
			def c = z - y
			text += "\nEdited after $c ms."
			m.edit(('> ' + text).replace('\n', '\n> ').block('accesslog'))
		}
	}
}
