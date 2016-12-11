package hlaaftana.karmafields.registers

import groovy.json.JsonOutput
import hlaaftana.discordg.Client
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util

class CookieCutterCommands {
	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = kf.client

		bot.command("cleverbot",
			group: "Cookie-cutter",
			description: "Talks to Cleverbot (cleverbot.io).",
			usages: [
				" (text)": "Talks to Cleverbot."
			]){
			decorate(kf.cleverbot.ask(args))
		}

		bot.command("urlencode",
			group: "Cookie-cutter",
			description: "Appropriates a string for URL parameters.",
			usages: [
				" (text)": "Converts the text.",
				" (file)": "Converts the text in the file and uploads a file."
			]){
			def text = json.attachments ? message.attachment.inputStream.text : args
			text = URLEncoder.encode(text)
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes("UTF-8"), "$usedAlias-${json.id}.txt")
			else decorate(text.surround('"'))
		}

		bot.command("urldecode",
			group: "Cookie-cutter",
			description: "Reverts a string from URL parameters to a normal one.",
			usages: [
				" (text)": "Converts the text.",
				" (file)": "Converts the text in the file and uploads a file."
			]){
			def text = json.attachments ? message.attachment.inputStream.text : args
			text = URLDecoder.decode(text)
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes("UTF-8"), "$usedAlias-${json.id}.txt")
			else decorate(text.surround('"'))
		}

		bot.command(["encodejson", "jsonize", "jsonify"],
			group: "Cookie-cutter",
			description: "Converts a string to JSON, filling in \\u, \\r, \\n, \\t and whatnot.",
			usages: [
				" (text)": "Converts the text.",
				" (file)": "Converts the text in the file and uploads a file."
			]){
			def text = json.attachments ? message.attachment.inputStream.text : args
			text = JSONUtil.json(text)
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes("UTF-8"), "$usedAlias-${json.id}.txt")
			else decorate(text)
		}

		bot.command(["prettyjson", "ppjson"],
			group: "Cookie-cutter",
			description: "Pretty prints JSON text.",
			usages: [
				" (text)": "Converts the text.",
				" (file)": "Converts the text in the file and uploads a file."
			]){
			def text = json.attachments ? message.attachment.inputStream.text : args
			text = JsonOutput.prettyPrint(text)
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes("UTF-8"), "$usedAlias-${json.id}.txt")
			else sendMessage(text.block("json"))
		}

		bot.command(["convertcase",
			~/convertcase<(\w+)(?:\s*,\s*|\s+)(\w+)>/],
			group: "Cookie-cutter",
			description: "Converts text to a given case type. Case types are: " +
				MiscUtil.converters.keySet().join(", "),
			usages: [
				"<casefrom[,] caseto> ...": "Converts the text to caseto assuming the text is casefrom.",
				"<...> (text)": "Converts the text.",
				"<...> (file)": "Converts the text in the file and uploads a file."
			]){
			if (captures.size() != 2){ decorate("Invalid cases."); return }
			def (from, to) = captures
			def text = json.attachments ? message.attachment.inputStream.text : args
			text = MiscUtil.convertCasing(text, from, to)
			if (text == null){ decorate("Invalid cases."); return }
			boolean file = text.size() > 1000 || json.attachments
			if (file) sendFile(text.getBytes("UTF-8"), "convertcase-$from-$to-${json.id}.txt")
			else decorate(text)
		}

		bot.command("say",
			group: "Cookie-cutter",
			description: "Repeats text.",
			usages: [
				" (text)": "Guess."
			]){ decorate(args) }

		bot.command("ping",
			group: "Cookie-cutter",
			description: "Testing response times.",
			usages: [
				"": "Starts the response time difference sequence."
			]){
			def a = System.currentTimeMillis() - timeReceived
			def text = "Started after $a ms."
			def m = decorate(text)
			def b = System.currentTimeMillis() - a
			text += "\nSent after $b ms."
			m.edit(('> ' + text).replace('\n', '\n> ').block("accesslog"))
			def c = System.currentTimeMillis() - b
			text += "\nEdited after $c ms."
			m.edit(('> ' + text).replace('\n', '\n> ').block("accesslog"))
		}
	}
}
