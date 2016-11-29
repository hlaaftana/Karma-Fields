package hlaaftana.karmafields

import hlaaftana.discordg.Client
import hlaaftana.discordg.Events
import hlaaftana.discordg.util.Log
import hlaaftana.discordg.util.bot.*
import hlaaftana.karmafields.registers.*

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class KarmaFields {
	static KarmaFields instance
	Client client = new Client(logName: "|><|")
	Client me = new Client(logName: "Selfbot")
	boolean botReady
	boolean meReady
	CommandBot bot
	CleverbotDotIO cleverbot = new CleverbotDotIO(Util.creds.cb_user,
		Util.creds.cb_key)
	YandexDictionary yandexDict = new YandexDictionary(key: Util.creds.yandex_dict_key)
	ExecutorService markovFileThreadPool = Executors.newFixedThreadPool(5)

	KarmaFields(){
		[client, me].each { Client c ->
			c.log.debug.enable()
			c.log.formatter = { Log.Message message ->
				String.format("[%s] [%s]: %s",
					message.level.name.toUpperCase(),
					message.sender, message.content)
			}
			c.http.ptb()
			c.http.baseUrl += "v6/"
			c.addReconnector()
		}

		client.mute("81402706320699392", "119222314964353025")
		client.eventThreadCount = 4

		me.serverTimeout *= 4
		me.includedEvents = [Events.SERVER, Events.CHANNEL, Events.ROLE,
			Events.MEMBER, Events.MESSAGE]
		me.excludedEvents = null

		me.listener(Events.READY){
			if (!meReady){
				client.fields.app_id = Util.creds.app_id
				bot.triggers += client.mentionRegex
				meReady = true
				println "--- Fully loaded. ---"
			}
		}

		client.listener(Events.READY){
			client.play("⌀")
			if (!botReady){
				if (!me.token){
					me.login(Util.creds.self_email, Util.creds.self_pass)
				}
				botReady = true
			}
		}

		bot = new CommandBot(triggers: ["|>", "><"], client: client,
			extraCommandArgs: [decorate: { e -> { a -> e.sendMessage(
				('> ' + a).replace('\n', '\n> ').block("accesslog")) } }])

		BotListeners.register(this)
	}

	Command findCommand(String name){
		bot.commands.find { it.aliases.findAll { !it.regex && it.toString() == name } }
	}

	def run(){
		client.login("|><|New"){
			client.log.fatal("Bot token invalid or nonexistant")
			System.exit(0)
		}

		bot.loggedIn = true

		[BotListeners, MetaCommands, ServerCommands, EntertainmentCommands,
			AdministrativeCommands, UsefulCommands, MiscCommands].each {
			it.register(this)
		}

		bot.initialize()
	}

	static main(args){
		instance = new KarmaFields()
		instance.run()
	}
}
