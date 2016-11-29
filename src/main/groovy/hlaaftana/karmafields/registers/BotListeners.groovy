package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.Events
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util

import static hlaaftana.discordg.util.WhatIs.whatis

class BotListeners {
	static register(KarmaFields kf){
		Client client = kf.client

		client.fields.lastWarned = false

		client.listener(Events.SERVER){
			try{
				server.role("|><|")?.edit(color: 0x0066c2)
			}catch (NoPermissionException ex){}
			server.sendMessage(
				"> Looks like I joined. Yay. Do |>help for commands.".block("accesslog"))
			File a = new File("guilds/${server.id}.json")
			a.createNewFile()
			if (a.text == ""){
				JSONUtil.dump(a, [:])
			}
		}

		client.listener(Events.ROLE_UPDATE){
			if (!role.locked ||
				(!Util.serverJson(json.guild_id).member_role &&
					!Util.serverJson(json.guild_id).bot_role &&
					!Util.serverJson(json.guild_id).guest_role)) return
			if (client.lastWarned) return
			client.lastWarned = true
			whatis(json.role.id){
				when(Util.serverJson(json.guild_id).member_role){
					server.sendMessage(("> This server's member role ($role) is now locked for me. " +
						"Please set a new member role using |>memberrole or give me a role with a higher position.").block("accesslog"))
				}

				when(Util.serverJson(json.guild_id).bot_role){
					server.sendMessage(("> This server's bot role ($role) is now locked for me. " +
						"Please set a new bot role using |>botrole or give me a role with a higher position.").block("accesslog"))
				}

				when(Util.serverJson(json.guild_id).guest_role){
					server.sendMessage(("> This server's guest role ($role) is now locked for me. " +
						"Please set a new guest role using |>guestrole or give me a role with a higher position.").block("accesslog"))
				}
			}
		}

		client.listener(Events.ROLE_DELETE){
			if (!Util.serverJson(json.guild_id).member_role &&
				!Util.serverJson(json.guild_id).bot_role &&
				!Util.serverJson(json.guild_id).guest_role) return
			whatis(json.role_id){
				when(Util.serverJson(json.guild_id).member_role){
					server.sendMessage(("> This server's member role ($role) is now deleted. " +
						"Please set a new member role using |>memberrole.").block("accesslog"))
				}

				when(Util.serverJson(json.guild_id).bot_role){
					server.sendMessage(("> This server's bot role ($role) is now deleted. " +
						"Please set a new bot role using |>botrole.").block("accesslog"))
				}

				when(Util.serverJson(json.guild_id).guest_role){
					server.sendMessage(("> This server's guest role ($role) is now deleted. " +
						"Please set a new guest role using |>guestrole.").block("accesslog"))
				}
			}
		}

		client.listener(Events.MESSAGE){
			kf.markovFileThreadPool.submit {
				File file = new File("markovs/${json.author.id}.txt")
				if (!file.exists()) file.createNewFile()
				file.append(json.content + "\n", "UTF-8")
			}
		}

		String latestId
		client.listener(Events.MEMBER){
			if (latestId == member.id) return
			else latestId = member.id
			if (server.id in ["145904657833787392", "195223058544328704", "198882877520216064"]){
				String message = """\
> A member just joined:
> Name: $member.name
> ID: $member.id
> Account creation time: $member.createTime
> To member, type |>member, to ban, type |>ban, to bot, type |>bot.""".block("accesslog")
				server.defaultChannel.sendMessage(message)
				server.modlog(message)
			}
			["guest", "member"].each { n ->
				if (server."auto$n" && !(member.bot && server.autobot)){
					try{
						member.addRole(server."${n}_role")
					}catch (NoPermissionException ex){
						server.sendMessage(
							"> I tried to auto$n ${Util.formatFull(member)} but I seem to not have permissions."
							.block("accesslog"))
						return
					}
					server.modlog("> Auto${n}ed ${Util.formatFull(member)}.".block("accesslog"))
				}
			}
			if (server.autobot && member.bot){
				try{
					member.addRole(server.bot_role)
				}catch (NoPermissionException ex){
					server.sendMessage(
						"> I tried to autobot ${Util.formatFull(member)} but I seem to not have permissions."
						.block("accesslog"))
					return
				}
				server.modlog("> Autobotted ${Util.formatFull(member)}.".block("accesslog"))
			}
		}
	}
}
