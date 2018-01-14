package hlaaftana.karmafields.registers

import groovy.transform.CompileStatic
import hlaaftana.karmafields.relics.CommandEventData
import hlaaftana.karmafields.relics.JSONUtil
import hlaaftana.karmafields.relics.CommandBot
import hlaaftana.karmafields.CommandRegister
import hlaaftana.karmafields.DataFile
import hlaaftana.karmafields.KarmaFields
import net.dv8tion.jda.core.entities.Role
import net.dv8tion.jda.core.events.Event
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException
import net.dv8tion.jda.core.hooks.EventListener

import java.awt.Color
// import from when i needed the guild create check for retard discord4j:
//import java.time.ZoneOffset

class BotListeners extends CommandRegister {
	@Lazy def exceptionPw = new PrintWriter(KarmaFields.exceptionLogFile)
	
	def register(){
		client.addEventListener new EventListener() {
			@Override
			@CompileStatic
			void onEvent(Event event) {
				if (!(event instanceof GuildJoinEvent)) return
				GuildJoinEvent e = (GuildJoinEvent) event
				// check for initial guild create was here during discord4j... rip little retard...
				// thing is i didnt even need the check with my own library that i made and used myself,
				// discord4j had like 4000 people backing it and no one bothered to separate guild create and join

				try {
					Role role = e.guild.getRolesByName('|><|', false)[0]
					role.manager.setColor(new Color(0x66c2)).queue()
					// another retarded relic of discord4j:
					//role?.edit(new Color(0x0066c2), role.hoisted, role.name, role.permissions, role.mentionable)
				} catch (ignored) {}

				e.guild.defaultChannel.sendMessage format('Looks like I joined. Do |>help or ><help to learn more.')
				File a = new File("guilds/${e.guild.id}.json")
				if (!a.text) JSONUtil.dump(a, [:])
				KarmaFields.guildData[e.guild.id] = new DataFile(a)
			}
		}

		bot.listenerSystem.addListener(CommandBot.Events.EXCEPTION) { CommandEventData d ->
			if (d.exception instanceof InsufficientPermissionException){
				try{
					d.channel.sendMessage format('It seems I don\'t have permissions. Discord: ' +
							((InsufficientPermissionException) d.exception).errorMessage) queue()
				}catch (InsufficientPermissionException ignored){}
			}else{
				d.channel.sendMessage format(d.exception.toString()) queue()
				bot.log.warn "When running command: $d.content"
				bot.log.warn "Got exception: $d.exception"
				KarmaFields.exceptionLogFile.append(d.content + '\r\n')
				d.exception.printStackTrace(exceptionPw)
				exceptionPw.flush()
			}
		}
	}
}
