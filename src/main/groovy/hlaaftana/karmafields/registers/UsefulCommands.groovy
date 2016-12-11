package hlaaftana.karmafields.registers

import com.mashape.unirest.http.Unirest
import hlaaftana.discordg.Client
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.objects.DiscordObject
import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.util.ConversionUtil
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import static hlaaftana.discordg.util.WhatIs.whatis
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.Arguments
import static hlaaftana.karmafields.Arguments.run as argp
import hlaaftana.karmafields.BrainfuckInterpreter
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util
import java.awt.Color
import java.text.SimpleDateFormat
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

class UsefulCommands {
	static CompilerConfiguration cc

	static {
		ImportCustomizer imports = new ImportCustomizer()
		imports.addStarImports(
			"hlaaftana.discordg",
			"hlaaftana.discordg.collections",
			"hlaaftana.discordg.dsl",
			"hlaaftana.discordg.exceptions",
			"hlaaftana.discordg.logic",
			"hlaaftana.discordg.net",
			"hlaaftana.discordg.objects",
			"hlaaftana.discordg.status",
			"hlaaftana.discordg.util",
			"hlaaftana.discordg.util.bot",
			"hlaaftana.discordg.voice",
			"hlaaftana.karmafields",
			"hlaaftana.karmafields.registers",
			"java.awt")
		imports.addImports(
			"java.awt.image.BufferedImage",
			"javax.imageio.ImageIO",
			"java.util.List")
		cc = new CompilerConfiguration()
		cc.addCompilationCustomizers(imports)
	}

	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = kf.client

		bot.command("eval",
			group: "Useful",
			description: "Evaluates Groovy code. Everyone can use this.",
			usages: [
				" (code)": "Evaluates the given code."
			],
			examples: [
				" (33 & 42).intdiv(6).times { println it }"
			],
			batchable: true){ d ->
			String dea = args
				.replaceAll(/^```\w*\n/, "")
				.replaceAll(/```$/, "")
			if (json.author.id == kf.me.id &&
				usedTrigger.toString() != "><"){
				try{
					sendMessage(("> " + new GroovyShell(
						new Binding(d + [data: d, kf: kf,
							now: System.&currentTimeMillis] +
							kf.properties +
								Util.metaClass.methods.collectEntries {
									[(it.name): Util.&"$it.name"] }), this.cc)
							.evaluate(dea).toString()).block("groovy"))
				}catch (ex){
					sendMessage(ex.toString().block("groovy"))
				}
			}else{
				def evaluation
				try{
					evaluation = JSONUtil.parse(
						Unirest.post("http://groovyconsole.appspot.com/executor.groovy")
						.field("script", dea)
						.asString().body)
				}catch (ex){
					decorate("Failed to request evaluation.")
					return
				}
				String output = ""
				if (evaluation["executionResult"]){
					output += "\n" + "> Result:\n$evaluation.executionResult".block("groovy")
				}
				if (evaluation["outputText"]){
					output += "\n" + "> Output:\n$evaluation.outputText".block("groovy")
				}
				if (evaluation["stacktraceText"]){
					output += "\n" + "> Error:\n$evaluation.stacktraceText".block("groovy")
				}
				try{
					sendMessage(output)
				}catch (ex){
					Message dong = decorate("Message too long. Uploading JSON result of evaluation...")
					sendFile(JSONUtil.pjson(evaluation).getBytes("UTF-8"), "evaluation_${message.id}.json")
					dong.delete()
				}
			}
		}

