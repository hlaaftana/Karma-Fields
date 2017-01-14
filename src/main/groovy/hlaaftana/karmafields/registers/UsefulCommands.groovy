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
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

class UsefulCommands {
	static CompilerConfiguration cc

	static {
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
			'hlaaftana.karmafields.registers',
			'java.awt')
		imports.addImports(
			'java.awt.image.BufferedImage',
			'javax.imageio.ImageIO',
			'java.util.List')
		cc = new CompilerConfiguration()
		cc.addCompilationCustomizers(imports)
	}

	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = kf.client

		bot.command('eval',
			group: 'Useful',
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
			if (json.author.id == kf.me.id &&
				usedTrigger.toString() != '><'){
				try{
					sendMessage(('> ' + new GroovyShell(
						new Binding(d + [data: d, kf: kf,
							now: System.&currentTimeMillis] +
							kf.properties +
								Util.metaClass.methods.collectEntries {
									[(it.name): Util.&"$it.name"] }), this.cc)
							.evaluate(dea).toString()).block('groovy'))
				}catch (ex){
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

		bot.command(['showcolor', 'showcolour',
			~/showcolou?r<(\d+?)>/,
			~/showcolou?r<(\d+?)\s*,\s*(\d+?)>/],
			group: 'Useful',
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

		bot.command(['logs',
			~/logs(\+)?/,
			~/logs<(\d+?)>(\+)?/,
			~/logs<$Util.CHANNEL_ARG_REGEX>(\+)?/,
			~/logs<(\d+?)\s*,\s*$Util.CHANNEL_ARG_REGEX>(\+)?/,
			~/logs<$Util.CHANNEL_ARG_REGEX\s*,\s*(\d+?)>(\+)?/],
			group: 'Useful',
			description: 'Looks up messages from certain points in time in a channel.',
			usages: [
				'': 'Looks up most recent posts.',
				' first': 'Looks up first posts.',
				' id (id)': 'Looks up posts at the ID given.',
				' date (year-month-day-...)': 'Looks up posts at the given date (UTC). ' +
					'Every part of the part before is separated by a dash. ' +
					'The progression is year, month, day, hour, minute, second and ' +
					'millisecond. Keep in mind that \'2016\' works but \'16\' has problems. ' +
					'Check examples if you\'re confused.',
				' unix (timestamp)': 'Looks up posts at the given Unix timestamp.',
				' multiplier (multiplier)': 'Multiplies the channel id by the given ' +
					'multiplier and looks up messages at that ID. Shouldn\'t be more than ' +
					'2, unless the channel is old Discord-creation-wise.',
				'<(number)> ...': 'Sets the post number, not more than 100. ' +
					'Arguments can be any of the above.',
				'<(channel), (number)> ...': 'Sets the post number and the channel ' +
					'to get the messages from. Order should keep.',
				'+ ...': 'Shows posts after the given time. Otherwise before. ' +
					'Does not work for first.'
			],
			examples: [
				'<25>',
				'+',
				'<25>+',
				' id 243730654498521100',
				' date 2016-07-04',
				' date 2016-07-04-15-03-39',
				' unix 1479959375236',
				' multiplier 1.405',
				'<27> date 2016-07-04-15-03-39',
				'<27>+ date 2016-07-04-15-03-39',
			],
			defaultPerms: 'can read message history'){
			def cs = captures.clone()
			boolean after = cs.remove('+')
			def maddy = cs.find { it.isInteger() }
			int number
			if (maddy){
				cs.removeElement(maddy)
				number = Math.min(maddy.toInteger(), 100)
			}else number = 50
			def chan = cs[0] ? server.channel(cs[0].removeFromStart('#')) :
				channel
			if (!chan){
				formatted('Invalid channel. Must be in this server.')
				return
			}
			String id
			String error
			Arguments a = new Arguments(args)
			String choice = args ? a.next() : 'first'
			whatis(choice){
				when('first'){
					id = chan.id
					after = true
				}
				when('id'){
					if (DiscordObject.isId(a.rest)) id = a.rest
					else error = 'Invalid ID. IDs are numbers, you can right click '+
						'on things and get their IDs by turning on Developer Mode.'
				}
				when('unix'){
					try{
						id = DiscordObject.millisToId(a.rest.toLong())
					}catch (ex){
						error = 'Invalid Unix timestamp. https://en.wikipedia.org/wiki/Unix_time'
					}
				}
				when('multiplier'){
					try{
						id = ((chan.id.toLong() *
							new BigDecimal(a.rest)) as long).toString()
						if (id > message.id) error = 'The multiplier seems to be too big.'
					}catch (NumberFormatException ex){
						error = 'Invalid multiplier. Needs to be a decimal number ' +
							'with a period instead of a comma.'
					}
				}
				when('date'){
					if (a.rest ==~ /\d+(\-\d+)*/){
						long date = ConversionUtil.experimentalDateParser(a.rest).time
						if (date < chan.createdAtMillis) id = chan.id
						else id = DiscordObject.millisToId(date)
					}else{
						error = 'Invalid date. Every number has to be separated by ' +
							'a dash character. The order is year, month, day, hour,' +
							' minute, second and millisecond. If the year is 2016, ' +
							'it cannot be represented as 16.'
					}
				}
				otherwise {
					error = 'Invalid arguments.'
				}
			}
			
			if (error){
				formatted(error)
				return
			}

			SimpleDateFormat df = new SimpleDateFormat('HH:mm:ss')
			df.timeZone = TimeZone.getTimeZone('Etc/UTC')
			String t = ''
			chan.requestLogs(number, id, after).reverse().each {
				String m = "[${df.format(it.timestamp ?: it.createdAt)}] [$it.author.name]: $it.content"
				if (it.attachments) m += " (attached: ${it.attachments.collect { it.url }.join(", ")})"
				m = m.replaceAll(/(?!\r)\n/, '\r\n')
				m += '\r\n'
				t += m
			}
			t = t.replace("`", "‘")
			if (t.size() > 1200) sendFile(t.getBytes("UTF-8"),
				"logs-$chan.id-$id-$number-${after ? "after" : "before"}.txt")
			else sendMessage(t.block('accesslog'))
		}

		bot.command(['brainfuck', 'bf',
			~/(?:brainfuck|bf)<(\w+)>/],
			group: 'Useful',
			description: 'Interprets Brainfuck code.',
			usages: [
				' (code)': 'Interprets the code.',
				'<(mode)> (code)': 'Interprets the code and prints the output with ' +
					'the given mode. Default mode is char, other modes are unicode and num.' +
					' unicode converts the stack to Unicode characters, char adds 32 and ' +
					'converts them, while num outputs the number values of the stack.',
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

		bot.command(['mymessages',
			~/mymessages([~\&\^.]+)/],
			group: 'Useful',
			description: 'Gives (information about) your messages that I have logged.',
			usages: [
				'': 'Sends you your log file.',
				'~ (...)': 'Accepts given search queries as regex patterns.',
				'^ (...)': 'Ignores case when searching.',
				'. (...)': 'Counts the search queries as whole words.',
				'& (...)': 'Doesn\'t edit the file when performing text operations and uploads it.',
				' count|c (text)': 'Counts how many times you said the given phrase.',
				' line (number)': 'Shows 3 lines near the given line number.',
				' remove (text)': 'Removes text from your log file.',
				' replace \'(text)\' \'(newtext)\'': 'Replaces the given text with the '+
					'given new text in your logs.',
				' search (text)': 'Searches the given text in your messages.',
				' hidechannel (channel)': 'Stops logging messages from the '+
					'given channel, or the current one if none given.',
				' unhidechannel (channel)': 'Starts logging messages from the '+
					'given channel, or the current one if none given.'
			]){
			File file = new File("markovs/${json.author.id}.txt")
			if (!file.exists()) formatted('I haven\'t logged any of your messages.')
			else if (!args) sendFile(file)
			else {
				def params = captures[0]?.toList() ?: []
				boolean ww = params.contains('.')
				boolean ic = params.contains('^')
				boolean rx = params.contains('~')
				boolean ne = params.contains('&')
				Arguments a = new Arguments(args)
				boolean upload = false
				def (filetext, filename) = []
				whatis(a.next()){
					when(['count','c']){
						def (query, regex) = [a.rest, ww || ic || rx]
						if (regex && !rx) query = Pattern.quote(query)
						if (ww || ic || rx){
							if (ww) query = /\b/ + query + /\b/
							if (ic) query = /(?i)/ + query
							formatted("I have found the phrase \"$a.rest\" " +
								(file.text =~ query).count +
								' times in your messages.')
						}
						else formatted("I have found the phrase \"$a.rest\" " +
							file.text.count(query) + ' times in your messages.')
					}
					when('line'){
						def lines = file.readLines()
						int ln
						try{
							ln = a.rest.toBigInteger() % lines.size()
						}catch (NumberFormatException ex){
							formatted('Invalid line number.')
							return
						}
						formatted([lines[ln], lines[ln + 1], lines[ln + 2]].join('\n'))
					}
					when('search'){
						def ft = file.text
						def results = []
						def (query, regex) = [a.rest, ww || ic || rx]
						if (regex && !rx) query = Pattern.quote(query)
						if (ww) query = /\b/ + query + /\b/
						if (ic) query = /(?i)/ + query
						def matcher = ft =~ query
						while (matcher.find() && results.size() != 15){
							int s = matcher.start()
							int lnc = 0
							while (s != 0){
								if (ft[s] == '\n') lnc += 1
								if (lnc == 2){
									lnc = 0
									++s
									break
								}
								--s
							}
							int e = matcher.end()
							while (e != ft.size()){
								if (ft[e] == '\n') lnc += 1
								if (lnc == 2){
									lnc = 0
									--e
									break
								}
								++e
							}
							results.add(ft.substring(s, e))
						}
						def x = 'Results:\r\n\r\n' + results.join('\r\n\r\n')
						if (x.size() > 1000)
							sendFile(x.getBytes('UTF-8'),
								"${json.author.id}-search-${json.id}.txt")
						else formatted(x)
					}
					when('remove'){
						try{
							def (regex, query) = [ww || ic || rx, a.rest]
							if (regex && !rx) query = Pattern.quote(query)
							if (regex){
								if (ww) query = /\b/ + query + /\b/
								if (ic) query = /(?i)/ + query
							}
							filetext = regex ?
								file.text.replaceAll(query, '') :
								file.text.replace(query, '')
						}catch (ex){
							formatted('There was a problem with your '
								+ 'pattern:\n' + ex.message)
							return
						}
						filename = "${json.author.id}-without-${json.id}.txt"
						if (ne) upload = true
						else {
							file.write(filetext)
							formatted 'Done.'
						}
					}
					when('replace'){
						try{
							def (regex, query) = [ww || ic || rx, a.next()]
							if (regex && !rx) query = Pattern.quote(query)
							if (regex){
								if (ww) query = /\b/ + query + /\b/
								if (ic) query = /(?i)/ + query
							}
							def substitute = a.next()
							filetext = regex ?
								file.text.replaceAll(query, substitute) :
								file.text.replace(query, substitute)
						}catch (ex){
							formatted('There was a problem with your '
								+ 'pattern:\n' + ex.message)
							return
						}
						filename = "${json.author.id}-replaced-${json.id}.txt"
						if (ne) upload = true
						else {
							file.write(filetext)
							formatted 'Done.'
						}
					}
					when('hidechannel'){
						def ch = message.channelIdMentions ?
							message.channelIdMentions[0] :
							a.rest ? server.channel(a.rest)?.id :
							json.channel_id
						if (ch){
							JSONUtil.modify(new File('markovdata.json'), [(json.author.id):
								[blacklist: [ch]]])
							formatted 'Done.'
						}else formatted('Invalid channel, or not in this server. ' +
							'If you can\'t add the channel from the server it\'s from, ' +
							'copy its ID and type <#(id)>.')
					}
					when('unhidechannel'){
						def ch = message.channelIdMentions ?
							message.channelIdMentions[0] :
							a.rest ? server.channel(a.rest)?.id :
							json.channel_id
						if (ch){
							def x = JSONUtil.parse(new File('markovdata.json'))
							if (x.containsKey(json.author.id) &&
								x[json.author.id].containsKey('blacklist')){
								x[json.author.id].blacklist -= ch
								JSONUtil.dump(new File('markovdata.json'), x)
							}
							formatted 'Done.'
						}else formatted('Invalid channel, or not in this server. ' +
							'If you can\'t add the channel from the server it\'s from, ' +
							'copy its ID and type <#(id)>.')
					}
				}
				if (!upload) return
				try{
					sendFile(filetext.getBytes('UTF-8'), filename)
				}catch (ex){
					String url = Util.uploadToPuush("$filename\r\n\r\n$filetext", filename)
					if (url)
						formatted("The file was too big to upload, so I uploaded it to $url.")
					else
						formatted('The file was too big to upload, and when I tried ' +
							'to upload it to another website it was still too big.')
				}
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
			drawString('Hex: #' + Integer.toHexString(rgb).padLeft(6, "0"), 10, 20)
			drawString("RGB: $c.red, $c.green, $c.blue", 10, 40)
			drawString("Dec: $rgb", 10, 60)
		}
	}
}
