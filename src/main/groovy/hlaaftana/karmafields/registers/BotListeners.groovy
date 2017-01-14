package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.exceptions.MessageInvalidException
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util

import static hlaaftana.discordg.util.WhatIs.whatis

class BotListeners {
	static register(KarmaFields kf){
		Client client = kf.client

		Util.modifyState(lockedRoleLastWarned: [:])

		client.listen('server'){
			MiscUtil.defaultValueOnException {
				server.role('|><|')?.edit(color: 0x0066c2)
			}

			server.formatted('Looks like I joined. Yay. Do |>help or ><help for commands.')
			File a = new File("guilds/${server.id}.json")
			a.createNewFile()
			if (a.text == ''){
				JSONUtil.dump(a, [:])
			}
		}

		client.listen('role_update'){
			if (!server.member_role &&
				!server.bot_role &&
				!server.guest_role) return
			whatis(role.id){
				['member', 'guest', 'bot'].each { x ->
					when(server."${x}_role"){
						if (role.locked){
							if (!Util.state.lockedRoleLastWarned[role.id]){
								server.formatted("This server's $x role " +
									"(${role.inspect()}) is now " +
									"locked for me. Please set a new $x role " +
									"using |>${x}role or give me a role with " +
									'a higher position.')
								Util.modifyState(lockedRoleLastWarned: [(role.id): true])
							}
						}else{
							Map s = Util.state.clone()
							s.lockedRoleLastWarned.remove(role.id)
							JSONUtil.dump('state.json', s)
						}
					}
				}
			}
		}

		client.listen('role_delete'){
			if (!server.member_role &&
				!server.bot_role &&
				!server.guest_role) return
			whatis(json.role_id){
				['member', 'guest', 'bot'].each { x ->
					when(server."${x}_role"){
						server.formatted("This server's $x role (${role.inspect()})" +
							" is now deleted. " +
							"Please set a new $x role using |>${x}role.")
					}
				}
			}
		}

		kf.bot.listenerSystem.addListener(CommandBot.Events.NO_COMMAND){ d ->
			kf.markovFileThreadPool.submit {
				if (d.json.channel_id in JSONUtil.parse(new File(
					'markovdata.json'))[d.json.author.id]?.blacklist) return
				File file = new File("markovs/${d.json.author.id}.txt")
				if (!file.exists()) file.createNewFile()
				file.append(d.json.content + '\r\n', 'UTF-8')
			}
		}
		
		kf.bot.listenerSystem.listen(CommandBot.Events.EXCEPTION){
			if (exception in MessageInvalidException){
				channel.formatted('Unfortunately my message was too long.')
			}
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
			}
		}

		client.listen('member'){
			['guest', 'member'].each { n ->
				if (server."auto$n" && !(member.bot && server.autobot)){
					try{
						member.addRole(server."${n}_role")
					}catch (NoPermissionException ex){
						server.formatted("I tried to auto$n ${member.inspect()} " +
							'but I seem to not have permissions.')
						return
					}
					server.modlog("Auto${n}ed ${member.inspect()}.")
				}
			}
			if (server.autobot && member.bot){
				try{
					member.addRole(server.bot_role)
				}catch (NoPermissionException ex){
					server.formatted("I tried to autobot ${member.inspect()} " +
						'but I seem to not have permissions.')
					return
				}
				server.formatted("Autobotted ${member.inspect()}.")
			}
		}
	}
}
