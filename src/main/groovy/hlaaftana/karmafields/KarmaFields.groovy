package hlaaftana.karmafields

import hlaaftana.discordg.Client
import hlaaftana.discordg.logic.EventData
import hlaaftana.discordg.objects.*
import hlaaftana.discordg.util.*
import hlaaftana.discordg.util.bot.*
import hlaaftana.karmafields.kismet.Kismet
import hlaaftana.karmafields.kismet.KismetInner
import hlaaftana.karmafields.kismet.Block
import hlaaftana.karmafields.registers.*

class KarmaFields {
	static long start = System.currentTimeMillis()
	static Map defaultPermissions = [
		view_perms: 'has_permission message.author message.channel "administrator"',
		edit_perms: 'has_permission message.author message.channel "administrator"',
		view_server_data: 'has_permission message.author message.channel "administrator"',
		edit_server_data: 'has_permission message.author message.channel "administrator"',
		'24': 'has_permission message.author message.channel "sendMessages"',
		'25': 'message.member.owner',
		purgeroles: 'has_permission message.author message.channel "administrator"',
		'22': 'has_permission message.author message.channel "manageServer"',
		'32': 'has_permission message.author message.channel "manageServer"'
	].collectEntries { k, v -> [(k): parseDiscordKismet(v)] }
	static DataFile creds = new DataFile('creds.json')
	static Client client = new Client(logName: '|><|', eventThreadCount: 4)
	static Client me = new Client(logName: 'Selfbot')
	static boolean botReady
	static boolean meReady
	static CommandBot bot
	static List<CommandRegister> registers = []
	static CleverbotDotIO cleverbot = new CleverbotDotIO(
		creds.cb_user, creds.cb_key)
	static Map<String, DataFile> guildData = [:]
	@Lazy static File exceptionLogFile = new File("dumps/exceptions_${System.currentTimeMillis()}.txt")

	static {
		[client, me].each { Client c -> c.with {
			log.debug.enable()
			log.formatter = { Log.Message message ->
				String.format('[%s] [%s]: %s',
					message.level.name.toUpperCase(),
					message.by, message.content)
			}
			http.canary()
			http.baseUrl += 'v6/'
			addReconnector()
		} }

		me.listen('ready'){
			if (!meReady){
				client.fields.appId = creds.app_id
				meReady = true
				println '--- Fully loaded in ' + ((System.currentTimeMillis() -
					start) / 1000) + ' seconds. ---'
			}
		}

		client.listen('ready'){
			client.play '⌀'
			if (!botReady){
				client.servers.each {
					def file = new File("guilds/${it.id}.json")
					if (!file.exists()) file.write('{}')
					guildData[it.id] = new DataFile(file)
				}
				registers.findAll { it.registerAfterReady }*.register()
				if (!me.token){
					me.login(creds.self_email, creds.self_pass)
				}
				botReady = true
			}
		}

		bot = new CommandBot(logName: '|><|Bot', triggers: ['|>', '><'], client: client,
			extraCommandArgs: [formatted: { e -> e.channel.&formatted },
				guildData: { e -> KarmaFields.guildData[e.server.id] }])
		bot.log.formatter = { Log.Message message ->
			String.format('[%s] [%s]: %s',
				message.level.name.toUpperCase(),
				message.by, message.content)
		}
	}

	static Command findCommand(String name, Message msg = null){
		bot.commands.find { (msg ? it.allows(msg) : true) && (it.id == name ||
			it.aliases.findAll { !it.regex && it.toString() == name }) }
	}

	static List<Command> findCommands(String name, Message msg = null){
		bot.commands.findAll { (msg ? it.allows(msg) : true) && (it.id == name ||
			it.aliases.findAll { !it.regex && it.toString() == name }) }
	}
	
	static boolean checkPerms(message, name, boolean defaul = false){
		def n = name.toString()
		def s = message.server?.guildData().perms?.get(n)
		def om
		if (s) om = new Message(message.client, s.message_object)
		def b = s ? parseDiscordKismet(s.code, [__original_message: om]) :
			defaultPermissions[n]
		if (b){
			Block x = b.anonymousClone()
			x.context.data = x.context.getData() + [message: message]
			x.evaluate().asBoolean()
		}else defaul
	}
	
	static Block parseDiscordKismet(String code, Map extra = [:]){
		Kismet.parse(code, (KismetInner.defaultContext + Util.discordKismetContext
			+ extra).collectEntries { k, v -> [(k): Kismet.model(v)] })
	}
	
	static EventData fabricateCopy(Map edited, EventData d){
		Message modifiedMessage = new Message(d.message.client, d.message.object.clone())
		modifiedMessage.object << edited
		EventData newData = d.clone()
		newData.message = modifiedMessage
		def a = newData.json.clone()
		a << edited
		newData.json = a
		newData
	}

	static EventData fabricateEventFromMessageObject(Map object){
		EventData ed = EventData.create('MESSAGE_CREATE',
			client.eventDataCalls.MESSAGE_CREATE.curry(client, object))
		ed['guild'] = ed['server']
		ed['json'] = object
		ed['rawType'] = 'MESSAGE_OBJECT'
		ed
	}

	static run(){
		client.login('|><|New'){
			client.log.fatal('Bot token invalid or nonexistant')
			System.exit(0)
		}

		bot.loggedIn = true

		[BotListeners, MetaCommands, ServerCommands, UsefulCommands,
			CookieCutterCommands, CustomCommands].each {
			registers << it.newInstance()
		}
		
		registers.findAll { !it.registerAfterReady }*.register()
		
		bot.commands.findAll { it in DSLCommand }.each { DSLCommand c ->
			Closure x = c.response.clone()
			c.response = { aa ->
				if (message.private && c.info.serverOnly){
					formatted('This command only works in a server.')
					return
				}
				if (c.info.checkPerms){
					try{
						if (!checkPerms(message, c.id, true)){
							formatted('You don\'t have sufficient permissions.')
							return
						}
					}catch (ex){
						println "---SERVER $serverId COMMAND $c.alias FIX PERMS---"
						println ex
						formatted 'The permissions for this command seem to be broken. Sorry for the inconvenience.'
						return
					}
				}	
				Closure copy = x.clone()
				copy.delegate = aa
				copy.resolveStrategy = Closure.DELEGATE_FIRST
				copy(aa)
			}
		}
		bot.initialize()
		bot.listenerSystem.removeListener(CommandBot.Events.EXCEPTION, bot.exceptionListener)
	}

	static main(args){
		run()
	}
}
