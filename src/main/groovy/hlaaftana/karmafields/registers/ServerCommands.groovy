package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.objects.Permissions
import hlaaftana.discordg.objects.Role
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.Arguments
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.PermissionParser
import hlaaftana.karmafields.Util
import static hlaaftana.discordg.util.WhatIs.whatis
import static groovyx.gpars.GParsPool.withPool
import static java.lang.System.currentTimeMillis as now

class ServerCommands {
	static jsonConversionTypes = [
		string: Closure.IDENTITY,
		number: { it.replace('_', '') as BigInteger },
		rational: { it.replace('_', '') as BigDecimal },
		boolean: Boolean.&parseBoolean,
		null: { null }
	]
	
	static colorNameTypes = [
		'#X': { '#' + Integer.toHexString(it).padLeft(6, '0').toUpperCase() },
		'#x': { '#' + Integer.toHexString(it).padLeft(6, '0') },
		'X': { Integer.toHexString(it).padLeft(6, '0').toUpperCase() },
		'x': { Integer.toHexString(it).padLeft(6, '0') }
	]
	
	static roleOptions = [
		color: { Role r ->
			!r.hoist && !r.permissionValue && r.colorValue &&
			(r.name ==~ /#?[A-Fa-f0-9]+/ ||
				MiscUtil.namedColors[r.name.toLowerCase().replaceAll(/\s+/, '')])
		},
		unused: { Role r ->
			!r.server.members*.object*.roles.flatten().contains(r.id)
		},
		no_overwrites: { Role r ->
			!r.server.channels*.overwriteMap.sum()[r.id]
		},
		no_permissions: { Role r ->
			!r.permissionValue
		},
		color_ignore_perms: { Role r ->
			!r.hoist && r.colorValue && (r.name ==~ /#?[A-Fa-f0-9]+/ ||
				MiscUtil.namedColors[r.name.toLowerCase().replaceAll(/\s+/, '')])
		}
	]

	static {
		roleOptions.colour = roleOptions.color
		roleOptions.colour_ignore_perms = roleOptions.color_ignore_perms
		[str: 'string', num: 'number', rat: 'rational', bool: 'boolean'].each { k, v ->
			jsonConversionTypes[k] = jsonConversionTypes[v]
		}
	}
	
	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = kf.client

		bot.command(['permissions', 'perms'],
			group: 'Server',
			description: 'Sets permission rules for commands (server-specific). ' +
				'Administrator permission is needed unless the perms for this command are changed.',
			usages: [
				' for (command)': 'Sends you the permission rules for the given command in private.',
				' list names': 'Lists the permission name aliases.',
				' list cmds|commands': 'Lists the commands with set rules in the server.',
				' set (command) (text or file)': 'Sets the permission rules to the given text.',
				' append (command) (text or file)': 'Appends the given text to the permission rules.',
				' delete (command)': 'Deletes permissions for the given command.',
				' test (text or file)': 'Tests the given rules for the message.'
			],
			defaultPerms: 'can administrator',
			serverOnly: true){
			def a = new Arguments(args)
			whatis(a.next()){
				when('for'){
					def name = kf.findCommand(a.rest).alias.toString()
					def text = server.perms?.getAt(name)
					if (text)
						author.sendMessage(text.block('groovy'))
					else
						formatted("I've found no permissions for $usedTrigger$a.rest.")
				}
				when('list'){
					whatis(a.next()){
						when('names'){
							formatted(Permissions.BitOffsets.values()
								.collect { "$it: ${it.locals.join(', ')}" }.join('\n'))
						}
						when(['cmds', 'commands']){
							def list = server.perms?.keySet()
							if (list)
								author.sendMessage(list.join(', ').block('accesslog'))
							else
								formatted('No commands have special permissions in this server.')
						}
					}
				}
				when('delete'){
					def command = kf.findCommand(a.next()).alias.toString()
					new File("guilds/{$serverId}.json").with {
						write(JSONUtil.parse(text).with { perms?.remove(command) })
					}
					formatted('Successfully deleted command permissions.')
				}
				when('set'){
					def command = kf.findCommand(a.next()).alias.toString()
					def text = json.attachments ?
						message.attachment.inputStream.text :
						a.rest.trim()
					try{
						PermissionParser.from(text).apply(member, message)
					}catch (ex){
						formatted("Caught exception: $ex\nPlease fix your permissions.")
						return
					}
					Util.modifyServerJson(server, [perms: [(command): text]])
					formatted('Successfully set command permissions.')
				}
				when('append'){
					def command = kf.findCommand(a.next()).alias.toString()
					def old = server.perms?.getAt(command) ?: ""
					def text = (old + "\n" + (json.attachments ?
						message.attachment.inputStream.text :
						a.rest)).trim()
					try{
						PermissionParser.from(text).apply(member, message)
					}catch (ex){
						formatted("Caught exception: $ex\nPlease fix your permissions.")
						return
					}
					Util.modifyServerJson(server, [perms: [(command): text]])
					formatted('Successfully appended command permissions.')
				}
				when('test'){
					def text = json.attachments ?
						message.attachment.inputStream.text :
						a.rest.trim()
					try{
						formatted(PermissionParser.from(text).apply(member, message))
					}catch (ex){
						formatted(ex)
					}
				}
			}
		}

