package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.objects.DiscordObject
import hlaaftana.discordg.objects.User
import hlaaftana.discordg.util.bot.CommandBot
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

		bot.command("markov",
			group: "Entertainment",
			description: "Generates a sentence based off of order of words from previous messages.",
			usages: [
				"": "A sentence from your messages.",
				" (@mention or id)": "A sentence from the supplied user's messages.",
				" name (username)": "A sentence from the user found from the given username.",
				" unique (username#discrim)": "A sentence form the user found from the given username and discriminator combination.",
				" random": "A sentence from a random user's messages."
			],
			examples: [
				"",
				" @hlaaf#7436",
				" random",
				" name hlaaf",
				" unique hlaaf#7436"
			],
			batchable: true){
			DiscordObject id
			String input = args.trim()
			if (!message.mentions.empty){
				id = message.mentions[0]
			}else if (input){
				if (input ==~ /<@(\d+)>/){
					id = DiscordObject.forId((input =~ /<@(\d+)>/)[0][1])
				}else if (input ==~ /\d+/){
					id = DiscordObject.forId((input =~ /\d+/)[0])
				}else if (input == "random"){
					id = DiscordObject.forId "random"
				}else if (input in ["everyone", "@everyone"]){
					decorate("I've removed the everyone option because it takes years to get all the messages.")
					return
				}else if (input.tokenize()[0] == "name"){
					String fullName = input.substring("name".size()).trim()
					id = message.server.members.find { it.name == fullName }
				}else if (input.tokenize()[0] == "unique"){
					String full = input.substring("unique".size()).trim()
					id = client.members.groupBy { it.nameAndDiscrim }[full][0]
				}
			}else{
				id = message.author
			}
			if (id == null){
				decorate("Invalid ID.")
				return
			}
			List<List<String>> words
			if (id.id == "random"){
				words = (new File("markovs/").listFiles() as List).sample().readLines()*.tokenize().collect { it as List }
			}else{
				File file = new File("markovs/${id.id}.txt")
				if (!file.exists()){
					decorate("Logs for user ${Util.formatFull(id)} doesn't exist.")
					return
				}
				words = file.readLines()*.tokenize().collect { it as List }
			}
			boolean stopped
			int iterations
			List sentence = []
			while (!stopped){
				if (++iterations > 2000){
					decorate("Too many iterations. Markov for " +
						(id instanceof User ? Util.formatFull(id) : "$id") +
						" took too long.")
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
			decorate("Markov for " +
				(id instanceof User ? Util.formatFull(id) : "$id") + ":\n" +
				sentence.join(" ").replace("`", ""))
		}

		bot.command(["putlocker", "pl"],
			group: "Entertainment",
			description: "Searches putlocker.is for movies and TV shows.",
			usages: [
				" (query)": "Searches with given query."
			]){
			def elem = Jsoup.connect("http://putlocker.is/search/search.php?q=" +
				URLEncoder.encode(args))
				.userAgent("Mozilla/5.0 (Windows NT 10.0; WOW64; rv:48.0) " +
					"Gecko/20100101 Firefox/48.0")
				.referrer("http://putlocker.is/")
				.get()
				.select(".content-box")
				.select("table")[1]
				.select("a")[0]
			decorate(elem.attr("title") + "\n" + elem.attr("href"))
		}
	}
}
