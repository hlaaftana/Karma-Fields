package hlaaftana.kf.discordg.registers

import groovy.transform.CompileStatic
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.objects.Guild
import hlaaftana.discordg.util.bot.BotExceptionEventData
import hlaaftana.kf.discordg.CommandRegister
import hlaaftana.kf.discordg.DataFile
import hlaaftana.kf.discordg.KarmaFields
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.discordg.util.bot.CommandEventData
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.objects.Role

// import from when i needed the guild create check for retard discord4j:
//import java.time.ZoneOffset

@CompileStatic
class BotListeners extends CommandRegister {
	@Lazy PrintWriter exceptionPw = new PrintWriter(KarmaFields.exceptionLogFile)
	
	def register(){
		client.addListener 'guild', { Map<String, Object> e ->
			// compilestatic
			def guild = (Guild) e.guild

			try {
				Role role = guild.role('|><|')
				role.edit(color: 0x66c2)
			} catch (ignored) {}

			guild.sendMessage format('Looks like I joined. Do |>help or ><help to learn more.')
			File a = new File("guilds/${guild.id}.json")
			if (!a.text) JSONUtil.dump(a, [:])
			KarmaFields.guildData[guild.id] = new DataFile(a)
		}

		bot.listenerSystem.addListener(CommandBot.Events.EXCEPTION) { BotExceptionEventData d ->
			def exception = d.exception
			if (exception instanceof NoPermissionException){
				try {
					d.sendMessage format('It seems I don\'t have permissions. Discord: ' +
							((NoPermissionException) exception).response)
				} catch (NoPermissionException ignored) {
					// can't send messages
				}
			}else{
				d.sendMessage format(exception.toString())
				bot.log.warn "When running command: $d.commandData.content."
				bot.log.warn "Got exception: $d.exception"
				KarmaFields.exceptionLogFile.append(d.commandData.content + '\r\n')
				d.exception.printStackTrace(exceptionPw)
				exceptionPw.flush()
			}
		}
	}
}
