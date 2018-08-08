package hlaaftana.kf.registers

import com.mashape.unirest.http.Unirest
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.discordg.Snowflake
import hlaaftana.discordg.objects.Channel
import hlaaftana.discordg.objects.Emoji
import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.User
import hlaaftana.kf.Arguments
import hlaaftana.kf.BrainfuckInterpreter
import hlaaftana.kf.CommandRegister
import hlaaftana.kf.Util
import hlaaftana.discordg.util.bot.CommandEventData
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.kismet.Kismet
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.exceptions.NoPermissionException
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.SimpleBindings
import java.awt.*
import java.util.List
import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
class UsefulCommands extends CommandRegister {
	{ group = 'Useful' }
	static ScriptEngine jsEngine = new ScriptEngineManager().getEngineByName('javascript')
	static CompilerConfiguration cc = new CompilerConfiguration()
	static final File markovFolder = new File('../markovs')

	static BrainfuckInterpreter.Modes death(String name) {
		BrainfuckInterpreter.Modes.valueOf((name ?: "CHAR").toUpperCase())
	}

	static {
		jsEngine.eval('''java = undefined, org = undefined, javax = undefined, com = undefined,
edu = undefined, javafx = undefined, exit = undefined, quit = undefined, load = undefined,
loadWithNewGlobal = undefined, DataView = undefined, JSAdapter = undefined, JavaImporter = undefined,
Packages = undefined, Java = undefined;''')
		ImportCustomizer imports = new ImportCustomizer()
		imports.addStarImports(
			'hlaaftana.kf.registers',
			'hlaaftana.kf',
			'hlaaftana.discordg',
			'hlaaftana.discordg.util',
			'hlaaftana.discordg.objects',
			'hlaaftana.kismet',
			'java.awt')
		imports.addStaticStars(
			'hlaaftana.kf.KarmaFields',
			'hlaaftana.kf.Util'
		)
		imports.addImports(
			'java.awt.image.BufferedImage',
			'javax.imageio.ImageIO',
			'java.util.List')
		imports.addStaticImport('now', 'java.lang.System', 'currentTimeMillis')
		cc.addCompilationCustomizers(imports)
	}

	def register() {
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
			if (author.id == '98457401363025920' &&
				trigger.toString() != 'poo! ')
				try {
					sendMessage new GroovyShell(
						new Binding(*: d.properties, *: d.extra, data: d), cc)
							.evaluate(dea).toString()
				} catch (ex) {
					if (captures?.contains('!')) ex.printStackTrace()
					sendMessage ex.toString()
				}
			else {
				Map<String, Object> evaluation
				try {
					evaluation = (Map<String, Object>) JSONUtil.parse(
						Unirest.post('http://groovyconsole.appspot.com/executor.groovy')
						.field('script', dea)
						.asString().body)
				} catch (ignored) {
					return respond('Its trolled.')
				}
				StringBuilder output = new StringBuilder()
				if (evaluation.executionResult)
					output.append '\n' append "Result:\n$evaluation.executionResult"
				if (evaluation.outputText)
					output.append '\n' append "Output:\n$evaluation.outputText"
				if (evaluation.stacktraceText)
					output.append '\n' append "Error:\n$evaluation.stacktraceText"
				try {
					sendMessage(output.toString())
				} catch (ignored) {
					Message dong = respond 'Message too long. Uploading JSON result of evaluation...'
					sendFile('', new ByteArrayInputStream(JSONUtil.pjson(evaluation).getBytes('UTF-8')),
							"evaluation_${message.id}.json")
					dong.delete()
				}
			}
		}

		command('kismet',
			id: 39,
			description: 'Evaluates Kismet code. Kismet examples and source: https://github.com/hlaaftana/Kismet',
			examples: [
				' |> 33 [bit_and 42] [div 6] [range 1] [join "\\n"]',
				' product [range 1 100]'
			]) {
			try {
				def t = Thread.start {
					try {
						sendMessage(Kismet.eval(arguments.replaceAll(/^\s*```\w*\s+/, '')
								.replaceAll(/```\s*$/, '')).toString())
					} catch (ex) {
						sendMessage(ex.toString())
					}
				}
				Thread.sleep(12000)
				if (t.alive) {
					t.interrupt()
					respond('Code ran for more than 12 seconds.')
				}
			} catch (ex) {
				sendMessage(ex.toString())
			}
		}

		command('kismet!',
				id: 40,
				description: 'Evaluates kismet code.',
				hide: true) {
			if (author.id != '98457401363025920') return respond('Who le hall are you')
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
			if (r instanceof String) return respond(r)
			int color = r as int
			int width = 250, height = 250
			if (captures) {
				width = captures[0].toInteger()
				height = captures.last().toInteger()
			}
			ByteArrayOutputStream y = drawColor(color, width, height)
			try {
				channel.sendFile(new ByteArrayInputStream(y.toByteArray()), 'color.png')
			} catch (NoPermissionException ignored) {
				respond('I cant send files. ')
			}
		}

