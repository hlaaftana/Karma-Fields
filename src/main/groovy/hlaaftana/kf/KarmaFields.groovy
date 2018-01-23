package hlaaftana.kf

import groovy.transform.CompileStatic
import hlaaftana.discordg.objects.Invite
import hlaaftana.discordg.util.Log
import hlaaftana.discordg.util.bot.CleverbotDotIO
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.discordg.util.bot.CommandEventData
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.CommandType
import hlaaftana.discordg.util.bot.DSLCommand
import hlaaftana.kf.registers.*
import hlaaftana.discordg.*
import hlaaftana.discordg.objects.Message

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
	static Client client = new Client(token: creds.<String>get('token'))
	static boolean ready
	static CommandBot bot = new CommandBot(logName: '|><|Bot', triggers: ['P!', 'poo! '], client: client,
		formatter: this.&format, extraCommandArgs: [guildData: { CommandEventData e -> guildData[e.guild.id] }])
	static String appId = creds.<String>get('app_id')
	static List<CommandRegister> registers = []
	static CleverbotDotIO cleverbot = new CleverbotDotIO(
			creds.<String>get('cb_user'),
			creds.<String>get('cb_key'))
	static Map<String, DataFile> guildData = [:]
	@Lazy static File exceptionLogFile = new File("dumps/exceptions_${System.currentTimeMillis()}.txt")

	static {
		MiscUtil.registerStaticMethods()

		client.addListener 'ready', {
			client.play 'poo! help'
			client.fields.oldInvites = (List<Invite>) client.guild(287659842330558464).channels.collectMany {
				if (!it.permissionsFor(client)['manageChannel']) return Collections.emptyList()

				(Collection) it.requestInvites()
			}
			if (!ready) {
				for (g in client.guilds) {
					File file = new File("guilds/${g.id}.json")
					if (!file.exists()) file.write('{}')
					guildData[g.id] = new DataFile(file)
				}
				registers.findAll { it.registerAfterReady }*.register()
				ready = true
			}
		}

		client.addReconnector()

		bot.log.formatter = client.log.formatter = { Log.Message message ->
			String.format('[%s] [%s]: %s',
					message.level.name.toUpperCase(),
					message.by, message.content)
		}
	}

	static String format(String s) {
		MiscUtil.block('> ' + s.readLines().join('\n> '), 'accesslog')
	}

	static DSLCommand findCommand(String name, Message msg = null){
		bot.commands.find { it instanceof DSLCommand && (msg ? it.allows(msg) : true) && (it.info.id == name ||
			it.aliases.findAll { !it.regex && it.toString() == name }) } as DSLCommand
	}

	static List<DSLCommand> findCommands(String name, Message msg = null){
		bot.commands.findAll { it instanceof DSLCommand && (msg ? it.allows(msg) : true) && (it.info.id == name ||
			it.aliases.findAll { !it.regex && it.toString() == name }) } as List<DSLCommand>
	}

	static run(){
		for (Class<? extends CommandRegister> it in [BotListeners, MetaCommands,
		                                             GuildCommands, UsefulCommands,
		                                             QuickCommands]) {
			def r = it.newInstance()
			registers.add(r)
			if (!r.registerAfterReady) r.register()
		}

		bot.initialize()
		bot.listenerSystem.removeListener(CommandBot.Events.EXCEPTION, bot.exceptionListener)
		client.login()
	}

	static main(args){
		run()
	}
}
