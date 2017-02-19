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
import hlaaftana.karmafields.CommandRegister;
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util
import hlaaftana.karmafields.kismet.Block
import hlaaftana.karmafields.kismet.Kismet;

import static hlaaftana.discordg.util.WhatIs.whatis
import static groovyx.gpars.GParsPool.withPool
import static java.lang.System.currentTimeMillis as now

class ServerCommands extends CommandRegister {
	static jsonConversionTypes = [
		string: Closure.IDENTITY,
		number: { it.replace('_', '') as BigInteger },
		decimal: { it.replace('_', '') as BigDecimal },
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
				r.name ==~ /#?[A-Fa-f0-9]+/
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
			!r.hoist && r.colorValue && r.name ==~ /#?[A-Fa-f0-9]+/
		}
	]

	static {
		roleOptions.colour = roleOptions.color
		roleOptions.colour_ignore_perms = roleOptions.color_ignore_perms
		[str: 'string', num: 'number', dec: 'decimal', bool: 'boolean'].each { k, v ->
			jsonConversionTypes[k] = jsonConversionTypes[v]
		}
	}
	
	def command(Map x = [:], ...args){ bot.command(x + [group: 'Server'], *args) }
	def register(){
		command(['permissions', 'perms'],
			id: '20',
			description: 'Sets permission rules for commands (server-specific). ' +
				'Administrator permission is needed unless the perms for this command are changed.',
			usages: [
				' for (command)': 'Sends you the permission rules for the given command in private.',
				' list names': 'Lists the permission name aliases.',
				' list cmds|commands': 'Lists the commands with set rules in the server.',
				' set (command) (text or file)': 'Sets the permission rules to the given text.',
				' append (command) (text or file)': 'Appends the given text to the permission rules.',
				' delete (command)': 'Deletes permissions for the given command.',
			],
			serverOnly: true){
			def a = new Arguments(args)
			whatis(a.next()){
				when('for'){
					if (!KarmaFields.checkPerms(message, 'view_perms')){
						formatted('You don\'t have sufficient permissions.')
						return
					}
					def name = KarmaFields.findCommand(a.rest)?.id
					if (!name){
						formatted 'Invalid command.'
						return
					}
					def text = guildData.perms?.get(id)?.code
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
							if (!KarmaFields.checkPerms(message, 'view_perms')){
								formatted('You don\'t have sufficient permissions.')
								return
							}
							def list = guildData.perms?.keySet()
							if (list)
								author.sendMessage(list.join(', ').block('accesslog'))
							else
								formatted('No commands have special permissions in this server.')
						}
					}
				}
				when('delete'){
					if (!KarmaFields.checkPerms(message, 'edit_perms')){
						formatted('You don\'t have sufficient permissions.')
						return
					}
					def command = KarmaFields.findCommand(a.next())?.id
					if (!command){
						formatted 'Invalid command.'
						return
					}
					guildData.modify { perms { remove(command) } }
					formatted('Successfully deleted command permissions.')
				}
				when('set'){
					if (!KarmaFields.checkPerms(message, 'edit_perms')){
						formatted('You don\'t have sufficient permissions.')
						return
					}
					def command = KarmaFields.findCommand(a.next())?.id
					if (!command){
						formatted 'Invalid command.'
						return
					}
					def code = json.attachments ?
						message.attachment.inputStream.text :
						a.rest.trim()
					try{
						Kismet.parse(code)	
					}catch (ex){
						formatted "Error when parsing:\n$ex"
						return
					}
					guildData.modify(perms: [
						(command): [
							message_object: message.object,
							code: code
						]
					])
					formatted('Successfully set command permissions. However, they are untested.')
				}
				when('append'){
					if (!KarmaFields.checkPerms(message, 'edit_perms')){
						formatted('You don\'t have sufficient permissions.')
						return
					}
					def command = KarmaFields.findCommand(a.next())?.id
					if (!command){
						formatted 'Invalid command.'
						return
					}
					def old = guildData.perms?.getAt(command) ?: ""
					def code = json.attachments ?
						message.attachment.inputStream.text :
						a.rest.trim()
					code = old + '\n' + code
					try{
						Kismet.parse(code)	
					}catch (ex){
						formatted "Error when parsing:\n$ex"
						return
					}
					guildData.modify(perms: [
						(command): [
							message_object: message.object,
							code: code
						]
					])
					formatted('Successfully appended command permissions.')
				}
			}
		}
		
		command('listener',
			id: '22',
			description: 'Runs commands after certain events.',
			usages: [
				' add (event) (kismet)': 'Calls the Kismet code when the event is triggered. Returns an ID of the event listener.',
				' edit (id) (kismet)': 'Edits the listener to the new given code.',
				' delete (id)': 'Deletes the event listener with the given ID.',
				' list': 'Lists listeners.',
				' list (event)': 'Lists listeners for the given event.',
				' info (name)': 'Gives information about the given listener.',
				' json (name)': 'Gives a JSON object about the given listener.'
			],
			checkPerms: true,
			serverOnly: true){
			Arguments a = new Arguments(args)
			def option = a.next()
			whatis(option){
				when('add'){
					if (!a.hasNext()){
						formatted 'Not enough arguments.'
						return
					}
					def event = a.next()
					def code = json.attachments ?
						message.attachment.inputStream.text :
						a.rest.trim()
					try{
						Kismet.parse(code)	
					}catch (ex){
						formatted "Error when parsing:\n$ex"
						return
					}
					guildData.modify(listeners: [
						(json.id): [
							event: event,
							id: json.id,
							message_object: message.object,
							code: code
						]
					])
					formatted "Successfully added listener. Its ID is $json.id."
				}
				when('edit'){
					if (!a.hasNext()){
						formatted 'Not enough arguments.'
						return
					}
					def id = a.next()
					def code = json.attachments ?
						message.attachment.inputStream.text :
						a.rest.trim()
					try{
						Kismet.parse(code)	
					}catch (ex){
						formatted "Error when parsing:\n$ex"
						return
					}
					guildData.modify(listeners: [
						(id): [
							message_object: message.object,
							code: code
						]
					])
					formatted "Successfully edited listener."
				}
				when('delete'){
					if (!a.hasNext()){
						formatted 'Not enough arguments.'
						return
					}
					def id = a.next()
					if (a.hasNext()){
						formatted 'Too many arguments.'
						return
					}
					guildData.listeners { remove(id) }
					formatted "Removed listener $id."
				}
				when('list'){
					def event = a.hasNext() ? a.next() : null
					def header = event ? "Listeners for event $event:\n" : 'All listeners:\n'
					def ls = event ? guildData.listeners?.values().findAll { it.event == event } :
						guildData.listeners?.values()
					formatted(header + (ls*.id.join(', ') ?: 'None.'))
				}
				when('info'){
					def id = a.next()
					def listener = guildData.listeners?.get(id)
					if (!listener){
						formatted 'Listener not found.'
						return
					}
					formatted """\
Listener $id:
Event: $listener.event
Code: $listener.code"""
				}
			}
		}

		command(['filterroles', 'roles',
			~/(?:filter)?roles([\-!]+)/],
			id: '23',
			description: 'Finds roles with specific filters. If no filters are given, all filters will be used.\n\n' +
				'List of filters: ' + roleOptions.keySet().join(', '),
			usages: [
				'': 'Uses all filters.',
				' (filter1) (filter2)...': 'Uses the given filters. Note: space separated.',
				'- ...': 'Removes the filtered roles.',
			],
			examples: [
				'',
				' color',
				' unused no_overwrites',
				' unused ! color'
			],
			serverOnly: true){
			def params = captures[0]?.toList() ?: []
			List<Role> roles = server.roles
			roles.remove(server.defaultRole)
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
			options.each {
				roles = roles.findAll(it)
			}
			if (params.contains('-')){
				if (!KarmaFields.checkPerms(message, 'purgeroles')){
					formatted('You don\'t have sufficient permissions.')
					return
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
			}else{
				formatted("${roles.join(", ")}\n${roles.size()} total")
			}
		}
		
		command(['color', 'colour',
			~/colou?r<(#?x|X)>/],
			id: '24',
			description: 'Creates/reuses a role that has a color and no other special aspect to it.',
			usages: [
				'<[#]x|X> ...': 'Gives the color role with a specific type of name. ' +
					'This is case sensitive, and can be one of ' +
					colorNameTypes.keySet().join(', '),
				' (hexadecimal number)': 'Uses a hexadecimal number as the color',
				' (rgb number tuple)': 'Uses numbers separated by commas as RGB numbers for the color.',
				' (named color)': 'Uses a human readable color (spaces ignored) as the color. (list: \'http://www.december.com/html/spec/colorsvg.html\')',
				' random': 'Uses a random color between 0x000000 and 0xFFFFFF.'
			],
			examples: [
				' fb4bf4',
				' 44, 43, 103',
				' red',
				' navy blue'
			],
			checkPerms: true,
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
				def nt = colorNameTypes[captures[0] ?: guildData.color_name_type ?: 'x'] ?:
					colorNameTypes['x']
				def a = server.defaultRole.permissionValue
				Map groupedRoles = member.roles.groupBy {
					it.hoist || !(it.name ==~ /#?[0-9a-fA-F]+/) ||
					it.name.contains(" ") ||
					it.permissionValue > a ||
					it.permissionValue != 0 || it.locked ? 1 : 0 }
				// 0 is color roles, 1 is the others
				boolean created
				Role role
				if (color){
					role = server.roles.find {
						!it.hoist && it.permissionValue <= a &&
							it.name ==~ /#?[0-9a-fA-F]+/ &&
							it.colorValue == color && !it.locked
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
				if (guildData.cache.containsKey('delete_unused_color_roles') ?
					guildData.delete_unused_server_roles : true)
					groupedRoles[0].findAll { !(it.members - author) }*.delete()
				formatted(m)
			}catch (NoPermissionException ex){
				formatted('I don\'t have sufficient permissions. I need Manage Roles.')
			}
		}
		
		command('modify',
			id: '25',
			description: 'Modifies server specific data. This command should require strict permissions.',
			usages: [
				'': 'Will send the current JSON file for the server.',
				' json (json or file)': 'Adds the JSON object to the server data. Must be an object.',
				' property (name) (type) (value)': 'Sets a property with the given ' +
					'value considering the given type. The type can be one of ' +
					jsonConversionTypes.keySet().join(', ')
			],
			checkPerms: true,
			serverOnly: true){
			if (!args && KarmaFields.checkPerms(message, 'view_server_data', true)){
				author.sendFile(new File("guilds/${serverId}.json"))
				return
			}
			if (!KarmaFields.checkPerms(message, 'edit_server_data', true)){
				formatted('You don\'t have sufficient permissions.')
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
					guildData.modify(json)
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
		
		command(['customcommand', 'cc', ~/(?:cc|customcommand)([\-~]+)/],
			id: '32',
			description: 'Creates a custom command written in Kismet for a server. ' +
				'They have IDs.',
			usages: [
				' create': 'Creates a custom command and gives you the ID. This ID is vital.',
				'- ...': 'Removes something instead of adding it.',
				'~ ...': 'Makes a thing regex.',
				' code (id) (kismet)': 'Sets the code to the given Kismet code.',
				' alias (id) (name)': 'Adds an alias to the command.',
				' trigger (id) (trigger)': 'Adds a trigger to the command.',
				' description (id) (text)': 'Adds a description to the command.',
				' deprecate (id) (preferred)': 'Deprecates the command.',
				' checkperms (id) [true|false]': 'Enables checking permissions.',
				' usage (id) (use case in quotes) (descrption)': 'Adds a usage.',
				' example (id) (example)': 'Adds an example.',
				' delete (id)': 'Deletes the given command.'
			],
			checkPerms: true,
			serverOnly: true){
			def get = { id ->
				gulidData.commands?.get(id)
			}.memoize()
			def rem = { id, name, name2 = null ->
				guildData.modify {
					commands {
						"$id" {
							if (name2) "$name" { remove(name2) }
							else remove(name)
						}
					}
				}
			}
			def ad = { id, name, value ->
				guildData.modify(commands: [(id): [(name): value]])
			}
			def remove = captures.contains('-')
			def regex = captures.contains('~')
			Arguments a = new Arguments(args)
			def choice = a.next()
			whatis(choice){
				when('create'){
					guildData.modify(commands: [
						(message.id): [
							triggers: bot.triggers.collect { it.regex ?
								[it.toString()] : it.toString() }
						]
					])
					formatted "Successfully created command at $message.id."
				}
				when('code'){
					def id = a.next()
					def text = json.attachments ?
						message.attachment.inputStream.text :
						a.rest
					if (remove){
						rem(id, 'code')
						rem(id, 'message_object')
					}else if (text){
						ad(id, 'code', text)
						ad(id, 'message_object', message.object)
					}else{
						try{
							formatted get(id)?.code
						}catch (ex){
							sendFile("cc-$message.id-code-${id}.txt",
								get(id)?.code?.getBytes('UTF-8'))
						}
					}
					formatted 'Done.'
				}
				when('alias'){
					def id = a.next()
					if (remove) rem(id, 'aliases', regex ? [a.rest] : a.rest)
					else if (a.rest) ad(id, 'aliases', regex ? [[a.rest]] : [a.rest])
					else {
						formatted get(id)?.aliases?.collect { it instanceof String ?
							"\"$it\"" : "/$it/" }?.join(', ')
						return
					}
					formatted 'Done.'
				}
				when('trigger'){
					def id = a.next()
					if (remove) rem(id, 'triggers', regex ? [a.rest] : a.rest)
					else if (a.rest) ad(id, 'triggers', regex ? [[a.rest]] : [a.rest])
					else {
						formatted get(id)?.triggers?.collect { it instanceof String ?
							"\"$it\"" : "/$it/" }?.join(', ')
						return
					}
					formatted 'Done.'
				}
				when('usage'){
					def id = a.next()
					def x = a.next()
					if (remove) rem(id, 'usages', x)
					else if (a.rest) ad(id, 'usages', [(x): a.rest])
					else {
						formatted get(id)?.usages?.collect { k, v -> "\"$k\": $v" }?.join('\n')
						return
					}
					formatted 'Done.'
				}
				when('example'){
					def id = a.next()
					if (remove) rem(id, 'examples', a.rest)
					else if (a.rest) ad(id, 'examples', [a.rest])
					else {
						formatted get(id)?.examples?.join('\n')
						return
					}
					formatted 'Done.'
				}
				when('description'){
					def id = a.next()
					if (remove) rem(id, 'description')
					else if (a.rest) ad(id, 'description', a.rest)
					else {
						formatted get(id)?.description
						return
					}
					formatted 'Done.'
				}
				when('deprecate'){
					def id = a.next()
					if (remove) rem(id, 'deprecated')
					else if (a.rest) ad(id, 'deprecated', a.rest)
					else {
						formatted get(id)?.deprecated
						return
					}
					formatted 'Done.'
				}
				when('checkperms'){
					def id = a.next()
					if (remove) rem(id, 'checkPerms')
					else if (a.rest && a.rest in ['true', 'false']) ad(id, 'checkPerms', a.rest.toBoolean())
					else {
						formatted get(id)?.checkPerms
						return
					}
					formatted 'Done.'
				}
				when('delete'){
					def id = a.next()
					guildData.modify {
						commands {
							remove id
						}
					}
					formatted 'Done.'
				}
			}
			KarmaFields.registers.find { it instanceof CustomCommands }.update()
		}
	}
}
