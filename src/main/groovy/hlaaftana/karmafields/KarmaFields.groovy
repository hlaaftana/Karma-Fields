package hlaaftana.karmafields

import groovy.transform.CompileStatic
import hlaaftana.karmafields.relics.CleverbotDotIO
import hlaaftana.karmafields.relics.CommandBot
import hlaaftana.karmafields.registers.*
import hlaaftana.karmafields.relics.CommandEventData
import hlaaftana.karmafields.relics.DSLCommand
import hlaaftana.karmafields.relics.Log
import hlaaftana.karmafields.relics.MiscUtil
import sx.blah.discord.Discord4J
import sx.blah.discord.api.ClientBuilder
import sx.blah.discord.api.IDiscordClient
import sx.blah.discord.api.events.IListener
import sx.blah.discord.handle.impl.events.ReadyEvent
import sx.blah.discord.handle.obj.IIDLinkedObject
import sx.blah.discord.handle.obj.IMessage

@CompileStatic
class KarmaFields {
	/*static Map defaultPermissions = [
		view_perms: 'has_permission message.author message.channel "administrator"',
		edit_perms: 'has_permission message.author message.channel "administrator"',
		view_guild_data: 'has_permission message.author message.channel "administrator"',
		edit_guild_data: 'has_permission message.author message.channel "administrator"',
		'24': 'has_permission message.author message.channel "sendMessages"',
		'25': 'message.member.owner',
		purgeroles: 'has_permission message.author message.channel "administrator"',
		'22': 'has_permission message.author message.channel "manageGuild"',
		'32': 'has_permission message.author message.channel "manageGuild"'
	].collectEntries { k, v -> [(k): ] }*/
	static DataFile creds = new DataFile('creds.json')
	static IDiscordClient client = new ClientBuilder().withToken((String) creds.get('token')).build()
	static boolean ready
	static CommandBot bot = new CommandBot(logName: '|><|Bot', triggers: ['|>', '><'], client: client, formatter: this.&format,
		extraCommandArgs: [guildData: { CommandEventData e -> guildData[e.guild.stringID] }])
	static String appId = creds.get('app_id')
	static List<CommandRegister> registers = []
	static CleverbotDotIO cleverbot = new CleverbotDotIO((String) creds.get('cb_user'), (String) creds.get('cb_key'))
	static Map<String, DataFile> guildData = [:]
	@Lazy static File exceptionLogFile = new File("dumps/exceptions_${System.currentTimeMillis()}.txt")

	static {
		MiscUtil.registerCollectionMethods()
		MiscUtil.registerStringMethods()
		Discord4J.disableAudio()

		client.dispatcher.registerListener new IListener<ReadyEvent>() {
			@Override
			void handle(ReadyEvent readyEvent) {
				client.changePlayingText '⌀'
				if (!ready) {
					for (g in client.guilds) {
						File file = new File("guilds/${g.stringID}.json")
						if (!file.exists()) file.write('{}')
						guildData[g.stringID] = new DataFile(file)
					}
					registers.findAll { it.registerAfterReady }*.register()
					ready = true
				}
			}
		}

		bot.log.formatter = { Log.Message message ->
			String.format('[%s] [%s]: %s',
				message.level.name.toUpperCase(),
				message.by, message.content)
		}
	}

	static String format(String s) {
		MiscUtil.block('> ' + s.readLines().join('\n> '), 'accesslog')
	}

	static DSLCommand findCommand(String name, IMessage msg = null){
		bot.commands.find { it instanceof DSLCommand && (msg ? it.allows(msg) : true) && (it.info.id == name ||
			it.aliases.findAll { !it.regex && it.toString() == name }) } as DSLCommand
	}

	static List<DSLCommand> findCommands(String name, IMessage msg = null){
		bot.commands.findAll { it instanceof DSLCommand && (msg ? it.allows(msg) : true) && (it.info.id == name ||
			it.aliases.findAll { !it.regex && it.toString() == name }) } as List<DSLCommand>
	}

	static run(){
		for (Class<? extends CommandRegister> it in [BotListeners, MetaCommands,
		                                             GuildCommands, UsefulCommands,
		                                             CookieCutterCommands]) {
			def r = it.newInstance()
			registers.add(r)
			if (!r.registerAfterReady) r.register()
		}

		client.login()

		bot.initialize()
		bot.listenerSystem.removeListener(CommandBot.Events.EXCEPTION, bot.exceptionListener)
	}

	static String isMention(String thing) {
		if (thing.length() >= 20 && thing[0] == '<' && thing[-1] && '>' && thing[1] == '#' || thing[1] == '@') {
			String a = thing[2..-2]
			if (a[1] == '&' || a[1] == '!') a = a[1..-1]
			a.long ? a : null
		}else null
	}

	static String resolveId(thing){
		if (null == thing) null
		else if (thing instanceof String){
			String a = isMention(thing)
			if (a) a
			else if (thing.long) thing
			else null
		} else if (thing instanceof IIDLinkedObject) thing.stringID
		else if (thing instanceof Number) thing as long
		else null
	}

	static main(args){
		run()
	}
}