		command(['javascript', 'js'],
			id: '34',
			description: 'Interprets JavaScript code.',
			usages: [' (code)': 'Evaluates the code. Note you have to add return to the end.']){
			try {
				def t = Thread.start {
					try {
						sendMessage(jsEngine.eval("String(function(){ $arguments }())").toString())
					} catch (ex) {
						sendMessage(ex.toString())
					}
				}
				Thread.sleep(12000)
				if (t.alive) {
					t.interrupt()
					respond('Code ran for more than 12 seconds.')
				}
			} catch (ex) {
				sendMessage(ex.toString())
			}
		}

		command(['livescript', 'ls'],
				id: '40',
				description: 'Interprets LiveScript ("https://livescript.net/") code.',
				usages: [' (code)': 'Evaluates the code. Note you have to add return to the end.']){
			try {
				final file = new File("temp/${System.currentTimeMillis()}.ls")
				file.write(arguments)
				final cmd = "lsc.cmd -c -p \"$file.absolutePath\"".execute()
				if (cmd.err.text) return respond('> Livescript error:\n' + cmd.err.text)
				final code = cmd.text
				def t = Thread.start {
					try {
						final result = jsEngine.eval(code)
						final str = jsEngine.eval("String(result)", new SimpleBindings(result: result))
						sendMessage(str.toString())
					} catch (ex) {
						sendMessage(ex.toString())
					}
				}
				Thread.sleep(12000)
				if (t.alive) {
					t.interrupt()
					respond('Code ran for more than 12 seconds.')
				}
			} catch (ex) {
				sendMessage(ex.toString())
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
			def mode = MiscUtil.<BrainfuckInterpreter.Modes>defaultValueOnException(
				BrainfuckInterpreter.Modes.CHAR){
				death(captures[0])
			}
			def intrp = new BrainfuckInterpreter()
			boolean done = false
			Thread a = Thread.start {
				def r = intrp.interpret(arguments, mode)
				sendMessage(String.format('''\
					|Output:
					|%s
					|Steps: %d, stack position: %d
					|Stack: %s'''.stripMargin(),
					JSONUtil.json(r), intrp.steps, intrp.stackPosition,
					intrp.stack[0..intrp.max].withIndex().collect { k, v -> "[$k:$v]" }
						.join(' ')))
				done = true
			}
			Thread.sleep 5000
			if (!done){
				a.interrupt()
				respond('Evaluation took longer than 5 seconds.\n' +
					"Steps: $intrp.steps, stack position: $intrp.stackPosition\n" +
					'Stack: ' + intrp.stack.findAll().collect { v, k -> "[$k:$v]" }.join(" "))
			}
		}


		command(['markov', 'markov!'],
			id: '10',
			description: 'Generates a sentence based off of order of words from text.',
			usages: [
				' (file)': 'Generates a sentence from the given file (<=5mb).',
				' (user)': 'Generates a sentence from old user logs if i have their logs.',
			],
			batchable: true) {
			def text, fn
			if (message.attachments) {
				final attach = message.attachment
				if (attach.size > 5120)
					return respond("file size was $attach.size bytes but i need it to be less than 5 mb")
				final f = new File(markovFolder, "${attach.size}b - $attach.filename")
				if (f.exists() && f.size() == attach.size) text = f.text
				else f.write(text = attach.url.toURL().newInputStream().text)
				fn = attach.filename
			} else {
				final user = !arguments ? author : Snowflake.isId(arguments) ?
						new Member(null, [id: arguments, name: arguments]) : guild?.find(guild?.memberCache, arguments)
				if (null == user) return respond("is that a user")
				final file = new File(markovFolder, "${user.id}.txt")
				if (!file.exists()) return respond("Logs for user $user.name don't exist.")
				text = file.text
				fn = user.name
			}
			final start = System.currentTimeMillis()
			final lines = text.readLines()
			List<List<String>> sentences = new ArrayList<List<String>>(lines.size().intdiv(4).intValue())
			for (l in lines) {
				final t = l.tokenize()
				if (!t.empty) sentences.add(t)
			}
			List<String> output
			try {
				output = markov(sentences)
			} catch (TooManyLoopsException ignored) {
				return respond('Looks like I got stuck in a loop.')
			}
			final time = System.currentTimeMillis() - start
			def msg = new StringBuilder()
			if (alias.length() == 7) msg << '(took ' << time << 'ms)'
			msg << '\n'
			msg << output.join(' ')
					.replaceAll(Channel.MENTION_REGEX) { full, id -> "[#${guild.channel(id) ?: 'unknown-channel'}]" }
					.replaceAll(User.MENTION_REGEX) { full, id -> "[@${guild.member(id) ?: 'unknown-user'}]" }
					.replaceAll(Emoji.REGEX) { full, name, id -> "[:$name:]" }
			respond msg.toString()
		}

		command('mylogs',
			id: '1O',
			description: 'Meddles with your logs',
			usages: [
				' [~|w]search[/n] (terms)': 'Searches through your logs line by line, where n is the optional result number, ' +
				       '~ allows for regex searching and w searches words.',
				' [~|w]count (search term)': 'Counts how many times you said something. ~ allows for regex, w searches words.',
				' line (number)': 'Shows you the line number in your logs and the 2 surrounding lines of it.'
			]) {
			final file = new File("../markovs/${author.id}.txt")
			if (!file.exists()) return respond('You don\'t have any logs.')

			final a = new Arguments(arguments)
			if (!a.hasNext()) return

			final arg = a.next()
			Matcher matcher

			if ((matcher = (arg =~ /([~w])?search(?:\/(\d+))?/))) {
				final term = a.rest
				final lines = file.readLines()
				final option = ' ~w'.indexOf(matcher.group(1) ?: ' ')
				def regex
				if (option == 1) regex = ~term
				else if (option == 2) regex = ~/\b${Pattern.quote term}\b/
				def number = Integer.parseInt(matcher.group(2) ?: '1')
				for (int i = 0; i < lines.size(); ++i) {
					final liner = lines[i]
					final contains = null == regex ? liner.contains(term) : (liner =~ regex).find()
					if (contains && !(--number)) {
						final line = liner.replaceAll('(' + Pattern.quote(term) + ')', '[$1]')
						respond """[line $i]
${lines[i - 1]}
$line
${lines[i + 1]}"""  .replaceAll(Channel.MENTION_REGEX) { full, id -> "[#${guild.channel(id) ?: 'unknown-channel'}]" }
		.replaceAll(User.MENTION_REGEX) { full, id -> "[@${guild.member(id) ?: 'unknown-user'}]" }
		.replaceAll(Emoji.REGEX) { full, name, id -> "[:$name:]" }
						return
					}
				}
				return respond('No results found.')
			} else if ((matcher = (arg =~ /([~w])?count/))) {
				final term = a.rest
				final lines = file.readLines()
				final option = ' ~w'.indexOf(matcher.group(1) ?: ' ')
				def regex
				if (option == 1) regex = ~term
				else if (option == 2) regex = ~/\b${Pattern.quote term}\b/
				int number = 0
				for (int i = 0; i < lines.size(); ++i) {
					final line = lines[i]
					final contains = null == regex ? line.contains(term) : (line =~ regex).find()
					if (contains) ++number
				}
				return respond("Counted $number results.")
			} else if (arg == 'line') {
				final i = Integer.parseInt(a.rest)
				final lines = file.readLines()
				respond """[line $i]
${lines[i - 1]}
${lines[i]}
${lines[i + 1]}"""  .replaceAll(Channel.MENTION_REGEX) { full, id -> "[#${guild.channel(id) ?: 'unknown-channel'}]" }
						.replaceAll(User.MENTION_REGEX) { full, id -> "[@${guild.member(id) ?: 'unknown-user'}]" }
						.replaceAll(Emoji.REGEX) { full, name, id -> "[:$name:]" }
			}
		}
	}

