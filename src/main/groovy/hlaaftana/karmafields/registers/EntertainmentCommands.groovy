package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.objects.DiscordObject
import hlaaftana.discordg.objects.User
import hlaaftana.discordg.util.bot.CommandBot
import static hlaaftana.discordg.util.WhatIs.whatis
import hlaaftana.karmafields.Arguments
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.Jsoup

class EntertainmentCommands {
	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = kf.client

		bot.command('markov',
			group: 'Entertainment',
			description: 'Generates a sentence based off of order of words from text.',
			usages: [
				'': 'A sentence from your messages.',
				' (@mention or id or name)': 'A sentence from the supplied user\'s messages.',
				' unique (username#discrim)': 'A sentence form the user found from the given username and discriminator combination.',
				' random': 'A sentence from a random user\'s messages.',
				' file (file)': 'A sentence from the given file.'
			],
			examples: [
				'',
				' @hlaaf#7436',
				' hlaaf',
				' random',
				' unique hlaaf#7436'
			],
			batchable: true){
			DiscordObject id
			Arguments a = new Arguments(args)
			String input = a.next()
			boolean ret = false
			if (!message.mentions.empty)
				id = message.mentions[0]
			else if (input)
			whatis(input){
				when(~/<@(\d+)>/){ id = (server ?: client).member(input.find(/\d+/))?.toUser() ?:
					new DiscordObject(null, [id: input.find(/\d+/), name: 'unknown user']) }
				when('random'){ id = DiscordObject.forId 'random' }
				when('unique'){ id = (server ?: client).members.groupBy {
					it.nameAndDiscrim }[a.rest][0].toUser() }
				when('file'){
					def text, fn
					if (json.attachments){ 
						text = message.attachment.inputStream.text
						fn = message.attachment.name
					}else if (a.rest){
						try{
							URL url = new URL(a.rest)
							text = url.text
							fn = url.file
						}catch (ex){
							formatted 'Invalid URL.'
							ret = true
						}
					}
					id = new DiscordObject(null, [name: fn, id: 'file', text: text])
				}
				
				otherwise {
					def u = (server ?: client).member(args)?.toUser()
					id = u ?: (DiscordObject.isId(input) ?
						new DiscordObject(null, [id: input.find(/\d+/), 
							name: 'unknown user']) : null)
				}
			}
			else id = author
			
			if (ret) return
			
			if (id == null){
				formatted('Invalid user.')
				return
			}
			List<List<String>> words
			if (id.id == 'random'){
				File file = new File((new File('markovs/').list() as List).sample())
				id = client.user(file.name.substring(file.name.indexOf((char) '.')))
				words = file.readLines()*.tokenize().collect { it as List }
			}else if (id.id == 'file'){
				words = id.object.text.readLines()*.tokenize().collect { it as List }
			}else{
				File file = new File("markovs/${id.id}.txt")
				if (!file.exists()){
					formatted("Logs for user ${id.inspect()} doesn't exist.")
					return
				}
				words = file.readLines()*.tokenize().collect { it as List }
			}
			boolean stopped
			int iterations
			List sentence = []
			while (!stopped){
				if (++iterations > 2000){
					formatted('Too many iterations. Markov for ' +
						(id instanceof User ? id.inspect() : id.toString()) +
						' took too long.')
					return
				}
				if (!sentence.empty){
					List lists = words.findAll { it.contains(sentence.last()) }
					if (lists.empty){
						sentence += words.flatten().sample()
						continue
					}
					List nextWords = []
					lists.each { List l ->
						for (int i; i < l.size(); i++){
							if (l[i] == sentence.last()){
								if ((i + 1) == l.size()) break
								nextWords += l[i + 1]
							}
						}
					}
					if (nextWords.empty){
						stopped = true
						break
					}else{
						String producedWord = nextWords.sample()
						sentence += producedWord
						continue
					}
				}else{
					sentence += words.flatten().sample()
					continue
				}
			}
			def m = sentence.join(' ').replace('`', '')
				.replaceAll(/<@!?(\d+)>/){ _, i ->
					'@' + (client.user(i)?.name ?: 'unknown user') }
				.replaceAll(/<#(\d+)>/){ _, i ->
					'#' + (client.channel(i)?.name ?: 'unknown channel') }
				.replaceAll(/<@&(\d+)>/){ _, i ->
					'@' + (client.role(i)?.name ?: 'unknown role') }
			formatted('Markov for ' + id.inspect() + ':\n' + m)
		}
	}
}
