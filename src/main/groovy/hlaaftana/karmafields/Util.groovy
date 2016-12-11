package hlaaftana.karmafields

import hlaaftana.discordg.objects.DiscordObject
import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.Message;
import hlaaftana.discordg.objects.User
import hlaaftana.discordg.objects.Server
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.Command;
import hlaaftana.discordg.util.JSONUtil
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.Random;

import javax.imageio.ImageIO

class Util {
	static Random colorRandom = new Random()

	static {
		Graphics2D.metaClass.drawStraightPolygon = { double length, int sides, double x, double y ->
			def g = delegate
			double angle = (180 * (sides - 2)) / sides
			def last = [x, y]
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
					delegate.textChannel(it)?.decorate(a)
				}
			}
		}
		DiscordObject.metaClass.decorate = { a ->
			delegate.client.sendMessage(delegate, ('> ' + a).replace('\n', '\n> ').block("accesslog"))
		}
		MiscUtil.registerStringMethods()
		MiscUtil.registerCollectionMethods()
	}

	static OutputStream draw(Map args = [:], Closure closure){
		BufferedImage image = new BufferedImage(args.width ?: 256,
			args.height ?: 256, args.colorType ?: BufferedImage.TYPE_INT_RGB)
		Graphics2D graphics = image.createGraphics()
		Closure ass = closure.clone()
		ass.delegate = graphics
		ass.resolveStrategy = Closure.DELEGATE_FIRST
		ass(image)
		graphics.dispose()
		ByteArrayOutputStream baos = new ByteArrayOutputStream()
		ImageIO.write(image, args.imageType ?: "png", baos)
		baos
	}

	static List splitArgs(String arguments, int max = 0){
		List list = [""]
		String currentQuote
		arguments.toList().each { String ch ->
			if (max && list.size() == max){
				list[list.size() - 1] += ch
			}else{
				if (currentQuote){
					if (ch == currentQuote) currentQuote = null
					else list[list.size() - 1] += ch
				}else{
					if (ch in ['"', "'"]) currentQuote = ch
					else if (Character.isSpaceChar(ch as char)) list += ""
					else list[list.size() - 1] += ch
				}
			}
		}
		list
	}

	static String dateFormat = "yyyy-MM-dd HH:mm:ss.SSS z"
	static String formatName(DiscordObject obj){ "\"${obj.name.replace("\"", "\\\"")}\"" }
	static String formatUrl(String url){ "\"$url\"" }
	static String formatFull(DiscordObject obj){ "\"${obj.name.replace("\"", "\\\"")}\" ($obj.id)" }
	static String formatLongUser(User user){ "\"$user.name\"#$user.discrim ($user.id)" }

	static String formatMessage(Message msg){
		LocalDateTime time = MiscUtil.dateToLDT(msg.timestamp)
		String.format("{%s|%s} [%s] <%s>: %s",
			time.toLocalDate(),
			time.toLocalTime(),
			msg.private ? "DM" :
				"$msg.server#$msg.channel",
			msg.author, msg.content)
	}

	static serverJson(server){
		File file = new File("guilds/${DiscordObject.id(server)}.json")
		if (!file.exists()) file.write("{}")
		JSONUtil.parse(file)
	}

	static modifyServerJson(server, aa){
		JSONUtil.modify("guilds/${DiscordObject.id(server)}.json", aa)
	}

	static boolean checkPerms(Command command, Message message){
		def a = (serverJson(message.server)["permissions"]
			?: [:])[command.hashCode().toString()]
	}

	static Map getCreds(){
		JSONUtil.parse(new File("creds.json"))
	}

	static resolveColor(Map data){
		int color
		def arg = data.args.trim().replace('#', '').replaceAll(/\s+/, "").toLowerCase()
		if (arg.toLowerCase() == "random"){
			color = Util.colorRandom.nextInt(0xFFFFFF)
		}else if (arg.toLowerCase() in ["me", "my", "mine"]){
			color = data.author instanceof Member ? data.author.colorValue : 0
		}else if (arg ==~ /[0-9a-fA-F]+/){
			try{
				color = Integer.parseInt(arg, 16)
			}catch (NumberFormatException ex){
				return "Invalid hexadecimal number. Probably too large."
			}
		}else if (arg ==~ /(?:rgb\()?[0-9]+,[0-9]+,[0-9]+(?:\))?/){
			int[] rgb = (arg =~ /\d+/).collect()*.toInteger()
			color = (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]
		}else if (arg ==~ /\w+/){
			if (!MiscUtil.namedColors.containsKey(arg)){
				return "Invalid named color. List here: " +
					Util.formatUrl(
						"http://www.december.com/html/spec/colorsvg.html")
			}
			color = MiscUtil.namedColors[arg]
		}
		color
	}
}