		bot.command(['modlog',
			~/modlog(\-)?/],
			group: 'Server',
			description: 'Adds the given channel as a mod log.',
			usages: [
				' (#channel or id or name)': 'Adds the channel to the mod log channels.',
				'- (#channel or id or name)': 'Removes the channel from the mod log channels.'
			],
			defaultPerms: 'can manage channels',
			serverOnly: true){
			def channel = message.channelMentions ?
				message.channelMentions[0] :
				server.channel(args)
			if (channel){
				if (captures[0] == '-'){
					if (server.modlogs) server.modlogs -= channel.id
				}else{
					if (server.modlogs) server.modlogs += channel.id
					else server.modlogs = [channel.id]
				}
				formatted("Channel ${channel.inspect()} successfully ${captures[0] == '-' ? "removed as a" : "added as a"} mod log.")
			}else{
				if (server.modlogs){
					server.modlogs = (server.modlogs ?: []).findAll { server.channel(it) }
					formatted('The current modlog channels are ' +
						server.modlogs.collect {
							server.channel(it).inspect() }.join(', ') + '.')
				}else formatted('Invalid channel.')
			}
		}

		["guest", "member", "bot"].each { n ->
			bot.command(["${n}role",
				~(n + /role(\-)?/)],
				group: 'Server',
				description: "Sets or unsets the \"$n\" role for the server.",
				usages: [
					'': "Shows which role the $n role is set to.",
					' (@rolemention or id or name)': "Sets the $n role to the specified role.",
					'-': "Removes the $n role."
				],
				defaultPerms: "can manage roles",
				serverOnly: true){
				if (captures[0] == '-'){
					server."${n}_role" = null
					formatted("Current $n role removed.")
					return
				}
				if (!args){
					def role = server.role(
						server."${n}_role")
					if (role)
						formatted("The $n role is set to " +
							role.inspect() + '.')
					else if (server."${n}_role")
						formatted("The $n role is set to " +
							server."${n}_role" +
							' but the role with that ID doesn\'t seem to exist anymore.')
					else
						formatted("No $n role has been set.")
					return
				}
				Role role = message.roleMentions ?
					message.roleMentions[0] :
					server.role(args)
				if (!role)
					formatted('Invalid role.')
				else if (role?.locked)
					formatted('Unfortunately that role is locked for me so I can\'t use it.')
				else if (role?.isLockedFor(member))
					formatted('Unfortunately the role is locked for you so I can\'t account you on permissions.')
				else if (role){
					server."${n}_role" = role.id
					formatted("Role \"$role\" ($role.id) successfully added as $n role.")
				}
			}
		}

		['guest', 'member', 'bot'].each { n ->
			bot.command("auto$n",
				group: 'Server',
				description: 'Automatically gives new users the set $n role for the server.',
				usages: [
					"": "Returns whether auto${n}ing is currently on or off.",
					" on": "Turns auto${n}ing on.",
					" off": "Turns auto${n}ing off.",
					" toggle": "Toggles auto${n}ing."
				],
				defaultPerms: 'can manage roles',
				serverOnly: true){
				if (!args){
					formatted("Auto$n is currently " +
						server."auto$n" ? 'on.' : 'off.')
				}
				if (args.toLowerCase() in ['on', 'true', 'yes']){
					server."auto$n" = true
					formatted("Auto$n is now on.")
				}
				if (args.toLowerCase() in ['off', 'false', 'no']){
					server."auto$n" = false
					formatted("Auto$n is now off.")
				}
				if (args.toLowerCase() == 'toggle'){
					server."auto$n" = !server."auto$n"
					formatted("Auto$n is now " +
						server."auto$n" ? 'on.' : 'off.')
				}
			}
		}

