package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.logic.EventData
import hlaaftana.discordg.objects.Channel
import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.objects.Role
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util
import static java.lang.System.currentTimeMillis as now
import static groovyx.gpars.GParsPool.withPool

class AdministrativeCommands {
	static roleOptions = [
		color: { Role it ->
			!it.hoist && !it.permissionValue && it.colorValue &&
			(it.name ==~ /#?[A-Fa-f0-9]+/ ||
				MiscUtil.namedColors[it.name.toLowerCase().replaceAll(/\s+/, "")])
		},
		unused: { Role it ->
			!it.server.members*.object*.roles.flatten().contains(it.id)
		},
		no_overwrites: { Role it ->
			!it.server.channels*.permissionOverwriteMap.sum()[it.id]
		},
		no_permissions: { Role it ->
			!it.permissionValue
		},
		color_ignore_perms: { Role it ->
			!it.hoist && it.colorValue && (it.name ==~ /#?[A-Fa-f0-9]+/ ||
				MiscUtil.namedColors[it.name.toLowerCase().replaceAll(/\s+/, "")])
		}
	]

	static {
		roleOptions.colour = roleOptions.color
		roleOptions.colour_ignore_perms = roleOptions.color_ignore_perms
	}

	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = kf.client

		bot.command(["perms", "permissions"],
			group: "Administrative",
			hide: true,
			allowsPermissions: true){

		}

		bot.command(["modlog",
			~/modlog\-?/],
			group: "Administrative",
			description: "Adds the given channel as a mod log.",
			usages: [
				" (#channel or id or name)": "Adds the channel to the mod log channels.",
				"- (#channel or id or name)": "Removes the channel from the mod log channels."
			],
			allowsPermissions: true){
			def channel = message.channelMentions ?
				message.channelMentions[0] :
				message.server.channel(args)
			if (!author.permissions["manageChannels"]){
				decorate("Seems so you don't have Manage Channels so I can't let you do that.")
			}else if (channel){
				if (captures[0] == "-"){
					if (message.server.modlogs) message.server.modlogs -= channel.id
				}else{
					if (message.server.modlogs) message.server.modlogs += channel.id
					else message.server.modlogs = [channel.id]
				}
				decorate("Channel #${Util.formatFull(channel)} successfully ${captures[0] == '-' ? "removed as a" : "added as a"} mod log.")
			}else{
				if (message.server.modlogs){
					message.server.modlogs = (message.server.modlogs ?: []).findAll { message.server.channel(it) }
					decorate("The current modlog channels are " +
						message.server.modlogs.collect { '#' +
							Util.formatFull(message.server.channel(it)) }.join(", ") + ".")
				}else
					decorate("Invalid channel.")
			}
		}

		["guest", "member", "bot"].each { n ->
			bot.command(n + "role",
				group: "Administrative",
				description: "Sets or unsets the \"$n\" role for the server.",
				usages: [
					"": "Shows which role the $n role is set to.",
					" (@rolemention or id or name)": "Sets the $n role to the specified role.",
					" (invalid role identifier)": "Removes the $n role. This can be 0 or another number that isn't an ID or the name of a role that doesn't exist like 'remove'."
				],
				allowsPermissions: true){
				changeRole(it, n)
			}
		}

		["guest", "member"].each { n ->
			bot.command("auto$n",
				group: "Administrative",
				description: "Automatically gives new users the set $n role for the server.",
				usages: [
					"": "Returns whether auto${n}ing is currently on or off.",
					" on": "Turns auto${n}ing on.",
					" off": "Turns auto${n}ing off.",
					" toggle": "Toggles auto${n}ing."
				],
				allowsPermissions: true){
				autoRole(it, n)
			}
		}

