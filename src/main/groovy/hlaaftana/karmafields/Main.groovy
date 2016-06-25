package hlaaftana.karmafields

import hlaaftana.discordg.*

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
import hlaaftana.discordg.objects.*
import hlaaftana.discordg.exceptions.*
import hlaaftana.discordg.util.*
import hlaaftana.discordg.util.bot.*
import java.nio.ByteBuffer
import pura.lang.*
import pura.util.*

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import hlaaftana.pg.*
import hlaaftana.pg.portal.*
import hlaaftana.pg.profiles.*

import hlaaftana.levelchateau.*
import hlaaftana.levelchateau.pages.*

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

Module pura = Parser.parse(";")
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

splitter = { String arguments ->
	List list = [""]
	String currentQuote
	arguments.toList().each { String ch ->
		if (currentQuote){
			if (ch == currentQuote) currentQuote = null
			else list[list.size() - 1] += ch
		}else{
			if (ch in ['"', "'"]) currentQuote = ch
			else if (Character.isSpaceChar(ch as char)) list += ""
			else list[list.size() - 1] += ch
		}
	}
	list
}

draw = { int width = 256, int height = 256, Closure closure ->
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
formatName = { DiscordObject obj -> "\"${obj.name.replace("\"", "\\\"")}\"" }
formatUrl = { String url -> "\"$url\"" }
formatFull = { DiscordObject obj -> "\"${obj.name.replace("\"", "\\\"")}\" ($obj.id)" }
formatLongUser = { User user -> "\"$user.name\"#$user.discrim ($user.id)" }

serverInfo = { Server server ->
	[
		Name: formatName(server),
		ID: server.id,
		Icon: formatUrl(server.icon),
		"Creation date": server.createTime.format(dateFormat),
		Members: [server.memberCount, server.members.size()].max(),
		Roles: server.roles.size(),
		Channels: "${server.channels.size()} total: ${server.textChannels.size()} text, ${server.voiceChannels.size()} voice",
		"Latest member": formatFull(server.latestMember),
		Region: formatName(server.region),
		Owner: formatFull(server.owner),
		"Default channel": formatFull(server.defaultChannel)
	]
}

userInfo = { User user ->
	[
		Name: formatName(user) + "#$user.discrim",
		ID: user.id,
		Avatar: formatUrl(user.avatar),
		"Creation date": user.createTime.format(dateFormat),
		Bot: "$user.bot".capitalize(),
		"Shared servers": user.sharedServers.size(),
		Game: user.game ? (user.game.type == 1 ? "Streaming " : "Playing ") + user.game.name : "nothing",
		Status: user.status.capitalize()
	]
}

memberInfo = { Member member ->
	userInfo(member) << [
		"Join date": member.joinDate.format(dateFormat),
		Mute: member.mute,
		Deaf: member.deaf,
		Roles: member.roles.collect { it.name.surround('"') }.with {
			size() > 3 ? delegate[0..1] + (delegate[2] + "...") : delegate
		}.join(", ") + " (${member.roles.size()})",
		Color: "#" + Integer.toHexString(member.colorValue),
		Nickname: member.rawNick
	]
}

encode64Bits = { byte[] bytes ->
	List byteLists = (bytes as List).collate(8)
	List longList = byteLists.collect { List singularByteList ->
		long collected = 0
		singularByteList.each { byte byte_ ->
			collected <<= 8
			collected |= byte_
		}
		return collected
	}
	return longList as long[]
}

decode64Bits = { long[] longs ->
	new String((longs as List).collect { long long_ ->
		ByteBuffer.allocate(8).putLong(long_).array()
	}.flatten() as byte[], "UTF-8")
}

compressString = { String decompressedString ->
	List byteLists = (decompressedString.bytes as List).collate(2)
	List charList = byteLists.collect { List singularByteList ->
		short collected = 0
		singularByteList.each { byte byte_ ->
			collected <<= 8
			collected |= byte_
		}
		collected as char
	}
	new String(charList as char[])
}

decompressString = { String compressedString ->
	new String((compressedString.toCharArray() as List).collect { char char_ ->
		ByteBuffer.allocate(2).putChar(char_).array()
	}.flatten() as byte[], "UTF-8")
}

serverJson = { server ->
	File file = new File("guilds/${DiscordObject.id(server)}.json")
	if (!file.exists()) file.write("{}")
	JSONUtil.parse(file)
}

