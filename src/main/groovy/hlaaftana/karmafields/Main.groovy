package hlaaftana.karmafields

import hlaaftana.discordg.*

import static java.lang.System.currentTimeMillis as now

import java.awt.*
import java.util.List
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.time.*
import java.text.SimpleDateFormat
import java.util.concurrent.*

import javax.script.ScriptEngineManager
import javax.swing.JOptionPane

import com.mashape.unirest.http.Unirest

import groovy.json.*
import static groovyx.gpars.GParsPool.withPool
import hlaaftana.discordg.objects.*
import hlaaftana.discordg.exceptions.*
import hlaaftana.discordg.util.*
import hlaaftana.discordg.util.bot.*
import static hlaaftana.discordg.util.WhatIs.whatis
import java.nio.ByteBuffer

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import static hlaaftana.karmafields.Arguments.run as argp
import static hlaaftana.karmafields.PermissionParser.from as parsePerms

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import org.codehaus.groovy.syntax.Numbers

import org.jruby.embed.ScriptingContainer
import org.jruby.javasupport.JavaEmbedUtils
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.Jsoup

MiscUtil.registerStringMethods()
MiscUtil.registerListMethods()

ImportCustomizer imports = new ImportCustomizer()
imports.addStarImports(
	"hlaaftana.discordg",
	"hlaaftana.discordg.dsl",
	"hlaaftana.discordg.objects",
	"hlaaftana.discordg.oauth",
	"hlaaftana.discordg.status",
	"hlaaftana.discordg.request",
	"hlaaftana.discordg.util",
	"hlaaftana.discordg.util.bot",
	"hlaaftana.pg",
	"hlaaftana.pg.portal",
	"hlaaftana.pg.profiles",
	"hlaaftana.levelchateau",
	"hlaaftana.levelchateau.pages",
	"hlaaftana.karmafields",
	"java.awt")
imports.addImports(
	"java.awt.image.BufferedImage",
	"javax.imageio.ImageIO",
	"java.util.List")
CompilerConfiguration cc = new CompilerConfiguration()
cc.addCompilationCustomizers(imports)

List split(String arguments, int max = 0){
	List list = [""]
	String currentQuote
	arguments.toList().each { String ch ->
		if (max && list.size() == max){
			list[list.size() - 1] += ch
		}else{
			if (currentQuote){
				if (ch == currentQuote) currentQuote = null
				else list[list.size() - 1] += ch
			}else{
				if (ch in ['"', "'"]) currentQuote = ch
				else if (Character.isSpaceChar(ch as char)) list += ""
				else list[list.size() - 1] += ch
			}
		}
	}
	list
}

Graphics2D.metaClass.drawStraightPolygon = { double length, int sides, double x, double y ->
    def g = delegate
    double angle = (180 * (sides - 2)) / sides
    def last = [x, y]
    sides.times { int i ->
        def add = [length * Math.cos(2 * i * Math.PI / sides),
			length * Math.sin(2 * i * Math.PI / sides)]
		def rounded = [(int) Math.round(last[0] + add[0]), (int) Math.round(last[1] + add[1])]
        g.drawLine((int) last[0], (int) last[1], *rounded)
        last = sides % 2 ? [last[0] + add[0], last[1] + add[1]] : rounded
    }
}

OutputStream draw(int width = 256, int height = 256, Closure closure){
	BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
	Graphics2D graphics = image.createGraphics()
	Closure ass = closure.clone()
	ass.delegate = graphics
	ass.resolveStrategy = Closure.DELEGATE_FIRST
	ass(image)
	graphics.dispose()
	ByteArrayOutputStream baos = new ByteArrayOutputStream()
	ImageIO.write(image, "png", baos)
	baos
}

dateFormat = "yyyy-MM-dd HH:mm:ss.SSS z"
def formatName(DiscordObject obj){ "\"${obj.name.replace("\"", "\\\"")}\"" }
def formatUrl(String url){ "\"$url\"" }
def formatFull(DiscordObject obj){ "\"${obj.name.replace("\"", "\\\"")}\" ($obj.id)" }
def formatLongUser(User user){ "\"$user.name\"#$user.discrim ($user.id)" }

def serverJson(server){
	File file = new File("guilds/${DiscordObject.id(server)}.json")
	if (!file.exists()) file.write("{}")
	JSONUtil.parse(file)
}

def modifyServerJson(server, aa){
	JSONUtil.modify("guilds/${DiscordObject.id(server)}.json", aa)
}

Server.metaClass {
	propertyMissing << { String name ->
		serverJson(delegate)[name]
	} << { String name, value ->
		modifyServerJson(delegate, [(name): value])
	}
}

boolean checkPerms(Command command, Message message){
	def a = (serverJson(message.server)["permissions"]
		?: [:])[command.hashCode().toString()]
}

String formatMessage(Message msg){
	LocalDateTime time = MiscUtil.dateToLDT(msg.timestamp)
	String.format("{%s|%s} [%s] <%s>: %s",
		time.toLocalDate(),
		time.toLocalTime(),
		msg.private ? "DM" :
			"$msg.server#$msg.channel",
		msg.author, msg.content)
}

static Map getCreds(){
	JSONUtil.parse(new File("creds.json"))
}

boolean botReady
boolean meReady

ExecutorService markovFileThreadPool = Executors.newFixedThreadPool(5)
boolean youCanCrash = false
long start = now()
CommandBot bot
personalBot = null
Client me = new Client(logName: "Selfbot")
Client client = new Client(logName: "|><|")
Client appStorage = new Client(token: creds["app_storage_token"])
[client, me, appStorage]*.log*.debug*.enable()
binding.me = me
binding.client = client
client.requester.ptb()
client.requester.baseUrl += "v6/"
client.addReconnector()
client.mute("81402706320699392", "119222314964353025")
me.addReconnector()
me.requester.ptb()
me.requester.baseUrl += "v6/"
me.serverTimeout *= 4
me.includedEvents = [Events.SERVER, Events.CHANNEL, Events.ROLE, Events.MEMBER,
	Events.MESSAGE]
