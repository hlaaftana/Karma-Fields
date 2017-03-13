package hlaaftana.karmafields

import hlaaftana.discordg.Client
import hlaaftana.discordg.util.bot.CommandBot

abstract class CommandRegister {
	String group
	boolean registerAfterReady = false

	def command(Map x = [:], ...args){
		def c = bot.command((group ? [group: group] : [:]) + x, *args)
		Closure y = c.response.clone()
		c.response = { aa ->
			if (message.private && c.info.serverOnly){
				formatted('This command only works in a server.')
				return
			}
			if (c.info.checkPerms){
				try{
					if (!KarmaFields.checkPerms(message, c.id, true)){
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
			Closure copy = y.clone()
			copy.delegate = aa
			copy.resolveStrategy = Closure.DELEGATE_FIRST
			copy(aa)
		}
		c
	}
	static CommandBot getBot(){ KarmaFields.bot }
	static Client getClient(){ KarmaFields.client }
	abstract register()
}
