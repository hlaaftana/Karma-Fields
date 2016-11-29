package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.util.bot.Command
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.KarmaFields

class MiscCommands {
	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = kf.client

		client.pools.batch = Client.newPool(5, 30_000)
		bot.command(["batch",
			~/batch<(\w+?)>/,
			~/batch<(\w+?)\s*,\s*(\d+?)>/],
			group: "Misc",
			description: "Runs a command multiple times. Maximum of 5.",
			usages: [
				"<(name), (number)> (arguments)": "Calls (name) (number) times with (arguments).",
				"<(name), (number)>": "Calls the (name) command (number) times.",
				"<(name)> (arguments)": "Calls the (name) command 3 times with (arguments).",
				"<(name)>": "Calls the (name) command 3 times."
			],
			examples: [
				"<markov>",
				"<markov, 2>",
				"<markov> @hlaaf#7436"
			]){ d ->
			client.askPool("batch", message.server?.id ?: message.channel.id){
				String commandName = captures[0]
				Command ass = kf.findCommand(commandName)
				if (!ass){
					decorate("Invalid command name. Type \"${usedTrigger}usages batch\" to see how this command can be used.")
					return
				}
				if (!ass.info.batchable){
					decorate("Unfortunately that command is not deemed safe enough to be called multiple times in a short time.")
					return
				}
				int time
				try {
					time = Math.min(5, captures[1].toInteger()) ?: 3
				}catch (ex){
					time = 3
				}
				Message modifiedMessage = new Message(message.client, message.object.clone())
				String newContent = args ? "$usedTrigger$commandName $args" : "$usedTrigger$commandName"
				modifiedMessage.object["content"] = newContent
				Map newData = d.clone()
				newData["message"] = modifiedMessage
				def a = newData["json"].clone()
				a.content = newContent
				newData["json"] = a
				time.times {
					Thread.start { ass(newData) }
					null
				}
			}
		}
	}
}
