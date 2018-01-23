package hlaaftana.kf.registers

import groovy.transform.CompileStatic
import hlaaftana.kf.CommandRegister
import hlaaftana.kf.KarmaFields
import hlaaftana.kf.Util
import hlaaftana.discordg.util.bot.CommandPattern
import hlaaftana.discordg.util.bot.DSLCommand
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.objects.Message

@CompileStatic
class MetaCommands extends CommandRegister {
	{ group = 'Meta' }

	static LinkedHashMap<String, LinkedHashMap<String, Object>> groups = [
		Meta: [
			description: 'Commands about the bot itself.'
		],
		Useful: [
			description: 'Commands to help you in certain topics.'
		],
		Guild: [
			description: 'Commands to use in guilds.'
		],
		Quick: [
			description: 'Commands that have easy functionality.'
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
				formatted(client.user(98457401363025920).createOrGetPrivateChannel(),
						"Feedback by ${Util.formatLongUser(author)}:\n$arguments")
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
Author: claude#7436
Source code: "https://github.com/hlaaftana/Karma-Fields"
Library: DiscordG ("https://github.com/hlaaftana/DiscordG")
Memory usage: ${Runtime.runtime.totalMemory() / (1 << 20)}/${Runtime.runtime.maxMemory() / (1 << 20)}MB
Invite: https://discordapp.com/oauth2/authorize?client_id=$KarmaFields.appId&scope=bot&permissions=268435456""")
		}

		command(['help', 'commands'],
			id: '4',
			description: 'Lists command groups or gives information about a command or group.',
			usages: [
				'': 'Lists group and gives information about how to call the bot.',
				' (group)': 'Lists all commands of a group.',
				' (command)': 'Shows the description, usage and the examples (if any) of the command.',
				' property {name}': 'Gives info about the given property.'
			]){
			if (arguments){
				def (Map<String, Object> group, List<DSLCommand> commands) = [groups[arguments.trim().toLowerCase().capitalize()],
					KarmaFields.findCommands(arguments, message)]
				if (group){
					List<Tuple2<Set<CommandPattern>, Set<CommandPattern>>> cmds = bot.commands
						.findAll { it instanceof DSLCommand && it.allows(message) && it.info.group == group.name && !it.info.hide }
						.collect {
							new Tuple2<Set<CommandPattern>, Set<CommandPattern>>(
								it.triggers.findAll { !it.regex && it.allows(message) },
								it.aliases.findAll { !it.regex && it.allows(message) })
						}
					sendMessage """```accesslog
> $group.name: $group.description
> Commands:
${cmds.collect { i -> "${i.second.join(', ')} [${i.first.join(' ')}]" }.join('\n')}
```"""
				}else if (commands){
					String msg = commands.collect(this.&formatCommand.curry(message)
							.rcurry(trigger.toString(), arguments)).join('\n')
					if (msg.size() < 2000) sendMessage(msg)
					else sendFile('', new ByteArrayInputStream(msg.getBytes('UTF-8')),
							"command-help-${((DSLCommand) command).info.id}-${message.id}.txt")
				}else formatted('Command or group not found.')
			}else{
				def randomCmd = MiscUtil.sample(bot.commands).alias
				sendMessage("""```accesslog
> My prefixes are "P!" and "poo! ", meaning when you call a command you have to put one of those before the command name.
> For example: "$trigger$alias" calls this command, and "$trigger$alias $randomCmd" calls this command with the arguments "$randomCmd".
> In command usages, {} implies the argument can be put in quotes and has to be if it has spaces,
> [] implies it's optional, () implies it doesn't use quotes, | implies different choices,
> ... implies more arguments to follow.
> Commands are sectioned via groups. Unfortunately I can't list every command here, so I'm just gonna list the groups.
> You can do "$trigger$alias (groupname)" to list a groups commands. For example, try "$trigger$alias ${MiscUtil.sample(groups.keySet())}".
>${'-' * 20}<
${groups.collect { k, v -> "> $k: $v.description" }.join('\n')}
```""")
			}
		}
	}

	static String formatCommand(Message msg, DSLCommand command,
	                            String preferredTrigger = null, String preferredName = null){
		if (command.info.deprecated)
			return MiscUtil.block("> This command is deprecated. Use \"$command.info.preferred\" instead.", 'accesslog')
		def triggers = command.triggers.findAll { !it.regex && it.allows(msg) }
		def aliases = command.aliases.findAll { !it.regex && it.allows(msg) }
		preferredTrigger = preferredTrigger ?: triggers[0]
		preferredName = preferredName ?: aliases[0]
		def output = "> $command.info.id: ${aliases.join(', ')} [${triggers.join(' ')}] ($command.info.group)"
		if (command.info.description) output += ": $command.info.description"
		if (command.info.usages) {
			output += """
>${'-' * 20}<
> Usages:
""" + command.info.usages.collect { Map.Entry e ->
				"$preferredTrigger$preferredName$e.key / $e.value"
			}.join('\n')
		}
		if (command.info.examples) {
			output += """
>${'-' * 20}<
> Examples:
""" + command.info.examples.collect { "$preferredTrigger$preferredName$it" }.join("\n")
		}
		MiscUtil.block(output, "accesslog")
	}
}
