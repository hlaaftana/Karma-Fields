package hlaaftana.karmafields.registers

import com.mashape.unirest.http.Unirest
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.objects.Message
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.karmafields.kismet.Kismet

import javax.script.SimpleBindings

import static hlaaftana.discordg.util.WhatIs.whatis
import hlaaftana.karmafields.Arguments
import hlaaftana.karmafields.BrainfuckInterpreter
import hlaaftana.karmafields.CommandRegister
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.kismet.KismetException
import hlaaftana.karmafields.Util
import javax.script.ScriptEngine
import javax.script.ScriptException
import java.awt.Color
import javax.script.ScriptEngineManager
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

class UsefulCommands extends CommandRegister {
	{ group = 'Useful' }
	static ScriptEngine jsEngine = new ScriptEngineManager().getEngineByName('javascript')
	static CompilerConfiguration cc

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
			'hlaaftana.discordg.util',
			'hlaaftana.discordg.util.bot',
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
		cc = new CompilerConfiguration()
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
			batchable: true){ d ->
			String dea = args
				.replaceAll(/^```\w*\n/, '')
				.replaceAll(/```$/, '')
			if (json.author.id == KarmaFields.me.id &&
				usedTrigger.toString() != '><'){
				try{
					sendMessage(('> ' + new GroovyShell(
						new Binding(d + [data: d, now:
							System.&currentTimeMillis]), this.cc)
							.evaluate(dea).toString()).block('groovy'))
				}catch (ex){
					if (captures.contains('!')) ex.printStackTrace()
					sendMessage(ex.toString().block('groovy'))
				}
			}else{
				def evaluation
				try{
					evaluation = JSONUtil.parse(
						Unirest.post('http://groovyconsole.appspot.com/executor.groovy')
						.field('script', dea)
						.asString().body)
				}catch (ex){
					formatted('Failed to request evaluation.')
					return
				}
				String output = ''
				if (evaluation.executionResult){
					output += '\n' + "> Result:\n$evaluation.executionResult".block('groovy')
				}
				if (evaluation.outputText){
					output += '\n' + "> Output:\n$evaluation.outputText".block('groovy')
				}
				if (evaluation.stacktraceText){
					output += '\n' + "> Error:\n$evaluation.stacktraceText".block('groovy')
				}
				try{
					sendMessage(output)
				}catch (ex){
					Message dong = formatted('Message too long. Uploading JSON result of evaluation...')
					sendFile(JSONUtil.pjson(evaluation).getBytes('UTF-8'), "evaluation_${message.id}.json")
					dong.delete()
				}
			}
		}

		command('template',
			id: '36',
			description: 'Custom command templates.',
			usages: [
		        ' get (name)': 'Gives you the given template.',
				' list': 'Gives you a list of templates.',
				' submit {name} (file or text)': 'Submits a new template.'
			]){
			Arguments a = new Arguments(args)
			whatis(a.next()){
				when('get'){
					def x = new File("templates/${a.rest}.ksmt")
					if (x.exists()) {
						try {
							sendFile x
						} catch (ex) {
							formatted 'File was too big. Yikes. Try the GitHub repository.'
						}
					}else formatted 'File doesn\'t exist.'
				}
				when('list'){
					def l = new File('templates').list().findAll { it.endsWith('ksmt') }
						.collect { it[0..-6] }
					formatted l.join(', ')
				}
				when('submit'){
					def n = a.next()
					def t = json.attachments ? message.attachment.newInputStream().text : a.rest
					new File("templates/submissions/$n-${json.id}.ksmt").write(t)
					formatted 'Done.'
				}
			}
		}

		command(['kismet', ~/kismet(!)/],
			id: '27',
			description: 'Evaluates Kismet code. Kismet is a custom language specifically ' +
				'for this bot. Docs: https://gist.github.com/hlaaftana/51745113c38ef7dd08b6fe83e2b1cabf.',
			usages: [
				' (text)': 'The evals it.'
			]){
			try{
				formatted(KarmaFields.parseDiscordKismet(args, [__original_message: message,
					message: message]).evaluate())
			}catch (KismetException ex){
				if (captures.contains('!')) ex.printStackTrace()
				formatted(ex)
			}
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
			batchable: true){ d ->
			def r = Util.resolveColor(d)
			if (r instanceof String){
				formatted(r)
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
				sendFile(y, filename: 'color.png')
			}catch (NoPermissionException ex){
				formatted('I don\'t seem to have permissions to send files. ' +
					'Maybe you need to try in a testing channel?')
			}
		}

		command(['javascript', 'js'],
			id: '34',
			description: 'Interprets JavaScript code.',
			usages: [' (code)': 'Evaluates the code.']){
			try{
				sendMessage(('> ' + jsEngine.eval("String(function(){ $args }())"))
					.block('js'))
			}catch (ScriptException ex){
				sendMessage(('> ' + ex).block('js'))
			}
		}

		command(['graph'],
			id: '37',
			description: 'Graphs code.',
			usages: [' {x low bound} {x high bound} {marker step} {width} {height} {per pixel} {language} (code)':
				'Low bound and high bound will be infinite if not numbers. Language is js, ' +
					'javascript or kismet.']){
			def run
			def a = new Arguments(args)
			def (low, high, step, width, height, pp, lang, code) = [a.next(),
                a.next(), a.next(),  a.next(), a.next(), a.next(), a.next(), a.rest]
			low = low.number ? low.toBigDecimal() : null
			high = high.number ? high.toBigDecimal() : null
			step = step.toBigDecimal()
			pp = pp.toBigDecimal()
			width = (int) Math.min(2000, Math.max(100, width.toInteger()))
			height = (int) Math.min(2000, Math.max(100, height.toInteger()))
			if (lang == 'kismet'){
				def torun = Kismet.parse(code, [x: null])
				run = { x -> torun.context.getData().x = Kismet.model(x) }
			}else if (lang == 'js' || lang == 'javascript'){
				run = { x -> jsEngine.eval(code, new SimpleBindings(x: x)) }
			}
			sendFile(filename: "graph-${json.id}.png", Util.draw(width: width * 2 + 1, height: height * 2 + 1){
				color = new Color(0xFFFFFF)
				fillRect(0, 0, width * 2 + 1, height * 2 + 1)
				color = new Color(0)
				drawLine(width, 0, width, height * 2)
				drawLine(0, height, width * 2, height)
				(width / (step / pp)).times {
					drawLine((int) (width + ((it + 1) * (step / pp))), height - 2,
						(int) (width + ((it + 1) * (step / pp))), height + 2)
					drawLine((int) (width - ((it + 1) * (step / pp))), height - 2,
						(int) (width - ((it + 1) * (step / pp))), height + 2)
				}
				(height / (step / pp)).times {
					drawLine(width - 2, (int) (height + ((it + 1) * (step / pp))),
							width + 2, (int) (height + ((it + 1) * (step / pp))))
					drawLine(width - 2, (int) (height - ((it + 1) * (step / pp))),
							width + 2, (int) (height - ((it + 1) * (step / pp))))
				}
				color = new Color(0xff0000)
				int y0 = run(0) / pp
				drawLine(width + 1, (int) (height - y0) + 1, width + 1, (int) (height - y0) + 1)
				width.times {
					int y1 = run((it + 1) * pp) / pp
					int y2 = run((-it - 1) * pp) / pp
					drawLine((int) (width + it + 1), (int) (height - y1), (int) (width + it + 1), (int) (height - y1))
					drawLine((int) (width - it - 1), (int) (height - y2), (int) (width - it + 1), (int) (height - y2))
				}
			})
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
			BrainfuckInterpreter.Modes mode = MiscUtil.defaultValueOnException(
				BrainfuckInterpreter.Modes.CHAR){
				BrainfuckInterpreter.Modes."${(captures[0] ?: "CHAR").toUpperCase()}"
			}
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
						.join(' ')).block('accesslog'))
				done = true
			}
			Thread.sleep(5000)
			if (!done){
				a.interrupt()
				formatted('Evaluation took longer than 5 seconds.\n' +
					"Steps: $intrp.steps, stack position: $intrp.stackPosition\n" +
					'Stack: ' + intrp.stack.changedValues.collect { k, v -> "[$k:$v]" }.join(" "))
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
			if (json.attachments){
				text = message.attachment.newInputStream().text
				fn = message.attachment.name
			}else if (args){
				try{
					URL url = new URL(args)
					text = url.getText('User-Agent': 'Mozilla/5.0 (Windows NT 10.0; ' +
							'WOW64; rv:53.0) Gecko/20100101 Firefox/53.0', Accept: '*/*',
							Referer: url.toString())
					fn = url.file
				}catch (ex){
					ex.printStackTrace()
					formatted 'Invalid URL.'
					return
				}
			}else{
				formatted 'You need to give a URL or upload a file.'
				return
			}
			List sentences = (text.readLines() - '')*.tokenize()
			List output = [sentences.sample().sample()]
			int iterations = 0
			while (true){
				if (++iterations > 1000){
					formatted 'Seems I got stuck in a loop.'
					return
				}
				List following = []
				sentences.each {
					for (int i = 0; i < it.size(); i++){
						if (i != 0 && it[i - 1] == output.last())
							following += it[i]
					}
				}
				if (!following) break
				else output.add(following.sample())
			}
			formatted("Markov for $fn:\n" + output.join(' '))
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
			drawString('Hex: #' + Integer.toHexString(rgb).padLeft(6, "0"), 10, 20)
			drawString("RGB: $c.red, $c.green, $c.blue", 10, 40)
			drawString("Dec: $rgb", 10, 60)
		}
	}
}