	static ByteArrayOutputStream drawColor(int clr, int width, int height){
		width = Math.max(Math.min(width, 2500), 120)
		height = Math.max(Math.min(height, 2500), 120)
		Util.draw width: width, height: height, {
			final c = new Color(clr)
			int rgb = (c.RGB << 8) >>> 8
			color = c
			fillRect(0, 0, it.width, it.height)
			color = new Color(0xffffff > 2 * rgb ? 0xffffff : 0)
			drawString('Hex: #' + Integer.toHexString(rgb).padLeft(6, "0"), 10, 20)
			drawString("RGB: $c.red, $c.green, $c.blue", 10, 40)
			drawString("Dec: $rgb", 10, 60)
		}
	}

	static List<String> markov(List<List<String>> sentences) {
		List<String> sentence = new ArrayList<String>()
		def last
		sentence.add(last = MiscUtil.sample(MiscUtil.sample(sentences)))
		int iterations = 0
		while (true) {
			if (++iterations > 1000) throw new TooManyLoopsException()
			List<String> following = []
			for (s in sentences) {
				for (int i = 1; i < s.size(); i++) {
					final before = s[i - 1]
					if (before.hashCode() == last.hashCode() && before == last)
						following.add s[i]
				}
			}
			if (!following) break
			else sentence.add(last = MiscUtil.sample(following))
		}
		sentence
	}
}

@CompileStatic @InheritConstructors
class TooManyLoopsException extends Exception {}