modifyServerJson = { server, aa ->
	JSONUtil.modify("guilds/${DiscordObject.id(server)}.json", aa)
}

Map getCreds(){
	JSONUtil.parse(new File("creds.json"))
}

boolean botReady
boolean meReady

ExecutorService markovFileThreadPool = Executors.newFixedThreadPool(5)
boolean youCanCrash = false
long start = System.currentTimeMillis()
CommandBot bot
Client me = new Client(logName: "Selfbot", enableEventRegisteringCrashes: true)
Client client = new Client(logName: "|><|", confirmedBot: true, enableEventRegisteringCrashes: true)
Client appStorage = new Client(token: creds["app_storage_token"])
client.log.debug.enable()
me.log.debug.enable()
this.binding.setVariable("me", me)
this.binding.setVariable("client", client)
client.addReconnector()
me.addReconnector()
me.includedEvents = [Events.SERVER, Events.CHANNEL, Events.ROLE, Events.MEMBER,
	Events.MESSAGE]
me.excludedEvents = null
me.listener(Events.READY){
	if (!meReady){
		client.fields["botApp"] = appStorage.getApplication("193646883837706241")
		bot.triggers += client.mentionRegex
		meReady = true
		println "--- Fully loaded. ---"
	}
}

File log = "logs/${new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date(start))}.log" as File
log.createNewFile()
client.login("|><|New"){
	appStorage.getApplication("193646883837706241").bot.token
}
bot = new CommandBot(triggers: [/\|>/, /></], type: BotType.REGEX, client: client)
bot.loggedIn = true
bot.client.eventThreadCount = 4

class ArgumentChecker {
	def arguments
	ArgumentChecker(arguments){ this.arguments = arguments }
	ArgumentChecker(String arguments){ this.arguments = arguments.tokenize() }

	def getFirst(){ arguments[0] }
	String getRest(){ arguments.drop(1).join(" ") }

	static from(arguments, Closure closure){
		closure.delegate = new ArgumentChecker(arguments)
		closure.resolveStrategy = Closure.DELEGATE_FIRST
		closure()
	}

	def first(what, Closure closure){
		if (arguments[0].isCase(what)){
			closure.delegate = new ArgumentChecker(arguments.drop(1))
			closure.resolveStrategy = Closure.DELEGATE_FIRST
			closure()
		}
	}

	def starts(args, Closure closure){
		if (arguments[0..args.size() - 1] == args){
			closure.delegate = this
			closure.resolveStrategy = Closure.DELEGATE_FIRST
			closure()
		}
	}

	def starts(String string, Closure closure){
		if (arguments[0].isCase(string)){
			closure.delegate = new ArgumentChecker(arguments)
			closure.resolveStrategy = Closure.DELEGATE_FIRST
			closure()
		}
	}
}

bot.client.listener("ready"){
	bot.client.play("welcome to the afterworld.")
	if (!botReady){
		Thread.start { LPClient.login(creds["lp_username"], creds["lp_pass"]) }
		if (!me.token){
			me.login(creds["self_email"], creds["self_pass"])
		}
		botReady = true
	}
}

bot.client.addListener "server create", {
	if(it.server.id in bot.client.servers*.id) return
	it.server.roles.find { it.name == "|><|" }?.edit(color: 0x0066c2)
	it.server.defaultChannel.sendMessage("> Looks like I joined. Yay. Do `|>help` for commands.".block("verilog"))
	File a = new File("guilds/${it.server.id}.json")
	a.createNewFile()
	if (a.text == ""){
		JSONUtil.dump(a, [commands: []])
	}
}

bot.client.listener(Events.MESSAGE){
	markovFileThreadPool.submit {
		File file = new File("markovs/${author.id}.txt")
		if (!file.exists()) file.createNewFile()
		file.append(message.content + "\n", "UTF-8")
	}
}


