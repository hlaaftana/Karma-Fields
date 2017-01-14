package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.objects.Permissions
import hlaaftana.discordg.util.bot.Command
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.discordg.util.bot.DSLCommand
import hlaaftana.karmafields.Arguments
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util

class MetaCommands {
	static Map groups = [
		Meta: [
			description: 'Commands about the bot itself.'
		],
		Entertainment: [
			description: 'Commands you can use when you\'re bored.'
		],
		Useful: [
			description: 'Commands to help you in certain topics.'
		],
		Server: [
			description: 'Commands to use in servers.'
		],
		'Cookie-cutter': [
			description: 'Commands you can find in other bots, or commands that lack in use.'
		]
	]
	
	static Map serverProperties = [
		color_name_type: [
			types: ['str', 'string'],
			description: 'One of #x, #X, x, X. Designates the color role naming format.',
			default: 'x'
		],
		guest_role: [
			types: ['str', 'string'],
			description: 'An ID of the guest role for the server.'
		],
		member_role: [
			types: ['str', 'string'],
			description: 'An ID of the member role for the server.'
		],
		bot_role: [
			types: ['str', 'string'],
			description: 'An ID of the bot role for the server.'
		],
		autoguest: [
			types: ['bool', 'boolean'],
			description: 'Enables/disables autoguesting.'
		],
		automember: [
			types: ['bool', 'boolean'],
			description: 'Enables/disables automembering.'
		],
		autobot: [
			types: ['bool', 'boolean'],
			description: 'Enables/disables autobotting.'
		],
		member_vote_enabled: [
			types: ['bool', 'boolean'],
			description: 'Enables/disables the votemember command.',
			default: 'true'
		],
		member_vote_count: [
			types: ['num', 'number'],
			description: 'The sum of votes needed for a user to become member.',
			default: '5'
		],
		member_kick_vote_count: [
			types: ['num', 'number'],
			description: 'The sum of votes needed for a user to be kicked.',
			default: '-5'
		]
	]

	static {
		groups.each { k, v -> groups[k].name = k }
	}

	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = bot.client

		bot.command('feedback',
			group: 'Meta',
			description: 'Sends me (the bot author) feedback on the bot.',
			usages: [
				' (text)': 'Sends me the text as feedback.'
			]){
			try{
				client.user(kf.me).formatted("Feedback by ${Util.formatLongUser(author)}:\n$args")
				formatted('Feedback sent.')
			}catch (ex){
				formatted("Could not send feedback (${ex.class.simpleName}). Sorry for the inconvience.")
			}
		}

		bot.command(['info', 'information'],
			group: 'Meta',
			description: 'Sends information about the bot\'s backend.',
			usages: [
				'': 'Sends the information.'
			]){
			formatted("""Programming language: Groovy
Author: ${Util.formatLongUser(kf.me)}
Source code: "https://github.com/hlaaftana/Karma-Fields"
Library: DiscordG ("https://github.com/hlaaftana/DiscordG")
Memory usage: ${Runtime.runtime.totalMemory() / (1 << 20)}/${Runtime.runtime.maxMemory() / (1 << 20)}MB
Invite: ${Util.formatUrl(client.appLink(client.appId, 268435456))}""")
		}

		bot.command(['join', 'invite'],
			group: 'Meta',
			description: 'Gives the bot\'s URL to add it to servers. No arguments.'){
			formatted(Util.formatUrl(client.appLink(client.app_id, 268435456)))
		}

		bot.command(['help', 'commands'],
			group: 'Meta',
			description: 'Lists command groups or gives information about a command or group.',
			usages: [
				'': 'Lists group and gives information about how to call the bot.',
				' (group)': 'Lists all commands of a group.',
				' (command)': 'Shows the description, usage and the examples (if any) of the command.'
			]){
			if (args.startsWith('property ')){
				Arguments a = new Arguments(args)
				a.next()
				def name = a.next()
				if (name == 'list'){
					formatted('To find out about how to set properties, ' +
						"check \"$usedTrigger$usedAlias modify\".\n" +
						serverProperties.keySet().join(', '))
				}else if (serverProperties.containsKey(name)){
					def property = serverProperties[name]
					def output = "$name (${property.types.join(', ')})"
					if (property.default) output += " [${property.default}]"
					output += ": $property.description"
					formatted(output)
				}else{
					formatted('Invalid property name.')
				}
			}else if (args){
				def (group, command) = [this.groups[args.trim().toLowerCase().capitalize()],
					kf.findCommand(args)]
				if (group){
					def commands = bot.commands.findAll { it.info.group == group.name && !it.info.hide }
					def aliases = commands.collect {
						it.aliases.findAll { !it.regex }
					}
					sendMessage("""> $group.name: $group.description
> Commands:
${aliases.collect { it.collect { it.toString().surround '"' }.join(" or ") }.join(", ")}""".block("accesslog"))
				}else if (command){
					String msg = formatCommand(command, usedTrigger.toString(), args)
					if (msg.size() < 2000) sendMessage(msg)
					else {
						sendMessage("""${command.aliases.findAll { !it.regex }.collect { it.toString().surround '"' }.join(" or ")} ($command.group): $command.description
>${'-' * 20}<
The usage and the examples make this message more than 2000 characters, which is Discord's limit for messages. Unfortunately you have to use ${usedTrigger}usage and ${usedTrigger}examples separately.""".block("accesslog"))
					}
				}else{
					formatted('Command or group not found.')
				}
			}else{
				def randomCmd = bot.commands.sample().alias
				sendMessage("""> My prefixes are |> and ><, meaning when you call a command you have to put one of those before the command name (no space).
> For example: "$usedTrigger$usedAlias" calls this command, and "$usedTrigger$usedAlias $randomCmd" calls this command with the arguments "$randomCmd".
> Some commands use server properties, use "$usedTrigger$usedAlias property list" to get a list.
> Commands are sectioned via groups. Unfortunately I can't list every command here, so I'm just gonna list the groups and you can do "$usedTrigger$usedAlias (groupname)" to list its commands.
>${'-' * 20}<
${this.groups.collect { k, v -> "> $k: $v.description" }.join("\n")}""".block("accesslog"))
			}
		}