		bot.command('guest',
			group: 'Server',
			description: 'Removes a user\'s member role. If a guest role is set, gives the user a guest role as well.',
			usages: [
				'': 'Guests the latest user in the server.',
				' (@mentions)': 'Guests every user mentioned.'
			],
			defaultPerms: '''\
can manage roles
set role server.guest_role
lockedrole role
not''',
			serverOnly: true){
			if (!server.member_role)
				formatted('This server doesn\'t have a member role set.')
			else try{
				def guestRole = server.guest_role
				def memberRole = server.member_role
				def botRole = server.bot_role
				List<Member> members
				boolean errored
				if (message.mentions.empty){
					if (args){
						Arguments.splitArgs(args).each {
							def x = server.member(it)
							if (!x){
								formatted("Invalid member '$it'.")
								errored = true
							}
						}
					}else members = [server.lastMember]
				}else{
					members = message.mentions.collect { server.member(it) }
				}
				if (errored) return
				String output = "The following members were guested by $member ($member.id):"
				members.each {
					it.editRoles(it.object.roles + guestRole - memberRole - botRole - null)
					output += "\n$it ($it.id)"
				}
				formatted(output)
				server.modlogs.collect { server.channel(it) }*.formatted(output)
			}catch (NoPermissionException ex){
				formatted('Failed to guest member(s). I don\'t seem to have permissions.')
			}
		}

		bot.command('member',
			group: 'Server',
			description: 'Gives the set member role to a user/users. Member role is set using <memberrole>.',
			usages: [
				'': 'Members the latest user in the server.',
				' (@mentions)': 'Members every user mentioned.'
			],
			defaultPerms: '''\
can manage roles
set role server.member_role
lockedrole role
not''',
			serverOnly: true){
			if (!server.member_role)
				formatted('This server doesn\'t have a member role set.')
			else try{
				def guestRole = server.guest_role
				def memberRole = server.member_role
				def botRole = server.bot_role
				List<Member> members
				if (message.mentions.empty){
					members = [server.lastMember]
				}else{
					members = message.mentions.collect { server.member(it) }
				}
				String output = "The following members were membered by $member ($member.id):"
				members.each {
					it.editRoles(it.object.roles + memberRole - guestRole - botRole)
					output += "\n$it ($it.id)"
				}
				formatted(output)
				server.modlogs.collect { server.channel(it) }*.formatted(output)
			}catch (NoPermissionException ex){
				formatted('Failed to member member(s). I don\'t seem to have permissions.')
			}
		}

		bot.command('bot',
			group: 'Server',
			description: 'Gives the set bot role to a user/users. Bot role is set using <botrole>.',
			usages: [
				'': 'Bots the latest user in the server.',
				' (@mentions)': 'Bots every user mentioned.'
			],
			defaultPerms: '''\
can manage roles
set role server.bot_role
lockedrole role
not''',
			serverOnly: true){
			if (!server.bot_role){
				formatted('This server doesn\'t have a bot role set.')
			}else try{
				def guestRole = server.guest_role
				def memberRole = server.member_role
				def botRole = server.bot_role
				List<Member> members
				if (message.mentions.empty){
					members = [server.lastMember]
				}else{
					members = message.mentions.collect { server.member(it) }
				}
				String output = "The following members were botted by $member ($member.id):"
				members.each {
					it.editRoles(it.object.roles + botRole - guestRole - memberRole)
					output += "\n$it ($it.id)"
				}
				formatted(output)
				server.modlogs.collect { server.channel(it) }*.formatted(output)
			}catch (NoPermissionException ex){
				formatted('Failed to bot member(s). I don\'t seem to have permissions.')
			}
		}
		