me.excludedEvents = null
me.listener(Events.READY){
	if (!meReady){
		//client.fields["botApp"] = appStorage.requestApplication("193646883837706241")
		bot.triggers += client.mentionRegex
		meReady = true
		//personalBot = PersonalBot.run()
		println "--- Fully loaded. ---"
	}
}

client.login("|><|New"){
	appStorage.requestApplication("193646883837706241").bot.token
}
bot = new CommandBot(triggers: ["|>", "><"], client: client)
bot.loggedIn = true
client.eventThreadCount = 4

cleverbot = new CleverbotDotIO(creds["cb_user"], creds["cb_key"])
yandexdict = new YandexDictionary(key: creds["yandex_dict_key"])

client.listener(Events.READY){
	client.play("welcome to the afterworld.")
	if (!botReady){
		Thread.start { LPClient.login(creds["lp_username"], creds["lp_pass"]) }
		if (!me.token){
			me.login(creds["self_email"], creds["self_pass"])
		}
		botReady = true
	}
}

client.listener(Events.SERVER){
	server.role("|><|")?.edit(color: 0x0066c2)
	server.sendMessage(
		"> Looks like I joined. Yay. Do |>help for commands.".block("verilog"))
	File a = new File("guilds/${server.id}.json")
	a.createNewFile()
	if (a.text == ""){
		JSONUtil.dump(a, [:])
	}
}

def lastWarned = false
client.listener(Events.ROLE_UPDATE){
	if (!role.locked ||
		(!serverJson(json.guild_id).member_role &&
			!serverJson(json.guild_id).bot_role &&
			!serverJson(json.guild_id).guest_role)) return
	if (lastWarned) return
	lastWarned = true
	whatis(json.role.id){
		when(server.member_role){
			server.sendMessage(("> This server's member role ($role) is now locked for me. " +
				"Please set a new member role using |>memberrole or give me a role with a higher position.").block("verilog"))
		}

		when(server.bot_role){
			server.sendMessage(("> This server's bot role ($role) is now locked for me. " +
				"Please set a new bot role using |>botrole or give me a role with a higher position.").block("verilog"))
		}

		when(server.guest_role){
			server.sendMessage(("> This server's guest role ($role) is now locked for me. " +
				"Please set a new guest role using |>guestrole or give me a role with a higher position.").block("verilog"))
		}
	}
}

client.listener(Events.ROLE_DELETE){
	if (!serverJson(json.guild_id).member_role &&
		!serverJson(json.guild_id).bot_role &&
		!serverJson(json.guild_id).guest_role) return
	whatis(json.role_id){
		when(server.member_role){
			server.sendMessage(("> This server's member role ($role) is now deleted. " +
				"Please set a new member role using |>memberrole.").block("verilog"))
		}

		when(server.bot_role){
			server.sendMessage(("> This server's bot role ($role) is now deleted. " +
				"Please set a new bot role using |>botrole.").block("verilog"))
		}

		when(server.guest_role){
			server.sendMessage(("> This server's guest role ($role) is now deleted. " +
				"Please set a new guest role using |>guestrole.").block("verilog"))
		}
	}
}

client.listener(Events.MESSAGE){
	markovFileThreadPool.submit {
		File file = new File("markovs/${json.author.id}.txt")
		if (!file.exists()) file.createNewFile()
		file.append(message.content + "\n", "UTF-8")
	}
	if (message.private)
		client.textChannel(180735635106168832)
			.sendMessage(formatMessage(message))
}

me.listener(Events.MESSAGE){
	if (json.author.id == "98457401363025920"){
		if (message.content.toLowerCase().contains("java")){
			message.edit(message.content.replaceAll(/(?i)java/, { it + '\u2122' }))
		}
	}
}

client.listener(Events.MEMBER){
	if (server.id in ["145904657833787392", "195223058544328704", "198882877520216064"]){
		String message = """\
> A member just joined:
> Name: $member.name
> ID: $member.id
> Account creation time: $member.createTime
> To member, type |>member, to ban, type |>ban, to bot, type |>bot.""".block("verilog")
		server.defaultChannel.sendMessage(message)
		server.textChannel("bot-mod-log")?.sendMessage(message)
	}
	if (server.automember){
		try{
			member.addRole(server.role(server.member_role))
		}catch (NoPermissionException ex){
			server.sendMessage(
				"> I tried to automember ${formatFull(member)} but I seem to not have permissions to."
				.block("verilog"))
			return
		}
		server.defaultChannel.sendMessage("> Automembered ${formatFull(member)}."
			.block("verilog"))
		server.textChannel("bot-mod-log")
			?.sendMessage("> Automembered ${formatFull(member)}.".block("verilog"))
	}
}

Map groups = [
	Meta: [
		description: "Commands about the bot itself."
	],
	Messaround: [
		description: "Commands you can use when you're bored."
	],
	Administrative: [
		description: "Commands to ease your job as a staff member."
	],
	Misc: [
		description: "Uncategorized commands."
	]
]

groups.each { k, v -> groups[k].name = k }

bot.command("feedback",
	group: "Meta",
	description: "Sends me (the bot author) feedback on the bot.",
	usages: [
		" (text)": "Sends me the text as feedback."
	]){
	try{
		client.user(98457401363025920)
			.privateChannel.sendMessage("""\
			|> Feedback by ${formatLongUser(author)}:
			|$args""".stripMargin().block("verilog"))
		sendMessage("> Feedback sent.".block("verilog"))
	}catch (ex){
		sendMessage("> Could not send feedback. Sorry for the inconvience.".block("verilog"))
	}
}