		bot.command("autobot",
			group: "Administrative",
			description: "Automatically gives new bot accounts the set bot role for the server.",
			usages: [
				"": "Returns whether autobotting is currently on or off.",
				" on": "Turns autobotting on.",
				" off": "Turns autobotting off.",
				" toggle": "Toggles autobotting."
			],
			allowsPermissions: true){
			if (!args){
				decorate("Autobot is currently " +
					"${message.server.autobot ? "on" : "off"}.")
			}
			if (args.toLowerCase() in ["on", "true", "yes"]){
				if (!author.permissions["manageRoles"]){
					decorate("You do not have sufficient permissions.")
				}else{
					message.server."autobot" = true
					decorate("Autobot is now on.")
				}
			}
			if (args.toLowerCase() in ["off", "false", "no"]){
				if (!author.permissions["ban"]){
					decorate("You do not have sufficient permissions.")
				}else{
					message.server."autobot" = false
					decorate("Autobot is now off.")
				}
			}
			if (args.toLowerCase() == "toggle"){
				if (!author.permissions["ban"]){
					decorate("You do not have sufficient permissions.")
				}else{
					message.server.autobot = !message.server.autobot
					decorate("Autobot is now " +
						"${message.server.autobot ? "on" : "off"}.")
				}
			}
		}

		bot.command("guest",
			group: "Administrative",
			description: "Removes a user's member role. If a guest role is set, gives the user a guest role as well.",
			usages: [
				"": "Guests the latest user in the server.",
				" (@mentions)": "Guests every user mentioned."
			],
			allowsPermissions: true){
			if (!author.permissions["manageRoles"])
				decorate("You don't have sufficient permissions.")
			else if (!message.server.member_role)
				decorate("This server doesn't have a member role set.")
			else try{
				def guestRole = message.server.guest_role
				def memberRole = message.server.member_role
				def botRole = message.server.bot_role
				List<Member> members
				if (message.mentions.empty){
					members = [message.server.lastMember]
				}else{
					members = message.mentions.collect { message.server.member(it) }
				}
				String output = "The following members were guested by $author ($author.id):"
				members.each {
					it.editRoles(it.object.roles + guestRole - memberRole - botRole - null)
					output += "\n$it ($it.id)"
				}
				decorate(output)
				message.server.modlogs.collect { message.server.channel(it) }*.decorate(output)
			}catch (NoPermissionException ex){
				decorate("Failed to guest member(s). I don't seem to have permissions.")
			}
		}

		bot.command("member",
			group: "Administrative",
			description: "Gives the set member role to a user/users. Member role is set using <memberrole>.",
			usages: [
				"": "Members the latest user in the server.",
				" (@mentions)": "Members every user mentioned."
			],
			allowsPermissions: true){
			if (!author.permissions["manageRoles"])
				decorate("You don't have sufficient permissions.")
			else if (!message.server.member_role)
				decorate("This server doesn't have a member role set.")
			else try{
				def guestRole = message.server.guest_role
				def memberRole = message.server.member_role
				def botRole = message.server.bot_role
				List<Member> members
				if (message.mentions.empty){
					members = [message.server.lastMember]
				}else{
					members = message.mentions.collect { message.server.member(it) }
				}
				String output = "The following members were membered by $author ($author.id):"
				members.each {
					it.editRoles(it.object.roles + memberRole - guestRole - botRole)
					output += "\n$it ($it.id)"
				}
				decorate(output)
				message.server.modlogs.collect { message.server.channel(it) }*.decorate(output)
			}catch (NoPermissionException ex){
				decorate("Failed to member member(s). I don't seem to have permissions.")
			}
		}

		bot.command("bot",
			group: "Administrative",
			description: "Gives the set bot role to a user/users. Bot role is set using <botrole>.",
			usages: [
				"": "Bots the latest user in the server.",
				" (@mentions)": "Bots every user mentioned."
			],
			allowsPermissions: true){
			if (!author.permissions["manageRoles"]){
				decorate("You don't have sufficient permissions.")
			}else if (!message.server.bot_role){
				decorate("This server doesn't have a bot role set.")
			}else try{
				def guestRole = message.server.guest_role
				def memberRole = message.server.member_role
				def botRole = message.server.bot_role
				List<Member> members
				if (message.mentions.empty){
					members = [message.server.lastMember]
				}else{
					members = message.mentions.collect { message.server.member(it) }
				}
				String output = "The following members were botted by $author ($author.id):"
				members.each {
					it.editRoles(it.object.roles + botRole - guestRole - memberRole)
					output += "\n$it ($it.id)"
				}
				decorate(output)
				message.server.modlogs.collect { message.server.channel(it) }*.decorate(output)
			}catch (NoPermissionException ex){
				decorate("Failed to bot member(s). I don't seem to have permissions.")
			}
		}