		bot.command(['votemember', 'vm',
			~/(?:votemember|vm)(\-|\\)?/],
			group: 'Server',
			description: 'Votes for a user to become a member. ' +
				'Accounts who joined the server more than 12 hours ago ' +
				'need to join again for a vote.',
			usages: [
				'': 'Votes for the newest user.',
				' (@mentions)': 'Votes for the mentioned user(s).',
				'- ...': 'Votes against the user(s).',
				'\\ ...': 'Redacts your vote for the user(s).'
			],
			defaultPerms: '',
			serverOnly: true){
			if (!server.member_role){
				formatted('This server doesn\'t have a member role set.')
				return
			}
			if (!server.member_vote_enabled){
				formatted('This command isn\'t enabled for this server.')
				return
			}
			List<Member> members = (message.mentions(true) ?: [server.latestMember]) - null
			if (captures[0] == '\\'){
				def x = Util.state
				members.each {
					x.member_votes?.get(server.id)?.get(it.id)?.remove(json.author.id)
				}
				JSONUtil.dump(new File('state.json'), x)
				formatted('Done.')
				return
			}else{
				for (m in members){
					if ((timeReceived - m.joinedAt.time) > 43200000){
						formatted("Member ${m.inspect()} has been in this server for too long.")
						return
					}
					Util.modifyState(member_votes: [(server.id): [(m.id):
						[(json.author.id): captures[0] == '-' ? -1 : 1]]])
				}
				formatted("Voted for ${members*.inspect().join(', ')}.")
				def y = Util.state
				def x = y.member_votes[server.id]
				def toMember = x.findAll { k, v -> v.values().sum() >=
					(server.member_vote_count ?: 5) }.keySet()
				def toKick = x.findAll { k, v -> v.values().sum() <=
					(server.kick_member_vote_count ?: -5) }.keySet()
				[toMember, toKick]*.each { y.member_votes[server.id].remove(it) }
				JSONUtil.dump(new File('state.json'), y)
				toMember = toMember.collect { server.member(it) }
				toKick = toKick.collect { server.member(it) }
				def guestRole = server.guest_role
				def memberRole = server.member_role
				def botRole = server.bot_role
				if (toMember){
					String output = 'The following members were membered via a vote:'
					toMember.each {
						it.editRoles(it.object.roles + memberRole - guestRole - botRole)
						output += "\n$it ($it.id)"
					}
					formatted(output)
					server.modlogs.collect { server.channel(it) }*.formatted(output)
				}
				if (toKick){
					String output = 'The following members were kicked following a vote for member:'
					toKick.each {
						it.kick()
						output += "\n$it ($it.id)"
					}
					formatted(output)
					server.modlogs.collect { server.channel(it) }*.formatted(output)
				}
			}
		}

		bot.command(['ban',
			~/ban<(\d+)>/],
			group: 'Server',
			description: 'Bans a given user/users.',
			usages: [
				'': 'Bans the latest user in the server.',
				' (@mentions)': 'Bans every user mentioned.',
				'<(days)>': 'Bans the latest user and clears their messages for the past given number of days.',
				'<(days)> (@mentions)': 'Bans every user mentioned and clears their messages for the past given number of days.'
			],
			examples: [
				'',
				' @hlaaf#7436',
				'<3>',
				'<3> @hlaaf#7436'
			],
			defaultPerms: 'can ban',
			serverOnly: true){
			try{
				int days = captures[0].toInteger() ?: 0
				List<Member> members
				if (message.mentions.empty){
					if ((now() - server.lastMember.createdAt.time) >= 60_000 && !args.contains("regardless")){
						formatted("The latest member, ${server.latestMember.inspect()}, joined more than 1 minute ago. To ban them regardless of that, type \"${usedTrigger}ban regardless\".")
						return
					}
					members = [server.lastMember]
				}else{
					members = message.mentions.collect { server.member(it) }
				}
				String output = "The following members were banned by $member ($member.id):"
				members.each {
					it.ban(days)
					output += "\n$it ($it.id)"
				}
				formatted(output)
				server.modlog("> $output".replace('\n', '\n> ').block('accesslog'))
			}catch (NoPermissionException ex){
				formatted('Failed to ban member(s). I don\'t seem to have permissions.')
			}
		}