bot.command(["info", "information"],
	group: "Meta",
	description: "Sends information about the bot's backend.",
	usages: [
		"": "Sends the information."
	]){
	sendMessage("""\
	|> Programming language: Groovy
	|> Author: ${formatLongUser(me)}
	|> Source code: "https://github.com/hlaaftana/Karma-Fields"
	|> Library: DiscordG ("https://github.com/hlaaftana/DiscordG") (deprecated)
	|> Memory usage: ${Runtime.runtime.totalMemory() / (2 ** 20)}/${Runtime.runtime.maxMemory() / (2 ** 20)} MB
	|> Invite: ${formatUrl(client.fields.botApp.inviteUrl(new Permissions(268435456)))}""".stripMargin().block("verilog"))
}

Map bmCallTimes = [:]
bot.command(["bm", "batchmarkov",
	~/bm<(\d+?)>/,
	~/batchmarkov<(\d+?)>/],
	group: "Messaround",
	description: "Shortcut to running the batch command for the markov command. Can only be called 5 times per 30 seconds per computers not being infinitely powerful",
	usages: [
		"<(number)>": "Calls the markov command (number) times.",
		"<(number)> (arguments)": "Calls the markov command (number) times with (arguments)."
	],
	examples: [
		"",
		"<2>",
		"<2> @hlaaf#7436"
	]){
	if (bmCallTimes[message.server?.id ?: message.author.id] >= 3){
		sendMessage("> Slow down. You've called the command a bit more than enough times. Try again in a bit.".block("verilog"))
		return
	}
	if (bmCallTimes[message.server?.id ?: message.author.id])
		bmCallTimes[message.server?.id ?: message.author.id]++
	else bmCallTimes[message.server?.id ?: message.author.id] = 1
	BigInteger time = {
		try {
			captures[0].toBigInteger() ?: 3
		}catch (ex){
			3
		}
	}()
	if (time > 5) time = 5
	Message modifiedMessage = message
	String newContent = args ? "${usedTrigger}markov $args" : "${usedTrigger}markov"
	modifiedMessage.object["content"] = newContent
	Map newData = delegate.clone()
	newData << [message: modifiedMessage]
	newData.json.content = newContent
	Command ass = bot.commands.find { it.aliases*.toString().contains "markov" }
	time.times {
		Thread.start { ass(newData) }
		null
	}
	Thread.start {
		Thread.sleep(30_000)
		bmCallTimes[message.server?.id ?: message.author.id]--
	}
}

bot.command(["batch",
	~/batch<(\w+?)>/,
	~/batch<(\w+?)\s*,\s*(\d+?)>/],
	group: "Misc",
	description: "Runs a command multiple times. Maximum of 5.",
	usages: [
		"<(command name), (number)> (command arguments)": "Calls (command name) (number) times with arguments.",
		"<(command name), (number)>": "Calls (command name) (number) times.",
		"<(command name)> (command arguments)": "Calls (command name) 3 times with (command arguments).",
		"<(command name)>": "Calls (command name) 3 times."
	],
	examples: [
		"<markov>",
		"<markov, 2>",
		"<markov> @hlaaf#7436"
	]){
	String commandName = captures[0]
	Command ass = bot.commands.find { it.aliases.findAll { !it.regex }*.toString().contains commandName }
	if (!ass){
		sendMessage("> Invalid command name. Type \"|>usages batch\" to see how this command can be used.".block("verilog"))
		return
	}
	if (!ass.info.batchable){
		sendMessage("> Unfortunately that command is not deemed safe enough to be called multiple times in a short time.".block("verilog"))
		return
	}
	BigInteger time = {
		try {
			captures[1].toBigInteger() ?: 3
		}catch (ex){
			3
		}
	}()
	if (time > 5) time = 5
	Message modifiedMessage = new Message(message.client, message.object)
	String newContent = args ? "$usedTrigger$commandName $args" : "$usedTrigger$commandName"
	modifiedMessage.object["content"] = newContent
	Map newData = it.clone()
	newData << [message: modifiedMessage]
	newData.json.content = newContent
	println newContent
	time.times {
		Thread.start { ass(newData) }
		null
	}
}