bot.client.listener(Events.MEMBER){
	if (server.id in ["145904657833787392", "195223058544328704"]){
		String message = """\
> A member just joined:
> Name: $member.name
> ID: $member.id
> Account creation time: $member.createTime
> To member, type |>member, to ban, type |>ban, to bot, type |>bot.""".block("verilog")

		server.defaultChannel.sendMessage(message)
		server.textChannel("bot-mod-log")?.sendMessage(message)
	}
	if (serverJson(server).automember){
		member.addRole(server.role(serverJson(server).member_role))
		server.defaultChannel.sendMessage("> Automembered ${formatFull(member)}."
			.block("verilog"))
		server.textChannel("bot-mod-log")
			?.sendMessage("> Automembered ${formatFull(member)}.".block("verilog"))
	}
}

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
	|> Library: DiscordG ("https://github.com/hlaaftana/DiscordG") (deprecated)
	|> Memory usage: ${Runtime.runtime.totalMemory() / (2 ** 20)}/${Runtime.runtime.maxMemory() / (2 ** 20)} MB
	|> Invite: ${formatUrl(client.fields.botApp.inviteUrl(new Permissions(268435456)))}""".stripMargin().block("verilog"))
}

bot.command("softban",
	group: "Administrative",
	description: "Quickly bans and unbans users. Usable to clearing a user's messages when also kicking them.",
	usages: [
		"": "Softbans the latest user in the server.",
		" <@mentions>": "Softbans every user mentioned."
	],
	allowsPermissions: true){
	if (author.permissions["ban"] || author.roles*.name.join("").contains("Trusted")){
		try{
			List<Member> members
			if (message.mentions.empty){
				if ((System.currentTimeMillis() - message.server.lastMember.createTime.time) >= 60_000 && !args.contains("regardless")){
					sendMessage("> The latest member, ${formatFull(message.server.latestMember)}, joined more than 1 minutes ago. To softban them regardless of that, type \"|>softban regardless\".".block("verilog"))
					return
				}
				members = [message.server.lastMember]
			}else{
				members = message.mentions.collect { message.server.member(it) }
			}
			//client.fields["popcorn_bot_bans"] += members
			String staffOutput = "> The following members were softbanned by $author ($author.id):"
			members.each {
				it.ban(7)
				it.unban()
				staffOutput += "\n> $it ($it.id)"
				sendMessage("> Softbanned $it.".block("verilog"))
			}
			message.server.textChannel("171815384360419328").sendMessage(staffOutput.block("verilog"))
		}catch (NoPermissionException ex){
			sendMessage("> Failed to softban member(s). I don't seem to have permissions.".block("verilog"))
		}
	}
}

Map bmCallTimes = [:]
bot.command(["bm", "batchmarkov",
	~/bm(?:<(\d+?)>)?/,
	~/batchmarkov(?:<(\d+?)>)?/],
	group: "Fun",
	description: "Shortcut to running the batch command for the markov command.",
	ratelimit: "5 calls per 30 seconds",
	usages: [
		"<(number)>": "Calls the markov command (number) times.",
		"<(number)> (arguments)": "Calls the markov command (number) times with "
	]){
	if (bmCallTimes[message.server.id] >= 5){
		sendMessage("> Slow down. You've called the command a bit more than enough times. Try again in a bit.".block("verilog"))
		return
	}
	if (bmCallTimes[message.server.id]) bmCallTimes[message.server.id]++
	else bmCallTimes[message.server.id] = 1
	BigInteger time = {
		try {
			captures[0].toBigInteger() ?: 3
		}catch (ex){
			3
		}
	}()
	if (time > 5) time = 5
	Message modifiedMessage = message
	String newContent = args ? "$usedTrigger$usedAlias $args" : "$usedTrigger$usedAlias"
	modifiedMessage.object["content"] = newContent
	Map newData = delegate.clone()
	newData << [message: modifiedMessage]
	newData.fullData.content = newContent
	Command ass = bot.commands.find { it.aliases*.toString().contains "markov" }
	time.times {
		Thread.start {
			ass.run newData
			ass.run modifiedMessage
		}
	}
	Thread.sleep(30_000)
	bmCallTimes[message.server.id]--
}

bot.command(["batch",
	~/batch(?:<(\w+?)(?:, (\d+?))>)?/],
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
		"<markov, 3>",
		"<markov> @mention"
	]){
	String commandName = captures[0]
	BigInteger time = {
		try {
			captures[1].toBigInteger() ?: 3
		}catch (ex){
			3
		}
	}()
	if (time > 5) time = 5
	Message modifiedMessage = new Message(message.client, message.object)
	String newContent = args ? "$usedTrigger$usedAlias $args" : "$usedTrigger$usedAlias"
	modifiedMessage.object["content"] = newContent
	Map newData = data.clone()
	newData << [message: modifiedMessage]
	newData.fullData.content = newContent
	time.times {
		Command ass = bot.commands.find { it.aliases*.toString().contains commandName }
		ass newData
	}
}

bot.command("markov",
	group: "Fun",
	description: "Generates a sentence based off of order of words from previous messages.",
	usages: [
		"": "A sentence from your messages.",
		" <@mention or id>": "A sentence from the supplied user's messages.",
		" everyone": "A sentence from everyone's messages.",
		" random": "A sentence from a random user's messages."
	]){
	DiscordObject id
	String input = args.trim()
	if (!message.mentions.empty){
		id = message.mentions[0]
	}else if (!(input.empty)){
		if (input ==~ /<@(\d+)>/){
			id = DiscordObject.forId((input =~ /<@(\d+)>/)[0][1])
		}else if (input ==~ /\d+/){
			id = DiscordObject.forId((input =~ /\d+/)[0])
		}else if (input == "random"){
			id = DiscordObject.forId "random"
		}else if (input in ["everyone", "@everyone"]){
			id = DiscordObject.forId "everyone"
		}else if (input.tokenize()[0] == "name"){
			String fullName = ""
			boolean startedAndWhitespace = true
			input.substring(input.indexOf("name") + 4).toList().each {
				if (startedAndWhitespace && Character.isSpaceChar(it as char)) return
				else {
					startedAndWhitespace = false
					fullName += it
				}
			}
			id = client.members.find { it.name == fullName }
		}
	}
	if (id == null){ id = author }
	List<List<String>> words
	if (id.id == "everyone"){
		words = new File("markovs/").listFiles()*.readLines().flatten()*.tokenize().collect { it as List }
	}else if (id.id == "random"){
		words = (new File("markovs/").listFiles() as List).randomItem().readLines()*.tokenize().collect { it as List }
	}else{
		File file = new File("markovs/${id.id}.txt")
		if (!file.exists()){
			sendMessage("> Markov for user ${formatFull(id)} doesn't exist.".block("verilog"))
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

bot.command("memberrole",
	group: "Administrative",
	description: "Sets or unsets the \"member\" role for the server.",
	usages: [
		" <@rolemention or id or name>": "Sets the member role to the specified role."
	],
	allowsPermissions: true){
	Role role = message.roleMentions ?
		message.roleMentions[0] :
		message.server.role(args)
	if (role) JSONUtil.modify("guilds/${message.server.id}.json", [member_role: role.id])
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
			"${serverJson(message.server).automember ? "on" : "off"}."
			.block("verilog"))
	}
	if (args ==~ /\s*on\s*/){
		if (!author.permissions["manageRoles"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			modifyServerJson(message.server, [automember: true])
			sendMessage("> Automember is now on.".block("verilog"))
		}
	}
	if (args ==~ /\s*off\s*/){
		if (!author.permissions["ban"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			modifyServerJson(message.server, [automember: false])
			sendMessage("> Automember is now off.".block("verilog"))
		}
	}
	if (args ==~ /\s*toggle\s*/){
		if (!author.permissions["ban"]){
			sendMessage("> You do not have sufficient permissions.".block("verilog"))
		}else{
			modifyServerJson(message.server, [automember:
				!serverJson(message.server).automember])
			sendMessage("> Automember is now " +
				"${serverJson(message.server).automember ? "on" : "off"}.".block("verilog"))
		}
	}
}

bot.command("splitnum"){
	ArgumentChecker.from(args.split(" ")){
		def num = {
			try{
				Numbers.parseInteger(first)
			}catch (NumberFormatException ex){
				-1
			}
		}()
		if (num < 0){
			sendMessage("> Invalid number. Usage: |splitnum (number) (text)|".block("verilog"))
			return
		}
		def output = (rest =~ /(?:.|\n){${num}}/).collect().inspect()
		try{
			sendMessage("> $output".block("verilog"))
		}catch (ex){
			Message eh = sendMessage("> Output exceeded 2000 characters. Uploading file...")
			File file = new File("temp/splitnum_${System.currentTimeMillis()}.txt")
			file.createNewFile()
			file.write(output, "UTF-8")
			sendFile(file)
			eh.delete()
			file.delete()
		}
	}
}

bot.command("member",
	allowsPermissions: true){
	try{
		List<Member> members
		if (message.mentions.empty){
			members = [message.server.lastMember]
		}else{
			members = message.mentions.collect { message.server.member(it) }
		}
		String staffOutput = "> The following members were membered by $author ($author.id):"
		members.each {
			it.addRole(message.server.role(serverJson(message.server)["member_role"]))
			staffOutput += "\n> $it ($it.id)"
			message.channel.sendMessage("> Membered $it.".block("verilog"))
		}
		message.server.textChannel("bot-mod-log").sendMessage(staffOutput.block("verilog"))
	}catch (NoPermissionException ex){
		sendMessage("> Failed to member member(s). I don't seem to have permissions.".block("verilog"))
	}
}

bot.command("bot",
	allowsPermissions: true){
	try{
		List<Member> members
		if (message.mentions.empty){
			members = [message.server.lastMember]
		}else{
			members = message.mentions.collect { message.server.member(it) }
		}
		String staffOutput = "> The following members were botted by $author ($author.id):"
		members.each {
			it.addRole(message.server.role("Bot"))
			staffOutput += "\n> $it ($it.id)"
			message.channel.sendMessage("> Botted $it.".block("verilog"))
		}
		message.server.textChannel("bot-mod-log")?.sendMessage(
			staffOutput.block("verilog"))
	}catch (NoPermissionException ex){
		sendMessage("> Failed to bot member(s). I don't seem to have permissions.".block("verilog"))
	}
}

bot.command("ban",
	allowsPermissions: true){
	if (author.permissions["ban"] ||
		author.roles*.name.join("").contains("Trusted")){
		try{
			List<Member> members
			if (message.mentions.empty){
				if ((System.currentTimeMillis() - message.server.lastMember.createTime.time) >= 60_000 && !args.contains("regardless")){
					sendMessage("> The latest member, ${formatFull(message.server.latestMember)}, joined more than 1 minutes ago. To ban them regardless of that, type \"|>ban regardless\".".block("verilog"))
					return
				}
				members = [message.server.lastMember]
			}else{
				members = message.mentions.collect { message.server.member(it) }
			}
			//client.fields["popcorn_bot_bans"] += members
			String staffOutput = "> The following members were banned by $author ($author.id):"
			members.each {
				it.ban()
				staffOutput += "\n> $it ($it.id)"
				sendMessage("> Banned $it.".block("verilog"))
			}
			message.server.textChannel("171815384360419328").sendMessage(staffOutput.block("verilog"))
		}catch (NoPermissionException ex){
			sendMessage("> Failed to ban member(s). I don't seem to have permissions.".block("verilog"))
		}
	}
}

bot.command("eval"){
	String dea = args
	if (author.id == "98457401363025920" && !(match[0].toString() == "><")){
		try{
			sendMessage(("> " + new GroovyShell(
				new Binding(([bot: bot, client: client, me: me, script: this] << this.binding.variables) << it), cc)
					.evaluate(dea).toString()).block("verilog"))
		}catch (ex){
			sendMessage(ex.toString().block("groovy"))
		}
	}else{
		def evaluation = {
			try{
				return JSONUtil.parse(Unirest.post("http://groovyconsole.appspot.com/executor.groovy").field("script", args).asString().body)
			}catch (ex){
				sendMessage("> Failed to request evaluation.".block("verilog"))
				return null
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

bot.command("julia"){
	sendMessage(
		("> " + Unirest.post("http://localhost:4040/eval").body(new String(args.getBytes("UTF-8"), "UTF-8")).asString().body)
			.block("verilog"))
}.whitelist("98457401363025920")

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
}.whitelist("98457401363025920")

bot.command("pura") {
	try{
		sendMessage(("> " + Pura.run(args, pura, false).toString()).block("verilog"))
	}catch (ex){
		sendMessage((ex.toString()).block("groovy"))
	}
}.whitelist("98457401363025920")

bot.command("square") {
	def text = args.toUpperCase()
	def top = text.replace("", " ").substring(1)
	def allText = "$top\n"
	for (int i = 0; i < (text.substring(1).length() - 1); i++){
		def string = text.substring(1)
		def reversed = text.reverse().substring(1)
		allText += string[i] + (" " * ((string.length() * 2) - 1)) + reversed[i] + "\n"
	}
	try{
		sendMessage((allText + "${top.reverse().substring(1)}").block("verilog"))
	}catch (ex){
		sendMessage(("> Output surpassed 2000 characters.").block("verilog"))
	}
}

bot.command("apichanges"){
	client.server("81384788765712384") // discord api
	.textChannel("124294271900712960") // api-changes
	.logs.each { author.privateChannel.sendMessage(it.content); Thread.sleep(1000) }
	sendMessage("> Done.".block("verilog"))
}

bot.command("color",
	allowsPermissions: true){
	if (!message.server){
		sendMessage("> We aren't in a server.".block("verilog"))
		return
	}
	args = args.replaceAll(/\s+/, "")
	long color = 0
	if (args ==~ /[0-9a-fA-F]+/){
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
		if (created){
			sendMessage(("> Color created and added.\n> Your previous roles: ${author.roles*.name.join(", ")}").block("verilog"))
		}else{
			sendMessage(("> Color found and added.\n> Your previous roles: ${author.roles*.name.join(", ")}").block("verilog"))
		}
	}catch (NoPermissionException ex){
		sendMessage("> I don't have sufficient permissions. Give me Manage Roles.".block("verilog"))
	}
}

bot.command("user"){
	ArgumentChecker.from(args.tokenize()){
		first(["i", "info"]){
			User ass = author
			first(["n", "name"]){
				ass = message.private ?
					client.members.find {
						it.name.tokenize().join(" ") == rest.join(" ")
					}.user :
					message.server.members.find {
						it.name.tokenize().join(" ") == rest.join(" ")
					}
			}
			first(/\d+/){
				ass = message.private ? client.user(first) : message.server.member(first)
			}
			first(User.MENTION_REGEX){
				ass = message.private ? message.mentions[0] : message.server.member(message.mentions[0])
			}
			Map output = [:]
			if (ass instanceof Member){
				output << memberInfo(ass)
			}else{
				output << userInfo(ass)
			}
			sendMessage(output.collect { k, v ->
				v ? "> $k: $v\n" : ""
			}.join("").block("verilog"))
		}
	}
}

bot.command("server"){
	List fullArgs = args.tokenize()
	ArgumentChecker.from(fullArgs){
		first(["i", "info"]){
			Map output = [:]
			first(null){
				output << serverInfo(message.server)
			}
			first(["o", "owner"]){
				output << memberInfo(message.server.owner)
			}
			sendMessage(output.collect { k, v ->
				v ? "> $k: $v\n" : ""
			}.join("").block("verilog"))
		}
		first(["m", "most"]){
			first(["m", "member", "members"]){
				first(["r", "role", "roles"]){
					first(["c", "count"]){
						int amount = {
							try{
								return first.toInteger()
							}catch (ex){
								return 5
							}
						}()
						String output = "> The $amount members in this server with the highest role count are:"
						message.server.members.sort { -it.roles.size() }[0..amount - 1].each {
							output += "\n> $it: ${it.roles.size()}"
						}
						sendMessage(output.block("verilog"))
						return
					}
				}
			}
		}
	}
}

bot.command("channel"){
	List fullArgs = args.split(" ")
	ArgumentChecker.from(fullArgs){
		first("clear"){
			if (!author.fullPermissionsFor(message.channel).map["deleteMessages"]){
				sendMessage("> You do not have sufficient permissions.".block("verilog"))
				return
			}
			if (!message.server.me.fullPermissionsFor(message.channel).map["deleteMessages"]){
				sendMessage("> I do not have sufficient permissions.".block("verilog"))
				return
			}
			boolean fast = false
			int count = 50
			starts(/\d+/){
				count = arguments[0].toInteger()
			}
			if (count > 10000 && !(author == me.user)){
				sendMessage("> That is way too many messages.".block("verilog"))
				return
			}
			Message initial = sendMessage("> Getting messages...".block("verilog"))
			List messages = message.channel.getLogs(count + 1) - initial
			boolean done = false
			try{
				message.channel.clear(messages)
			}catch (NoPermissionException ex){
				initial.edit("> I do not have sufficient permissions.".block("verilog"))
				return
			}
			initial.edit("> Done.".block("verilog"))
		}
	}
}

bot.command("help", /\!/){
	sendMessage("\n" * 2000).deleteAfter(2000)
}.whitelist("110373943822540800")

bot.command(["join", "oauth", "invite"]){
	sendMessage(("> " + formatUrl(client.fields.botApp.inviteUrl(
		new Permissions(manageRoles: true)))).block("verilog"))
}

bot.command("encodebin"){
	String input = args
	if ((input ==~ /\s*/) && !message.attachments.empty){
		sendMessage("> Waiting a bit to download...".block("verilog"))
		input = message.attachments[0].inputStream.text
	}
	if (input ==~ /\s*/){
		sendMessage(("> No text specified. Either upload a file or add it to the message.").block("verilog"))
	}
	String output = input.toCharArray().collect { Integer.toBinaryString(it as int) }*.padLeft(8, "0").join(" ")
	try{
		sendMessage(("> " + output).block("verilog"))
	}catch (ex){
		sendMessage(("> Output exceeded 2000 characters. Attemptng to send file...").block("verilog"))
		File dag = "temp/binary_encode_${System.currentTimeMillis()}.txt" as File
		dag.createNewFile()
		dag.write(output, "UTF-8")
		sendFile(dag, filename: "encoded_binary.txt")
	}
}

bot.command("decodebin"){
	String input = args.replaceAll(/[^01 ]/, "")
	if ((input ==~ /\s*/) && !message.attachments.empty){
		sendMessage("> Waiting a bit to download...".block("verilog"))
		input = message.attachments[0].inputStream.text.replaceAll(/[^01 ]/, "")
	}
	if (input ==~ /\s*/){
		sendMessage(("> No text specified. Either upload a file or add it to the message.").block("verilog"))
	}
	String output = input.split(" ").collect { (Integer.parseInt(it, 2)) as char }.join("")
	try{
		sendMessage(("> " + output).block("verilog"))
	}catch (ex){
		sendMessage(("> Output exceeded 2000 characters. Attemptng to send file...").block("verilog"))
		File dag = "temp/binary_decode_${System.currentTimeMillis()}.txt" as File
		dag.createNewFile()
		dag.write(output, "UTF-8")
		sendFile(dag, filename: "decoded_binary.txt")
	}
}

Random colorRandom = new Random()
bot.command(["randomcolor", "randomcolour"]){
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
	sendFile(y, filename: "randomcolor.png")
}

bot.command(/to\\u/){
	sendMessage(("> " + args.collect {
			it > 0xff ?
				"\\u" + Integer.toHexString((it as char) as int).padLeft(4, "0") :
				it
		}.sum()).block("verilog"))
}

bot.command("test"){
	long gay = System.currentTimeMillis()
	def (year, month, day, hour, minute, second, millisecond) =
		message.rawTimestamp.split(/\D+/)*.toInteger()
	def aa = new GregorianCalendar(year, month, day, hour, minute, second)
	aa.set(Calendar.MILLISECOND, millisecond)
	long recv = gay - aa.timeInMillis
	long current = System.currentTimeMillis()
	String a = "> Message receive time: ~${recv}ms"
	Message ass = sendMessage(a.block("verilog"))
	long send = System.currentTimeMillis() - current
	current = System.currentTimeMillis()
	a += "\n> Message send time: ~${send}ms"
	ass.edit(a.block("verilog"))
	long edit = System.currentTimeMillis() - current
	a += "\n> Message edit time: ~${edit}ms"
	ass.edit(a.block("verilog"))
}

bot.command(["help", "commands"]){
	it.author.privateChannel.sendMessage("""> My prefix is `|>`, meaning when you call a command, you have to start it with `|>`.