		bot.command(['softban',
			~/softban<(\d+)>/],
			group: 'Server',
			description: 'Quickly bans and unbans users. Usable to clearing a user\'s messages when also kicking them. Original command is in R. Danny, had to add it to this bot for a private server at first.',
			usages: [
				'': 'Softbans the latest user in the server.',
				' (@mentions)': 'Softbans every user mentioned.',
				'<(days)>': 'Softbans the latest user and clears their messages for the past given number of days.',
				'<(days)> (@mentions)': 'Softbans every user mentioned and clears their messages for the past given number of days.'
			],
			examples: [
				'',
				' @hlaaf#7436',
				'<3>',
				'<3> @hlaaf#7436'
			],
			hide: true,
			defaultPerms: 'can ban',
			serverOnly: true){
			try{
				int days = captures[0].toInteger() ?: 7
				List<Member> members
				if (message.mentions.empty){
					if ((now() - server.lastMember.createdAt.time) >= 60_000 && !args.contains("regardless")){
						formatted("The latest member, ${server.latestMember.inspect()}, joined more than 1 minute ago. To ban them regardless of that, type \"${usedTrigger}ban regardless\".")
						return
					}
					members = [server.lastMember]
				}else{
					members = message.mentions.collect { server.member(it) }
				}
				String output = "The following members were softbanned by $member ($member.id):"
				members.each {
					it.ban(days)
					it.unban()
					output += "\n$it ($it.id)"
				}
				formatted(output)
				server.modlog("> $output".replace('\n', '\n> ').block('accesslog'))
			}catch (NoPermissionException ex){
				formatted("Failed to softban member(s). I don't seem to have permissions.")
			}
		}

		bot.command('purgeroles',
			group: 'Server',
			description: 'Purges roles with specific filters. If no filters are given, all filters will be used.\n\n' +
				'List of filters: ' + roleOptions.keySet().join(', '),
			usages: [
				'': 'Uses all filters.',
				' (filter1) (filter2)...': 'Uses the given filters. Note: space separated.'
			],
			examples: [
				'',
				' color',
				' unused no_overwrites'
			],
			defaultPerms: 'can manage roles',
			serverOnly: true){
			try{
				def options = []
				if (args){
					boolean neg = false
					for (o in args.tokenize()){
						if (o == '!') neg = true
						else{
							if (roleOptions[o])
								options.add(neg ? { !roleOptions[o](it) } : roleOptions[o])
							else {
								sendMessage("Unknown filter: $o.\nList of filters: " +
									roleOptions.keySet().join(", "))
								return
							}
							if (neg) neg = false
						}
					}
				}else{
					options = roleOptions.values()
				}
				List<Role> roles = server.roles
				roles.remove(server.defaultRole)
				options.each {
					roles = roles.findAll(it)
				}
				Message a = formatted("Deleting ${roles.size()} roles in about ${roles.size() / 2} seconds...")
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
				formatted('I don\'t have permissions to manage roles.')
			}
		}

		bot.command(['filterroles', 'purgedroles'],
			group: 'Server',
			description: 'Finds roles with specific filters. If no filters are given, all filters will be used.\n\n' +
				'List of filters: ' + roleOptions.keySet().join(', '),
			usages: [
				'': 'Uses all filters.',
				' (filter1) (filter2)...': 'Uses the given filters. Note: space separated.'
			],
			examples: [
				'',
				' color',
				' unused no_overwrites'
			],
			serverOnly: true){
			def options = []
			if (args){
				boolean neg = false
				for (o in args.tokenize()){
					if (o == '!') neg = true
					else{
						if (roleOptions[o])
							options.add(neg ? { !roleOptions[o](it) } : roleOptions[o])
						else {
							sendMessage("Unknown filter: $o.\nList of filters: " +
								roleOptions.keySet().join(", "))
							return
						}
						if (neg) neg = false
					}
				}
			}else{
				options = roleOptions.values()
			}
			List<Role> roles = server.roles
			roles.remove(server.defaultRole)
			options.each {
				roles = roles.findAll(it)
			}
			formatted("${roles.join(", ")}\n${roles.size()} total")
		}
		