bot.command("markov",
	group: "Messaround",
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
			sendMessage("> I've removed the everyone option because it takes years to get all the messages.".block("verilog"))
		}else if (input.tokenize()[0] == "name"){
			String fullName = input.substring("name".size()).trim()
			id = message.server.members.find { it.name == fullName }
		}else if (input.tokenize()[0] == "unique"){
			String full = input.substring("unique".size()).trim()
			id = client.members.groupBy { it.nameAndDiscrim }[full][0]
		}
	}
	if (id == null){ id = author }
	List<List<String>> words
	if (id.id == "random"){
		words = (new File("markovs/").listFiles() as List).randomItem().readLines()*.tokenize().collect { it as List }
	}else{
		File file = new File("markovs/${id.id}.txt")
		if (!file.exists()){
			sendMessage("> Logs for user ${formatFull(id)} doesn't exist.".block("verilog"))
			return
		}
		words = file.readLines()*.tokenize().collect { it as List }
	}
	boolean stopped
	int iterations
	List sentence = []
	while (!stopped){
		if (++iterations > 2000){
			sendMessage("> Too many iterations. Markov for ${id instanceof User ? formatFull(id) : "$id"} took too long.".block("verilog"))
			return
		}
		if (!sentence.empty){
			List lists = words.findAll { it.contains(sentence.last()) }
			if (lists.empty){
				sentence += words.flatten().randomItem()
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
			//List nextWords = lists.collect { it[it.indexOf(sentence.last()) + 1] }
			if (nextWords.empty){
				stopped = true
				break
			}else{
				String producedWord = nextWords.randomItem()
				sentence += producedWord
				continue
			}
		}else{
			sentence += words.flatten().randomItem()
			continue
		}
	}
	sendMessage("Markov for ${id instanceof User ? formatFull(id) : "$id"}:\n> ${sentence.join(" ").replace("`", "")}\n".block("verilog"))
}

bot.command(["perms", "permissions"],
	group: "Administrative",
	hide: true,
	allowsPermissions: true){

}

bot.command("modlog",
	group: "Administrative",
	description: "Adds the given channel as a mod log.",
	usages: [
		" (#channel or id or name)": "Adds the channel to the mod log channels."
	],
	allowsPermissions: true){
	Channel channel = message.channelMentions ?
		message.channelMentions[0] :
		message.server.textChannel(args)
	if (!author.permissions["manageChannels"]){
		sendMessage("> Seems so you don't have Manage Channels so I can't let you do that.".block("verilog"))
	}else if (channel){
		if (serverJson(message.server).modlogs) message.server.modlogs << channel.id
		else message.server.modlogs = [channel.id]
		sendMessage("> Channel #\"$channel\" ($channel.id) successfully added as mod log.".block("verilog"))
	}else sendMessage("> Invalid channel. Mention the channel or supply an ID or the name of the channel.".block("verilog"))
}

bot.command("guestrole",
	group: "Administrative",
	description: "Sets or unsets the \"guest\" role for the server.",
	usages: [
		" (@rolemention or id or name)": "Sets the guest role to the specified role."
	],
	allowsPermissions: true){
	if (!author.permissions["manageRoles"]){
		sendMessage("> Unfortunately you don't seem to have Manage Roles.".block("verilog"))
		return
	}
	Role role = message.roleMentions ?
		message.roleMentions[0] :
		message.server.role(args)
	if (role?.locked)
		sendMessage("> Unfortunately that role is locked for me so I can't use it.".block("verilog"))
	else if (role?.isLockedFor(author)) sendMessage("> Unfortunately the role is locked for you so I can't account you on permissions.".block("verilog"))
	else if (role){
		message.server.guest_role = role.id
		sendMessage("> Role \"$role\" ($role.id) successfully added as guest role.".block("verilog"))
	}
	else sendMessage("> Invalid role. Mention the role or supply an ID or the name of the role.".block("verilog"))
}

bot.command("memberrole",
	group: "Administrative",
	description: "Sets or unsets the \"member\" role for the server.",
	usages: [
		" (@rolemention or id or name)": "Sets the member role to the specified role."
	],
	allowsPermissions: true){
	if (!author.permissions["manageRoles"]){
		sendMessage("> Unfortunately you don't seem to have Manage Roles.".block("verilog"))
		return
	}
	Role role = message.roleMentions ?
		message.roleMentions[0] :
		message.server.role(args)
	if (role?.locked)
		sendMessage("> Unfortunately that role is locked for me so I can't use it.".block("verilog"))
	else if (role?.isLockedFor(author)) sendMessage("> Unfortunately the role is locked for you so I can't account you on permissions.".block("verilog"))
	else if (role){
		message.server.member_role = role.id
		sendMessage("> Role \"$role\" ($role.id) successfully added as member role.".block("verilog"))
	}
	else sendMessage("> Invalid role. Mention the role or supply an ID or the name of the role.".block("verilog"))
}

bot.command("botrole",
	group: "Administrative",
	description: "Sets or unsets the \"bot\" role for the server.",
	usages: [
		" (@rolemention or id or name)": "Sets the bot role to the specified role."
	],
	allowsPermissions: true){
	if (!author.permissions["manageRoles"]){
		sendMessage("> Unfortunately you don't seem to have Manage Roles.".block("verilog"))
		return
	}
	Role role = message.roleMentions ?
		message.roleMentions[0] :
		message.server.role(args)
	if (role?.locked)
		sendMessage("> Unfortunately that role is locked for me so I can't use it.".block("verilog"))
	else if (role?.isLockedFor(author)) sendMessage("> Unfortunately the role is locked for you so I can't account you on permissions.".block("verilog"))
	else if (role){
		message.server.bot_role = role.id
		sendMessage("> Role \"$role\" ($role.id) successfully added as bot role.".block("verilog"))
	}
	else sendMessage("> Invalid role. Mention the role or supply an ID or the name of the role.".block("verilog"))
}

bot.command("automember",
	group: "Administrative",
	description: "Automatically gives new members the set member role for the server.",
	usages: [
		"": "Returns whether automembering is currently on or off.",
		" on": "Turns automembering on.",
		" off": "Turns automembering off.",
		" toggle": "Toggles automembering."
	],
	allowsPermissions: true){
	if (args ==~ /\s*/){
		sendMessage("> Automember is currently " +
			"${message.server.automember ? "on" : "off"}."
			.block("verilog"))
	}
	if (args ==~ /\s*on\s*/){
		if (!author.permissions["manageRoles"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			message.server.automember = true
			sendMessage("> Automember is now on.".block("verilog"))
		}
	}
	if (args ==~ /\s*off\s*/){
		if (!author.permissions["ban"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			message.server.automember = false
			sendMessage("> Automember is now off.".block("verilog"))
		}
	}
	if (args ==~ /\s*toggle\s*/){
		if (!author.permissions["ban"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			message.server.automember = !message.server.automember
			sendMessage("> Automember is now " +
				"${message.server.automember ? "on" : "off"}.".block("verilog"))
		}
	}
}

bot.command("autoguest",
	group: "Administrative",
	description: "Automatically gives new guests the set guest role for the server.",
	usages: [
		"": "Returns whether autoguesting is currently on or off.",
		" on": "Turns autoguesting on.",
		" off": "Turns autoguesting off.",
		" toggle": "Toggles autoguesting."
	],
	allowsPermissions: true){
	if (args ==~ /\s*/){
		sendMessage("> Autoguest is currently " +
			"${message.server.autoguest ? "on" : "off"}."
			.block("verilog"))
	}
	if (args ==~ /\s*on\s*/){
		if (!author.permissions["manageRoles"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			message.server.autoguest = true
			sendMessage("> Autoguest is now on.".block("verilog"))
		}
	}
	if (args ==~ /\s*off\s*/){
		if (!author.permissions["ban"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			message.server.autoguest = false
			sendMessage("> Autoguest is now off.".block("verilog"))
		}
	}
	if (args ==~ /\s*toggle\s*/){
		if (!author.permissions["ban"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			message.server.autoguest = !message.server.autoguest
			sendMessage("> Autoguest is now " +
				"${message.server.autoguest ? "on" : "off"}.".block("verilog"))
		}
	}
}

bot.command("autoguest",
	group: "Administrative",
	description: "Automatically gives new guests the set guest role for the server.",
	usages: [
		"": "Returns whether autoguesting is currently on or off.",
		" on": "Turns autoguesting on.",
		" off": "Turns autoguesting off.",
		" toggle": "Toggles autoguesting."
	],
	allowsPermissions: true){
	if (args ==~ /\s*/){
		sendMessage("> Autoguest is currently " +
			"${message.server.autoguest ? "on" : "off"}."
			.block("verilog"))
	}
	if (args ==~ /\s*on\s*/){
		if (!author.permissions["manageRoles"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			message.server.autoguest = true
			sendMessage("> Autoguest is now on.".block("verilog"))
		}
	}
	if (args ==~ /\s*off\s*/){
		if (!author.permissions["ban"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			message.server.autoguest = false
			sendMessage("> Autoguest is now off.".block("verilog"))
		}
	}
	if (args ==~ /\s*toggle\s*/){
		if (!author.permissions["ban"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			message.server.autoguest = !message.server.autoguest
			sendMessage("> Autoguest is now " +
				"${message.server.autoguest ? "on" : "off"}.".block("verilog"))
		}
	}
}

bot.command("guest",
	group: "Administrative",
	description: "Removes a user's member role. If a guest role is set, gives the user a guest role as well.",
	usages: [
		"": "Guests the latest user in the server.",
		" (@mentions)": "Guests every user mentioned."
	],
	allowsPermissions: true){
	if (!author.permissions["manageRoles"])
		sendMessage("> You don't have sufficient permissions.".block("verilog"))
	else if (!serverJson(message.server).member_role)
		sendMessage("> This server doesn't have a member role set.".block("verilog"))
	else try{
		def guestRole = message.server.role(serverJson(message.server).guest_role)
		def memberRole = message.server.role(message.server.member_role)
		List<Member> members
		if (message.mentions.empty){
			members = [message.server.lastMember]
		}else{
			members = message.mentions.collect { message.server.member(it) }
		}
		String output = "> The following members were guested by $author ($author.id):"
		members.each {
			it.editRoles(it.roles + guestRole - memberRole - null)
			output += "\n> $it ($it.id)"
		}
		sendMessage(output.block("verilog"))
		message.server.modlogs.collect { message.server.channel(it) }*.sendMessage(output.block("verilog"))
	}catch (NoPermissionException ex){
		sendMessage("> Failed to guest member(s). I don't seem to have permissions.".block("verilog"))
	}
}

bot.command("member",
	group: "Administrative",
	description: "Gives the set member role to a user/users. Member role is set using |>memberrole",
	usages: [
		"": "Members the latest user in the server.",
		" (@mentions)": "Members every user mentioned."
	],
	allowsPermissions: true){
	if (!author.permissions["manageRoles"])
		sendMessage("> You don't have sufficient permissions.".block("verilog"))
	else if (!serverJson(message.server).member_role)
		sendMessage("> This server doesn't have a member role set.".block("verilog"))
	else try{
		def role = message.server.role(message.server.member_role)
		List<Member> members
		if (message.mentions.empty){
			members = [message.server.lastMember]
		}else{
			members = message.mentions.collect { message.server.member(it) }
		}
		String output = "> The following members were membered by $author ($author.id):"
		members.each {
			it.addRole(role)
			output += "\n> $it ($it.id)"
		}
		sendMessage(output.block("verilog"))
		message.server.modlogs.collect { message.server.channel(it) }*.sendMessage(output.block("verilog"))
	}catch (NoPermissionException ex){
		sendMessage("> Failed to member member(s). I don't seem to have permissions.".block("verilog"))
	}
}

bot.command("bot",
	group: "Administrative",
	description: "Gives the set bot role to a user/users. Bot role is set using |>botrole",
	usages: [
		"": "Bots the latest user in the server.",
		" (@mentions)": "Bots every user mentioned."
	],
	allowsPermissions: true){
	if (!author.permissions["manageRoles"]){
		sendMessage("> You don't have sufficient permissions.".block("verilog"))
	}else if (!serverJson(message.server).bot_role){
		sendMessage("> This server doesn't have a bot role set.".block("verilog"))
	}else try{
		def role = message.server.role(message.server.bot_role)
		List<Member> members
		if (message.mentions.empty){
			members = [message.server.lastMember]
		}else{
			members = message.mentions.collect { message.server.member(it) }
		}
		String output = "> The following members were botted by $author ($author.id):"
		members.each {
			it.addRole(role)
			output += "\n> $it ($it.id)"
		}
		sendMessage(output.block("verilog"))
		message.server.modlogs.collect { message.server.channel(it) }*.sendMessage(output.block("verilog"))
	}catch (NoPermissionException ex){
		sendMessage("> Failed to bot member(s). I don't seem to have permissions.".block("verilog"))
	}
}

bot.command(["ban",
	~/ban<(\d+)>/],
	group: "Administrative",
	description: "Bans a given user/users.",
	usages: [
		"": "Bans the latest user in the server.",
		" (@mentions)": "Bans every user mentioned.",
		"<(days)>": "Bans the latest user and clears their messages for the past given number of days.",
		"<(days)> (@mentions)": "Bans every user mentioned and clears their messages for the past given number of days."
	],
	examples: [
		"",
		" @hlaaf#7436",
		"<3>",
		"<3> @hlaaf#7436"
	],
	allowsPermissions: true){
	if (author.permissions["ban"] ||
		author.roles*.name.join("").toLowerCase().contains("trusted")){
		try{
			int days = captures[0] ?: 0
			List<Member> members
			if (message.mentions.empty){
				if ((now() - message.server.lastMember.createTime.time) >= 60_000 && !args.contains("regardless")){
					sendMessage("> The latest member, ${formatFull(message.server.latestMember)}, joined more than 1 minute ago. To ban them regardless of that, type \"|>ban regardless\".".block("verilog"))
					return
				}
				members = [message.server.lastMember]
			}else{
				members = message.mentions.collect { message.server.member(it) }
			}
			String output = "> The following members were banned by $author ($author.id):"
			members.each {
				it.ban(days)
				output += "\n> $it ($it.id)"
			}
			sendMessage(output.block("verilog"))
			message.server.modlogs.collect { message.server.channel(it) }*.sendMessage(output.block("verilog"))
		}catch (NoPermissionException ex){
			sendMessage("> Failed to ban member(s). I don't seem to have permissions.".block("verilog"))
		}
	}else sendMessage("> You don't have permissions.".block("verilog"))
}


bot.command(["softban",
	~/softban<(\d+)>/],
	group: "Administrative",
	description: "Quickly bans and unbans users. Usable to clearing a user's messages when also kicking them. Original command is in R. Danny, had to add it to this bot for a private server at first.",
	usages: [
		"": "Softbans the latest user in the server.",
		" (@mentions)": "Softbans every user mentioned.",
		"<(days)>": "Softbans the latest user and clears their messages for the past given number of days.",
		"<(days)> (@mentions)": "Softbans every user mentioned and clears their messages for the past given number of days."
	],
	examples: [
		"",
		" @hlaaf#7436",
		"<3>",
		"<3> @hlaaf#7436"
	],
	hide: true,
	allowsPermissions: true){
	if (author.permissions["ban"] ||
		author.roles*.name.join("").toLowerCase().contains("trusted")){
		try{
			int days = captures[0] ?: 7
			List<Member> members
			if (message.mentions.empty){
				if ((now() - message.server.lastMember.createTime.time) >= 60_000 && !args.contains("regardless")){
					sendMessage("> The latest member, ${formatFull(message.server.latestMember)}, joined more than 1 minute ago. To ban them regardless of that, type \"|>ban regardless\".".block("verilog"))
					return
				}
				members = [message.server.lastMember]
			}else{
				members = message.mentions.collect { message.server.member(it) }
			}
			String output = "> The following members were softbanned by $author ($author.id):"
			members.each {
				it.ban(days)
				it.unban()
				output += "\n> $it ($it.id)"
			}
			sendMessage(output.block("verilog"))
			message.server.modlogs.collect { message.server.channel(it) }*.sendMessage(output.block("verilog"))
		}catch (NoPermissionException ex){
			sendMessage("> Failed to softban member(s). I don't seem to have permissions.".block("verilog"))
		}
	}else sendMessage("> You don't have permissions.".block("verilog"))
}



bot.command("eval",
	group: "Misc",
	description: "Evaluates Groovy code. Everyone can use this.",
	usages: [
		" (code)": "Evaluates the given code."
	],
	examples: [
		" (33 & 42).intdiv(6).times { println it }"
	],
	batchable: true){
	String dea = args
		.replaceAll(/^```\w*\n/, "")
		.replaceAll(/```$/, "")
	if (json.author.id in ["98457401363025920", "215942738670125059"] &&
		usedTrigger.toString() != "><"){
		try{
			sendMessage(("> " + new GroovyShell(
				new Binding(it + ([bot: bot, client: client, me: me, script: this,
					now: System.&currentTimeMillis] +
					(this.binding.variables +
						this.metaClass.methods
							.collectEntries { [(it.name): this.&"$it.name"] }))),
						cc)
					.evaluate(dea).toString()).block("verilog"))
		}catch (ex){
			sendMessage(ex.toString().block("groovy"))
		}
	}else{
		def evaluation = {
			try{
				JSONUtil.parse(
					Unirest.post("http://groovyconsole.appspot.com/executor.groovy")
					.field("script", dea)
					.asString().body)
			}catch (ex){
				sendMessage("> Failed to request evaluation.".block("verilog"))
				null
			}
		}()
		if (evaluation == null) return
		String output = ""
		if (evaluation["executionResult"]){
			output += "\n" + "> Result:\n$evaluation.executionResult".block("verilog")
		}
		if (evaluation["outputText"]){
			output += "\n" + "> Output:\n$evaluation.outputText".block("verilog")
		}
		if (evaluation["stacktraceText"]){
			output += "\n" + "> Error:\n$evaluation.stacktraceText".block("verilog")
		}
		try{
			sendMessage(output)
		}catch (ex){
			Message dong = sendMessage("> Message too long. Uploading JSON result of evaluation...".block("verilog"))
			sendFile(JSONUtil.dump("temp/evaluation_${message.id}.json", evaluation))
			dong.delete()
		}
	}
}

ScriptingContainer cnt = new ScriptingContainer()
bot.command("ruby"){
	(([bot: bot, client: client, me: me, script: this] <<
		this.binding.variables) << it).each { k, v -> cnt.put(k, v) }
	try{
		sendMessage(("> " + JavaEmbedUtils.rubyToJava(
			cnt.parse(args).run())).block("verilog"))
	}catch (ex){
		sendMessage(ex.toString().block("rb"))
	}
	cnt.clear()
}.whitelist(["98457401363025920", "215942738670125059"])

Random colorRandom = new Random()
bot.command("color",
	group: "Misc",
	description: "Creates/reuses a role that has a color and no other special aspect to it.",
	usages: [
		" (hexadecimal number)": "Uses a hexadecimal number as the color",
		" (rgb number tuple)": "Uses numbers separated by commas as RGB numbers for the color.",
		" (named color)": "Uses a human readable color (spaces ignored) as the color. (list: \"http://www.december.com/html/spec/colorsvg.html\")",
		" random": "Uses a random color between 0x000000 and 0xFFFFFF. That's the infamous 16.7 million colors."
	],
	examples: [
		" fb4bf4",
		" 44, 43, 103",
		" red",
		" navy blue"
	],
	allowsPermissions: true){
	if (!message.server){
		sendMessage("> We aren't in a server.".block("verilog"))
		return
	}
	args = args.replaceAll(/\s+/, "")
	args = args.replace("#", "")
	long color = 0
	if (args.toLowerCase() == "random"){
		color = colorRandom.nextInt(0xFFFFFF)
	}else if (args ==~ /[0-9a-fA-F]+/){
		try{
			color = Long.parseLong(args, 16)
		}catch (NumberFormatException ex){
			sendMessage("> Invalid hexadecimal number. Probably too large. We're talking 64 bits here.".block("verilog"))
			return
		}
	}else if (args ==~ /(?:rgb\()?[0-9]+,[0-9]+,[0-9]+(?:\))?/){
		List<Byte> rgb = {
			try{
				return args.replaceAll(/rgb\((.*?)\)/){ full, ass -> ass }.tokenize(",").collect { Byte.parseByte((Short.parseShort(it) - 128).toString()) }
			}catch (NumberFormatException ex){
				return []
			}
		}()
		if (rgb.empty){
			sendMessage("> Invalid RGB tuple. An example of one is |14, 3, 240|. The values have to be between 0 and 256 (including 0, not including 256).".block("verilog"))
			return
		}
		rgb.each { byte c ->
			color <<= 8
			color |= c
		}
	}else if (args ==~ /\w+/){
		if (!MiscUtil.namedColors.containsKey(args)){
			sendMessage("> Invalid named color. List here: ${formatUrl("http://www.december.com/html/spec/colorsvg.html")}".block("verilog"))
			return
		}
		color = MiscUtil.namedColors[args]
	}
	try{
		List oldRoles = author.roles.findAll { it.hoist || !(it.name ==~ /#?[0-9a-fA-F]+/) || it.name.contains(" ") || it.permissionValue > author.server.defaultRole.permissionValue || !(it.permissionValue == 0) }
		boolean created
		Role role = message.server.roles.find {
			!it.hoist && it.permissionValue == 0 &&
				it.name ==~ /#?[0-9a-fA-F]+/ &&
				it.colorValue == color
		} ?: { ->
			created = true
			message.server.createRole(name:
				Long.toHexString(color).padLeft(6, "0"),
				color: color,
				permissions: 0)
		}()
		author.editRoles(oldRoles + role)
		sendMessage(("> Color ${created ? "created" : "found"} and added.\n> Your previous roles: ${author.roles*.name.join(", ")}").block("verilog"))
	}catch (NoPermissionException ex){
		sendMessage("> I don't have sufficient permissions. Give me Manage Roles.".block("verilog"))
	}
}

Map roleOptions = [
	color: { Role it ->
		!it.hoist && !it.permissionValue &&
		(it.name ==~ /#?[A-Za-z0-9]+/ ||
			MiscUtil.namedColors[it.name.toLowerCase().replaceAll(/\s+/, "")])
	},
	unused: { Role it ->
		!it.server.members*.object*.roles.flatten().contains(it.id)
	},
	no_overwrites: { Role it ->
		!it.server.channels*.permissionOverwriteMap.sum()[it.id]
	},
	no_permissions: { Role it ->
		!it.permissionValue
	}
]

roleOptions.colour = roleOptions.color

bot.command("purgeroles",
	group: "Administrative",
	description: "Purges roles with specific filters. If no filters are given, all filters will be used.\n\n" +
		"List of filters: ${roleOptions.keySet().join(", ")}",
	usages: [
		"": "Uses all filters.",
		" (filter1) (filter2)...": "Uses the given filters. Note: space separated."
	],
	examples: [
		"",
		" color",
		" unused no_overwrites"
	],
	allowsPermissions: true){
	if (!author.permissions["manageRoles"]){
		sendMessage(("> You don't have sufficient permissions. Ask a staff member to " +
			"call this command to do what you want.").block("verilog"))
		return
	}
	try{
		def options = []
		if (args){
			for (o in args.tokenize()){
				if (roleOptions[o]) options.add(roleOptions[o])
				else {
					sendMessage(("> Unknown filter: $o.\n> List of filters: " +
						roleOptions.keySet().join(", ")).block("verilog"))
					return
				}
			}
		}else{
			options = roleOptions.values()
		}
		List<Role> roles = message.server.roles
		roles.remove(message.server.defaultRole)
		options.each {
			roles = roles.findAll(it)
		}
		Message a = sendMessage("> Deleting ${roles.size()} roles in about ${roles.size() / 2} seconds...".block("verilog"))
		long s = now()
		if (roles){
			withPool {
				roles.dropRight(1).each {
					it.&delete.callAsync()
					Thread.sleep 500
				}
				roles.last().&delete.callAsync()
			}
		}
		a.edit("> Deleted all ${roles.size()} roles in ${(now() - s) / 1000} seconds.".block("verilog"))
	}catch (NoPermissionException ex){
		sendMessage("> I don't have permissions to manage roles.".block("verilog"))
	}
}

bot.command(["filterroles", "purgedroles"],
	group: "Misc",
	description: "Finds roles with specific filters. If no filters are given, all filters will be used.\n\n" +
		"List of filters: ${roleOptions.keySet().join(", ")}",
	usages: [
		"": "Uses all filters.",
		" (filter1) (filter2)...": "Uses the given filters. Note: space separated."
	],
	examples: [
		"",
		" color",
		" unused no_overwrites"
	]){
	def options = []
	if (args){
		for (o in args.tokenize()){
			if (roleOptions[o]) options.add(roleOptions[o])
			else {
				sendMessage(("> Unknown filter: $o.\n> List of filters: " +
					roleOptions.keySet().join(", ")).block("verilog"))
				return
			}
		}
	}else{
		options = roleOptions.values()
	}
	List<Role> roles = message.server.roles
	roles.remove(message.server.defaultRole)
	options.each {
		roles = roles.findAll(it)
	}
	sendMessage("> ${roles.join(", ")}\n> ${roles.size()} total".block("verilog"))
}

bot.command(["join", "invite"],
	group: "Meta",
	description: "Gives the bot's URL to add it to servers. No arguments."){
	sendMessage(("> " + formatUrl(client.fields.botApp.inviteUrl(
		new Permissions(manageRoles: true)))).block("verilog"))
}

bot.command(["randomcolor", "randomcolour"],
	group: "Misc",
	description: "Posts an image containing information about a random color which is also the background of the image.",
	usages: [
		"": "Sets width and height to 250.",
		" (number)": "Sets width and height to the number.",
		" (width) (height)": "Sets with and height individually."
	],
	batchable: true){
	List ints = args.tokenize().collect {
		try {
			it.replaceAll(/\D/, "").toInteger()
		}catch (ex){
			256
		}
	}
	def (width, height) = ints.size() == 0 ? [256, 256] :
		ints.size() == 1 ? [ints[0], ints[0]] :
		ints
	width > 2500 && (width = 2500)
	height > 2500 && (height = 2500)
	def y = draw(width, height){
		Color c = new Color(colorRandom.nextInt(0xffffff))
		int rgb = (c.RGB << 8) >>> 8
		color = c
		fillRect(0, 0, it.width, it.height)
		color = new Color([0xffffff, 0].max { it ? it - rgb : rgb - it })
		drawString("Hex: #" + Integer.toHexString(rgb).padLeft(6, "0"), 10, 20)
		drawString("RGB: $c.red, $c.green, $c.blue", 10, 40)
		drawString("Dec: $rgb", 10, 60)
	}
	try{
		sendFile(y, filename: "randomcolor.png")
	}catch (NoPermissionException ex){
		sendMessage("> I don't seem to have permissions to send files. Maybe you need to try in a testing channel?".block("verilog"))
	}
}

bot.command("jsonize",
	group: "Misc",
	description: "Appropriates a string for JSON, filling in \\u, \\r, \\n, \\t and whatnot.",){
	sendMessage(("> " + JSONUtil.json(args)).block("verilog"))
}

bot.command(["brainfuck", "bf"],
	group: "Misc",
	description: "Interprets Brainfuck code. I normally wouldn't have added this but I wrote the interpreter myself, and considering the lack of self esteem I have I really liked that it worked.",
	usages: [
		" (code)": "Interprets the code."
	]){
	def intrp = new BrainfuckInterpreter()
	boolean done
	Thread a = Thread.start {
		sendMessage(String.format("""\
		|> Output:
		|%s
		|> Steps: %d, stack position: %d""".stripMargin(),
			intrp.interpret(args), intrp.steps,
			intrp.stackPosition).block("verilog"))
		done = true
	}
	Thread.sleep(5000)
	if (!done){
		a.interrupt()
		sendMessage("> Evaluation took longer than 5 seconds. Total of $intrp.steps steps and stack position is $intrp.stackPosition.".block("verilog"))
	}
}

bot.command(["putlocker", "pl"],
	group: "Misc",
	description: "Searches putlocker.is for movies and TV shows.",
	usages: [
		" (query)": "Searches with given query."
	]){
	def elem = Jsoup.connect("http://putlocker.is/search/search.php?q=${URLEncoder.encode(args)}")
		.userAgent("Mozilla/5.0 (Windows NT 10.0; WOW64; rv:48.0) Gecko/20100101 Firefox/48.0")
		.referrer("http://putlocker.is/")
		.get()
		.select(".content-box")
		.select("table")[1]
		.select("a")[0]
	sendMessage("${elem.attr("title")} - ${elem.attr("href")}")
}

findCommand = { String name ->
	bot.commands.find { it.aliases.findAll { !it.regex && it.toString() == name } }
}

formatCommand = { Command command,
	String preferredTrigger = command.triggers.find { !it.regex },
	String preferredName = command.aliases.find { !it.regex } ->
	def output = """> ${command.aliases.findAll { !it.regex }.collect { it.toString().surround '"' }.join(" or ")} ($command.group): $command.description
> Usage:
${command.usages.collect { k, v -> "\"$preferredTrigger$preferredName$k\": $v" }.join("\n")}"""
	if (command.info.examples){
		output += """\n> Examples:
${command.examples.collect { "\"$preferredTrigger$preferredName$it\"" }.join("\n")}"""
	}
	output.block("verilog")
}

bot.command(["help", "commands"],
	group: "Meta",
	description: "Lists command groups or gives information about a command or group.",
	usages: [
		"": "Lists group and gives information about how to call the bot.",
		" (group)": "Lists all commands of a group.",
		" (command)": "Shows the description, usage and the examples (if any) of the command."
	]){
	if (args){
		def (group, command) = [groups[args.trim().toLowerCase().capitalize()],
			findCommand(args)]
		if (group){
			def commands = bot.commands.findAll { it.info.group == group.name && !it.info.hide }
			def aliases = commands.collect {
				it.aliases.findAll { !it.regex }
			}
			sendMessage("""> $group.name: $group.description
> Commands:
${aliases.collect { it.collect { it.toString().surround '"' }.join(" or ") }.join(", ")}""".block("verilog"))
		}else if (command){
			sendMessage(formatCommand(command, usedTrigger.toString(), args))
		}else{
			sendMessage("> Command or group not found.".block("verilog"))
		}
	}else{
		sendMessage("""> My prefixes are |> and ><, meaning when you call a command you have to put one of those before the command name (no space).
> For example: "$usedTrigger$usedAlias" calls this command, and "$usedTrigger$usedAlias markov" calls this command with the arguments "markov".
> Commands are sectioned via groups. Unfortunately I can't list every command here, so I'm just gonna list the groups and you can do "$usedTrigger$usedAlias <groupname>" to list its commands.
${groups.collect { k, v -> "> $k: $v.description" }.join("\n")}""".block("verilog"))
	}
}

bot.command("command",
	group: "Meta",
	description: "Gives information about a command.",
	usages: [
		" (command)": "Shows the description, usage and the examples (if any) of the command."
	]){
	sendMessage(formatCommand(command, usedTrigger.toString(), args))
}

println "> Commands added."

client.metaClass.getIncludedEvents = {
	delegate.listenerSystem.listeners.keySet() as List
}
bot.initialize()

println "> Bot initialized."