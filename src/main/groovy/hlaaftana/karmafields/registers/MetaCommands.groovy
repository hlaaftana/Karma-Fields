package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.objects.Permissions
import hlaaftana.discordg.util.bot.Command
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.discordg.util.bot.DSLCommand
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util

class MetaCommands {
	static Map groups = [
		Meta: [
			description: "Commands about the bot itself."
		],
		Entertainment: [
			description: "Commands you can use when you're bored."
		],
		Administrative: [
			description: "Commands to ease your job as a staff member."
		],
		Useful: [
			description: "Commands to help you in certain topics."
		],
		Server: [
			description: "Commands to use in servers, but not necessarily administrative."
		],
		Misc: [
			description: "Uncategorized commands."
		]
	]

	static {
		groups.each { k, v -> groups[k].name = k }
	}

	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = bot.client

		bot.command("feedback",
			group: "Meta",
			description: "Sends me (the bot author) feedback on the bot.",
			usages: [
				" (text)": "Sends me the text as feedback."
			]){
			try{
				client.user(215942738670125059).sendMessage(
					"> Feedback by ${Util.formatLongUser(message.author)}:\n$args"
					.block("accesslog"))
				decorate("Feedback sent.")
			}catch (ex){
				decorate("Could not send feedback (${ex.class.simpleName}). Sorry for the inconvience.")
			}
		}

		bot.command(["info", "information"],
			group: "Meta",
			description: "Sends information about the bot's backend.",
			usages: [
				"": "Sends the information."
			]){
			decorate("""Programming language: Groovy
Author: ${Util.formatLongUser(kf.me)}
Source code: "https://github.com/hlaaftana/Karma-Fields"
Library: DiscordG ("https://github.com/hlaaftana/DiscordG")
Memory usage: ${Runtime.runtime.totalMemory() / (1 << 20)}/${Runtime.runtime.maxMemory() / (1 << 20)}MB
Invite: ${Util.formatUrl(client.appLink(client.app_id, 268435456))}""")
		}

		bot.command(["join", "invite"],
			group: "Meta",
			description: "Gives the bot's URL to add it to servers. No arguments."){
			decorate(Util.formatUrl(client.appLink(client.app_id, 268435456)))
		}

		bot.command(["help", "commands"],
			group: "Meta",
			description: "Lists command groups or gives information about a command or group.",
			usages: [
				"": "Lists group and gives information about how to call the bot.",
				" (group)": "Lists all commands of a group.",
				" (command)": "Shows the description, usage and the examples (if any) of the command."
			]){
			if (args){
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
					sendMessage("> Command or group not found.".block("accesslog"))
				}
			}else{
				def randomCmd = bot.commands.sample().alias
				sendMessage("""> My prefixes are |> and ><, meaning when you call a command you have to put one of those before the command name (no space).
> For example: "$usedTrigger$usedAlias" calls this command, and "$usedTrigger$usedAlias $randomCmd" calls this command with the arguments "$randomCmd".
> Commands are sectioned via groups. Unfortunately I can't list every command here, so I'm just gonna list the groups and you can do "$usedTrigger$usedAlias <groupname>" to list its commands.
${this.groups.collect { k, v -> "> $k: $v.description" }.join("\n")}""".block("accesslog"))
			}
		}

		bot.command("command",
			group: "Meta",
			description: "Gives information about a command.",
			usages: [
				" (command)": "Shows the description, usage and the examples (if any) of the command."
			]){
			def command = kf.findCommand(args)
			String msg = formatCommand(command, usedTrigger.toString(), args)
			if (msg.size() < 2000) sendMessage(msg)
			else {
				sendMessage("""${command.aliases.findAll { !it.regex }.collect { it.toString().surround '"' }.join(" or ")} ($command.group): $command.description
>${'-' * 20}<
The usage and the examples make this message more than 2000 characters, which is Discord's limit for messages. Unfortunately you have to use ${usedTrigger}usage and ${usedTrigger}examples separately.""".block("accesslog"))
			}
		}

		bot.command("usage",
			group: "Meta",
			description: "Shows how a command is used.",
			usages: [
				" (command)": "Shows the usage of the command."
			]){
			def command = kf.findCommand(args)
			sendMessage((command.info.deprecated ? "> This command is deprecated. Use $command.preferred instead." : """> Usage:
${command.usages.collect { k, v -> "\"$usedTrigger$args$k\": $v" }.join("\n")}""").block("accesslog"))
		}

		bot.command("examples",
			group: "Meta",
			description: "Shows examples of how a command is used.",
			usages: [
				" (command)": "Shows some examples of the command. There might be none."
			]){
			def command = kf.findCommand(args)
			sendMessage((command.info.deprecated ? "> This command is deprecated. Use $command.preferred instead." :
				command.info.examples ? """> Examples:
${command.examples.collect { "\"$usedTrigger$args$it\"" }.join("\n")}""" :
				"> This command has no examples.").block("accesslog"))
		}
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