> Use this link if you want to invite me to your server: ${formatUrl(client.fields.botApp.inviteUrl(new Permissions(268435456)))}
> My commands are:
> |help/commands|: DMs you this message.
> |color (hex color, rgb tuple or SVG named color)|: Creates a new role with zero permissions and assigns it that color, gives it the hex code of the color as a name and then assigns the role to you.
> |square (text)|: Produces a square of the text you provded.
> |game (mention)|: Gets the full name of the game the mentioned user is playing.
> |markov|: Runs |markov (yourid)|.
> |markov (mention|id)|: Generates a sentence from a Markov chain of the user's logged messages.
> |markov everyone|: Generates a sentence from a Markov chain of every logged message.
> |markov random|: Generates a sentence from a Markov chain of a random user's logged messages.
> |splitnum (number) (text)|: Splits the text into (number) strings.
> |eval (code)|: Evaluates (code) as Groovy code. Available for everyone now.
> |apichanges|: DMs you the entire logs in the #apichanges channel in the Discord API server in case you're banned. **Do not try otherwise.**
> |join/oauth/invite|: Gives you the link to inivte this bot.
> |decodebin (binary numbers separated by spaces)|: Translates the given binary to text.
> |encodebin (text)|: Encodes (text) to binary.""".block("verilog"))
}

bot.client.metaClass.getIncludedEvents = {
	delegate.listenerSystem.listeners.keySet() as List
}
bot.initialize()