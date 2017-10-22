package hlaaftana.karmafields.registers

import com.mashape.unirest.http.Unirest
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import hlaaftana.karmafields.kismet.Kismet
import hlaaftana.karmafields.relics.CommandEventData
import hlaaftana.karmafields.relics.JSONUtil
import hlaaftana.karmafields.relics.MiscUtil
import sx.blah.discord.handle.obj.IMessage
import sx.blah.discord.handle.obj.IUser
import sx.blah.discord.util.MissingPermissionsException

import java.awt.image.BufferedImage

import hlaaftana.karmafields.BrainfuckInterpreter
import hlaaftana.karmafields.CommandRegister
import hlaaftana.karmafields.Util
import javax.script.ScriptEngine
import javax.script.ScriptException
import java.awt.Color
import javax.script.ScriptEngineManager
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

@CompileStatic
class UsefulCommands extends CommandRegister {
	{ group = 'Useful' }
	static ScriptEngine jsEngine = new ScriptEngineManager().getEngineByName('javascript')
	static CompilerConfiguration cc = new CompilerConfiguration()

	@CompileDynamic
	static BrainfuckInterpreter.Modes death(String name) {
		BrainfuckInterpreter.Modes."${(name ?: "CHAR").toUpperCase()}"
	}

	static {
		jsEngine.eval('''java = undefined, org = undefined, javax = undefined, com = undefined,
edu = undefined, javafx = undefined, exit = undefined, quit = undefined, load = undefined,
loadWithNewGlobal = undefined, DataView = undefined, JSAdapter = undefined, JavaImporter = undefined,
Packages = undefined, Java = undefined;''')
		ImportCustomizer imports = new ImportCustomizer()
		imports.addStarImports(
			'hlaaftana.discordg',
			'hlaaftana.discordg.collections',
			'hlaaftana.discordg.dsl',
			'hlaaftana.discordg.exceptions',
			'hlaaftana.discordg.logic',
			'hlaaftana.discordg.net',
			'hlaaftana.discordg.objects',
			'hlaaftana.discordg.status',
			'hlaaftana.karmafields.relics',
			'hlaaftana.karmafields.relics',
			'hlaaftana.discordg.voice',
			'hlaaftana.karmafields',
			'hlaaftana.karmafields.kismet',
			'hlaaftana.karmafields.registers',
			'java.awt')
		imports.addStaticStars(
			'hlaaftana.karmafields.KarmaFields',
			'hlaaftana.karmafields.Util'
		)
		imports.addImports(
			'java.awt.image.BufferedImage',
			'javax.imageio.ImageIO',
			'java.util.List')
		cc.addCompilationCustomizers(imports)
	}