		bot.command(['color', 'colour',
			~/colou?r<(#?x|X)>/],
			group: 'Server',
			description: 'Creates/reuses a role that has a color and no other special aspect to it.',
			usages: [
				'<[#]x|X> ...': 'Gives the color role with a specific type of name. ' +
					'This is case sensitive, and can be one of ' +
					colorNameTypes.keySet().join(', '),
				' (hexadecimal number)': 'Uses a hexadecimal number as the color',
				' (rgb number tuple)': 'Uses numbers separated by commas as RGB numbers for the color.',
				' (named color)': 'Uses a human readable color (spaces ignored) as the color. (list: \'http://www.december.com/html/spec/colorsvg.html\')',
				' random': 'Uses a random color between 0x000000 and 0xFFFFFF. That\'s the infamous 16.7 million colors.'
			],
			examples: [
				' fb4bf4',
				' 44, 43, 103',
				' red',
				' navy blue'
			],
			defaultPerms: 'can send messages',
			serverOnly: true){ d ->
			def r = Util.resolveColor(d)
			if (r instanceof String){
				formatted(r)
				return
			}
			int color = r
			if (color > 0xFFFFFF){
				formatted('Color is bigger than #FFFFFF.')
				return
			}
			try{
				def nt = colorNameTypes[captures[0] ?: server.color_name_type ?: 'x'] ?:
					colorNameTypes['x']
				def a = server.defaultRole.permissionValue
				Map groupedRoles = member.roles.groupBy {
					it.hoist || !(it.name ==~ /#?[0-9a-fA-F]+/) ||
					it.name.contains(" ") ||
					it.permissionValue > a ||
					it.permissionValue != 0 ? 1 : 0 }
				// 0 is color roles, 1 is the others
				boolean created
				Role role
				if (color){
					role = server.roles.find {
						!it.hoist && it.permissionValue <= a &&
							it.name ==~ /#?[0-9a-fA-F]+/ &&
							it.colorValue == color
					} ?: { ->
						created = true
						server.createRole(name:
							nt(color),
							color: color,
							permissions: 0)
					}()
				}
				member.editRoles((groupedRoles[1] ?: []) + (color ? role : []))
				def m = color ? "Color ${created ? "created" : "found"} and added." :
					'That color is the default color, so I just removed your other colors.'
				if (groupedRoles[0])
					m += "\nRemoved roles: ${groupedRoles[0]*.name.join(", ")}"
				formatted(m)
			}catch (NoPermissionException ex){
				formatted('I don\'t have sufficient permissions. I need Manage Roles.')
			}
		}
		
		bot.command(['colornametype', 'colournametype'],
			group: 'Server',
			description: 'Sets the default color role naming type for this server.',
			usages: [
				'': 'Sends the current naming type.',
				' (type)': 'Sets the naming type to the given one. Can be one of ' +
					colorNameTypes.keySet().join(', ')
			],
			defaultPerms: 'can manage roles',
			serverOnly: true){
			if (args){
				if (!colorNameTypes.containsKey(args))
					formatted('The given name type does not exist. Full list: ' +
						colorNameTypes.keySet().join(', '))
				else{
					Util.modifyServerJson(server, [color_name_type: args])
					formatted('Color name type set.')
				}
			}else{
				def x = server.color_name_type
				if (x) formatted("The current color name type for this server is '$x'.")
				else formatted('There is no color name type set for this server.')
			}
		}
		
		bot.command('modify',
			group: 'Server',
			description: 'Modifies server specific data. This command should require strict permissions.',
			usages: [
				'': 'Will send the current JSON file for the server.',
				' json (json or file)': 'Adds the JSON object to the server data. Must be an object.',
				' property (name) (type) (value)': 'Sets a property with the given ' +
					'value considering the given type. The type can be one of ' +
					jsonConversionTypes.keySet().join(', ')
			],
			defaultPerms: "can administrator",
			serverOnly: true){
			if (!args){
				author.sendFile(new File("guilds/${serverId}.json"))
				return
			}
			Arguments a = new Arguments(args)
			whatis(a.next()){
				when('json'){
					def text = json.attachments ? message.attachment.inputStream.text : a.rest
					def json
					try {
						json = JSONUtil.parse(text)
					}catch (ex){
						formatted("Invalid JSON. Exception:\n$ex")
						return
					}
					if (!(json instanceof Map)){
						formatted('JSON data must be an object.')
						return
					}
					def old = new File("guilds/${serverId}.json").bytes
					Util.modifyServerJson(serverId, json)
					author.sendFile(old, "${serverId}.json",
						content: 'Successfully added JSON data. Here\'s the old JSON file:')
				}
				when('property'){
					def (name, type, value) = [a.next(), a.next(), a.rest]
					if (!jsonConversionTypes.containsKey(type)){
						formatted('Invalid type. Types: ' + jsonConversionTypes.keySet().join(", "))
						return
					}
					try{
						value = jsonConversionTypes[type](value ?: null)
					}catch (ex){
						formatted("Error converting value to type:\n$ex")
						return
					}
					def old = new File("guilds/${serverId}.json").bytes
					server."$name" = value
					author.sendFile(old, "${serverId}.json",
						content: 'Successfully modified property. Here\'s the old JSON file:')
				}
			}
		}
	}
}