		bot.command(["ban",
			~/ban<(\d+)>/],
			group: "Administrative",
			description: "Bans a given user/users.",
			usages: [
				"": "Bans the latest user in the server.",
				" (@mentions)": "Bans every user mentioned.",
				"<(days)>": "Bans the latest user and clears their messages for the past given number of days.",
				"<(days)> (@mentions)": "Bans every user mentioned and clears their messages for the past given number of days."
			],
			examples: [
				"",
				" @hlaaf#7436",
				"<3>",
				"<3> @hlaaf#7436"
			],
			allowsPermissions: true){
			if (author.permissions["ban"]){
				try{
					int days = captures[0].toInteger() ?: 0
					List<Member> members
					if (message.mentions.empty){
						if ((now() - message.server.lastMember.createTime.time) >= 60_000 && !args.contains("regardless")){
							decorate("The latest member, ${Util.formatFull(message.server.latestMember)}, joined more than 1 minute ago. To ban them regardless of that, type \"${usedTrigger}ban regardless\".")
							return
						}
						members = [message.server.lastMember]
					}else{
						members = message.mentions.collect { message.server.member(it) }
					}
					String output = "The following members were banned by $author ($author.id):"
					members.each {
						it.ban(days)
						output += "\n$it ($it.id)"
					}
					decorate(output)
					message.server.modlog("> $output".replace('\n', '\n> ').block("accesslog"))
				}catch (NoPermissionException ex){
					decorate("Failed to ban member(s). I don't seem to have permissions.")
				}
			}else decorate("You don't have permissions.")
		}


		bot.command(["softban",
			~/softban<(\d+)>/],
			group: "Administrative",
			description: "Quickly bans and unbans users. Usable to clearing a user's messages when also kicking them. Original command is in R. Danny, had to add it to this bot for a private server at first.",
			usages: [
				"": "Softbans the latest user in the server.",
				" (@mentions)": "Softbans every user mentioned.",
				"<(days)>": "Softbans the latest user and clears their messages for the past given number of days.",
				"<(days)> (@mentions)": "Softbans every user mentioned and clears their messages for the past given number of days."
			],
			examples: [
				"",
				" @hlaaf#7436",
				"<3>",
				"<3> @hlaaf#7436"
			],
			hide: true,
			allowsPermissions: true){
			if (author.permissions["ban"]){
				try{
					int days = captures[0].toInteger() ?: 7
					List<Member> members
					if (message.mentions.empty){
						if ((now() - message.server.lastMember.createTime.time) >= 60_000 && !args.contains("regardless")){
							decorate("The latest member, ${Util.formatFull(message.server.latestMember)}, joined more than 1 minute ago. To ban them regardless of that, type \"${usedTrigger}ban regardless\".")
							return
						}
						members = [message.server.lastMember]
					}else{
						members = message.mentions.collect { message.server.member(it) }
					}
					String output = "The following members were softbanned by $author ($author.id):"
					members.each {
						it.ban(days)
						it.unban()
						output += "\n$it ($it.id)"
					}
					decorate(output)
					message.server.modlog("> $output".replace('\n', '\n> ').block("accesslog"))
				}catch (NoPermissionException ex){
					decorate("Failed to softban member(s). I don't seem to have permissions.")
				}
			}else decorate("You don't have permissions.")
		}

		bot.command("purgeroles",
			group: "Administrative",
			description: "Purges roles with specific filters. If no filters are given, all filters will be used.\n\n" +
				"List of filters: ${this.roleOptions.keySet().join(", ")}",
			usages: [
				"": "Uses all filters.",
				" (filter1) (filter2)...": "Uses the given filters. Note: space separated."
			],
			examples: [
				"",
				" color",
				" unused no_overwrites"
			],
			allowsPermissions: true){
			if (!author.permissions["manageRoles"]){
				decorate("You don't have sufficient permissions. Ask a staff " +
					"member to call this command to do what you want.")
				return
			}
			try{
				def options = []
				if (args){
					boolean neg = false
					for (o in args.tokenize()){
						if (o == "!") neg = true
						else if (this.roleOptions[o])
							options.add(neg ? { this.roleOptions[0](it).not() } : this.roleOptions[o])
						else {
							sendMessage("Unknown filter: $o.\nList of filters: " +
								this.roleOptions.keySet().join(", "))
							return
						}
						if (neg) neg = false
					}
				}else{
					options = this.roleOptions.values()
				}
				List<Role> roles = message.server.roles
				roles.remove(message.server.defaultRole)
				options.each {
					roles = roles.findAll(it)
				}
				Message a = decorate("Deleting ${roles.size()} roles in about ${roles.size() / 2} seconds...")
				long s = now()
				if (roles){
					withPool {
						roles.dropRight(1).each {
							it.&delete.callAsync()
							Thread.sleep 500
						}
						roles.last().&delete.callAsync()
					}
				}
				a.edit("> Deleted all ${roles.size()} roles in ${(now() - s) / 1000} seconds.".block("accesslog"))
			}catch (NoPermissionException ex){
				decorate("I don't have permissions to manage roles.")
			}
		}