		bot.command(["showcolor", "showcolour",
			~/showcolou?r<(\d+?)>/,
			~/showcolou?r<(\d+?)\s*,\s*(\d+?)>/],
			group: "Useful",
			description: "Posts an image containing (information about) a color.",
			usages: [
				" (color)": "Sets width and height to 250 and shows (color).",
				" random": "Sets width and height to 250 and shows a random color.",
				" my|me|mine": "Sets width and height to 250 and shows your color.",
				"<(size)> (color)": "Sets width and height to (size) and shows (color).",
				"<(width), (height)> (color)": "Sets width and height individually and shows (color)."
			],
			batchable: true){ d ->
			def r = Util.resolveColor(d)
			if (r instanceof String){
				decorate(r)
				return
			}
			int color = r
			def (width, height) = captures.size() == 1 ?
				[captures[0], captures[0]]*.toInteger() :
				captures.size() == 2 ?
				[captures[0], captures[1]]*.toInteger() :
				[250, 250]
			def y = drawColor(color, width, height)
			try{
				sendFile(y, filename: "color.png")
			}catch (NoPermissionException ex){
				decorate("I don't seem to have permissions to send files. " +
					"Maybe you need to try in a testing channel?")
			}
		}

		bot.command(["logs",
			~/logs(\+)?/,
			~/logs<(\d+?)>(\+)?/],
			group: "Useful",
			description: "Looks up messages from certain points in time in a channel.",
			usages: [
				"": "Looks up most recent posts.",
				" first": "Looks up first posts.",
				" id (id)": "Looks up posts at the ID given.",
				" date (year-month-day-...)": "Looks up posts at the given date (UTC). " +
					"Every part of the part before is separated by a dash. " +
					"The progression is year, month, day, hour, minute, second and " +
					"millisecond. Keep in mind that '2016' works but '16' has problems. " +
					"Check examples if you're confused.",
				" unix (timestamp)": "Looks up posts at the given Unix timestamp.",
				" multiplier (multiplier)": "Multiplies the channel id by the given " +
					"multiplier and looks up messages at that ID. Shouldn't be more than " +
					"2, unless the channel is old Discord-creation-wise.",
				"<(number)> (arguments)": "Sets the post number, not more than 100. " +
					"Arguments can be any of the above.",
				"+ (arguments)": "Shows posts after the given time. Otherwise before. " +
					"Does not work for first."
			],
			examples: [
				"<25>",
				"+",
				"<25>+",
				" id 243730654498521100",
				" date 2016-07-04",
				" date 2016-07-04-15-03-39",
				" unix 1479959375236",
				" multiplier 1.405",
				"<27> date 2016-07-04-15-03-39",
				"<27>+ date 2016-07-04-15-03-39",
			]){
			def maddy = captures.find { it.isInteger() }
			int number = maddy ? Math.min(maddy.toInteger(), 100) : 50
			boolean after = captures.any { it == "+" }
			String id
			String error
			Arguments a = new Arguments(args)
			String choice = args ? a.afterSpace : "first"
			whatis(choice){
				when("first"){
					id = message.channel.id
					after = true
				}
				when("id"){
					if (a.rest.long) id = a.rest
					else error = "Invalid ID. IDs are numbers, you can right click on things and get their IDs by turning on Developer Mode."
				}
				when("unix"){
					try{
						id = DiscordObject.millisToId(a.rest.toLong())
					}catch (ex){
						error = "Invalid Unix timestamp. https://en.wikipedia.org/wiki/Unix_time"
					}
				}
				when("multiplier"){
					try{
						id = ((message.channel.id.toLong() *
							new BigDecimal(a.rest)) as long).toString()
						if (id > message.id) error = "The multiplier seems to be too big."
					}catch (NumberFormatException ex){
						error = "Invalid multiplier. Needs to be a decimal number " +
							"with a period instead of a comma."
					}
				}
				when("date"){
					if (a.rest ==~ /\d+(\-\d+)*/){
						long date = ConversionUtil.experimentalDateParser(a.rest).time
						if (date < message.channel.createdAtMillis) id = message.channel.id
						else id = DiscordObject.millisToId(date)
					}else{
						error = "Invalid date. Every number has to be separated by " +
							"a dash character. The order is year, month, day, hour," +
							" minute, second and millisecond. If the year is 2016, " +
							"it cannot be represented as 16."
					}
				}
				otherwise {
					error = "Invalid arguments."
				}
			}

			if (error){
				decorate(error)
				return
			}

			SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss")
			df.timeZone = TimeZone.getTimeZone("Etc/UTC")
			String t = ""
			message.channel.requestLogs(number, id, after).reverse().each {
				String m = "[${df.format(it.timestamp ?: it.createdAt)}] [$it.author.name]: $it.content"
				if (it.attachments) m += " (attached: ${it.attachments.collect { it.url }.join(", ")})"
				m = m.replaceAll(/(?!\r)\n/, "\r\n")
				m += "\r\n"
				t += m
			}
			t = t.replace("`", "‘")
			if (t.size() > 1200) sendFile(t.getBytes("UTF-8"), "logs-$id-$number-${after ? "after" : "before"}.txt")
			else sendMessage(t.block("accesslog"))
		}

