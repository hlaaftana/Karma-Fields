package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.exceptions.MessageInvalidException
import hlaaftana.discordg.logic.EventData
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.CommandRegister
import hlaaftana.karmafields.DataFile;
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util
import hlaaftana.karmafields.kismet.Block
import hlaaftana.karmafields.kismet.Kismet;

import static hlaaftana.discordg.util.WhatIs.whatis

class BotListeners extends CommandRegister {
	static Map eventNames = [
		join: 'member',
		leave: 'member_leave'
	]
	@Lazy def exceptionPw = new PrintWriter(KarmaFields.exceptionLogFile)
	
	def register(){
		client.listen('server'){
			MiscUtil.defaultValueOnException {
				server.role('|><|')?.edit(color: 0x0066c2)
			}

			server.formatted('Looks like I joined. Do |>help or ><help to learn more.')
			File a = new File("guilds/${server.id}.json")
			a.createNewFile()
			if (a.text == ''){
				JSONUtil.dump(a, [:])
			}
			KarmaFields.guildData[server.id] = new DataFile(a)
		}

		bot.listenerSystem.listen(CommandBot.Events.EXCEPTION){
			if (exception in NoPermissionException){
				try{
					channel.formatted('It seems I don\'t have permissions. ' +
						(exception.response.message ?
							'Discord says ' + exception.response.message :
							'Discord didn\'t say how.'))
				}catch (NoPermissionException ex){
					println 'Don\'t have message permissions in ' +
						channel ' in ' + server
				}
			}else{
				channel.formatted(exception.toString())
				bot.log.warn "When running command: $message.content"
				bot.log.warn "Got exception: $exception"
				KarmaFields.exceptionLogFile.append(message.content + '\r\n')
				exception.printStackTrace(exceptionPw)
				exceptionPw.flush()
			}
		}
		
		eventNames.each { k, v ->
			client.listen(v){ e ->
				server.guildData().listeners.findAll { _, it -> it.event == k }.each { _, it ->
					Message d = new Message(client, it.message_object)
					Block x = KarmaFields.parseDiscordKismet(it.code, [__original_message:
                          Kismet.model(d)] + e.collectEntries { a, b -> [(a): Kismet.model(b)] })
					try{
						x.evaluate()
					}catch (ex){
						server.formatted "Error with listener $_:\n$ex"
					}
				}
			}
		}
	}
}
