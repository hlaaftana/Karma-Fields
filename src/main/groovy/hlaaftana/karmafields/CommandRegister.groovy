package hlaaftana.karmafields

import hlaaftana.discordg.Client
import hlaaftana.discordg.util.bot.CommandBot

abstract class CommandRegister {
	boolean registerAfterReady = false
	
	static CommandBot getBot(){ KarmaFields.bot }
	static Client getClient(){ KarmaFields.client }
	abstract register()
}