		bot.command('command',
			group: 'Meta',
			description: 'Gives information about a command.',
			usages: [
				' (command)': 'Shows the description, usage and the examples (if any) of the command.'
			]){
			def command = kf.findCommand(args)
			if (!command){
				formatted 'Invalid command.'
				return
			}
			String msg = formatCommand(command, usedTrigger.toString(), args)
			if (msg.size() < 2000) sendMessage(msg)
			else {
				sendMessage("""${command.aliases.findAll { !it.regex }.collect { it.toString().surround '"' }.join(" or ")} ($command.group): $command.description
>${'-' * 20}<
The usage and the examples make this message more than 2000 characters, which is Discord's limit for messages. Unfortunately you have to use ${usedTrigger}usage and ${usedTrigger}examples separately.""".block("accesslog"))
			}
		}

		bot.command('usage',
			group: 'Meta',
			description: 'Shows how a command is used.',
			usages: [
				' (command)': 'Shows the usage of the command.'
			]){
			def command = kf.findCommand(args)
			sendMessage((command.info.deprecated ? "> This command is deprecated. Use $command.preferred instead." : """> Usage:
${command.usages.collect { k, v -> "\"$usedTrigger$args$k\": $v" }.join("\n")}""").block("accesslog"))
		}

		bot.command('examples',
			group: 'Meta',
			description: 'Shows examples of how a command is used.',
			usages: [
				' (command)': 'Shows some examples of the command. There might be none.'
			]){
			def command = kf.findCommand(args)
			sendMessage((command.info.deprecated ? "> This command is deprecated. Use $command.preferred instead." :
				command.info.examples ? """> Examples:
${command.examples.collect { "\"$usedTrigger$args$it\"" }.join("\n")}""" :
				"> This command has no examples.").block("accesslog"))
		}
		
		Map batchAmounts = [:]
		client.pools.batch = Client.newPool(5, 30_000)
		client.pools.batch.suspend = { batchAmounts[it] >= 5 }
		bot.command(['batch',
			~/batch<(\w+?)>/,
			~/batch<(\w+?)\s*,\s*(\d+)>/],
			group: 'Meta',
			description: 'Runs a command multiple times. Maximum of 5.',
			usages: [
				'<(name), (number)> (arguments)': 'Calls (name) (number) times with (arguments).',
				'<(name), (number)>': 'Calls the (name) command (number) times.',
				'<(name)> (arguments)': 'Calls the (name) command 3 times with (arguments).',
				'<(name)>': 'Calls the (name) command 3 times.'
			],
			examples: [
				'<markov>',
				'<markov, 2>',
				'<markov> @hlaaf#7436'
			]){ d ->
			def bucket = server?.id ?: channel.id
			client.askPool('batch', bucket){
				int time
				try {
					time = Math.min(5, captures[1].toInteger()) ?: 3
				}catch (ex){
					time = 3
				}
				batchAmounts[bucket] = time + (batchAmounts[bucket] ?: 0)
				String commandName = captures[0]
				Command ass = kf.findCommand(commandName)
				if (!ass){
					formatted("No such command '$commandName'. Type \"${usedTrigger}usage batch\" to see how this command can be used.")
					return
				}
				if (!ass.info.batchable){
					formatted('Unfortunately that command is not deemed safe enough to be called multiple times in a short time.')
					return
				}
				Map newData = kf.fabricateCopy(d, content: args ?
					"$usedTrigger$commandName $args" : "$usedTrigger$commandName")
				time.times {
					Thread.start { ass(newData); batchAmounts[bucket]-- }
					null
				}
			}
		}

		bot.command(['hidelogs', 'hl'],
			group: 'Meta',
			description: 'Empty and functionless command to hide a message from your logs for the markov command.',
			usages: [
				' (text)': 'Does nothing.'
			]){}
	}

	static String formatCommand(DSLCommand command,
		String preferredTrigger = command.triggers.find { !it.regex },
		String preferredName = command.aliases.find { !it.regex }){
		def output = command.info.deprecated ? "> This command is deprecated. Use $preferredTrigger$command.preferred instead." : """> ${command.aliases.findAll { !it.regex }.collect { it.toString().surround '"' }.join(" or ")} ($command.group): $command.description
>${'-' * 20}<
> Usage:
${command.usages.collect { k, v -> "\"$preferredTrigger$preferredName$k\": $v" }.join("\n")}"""
		if (command.info.examples){
			output += """\n>${'-' * 20}<\n> Examples:
${command.examples.collect { "\"$preferredTrigger$preferredName$it\"" }.join("\n")}"""
		}
		output.block("accesslog")
	}
}