		bot.command(["brainfuck", "bf",
			~/(?:brainfuck|bf)<(\w+)>/],
			group: "Useful",
			description: "Interprets Brainfuck code.",
			usages: [
				" (code)": "Interprets the code.",
				"<(mode)> (code)": "Interprets the code and prints the output with " +
					"the given mode. Default mode is char, other modes are unicode and num." +
					" unicode converts the stack to Unicode characters, char adds 32 and " +
					"converts them, while num outputs the number values of the stack.",
			]){
			BrainfuckInterpreter.Modes mode = BrainfuckInterpreter.Modes.
				"${(captures[0] ?: "CHAR").toUpperCase()}"
			def intrp = new BrainfuckInterpreter()
			boolean done
			Thread a = Thread.start {
				def r = intrp.interpret(args, mode)
				sendMessage(String.format("""\
					|> Output:
					|%s
					|> Steps: %d, stack position: %d
					|> Stack: %s""".stripMargin(),
					JSONUtil.json(r), intrp.steps, intrp.stackPosition,
					intrp.stack.changedValues.collect { k, v -> "[$k:$v]" }
						.join(" ")).block("accesslog"))
				done = true
			}
			Thread.sleep(5000)
			if (!done){
				a.interrupt()
				decorate("Evaluation took longer than 5 seconds.\n" +
					"Steps: $intrp.steps, stack position: $intrp.stackPosition\n" +
					"Stack: " + intrp.stack.changedValues.collect { k, v -> "[$k:$v]" }.join(" "))
			}
		}

		bot.command("mymessages",
			group: "Useful",
			description: "Gives (information about) your messages that I have logged.",
			usages: [
				"": "Sends you your log file.",
				" count (text)": "Counts how many times you said the given phrase.",
				" countignorecase (text)": "Counts how many times you said the given " +
					"phrase, but ignores character case."
			]){
			File file = new File("markovs/${json.author.id}.txt")
			if (!file.exists()) decorate("I haven't logged any of your messages.")
			else if (!args) sendFile(file)
			else {
				Arguments a = new Arguments(args)
				def choice = a.afterSpace
				if (choice == "count")
					decorate("I have found the phrase \"$a.rest\" " +
						file.text.count(a.rest) + " times in your messages.")
				else if (choice == "countignorecase")
					decorate("I have found the phrase \"$a.rest\" " +
						file.text.toLowerCase().count(a.rest.toLowerCase()) +
						" times in your messages.")
			}
		}
	}

	static OutputStream drawColor(int clr, int width, int height){
		width = Math.max(Math.min(width, 2500), 120)
		height = Math.max(Math.min(height, 2500), 120)
		Util.draw(width: width, height: height){
			Color c = new Color(clr)
			int rgb = (c.RGB << 8) >>> 8
			color = c
			fillRect(0, 0, it.width, it.height)
			color = new Color([0xffffff, 0].max { it ? it - rgb : rgb - it })
			drawString("Hex: #" + Integer.toHexString(rgb).padLeft(6, "0"), 10, 20)
			drawString("RGB: $c.red, $c.green, $c.blue", 10, 40)
			drawString("Dec: $rgb", 10, 60)
		}
	}
}
