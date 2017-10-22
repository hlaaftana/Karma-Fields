package hlaaftana.karmafields.registers

import hlaaftana.karmafields.relics.CommandEventData
import hlaaftana.karmafields.relics.JSONUtil
import hlaaftana.karmafields.relics.CommandBot
import hlaaftana.karmafields.CommandRegister
import hlaaftana.karmafields.DataFile
import hlaaftana.karmafields.KarmaFields
import sx.blah.discord.api.events.IListener
import sx.blah.discord.handle.impl.events.guild.GuildCreateEvent
import sx.blah.discord.handle.obj.IRole
import sx.blah.discord.util.MissingPermissionsException

import java.awt.Color
import java.time.ZoneOffset

class BotListeners extends CommandRegister {
	@Lazy def exceptionPw = new PrintWriter(KarmaFields.exceptionLogFile)
	
	def register(){
		client.dispatcher.registerListener new IListener<GuildCreateEvent>(){
			@Override
			void handle(GuildCreateEvent e) {
				if (System.currentTimeMillis() -
						e.guild.getJoinTimeForUser(client.ourUser).toInstant(ZoneOffset.UTC).toEpochMilli()
							> 10000) return

				try {
					IRole role = e.guild.getRolesByName('|><|')[0]
					role?.edit(new Color(0x0066c2), role.hoisted, role.name, role.permissions, role.mentionable)
				} catch (ignored) {}

				e.guild.generalChannel.sendMessage format('Looks like I joined. Do |>help or ><help to learn more.')
				File a = new File("guilds/${e.guild.stringID}.json")
				if (!a.text) JSONUtil.dump(a, [:])
				KarmaFields.guildData[e.guild.stringID] = new DataFile(a)
			}
		}

		bot.listenerSystem.addListener(CommandBot.Events.EXCEPTION) { CommandEventData d ->
			if (d.exception instanceof MissingPermissionsException){
				try{
					d.channel.sendMessage format('It seems I don\'t have permissions. Discord: ' +
							((MissingPermissionsException) d.exception).errorMessage)
				}catch (MissingPermissionsException ignored){}
			}else{
				d.channel.sendMessage format(d.exception.toString())
				bot.log.warn "When running command: $d.content"
				bot.log.warn "Got exception: $d.exception"
				KarmaFields.exceptionLogFile.append(d.content + '\r\n')
				d.exception.printStackTrace(exceptionPw)
				exceptionPw.flush()
			}
		}
	}
}