	def register(){
		command(['eval', ~/eval(!)/],
			id: '26',
			description: 'Evaluates Groovy code. Everyone can use this.',
			usages: [
				' (code)': 'Evaluates the given code.'
			],
			examples: [
				' (33 & 42).intdiv(6).times { println it }'
			],
			batchable: true){ CommandEventData d ->
			String dea = arguments
				.replaceAll(/^```\w*\n/, '')
				.replaceAll(/```$/, '')
			if (author.longID == 98457401363025920 &&
				trigger.toString() != '><')
				try {
					sendMessage MiscUtil.block('> ' + new GroovyShell(
						new Binding((d.properties as Map) + (d.extra as Map) +
								[data: d, now: System.&currentTimeMillis]), cc)
							.evaluate(dea).toString(), 'groovy')
				} catch (ex) {
					if (captures?.contains('!')) ex.printStackTrace()
					sendMessage MiscUtil.block(ex.toString(), 'groovy')
				}
			else {
				Map<String, Object> evaluation
				try {
					evaluation = (Map<String, Object>) JSONUtil.parse(
						Unirest.post('http://groovyconsole.appspot.com/executor.groovy')
						.field('script', dea)
						.asString().body)
				} catch (ignored) {
					return formatted('Failed to request evaluation.')
				}
				StringBuilder output = new StringBuilder()
				if (evaluation.executionResult)
					output.append '\n' append MiscUtil.block("> Result:\n$evaluation.executionResult", 'groovy')
				if (evaluation.outputText)
					output.append '\n' append MiscUtil.block("> Output:\n$evaluation.outputText", 'groovy')
				if (evaluation.stacktraceText)
					output.append '\n' append MiscUtil.block("> Error:\n$evaluation.stacktraceText", 'groovy')
				try {
					sendMessage(output.toString())
				} catch (ignored) {
					IMessage dong = formatted('Message too long. Uploading JSON result of evaluation...')
					sendFile('', new ByteArrayInputStream(JSONUtil.pjson(evaluation).getBytes('UTF-8')),
							"evaluation_${message.stringID}.json")
					dong.delete()
				}
			}
		}

		command('kismet',
			id: 39,
			description: 'Evaluates kismet code.') {
			try {
				sendMessage(Kismet.eval(arguments.replaceAll(/^\s*```\w*\s+/, '')
					.replaceAll(/```\s*$/, '')).toString())
			} catch (ex) {
				sendMessage(MiscUtil.block(ex.toString(), 'groovy'))
			}
		}

		command('kismet!',
				id: 40,
				description: 'Evaluates kismet code.') {
			sendMessage(Kismet.eval(arguments.replaceAll(/^\s*```\w*\s+/, '')
					.replaceAll(/```\s*$/, '')).toString())
		}

		command(['showcolor', 'showcolour',
			~/showcolou?r<(\d+?)>/,
			~/showcolou?r<(\d+?)\s*,\s*(\d+?)>/],
			id: '28',
			description: 'Posts an image containing (information about) a color.',
			usages: [
				' (color)': 'Sets width and height to 250 and shows (color).',
				' random': 'Sets width and height to 250 and shows a random color.',
				' my|me|mine': 'Sets width and height to 250 and shows your color.',
				'<(size)> (color)': 'Sets width and height to (size) and shows (color).',
				'<(width), (height)> (color)': 'Sets width and height individually and shows (color).'
			],
			batchable: true){ CommandEventData d ->
			def r = Util.resolveColor(d)
			if (r instanceof String) return formatted(r)
			int color = r as int
			int width = 250
			int height = 250
			if (captures) {
				width = captures[0].toInteger()
				height = captures.last().toInteger()
			}
			ByteArrayOutputStream y = drawColor(color, width, height)
			try {
				sendFile('', new ByteArrayInputStream(y.toByteArray()), 'color.png')
			} catch (MissingPermissionsException ignored) {
				formatted('I don\'t seem to have permissions to send files. ' +
					'Maybe you need to try in a testing channel?')
			}
		}

		command(['javascript', 'js'],
			id: '34',
			description: 'Interprets JavaScript code.',
			usages: [' (code)': 'Evaluates the code.']){
			try{
				sendMessage(MiscUtil.block('> ' + jsEngine.eval("String(function(){ $arguments }())"), 'js'))
			}catch (ScriptException ex){
				sendMessage(MiscUtil.block('> ' + ex, 'js'))
			}
		}

		command(['brainfuck', 'bf',
			~/(?:brainfuck|bf)<(\w+)>/],
			id: '30',
			description: 'Interprets Brainfuck code.',
			usages: [
				' (code)': 'Interprets the code.',
				'<(mode)> (code)': 'Interprets the code and prints the output with ' +
					'the given mode. Default mode is char, other modes are unicode and num.' +
					' unicode converts the stack to Unicode characters, char adds 32 and ' +
					'converts them, while num outputs the number values of the stack.',
			]){
			BrainfuckInterpreter.Modes mode = MiscUtil.<BrainfuckInterpreter.Modes>defaultValueOnException(
				BrainfuckInterpreter.Modes.CHAR){
				death(captures[0])
			}
			BrainfuckInterpreter intrp = new BrainfuckInterpreter()
			boolean done = false
			Thread a = Thread.start {
				def r = intrp.interpret(arguments, mode)
				sendMessage(String.format('''```accesslog
					|> Output:
					|%s
					|> Steps: %d, stack position: %d
					|> Stack: %s```'''.stripMargin(),
					JSONUtil.json(r), intrp.steps, intrp.stackPosition,
					intrp.stack[0..intrp.max].withIndex().collect { k, v -> "[$k:$v]" }
						.join(' ')))
				done = true
			}
			Thread.sleep 5000
			if (!done){
				a.interrupt()
				formatted('Evaluation took longer than 5 seconds.\n' +
					"Steps: $intrp.steps, stack position: $intrp.stackPosition\n" +
					'Stack: ' + intrp.stack.findAll().collect { v, k -> "[$k:$v]" }.join(" "))
			}
		}


		command('markov',
			id: '10',
			description: 'Generates a sentence based off of order of words from text.',
			usages: [
				' (file)': 'Generates a sentence from the given file.',
				' (url)': 'Generates a sentence from the given URL.'
			],
			batchable: true){
			def text, fn
			if (message.attachments) {
				text = message.attachments[0].url.toURL().newInputStream().text
				fn = message.attachments[0].filename
			}else if (!arguments || !arguments.startsWith('http')) {
				IUser user = !arguments ? author : (arguments.long && arguments > '20000000000000000' ?
						guild.getUserByID(arguments.toLong()) : guild.getUsersByName(arguments)[0])
				File file = new File("../markovs/${user.stringID}.txt")
				text = file.text
				fn = file.name
			}
			else if (arguments.startsWith('http'))
				try {
					URL url = new URL(arguments)
					text = url.getText('User-Agent': 'Mozilla/5.0 (Windows NT 10.0; ' +
							'WOW64; rv:53.0) Gecko/20100101 Firefox/55.0', Accept: '*/*',
							Referer: url.toString())
					fn = url.file
				} catch (MalformedURLException ex) {
					ex.printStackTrace()
					return formatted('Invalid URL.')
				}
			else
				return formatted('I dont know what you posted bitch.')
			List<List<String>> sentences = (text.readLines() - '').collect { it.tokenize() }
			List<String> output = [MiscUtil.sample(MiscUtil.sample(sentences))]
			int iterations = 0
			while (true){
				if (++iterations > 1000)
					return formatted('Seems I got stuck in a loop.')
				List<String> following = []
				for (s in sentences)
					for (int i = 0; i < s.size(); i++)
						if (i != 0 && s[i - 1] == output.last())
							following.add s[i]
				if (!following) break
				else output.add(MiscUtil.sample(following))
			}
			formatted "Markov for $fn:\n".concat(output.join(' '))
		}
	}

	static ByteArrayOutputStream drawColor(int clr, int width, int height){
		width = Math.max(Math.min(width, 2500), 120)
		height = Math.max(Math.min(height, 2500), 120)
		Util.draw(width: width, height: height){ BufferedImage it ->
			Color c = new Color(clr)
			int rgb = (c.RGB << 8) >>> 8
			color = c
			fillRect(0, 0, it.width, it.height)
			color = new Color([0xffffff, 0].max { it ? it - rgb : rgb - it })
			drawString('Hex: #' + Integer.toHexString(rgb).padLeft(6, "0"), 10, 20)
			drawString("RGB: $c.red, $c.green, $c.blue", 10, 40)
			drawString("Dec: $rgb", 10, 60)
		}
	}
}
