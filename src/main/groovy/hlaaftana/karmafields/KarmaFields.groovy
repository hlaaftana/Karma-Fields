package hlaaftana.karmafields

import hlaaftana.discordg.Client
import hlaaftana.discordg.exceptions.MessageInvalidException
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.logic.EventData
import hlaaftana.discordg.objects.Channel
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.util.Log
import hlaaftana.discordg.util.bot.*
import hlaaftana.karmafields.registers.*

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class KarmaFields {
	static KarmaFields instance
	Client client = new Client(logName: '|><|', eventThreadCount: 4)
	Client me = new Client(logName: 'Selfbot')
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
				String.format('[%s] [%s]: %s',
					message.level.name.toUpperCase(),
					message.by, message.content)
			}
			http.canary()
			http.baseUrl += 'v6/'
			addReconnector()
		} }

		client.mute('81402706320699392', '119222314964353025')

		me.listen('ready'){
			if (!meReady){
				client.fields.appId = Util.creds.app_id
				bot.triggers += client.mentionRegex
				meReady = true
				println '--- Fully loaded. ---'
			}
		}

		client.listen('ready'){
			client.play('⌀')
			if (!botReady){
				if (!me.token){
					me.login(Util.creds.self_email, Util.creds.self_pass)
				}
				botReady = true
			}
		}

		bot = new CommandBot(triggers: ['|>', '><'], client: client,
			extraCommandArgs: [formatted: { e -> e.channel.&formatted }])
	}

	Command findCommand(String name){
		bot.commands.find { it.aliases.findAll { !it.regex && it.toString() == name } }
	}
	
	def clean(Channel channel, int count = 50, Client... clients = [client, me]){
		def x = clients.collectEntries { [(it.id): it] }
		channel.logs(count).findAll { 
			x.containsKey(it.object.author.id) }.each {
				x[it.object.author.id].deleteMessage(
					it.object.channel_id, it.object.id) }
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

	def run(){
		client.login('|><|New'){
			client.log.fatal('Bot token invalid or nonexistant')
			System.exit(0)
		}

		bot.loggedIn = true

		[BotListeners, MetaCommands, ServerCommands, EntertainmentCommands,
			UsefulCommands, CookieCutterCommands].each {
			it.register(this)
		}

		bot.commands.findAll { it in DSLCommand }.each { DSLCommand c ->
			Closure x = c.response.clone()
			c.response = { aa ->
				if (message.private && c.info.serverOnly){
					formatted('This command only works in a server.')
					return
				}
				if (c.info.defaultPerms){
					try{
						def perms = PermissionParser.from(server
							.perms?.get(c.alias.toString()) ?:
							c.info.defaultPerms)
						if (!perms.apply(member, message) && !member.owner){
							formatted('You don\'t have sufficient permissions.')
							return
						}
					}catch (ex){
						println "---SERVER $serverId COMMAND $c.alias FIX PERMS---"
					}
				}	
				Closure copy = x.clone()
				copy.delegate = aa
				copy.resolveStrategy = Closure.DELEGATE_FIRST
				copy(aa)
			}
		}
		bot.initialize()
	}

	static main(args){
		instance = new KarmaFields()
		instance.run()
	}
}
