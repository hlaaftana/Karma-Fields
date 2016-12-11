package hlaaftana.karmafields

import hlaaftana.discordg.Client
import hlaaftana.discordg.exceptions.MessageInvalidException
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
	ExecutorService markovFileThreadPool = Executors.newFixedThreadPool(5)

	KarmaFields(){
		[client, me].each { Client c -> c.with {
			log.debug.enable()
			log.formatter = { Log.Message message ->
				String.format("[%s] [%s]: %s",
					message.level.name.toUpperCase(),
					message.sender, message.content)
			}
			http.ptb()
			http.baseUrl += "v6/"
			addReconnector()
		} }

		client.mute("81402706320699392", "119222314964353025")
		client.eventThreadCount = 4

		me.serverTimeout *= 4
		me.includedEvents = ["server", "channel", "role",
			"member", "message"]
		me.excludedEvents = []

		me.listener("ready"){
			if (!meReady){
				client.fields.appId = Util.creds.app_id
				bot.triggers += client.mentionRegex
				meReady = true
				println "--- Fully loaded. ---"
			}
		}

		client.listener("ready"){
			client.play("⌀")
			if (!botReady){
				if (!me.token){
					me.login(Util.creds.self_email, Util.creds.self_pass)
				}
				botReady = true
			}
		}

		bot = new CommandBot(triggers: ["|>", "><"], client: client,
			extraCommandArgs: [decorate: { e -> e.channel.&decorate }],
			errorResponses: [(MessageInvalidException):
				{ channel.decorate("Unfortunately my message was too long.") }])
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
			AdministrativeCommands, UsefulCommands,
			MiscCommands, CookieCutterCommands].each {
			it.register(this)
		}

		bot.initialize()
	}

	static main(args){
		instance = new KarmaFields()
		instance.run()
	}
}
