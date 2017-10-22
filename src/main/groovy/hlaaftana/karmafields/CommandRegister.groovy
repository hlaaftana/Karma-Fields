package hlaaftana.karmafields

import hlaaftana.karmafields.relics.CommandBot
import hlaaftana.karmafields.relics.CommandEventData
import sx.blah.discord.api.IDiscordClient

abstract class CommandRegister {
	String group
	boolean registerAfterReady = false

	def command(Map x, alias, trigger = [], @DelegatesTo(CommandEventData) Closure closure){
		def c = bot.command((group ? [group: group] : [:]) + x, alias, trigger, closure)
		c
	}

	def command(alias, trigger = [], @DelegatesTo(CommandEventData) Closure closure){
		command([:], alias, trigger, closure)
	}

	static CommandBot getBot() { KarmaFields.bot }
	static IDiscordClient getClient() { KarmaFields.client }
	static String format(String s) { KarmaFields.format(s) }
	abstract register()
}
