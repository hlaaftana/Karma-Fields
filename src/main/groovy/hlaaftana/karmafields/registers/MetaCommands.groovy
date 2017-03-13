package hlaaftana.karmafields.registers

import java.util.Map;

import hlaaftana.discordg.Client
import hlaaftana.discordg.objects.Channel
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.objects.Permissions
import hlaaftana.discordg.util.bot.Command
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.discordg.util.bot.DSLCommand
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.karmafields.Arguments
import hlaaftana.karmafields.CommandRegister;
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util

class MetaCommands extends CommandRegister {
	{ group = 'Meta' }

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
		],
		Custom: [
			description: 'Commands written in Kismet and made by users in a server.'
		]
	]
	
	static Map serverProperties = [
		color_name_type: [
			types: ['str', 'string'],
			description: 'One of #x, #X, x, X. Designates the color role naming format.',
			default: 'x'
		],
		delete_unused_color_roles: [
			types: ['bool', 'boolean'],
			description: 'Deletes a color role after a user changes their color if no one else uses it.',
			default: 'true'
		]
	]

	static {
		groups.each { k, v -> groups[k].name = k }
	}

	def register(){
		command('feedback',
			id: '1',
			description: 'Sends me (the bot author) feedback on the bot.',
			usages: [
				' (text)': 'Sends me the text as feedback.'
			]){
			try{
				client.user(KarmaFields.me).formatted("Feedback by ${Util.formatLongUser(author)}:\n$args")
				formatted('Feedback sent.')
			}catch (ex){
				formatted("Could not send feedback (${ex.class.simpleName}). Sorry for the inconvience.")
			}
		}

		command(['info', 'information'],
			id: '2',
			description: 'Sends information about the bot\'s backend.',
			usages: [
				'': 'Sends the information.'
			]){
			formatted("""Programming language: Groovy
Author: ${Util.formatLongUser(KarmaFields.me)}
Source code: "https://github.com/hlaaftana/Karma-Fields"
Library: DiscordG ("https://github.com/hlaaftana/DiscordG")
Memory usage: ${Runtime.runtime.totalMemory() / (1 << 20)}/${Runtime.runtime.maxMemory() / (1 << 20)}MB
Invite: ${Util.formatUrl(client.appLink(client.appId, 268435456))}""")
		}

		command(['help', 'commands'],
			id: '4',
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
				def (group, commands) = [this.groups[args.trim().toLowerCase().capitalize()],
					KarmaFields.findCommands(args, message)]
				if (group){
					def cmds = bot.commands.findAll { it.allows(message) &&
						it.info.group == group.name && !it.info.hide }
					cmds = cmds.collect {
						[it.triggers.findAll { !it.regex && it.allows(message) },
							it.aliases.findAll { !it.regex && it.allows(message) }]
					}
					sendMessage("""> $group.name: $group.description
> Commands:
${cmds.collect { "${it[1].join(', ')} [${it[0].join(' ')}]" }.join('\n')}""".block("accesslog"))
				}else if (commands){
					String msg = commands.collect {
						formatCommand(message, it, usedTrigger.toString(), args)
					}.join('\n')
					if (msg.size() < 2000) sendMessage(msg)
					else sendFile("command-help-$command.id-${message.id}.txt", msg.getBytes('UTF-8'))
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
		
		Map batchAmounts = [:]
		client.pools.batch = Client.newPool(5, 30_000)
		client.pools.batch.suspend = { batchAmounts[it] >= 5 }
		command(['batch',
			~/batch<(\w+?)>/,
			~/batch<(\w+?)\s*,\s*(\d+)>/],
			id: '8',
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
				Command ass = KarmaFields.findCommand(commandName)
				if (!ass){
					formatted("No such command '$commandName'. Type \"${usedTrigger}usage batch\" to see how this command can be used.")
					return
				}
				if (!ass.info.batchable){
					formatted('Unfortunately that command is not deemed safe enough to be called multiple times in a short time.')
					return
				}
				Map newData = KarmaFields.fabricateCopy(d, content: args ?
					"$usedTrigger$commandName $args" : "$usedTrigger$commandName")
				time.times {
					Thread.start { ass(newData); batchAmounts[bucket]-- }
					null
				}
			}
		}
		
		command('clean',
			id: '9',
			description: 'Deletes its own messages.',
			usages: [
				'': 'Cleans in 50 messages.',
				' (amount)': 'Cleans in the recent gven amount of messages. Maximum of 500.'
			]){
			int num = MiscUtil.defaultValueOnException(50){ args.toInteger() }
			clear(channel, num, client)
		}
	}

	static String formatCommand(Message msg, DSLCommand command,
		String preferredTrigger = null, String preferredName = null){
		if (command.info.deprecated)
			return "> This command is deprecated. Use \"$command.preferred\" instead.".block('accesslog')
		def triggers = command.triggers.findAll { !it.regex && it.allows(msg) }
		def aliases = command.aliases.findAll { !it.regex && it.allows(msg) }
		preferredTrigger = preferredTrigger ?: triggers[0]
		preferredName = preferredName ?: aliases[0]
		def output = "> $command.id: ${aliases.join(', ')} [${triggers.join(' ')}] ($command.group)"
		if (command.info.description) output += ": $command.description"
		if (command.info.usages){
			output += """
>${'-' * 20}<
> Usages:
${command.usages.collect { k, v -> "$preferredTrigger$preferredName$k / $v" }.join('\n')}"""
		}
		if (command.info.examples){
			output += """
>${'-' * 20}<
> Examples:
${command.examples.collect { "$preferredTrigger$preferredName$it" }.join("\n")}"""
		}
		output.block("accesslog")
	}
	
	static clear(Channel channel, int num, Client cl){
		channel.permissionsFor(cl)['manageMessages'] ?
			channel.clear(cl, num) :
			channel.logs(num).findAll { it.object.author.id == cl.id }.each {
				it.delete()
				Thread.sleep 1000
			}
	}
}