		bot.command(["filterroles", "purgedroles"],
			group: "Administrative",
			description: "Finds roles with specific filters. If no filters are given, all filters will be used.\n\n" +
				"List of filters: ${this.roleOptions.keySet().join(", ")}",
			usages: [
				"": "Uses all filters.",
				" (filter1) (filter2)...": "Uses the given filters. Note: space separated."
			],
			examples: [
				"",
				" color",
				" unused no_overwrites"
			]){
			def options = []
			if (args){
				boolean neg = false
				for (o in args.tokenize()){
					if (o == "!") neg = true
					else if (this.roleOptions[o])
						options.add(neg ? { this.roleOptions[0](it).not() } : this.roleOptions[o])
					else {
						decorate("Unknown filter: $o.\nList of filters: " +
							this.roleOptions.keySet().join(", "))
						return
					}
					if (neg) neg = false
				}
			}else{
				options = this.roleOptions.values()
			}
			List<Role> roles = message.server.roles
			roles.remove(message.server.defaultRole)
			options.each {
				roles = roles.findAll(it)
			}
			decorate("${roles.join(", ")}\n${roles.size()} total")
		}
	}

	static changeRole(EventData data, String name){
		if (!data.author.permissions["manageRoles"]){
			data.decorate("Unfortunately you don't seem to have Manage Roles.")
			return
		}
		if (!data.args){
			def role = data.message.server.role(
				data.message.server."${name}_role")
			if (role)
				data.decorate("The $name role is set to " +
					Util.formatFull(role) + '.')
			else if (data.message.server."${name}_role")
				data.decorate("The $name role is set to " +
					data.message.server."${name}_role" +
					" but the role with that ID doesn't seem to exist anymore.")
			else
				data.decorate("No $name role has been set.")
			return
		}
		Role role = data.message.roleMentions ?
			data.message.roleMentions[0] :
			data.message.server.role(data.args)
		if (role?.locked)
			data.decorate("Unfortunately that role is locked for me so I can't use it.")
		else if (role?.isLockedFor(data.author))
			data.decorate("Unfortunately the role is locked for you so I can't account you on permissions.")
		else if (role){
			data.message.server."${name}_role" = role.id
			data.decorate("Role \"$role\" ($role.id) successfully added as $name role.")
		}
		else{
			data.message.server."${name}_role" = null
			data.decorate("${name.capitalize()} role removed.")
		}
	}

	static autoRole(EventData data, String name){
		if (!data.args){
			data.decorate("Auto$name is currently " +
				"${data.message.server."auto$name" ? "on" : "off"}.")
		}
		if (data.args.toLowerCase() in ["on", "true", "yes"]){
			if (!data.author.permissions["manageRoles"]){
				data.decorate("You do not have sufficient permissions.")
			}else{
				data.message.server."auto$name" = true
				data.decorate("Auto$name is now on.")
			}
		}
		if (data.args.toLowerCase() in ["off", "false", "no"]){
			if (!data.author.permissions["ban"]){
				data.decorate("You do not have sufficient permissions.")
			}else{
				data.message.server."auto$name" = false
				data.decorate("Auto$name is now off.")
			}
		}
		if (data.args.toLowerCase() == "toggle"){
			if (!data.author.permissions["ban"]){
				data.decorate("You do not have sufficient permissions.")
			}else{
				data.message.server."auto$name" = !data.message.server."auto$name"
				data.decorate("Auto$name is now " +
					"${data.message.server."auto$name" ? "on" : "off"}.")
			}
		}
	}
}
