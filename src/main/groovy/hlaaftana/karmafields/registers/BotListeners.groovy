package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util

import static hlaaftana.discordg.util.WhatIs.whatis

class BotListeners {
	static register(KarmaFields kf){
		Client client = kf.client

		client.fields.lockedRoleLastWarned = [:]

		client.listener("server"){
			MiscUtil.defaultValueOnException {
				server.role("|><|")?.edit(color: 0x0066c2)
			}

			server.decorate("Looks like I joined. Yay. Do |>help or ><help for commands.")
			File a = new File("guilds/${server.id}.json")
			a.createNewFile()
			if (a.text == ""){
				JSONUtil.dump(a, [:])
			}
		}

		client.listener("role_update"){
			if (!server.member_role &&
				!server.bot_role &&
				!server.guest_role) return
			whatis(role.id){
				["member", "guest", "bot"].each { x ->
					when(server."${x}_role"){
						if (role.locked){
							if (!client.lockedRoleLastWarned[role.id]){
								server.decorate("This server's $x role " +
									"(${Util.formatFull(role)}) is now " +
									"locked for me. Please set a new $x role " +
									"using |>${x}role or give me a role with " +
									"a higher position.")
								client.lockedRoleLastWarned[role.id] = true
							}
						}else client.lockedRoleLastWarned[role.id] = false
					}
				}
			}
		}

		client.listener("role_delete"){
			if (!server.member_role &&
				!server.bot_role &&
				!server.guest_role) return
			whatis(json.role_id){
				["member", "guest", "bot"].each { x ->
					when(server."${x}_role"){
						server.decorate("This server's $x role (${Util.formatFull(role)})" +
							" is now deleted. " +
							"Please set a new $x role using |>${x}role.")
					}
				}
			}
		}

		kf.bot.listenerSystem.addListener(CommandBot.Events.NO_COMMAND){ d ->
			kf.markovFileThreadPool.submit {
				File file = new File("markovs/${d.json.author.id}.txt")
				if (!file.exists()) file.createNewFile()
				file.append(d.json.content + "\n", "UTF-8")
			}
		}

		client.listener("member"){
			["guest", "member"].each { n ->
				if (server."auto$n" && !(member.bot && server.autobot)){
					try{
						member.addRole(server."${n}_role")
					}catch (NoPermissionException ex){
						server.decorate("I tried to auto$n ${Util.formatFull(member)} " +
							"but I seem to not have permissions.")
						return
					}
					server.modlog("Auto${n}ed ${Util.formatFull(member)}.")
				}
			}
			if (server.autobot && member.bot){
				try{
					member.addRole(server.bot_role)
				}catch (NoPermissionException ex){
					server.decorate("I tried to autobot ${Util.formatFull(member)} " +
						"but I seem to not have permissions.")
					return
				}
				server.decorate("> Autobotted ${Util.formatFull(member)}.".block("accesslog"))
			}
		}
	}
}
