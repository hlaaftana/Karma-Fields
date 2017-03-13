package hlaaftana.karmafields.registers

import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.util.bot.Restricted
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.CommandRegister
import hlaaftana.karmafields.kismet.Kismet

class CustomCommands extends CommandRegister {
	{ group = 'Custom'; registerAfterReady = true }
	Map abc = [:]
	
	def isCommandQualified(object){
		object.code && object.aliases && object.triggers
	}
	
	def registerCommand(server, id, object){
		def x = { it.collect { it instanceof Collection ? ~(it[0]) : it } }
		def y = object.clone()
		y.remove('aliases')
		y.remove('triggers')
		y.id = id
		y.server = server
		def cmd = command(y, x(object.aliases)){
			def bl = KarmaFields.parseDiscordKismet(command.info.code,
				[__original_message: Kismet.model(
					new Message(client, command.info.message_object)),
					message: Kismet.model(message), args: args,
					captures: captures, all_captures: allCaptures,
					command_match: match, command: command])
			bl.evaluate()
		}
		cmd.clearTriggers()
		cmd.addTrigger(x(object.triggers))
		cmd.whitelist(Restricted.Type.SERVER, server)
		cmd
	}
	
	def updateCommand(server, id, object){
		def cmd = abc[id]
		if (!cmd) throw new IllegalArgumentException('Invalid command')
		def x = { it.collect { it instanceof Collection ? ~(it[0]) : it } }
		def y = object.clone()
		y.remove('aliases')
		y.remove('triggers')
		y.id = id
		y.group = 'Custom'
		cmd.clearAliases()
		cmd.addAlias(x(object.aliases))
		cmd.clearTriggers()
		cmd.addTrigger(x(object.triggers))
		cmd.info = y
		abc[id] = cmd
	}
	
	def register(){
		update()
	}
	
	def update(){
		def check = abc.clone()
		client.servers.each { s ->
			if (!s.guildData().commands) return
			s.guildData().commands.each { k, v ->
				if (check.containsKey(k)){
					check.remove(k)
					v.toString()
					updateCommand(s, k, v)
				}else if (isCommandQualified(v)){
					abc[k] = registerCommand(s, k, v)
				}
			}
		}
		check.each { k, v ->
			bot.commands.remove(abc[k])
			abc.remove(k)
		}
	}
}
