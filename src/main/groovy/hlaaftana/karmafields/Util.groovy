package hlaaftana.karmafields

import com.mashape.unirest.http.Unirest
import groovy.transform.CompileStatic
import groovy.transform.CompileDynamic
import hlaaftana.discordg.objects.DiscordObject
import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.Message;
import hlaaftana.discordg.objects.User
import hlaaftana.discordg.objects.Server
import hlaaftana.discordg.util.ConversionUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.Command;
import hlaaftana.discordg.util.JSONUtil
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.Random;

import javax.imageio.ImageIO

@CompileStatic
class Util {
	static String CHANNEL_ARG_REGEX = /<?#?([\d\w\-]+?)>?/
	
	@CompileDynamic
	private static __$dynamicLoad(){
		Graphics2D.metaClass.drawStraightPolygon = { double length, int sides, double x, double y ->
			def g = delegate
			double angle = (180 * (sides - 2)) / sides
			def last = [x + length * Math.sin(angle), y]
			sides.times { int i ->
				def add = [length * Math.cos(2 * i * Math.PI / sides),
					length * Math.sin(2 * i * Math.PI / sides)]
				def rounded = [(int) Math.round(last[0] + add[0]), (int) Math.round(last[1] + add[1])]
				g.drawLine((int) last[0], (int) last[1], *rounded)
				last = sides % 2 ? [last[0] + add[0], last[1] + add[1]] : rounded
			}
		}
		Server.metaClass {
			propertyMissing << { String name ->
				serverJson(delegate)[name]
			} << { String name, value ->
				modifyServerJson(delegate, [(name): value])
			}

			modlog << { a ->
				delegate.modlogs.each {
					delegate.textChannel(it)?.formatted(a)
				}
			}
		}
		DiscordObject.metaClass.formatted = { a ->
			delegate.client.sendMessage(delegate, ('> ' + a).replace('\n', '\n> ').block("accesslog"))
		}
		MiscUtil.registerStringMethods()
		MiscUtil.registerCollectionMethods()
	}
	
	private static __$dynamicLoadResult = __$dynamicLoad()
	
	private static Random colorRandom = new Random()
	
	static OutputStream draw(Map args = [:], Closure closure){
		BufferedImage image = new BufferedImage((args['width'] as Integer) ?: 256,
			(args['height'] as Integer) ?: 256, (args['colorType'] as Integer) ?:
			BufferedImage.TYPE_INT_RGB)
		Graphics2D graphics = image.createGraphics()
		Closure ass = (Closure) closure.clone()
		ass.delegate = graphics
		ass.resolveStrategy = Closure.DELEGATE_FIRST
		ass(image)
		graphics.dispose()
		ByteArrayOutputStream baos = new ByteArrayOutputStream()
		ImageIO.write(image, (args['imageType']?.toString()) ?: 'png', baos)
		baos
	}

	static String dateFormat = 'yyyy-MM-dd HH:mm:ss.SSS z'
	static String formatName(DiscordObject obj){ '"' + obj.name.replace('\"', '\\\"') + '"' }
	static String formatUrl(String url){ '"' + url + '"' }
	static String formatLongUser(User user){ "\"$user.name\"#$user.discrim ($user.id)" }
	
	static String formatMessage(Message msg){
		LocalDateTime time = MiscUtil.dateToLDT(msg.timestamp)
		String.format("{%s|%s} [%s] <%s>: %s",
			time.toLocalDate(),
			time.toLocalTime(),
			msg.private ? 'DM' :
				"$msg.server#$msg.channel",
			msg.author, msg.content)
	}
	
	static serverJson(server){
		File file = new File("guilds/${DiscordObject.id(server)}.json")
		if (!file.exists() || file.size() == 0) file.write('{}')
		JSONUtil.parse(file)
	}
	
	static modifyServerJson(server, aa){
		JSONUtil.modify("guilds/${DiscordObject.id(server)}.json", aa)
	}
	
	static getCreds(){
		JSONUtil.parse(new File('creds.json'))
	}

	static getState(){
		JSONUtil.parse(new File('state.json'))
	}

	static File modifyState(Map x){
		JSONUtil.modify('state.json', x)
	}
	
	static String uploadToPuush(bytes, String filename = 'a'){
		Unirest.post('https://puush.me/api/up')
			.field('k', creds['puush_api_key'])
			.field('z', filename)
			.field('f', ConversionUtil.getBytes(bytes), filename)
			.asString().body.tokenize(',')[1]
	}

	static resolveColor(Map data){
		int color
		String arg = data['args'].toString().trim().replace('#', '')
			.replaceAll(/\s+/, "").toLowerCase()
		if (arg.toLowerCase() == 'random'){
			color = Util.colorRandom.nextInt(0xFFFFFF)
		}else if (arg.toLowerCase() in ['me', 'my', 'mine']){
			color = data['author'] instanceof Member ? ((Member) data['author']).colorValue : 0
		}else if (arg ==~ /[0-9a-fA-F]+/){
			try{
				color = Integer.parseInt(arg, 16)
			}catch (NumberFormatException ex){
				return 'Invalid hexadecimal number. Probably too large.'
			}
		}else if (arg ==~ /(?:rgb\()?[0-9]+,[0-9]+,[0-9]+(?:\))?/){
			int[] rgb = arg.findAll(/\d+/)*.toInteger()
			color = (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]
		}else if (arg ==~ /\w+/){
			if (!MiscUtil.namedColors.containsKey(arg)){
				return 'Invalid named color. List here: ' +
					Util.formatUrl(
						'http://www.december.com/html/spec/colorsvg.html')
			}
			color = MiscUtil.namedColors[arg]
		}
		color
	}
}
