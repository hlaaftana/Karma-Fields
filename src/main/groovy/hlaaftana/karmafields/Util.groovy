package hlaaftana.karmafields

import com.mashape.unirest.http.Unirest
import groovy.transform.CompileStatic
import groovy.transform.CompileDynamic
import groovy.transform.InheritConstructors
import hlaaftana.discordg.objects.Channel
import hlaaftana.discordg.objects.DiscordObject
import hlaaftana.discordg.objects.Integration
import hlaaftana.discordg.objects.Invite
import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.Message;
import hlaaftana.discordg.objects.PermissionOverwrite
import hlaaftana.discordg.objects.Role
import hlaaftana.discordg.objects.User
import hlaaftana.discordg.objects.Server
import hlaaftana.discordg.util.ConversionUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.karmafields.kismet.Kismet
import hlaaftana.karmafields.kismet.KismetInner
import hlaaftana.karmafields.kismet.Expression

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.time.LocalDateTime
import javax.imageio.ImageIO

@CompileStatic
class Util {
	static String CHANNEL_ARG_REGEX = /<?#?([\d\w\-]+?)>?/
	static Map discordKismetFunctions
	
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
		DiscordObject.metaClass.formatted = { a ->
			client.sendMessage(delegate, ('> ' + a).replace('\n', '\n> ').block("accesslog"))
		}
		User.metaClass.formatted = { a ->
			channel.sendMessage(('> ' + a).replace('\n', '\n> ').block("accesslog"))
		}
		Server.metaClass.guildData = { ->
			KarmaFields.guildData[id]
		}
		MiscUtil.registerStringMethods()
		MiscUtil.registerCollectionMethods()
		discordKismetFunctions = [
			has_permission: { Message orig, user, x = null, perm ->
				if (!x){
					if (user instanceof Member) user.permissions[perm]
					else throw new WhatAreYouDoingException('No server or channel supplied')
				}else if (x instanceof Channel)
					x.permissionsFor(user)[perm]
				else if (x instanceof Server)
					x.member(user).permissions[perm]
				else throw new WhatAreYouDoingException()
			},
			permissions_for: { Message orig, user, channel ->
				channel.permissionsFor(user)
			},
			locked: { Message orig, user, role ->
				role.isLockedFor(user)
			},
			role: { Message orig, server, role ->
				server.role(role)
			},
			channel: { Message orig, server, channel ->
				server.channel(channel)
			},
			member: { Message orig, server, user ->
				server.member(user)
			},
			permission_overwrite: { Message orig, channel, overwrite ->
				channel.overwrite(overwrite)
			},
			kick: { Message orig, server = null, user ->
				if (orig && !orig.authorPermissions['kick'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				if (user instanceof Member) user.kick()
				else server.kick(user)
			},
			ban: { Message orig, server = null, user ->
				if (orig && !orig.authorPermissions['ban'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				if (user instanceof Member) user.ban()
				else server.ban(user)
			},
			unban: { Message orig, server, user ->
				if (orig && !orig.authorPermissions['ban'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				server.unban(user)
			},
			add_role: { Message orig, server = null, user, role ->
				def s = user instanceof Member ? user.server : server
				def r = s.role(role)
				if (orig && !orig.authorPermissions['manageRoles'] || r.isLockedFor(orig.author))
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				s.addRole(user, r)
			},
			remove_role: { Message orig, server = null, user, role ->
				def s = user instanceof Member ? user.server : server
				def r = s.role(role)
				if (orig && !orig.authorPermissions['manageRoles'] || r.isLockedFor(orig.author))
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				s.removeRole(user, r)
			},
			get_bans: { Message orig, server ->
				if (orig && !orig.authorPermissions['ban'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				server.requestBans()
			},
			get_regions: { Message orig, server ->
				if (orig && !orig.authorPermissions['manageServer'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				server.requestRegions()
			},
			get_invites: { Message orig, server ->
				if (orig && !orig.authorPermissions['manageServer'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				server.requestInvites()
			},
			get_integrations: { Message orig, server ->
				if (orig && !orig.authorPermissions['manageServer'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				server.requestIntegrations()
			},
			edit: { Message orig, obj, data ->
				if (!orig){ obj.edit(data); return }
				def ap = orig.authorPermissions
				Closure hm = { ap[it] }
				if (obj instanceof Server || obj instanceof Invite || obj instanceof Integration){
					if (!hm('manageServer'))
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.edit(data)
				}else if (obj instanceof Role){
					if (!hm('manageRoles') || obj.isLockedFor(orig.author))
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.edit(data)
				}else if (obj instanceof PermissionOverwrite){
					if (!obj.channel.permissionsFor(orig.author)['managePermissions'])
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.edit(data)
				}else if (obj instanceof Channel){
					if (!obj.permissionsFor(orig.author)['manageChannels'])
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.edit(data)
				}else if (obj instanceof Member){
					if (obj.isSuperiorTo(obj.author))
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					[roles: 'manageRoles', nick: 'manageNicknames', mute: 'mute',
						deaf: 'deafen', channel_id: 'moveMembers'].each { k, v ->
						if (data.containsKey(k) && !hm(v))
							throw new AuthorOfThisScriptDoesntHavePermissionsException()
					}
					obj.edit(data)
				}else if (obj instanceof Message){
					if (obj.author != client)
						throw new WhatAreYouDoingException('This is not my message')
					else if (!hm('manageMessages') || obj.member.isSuperiorTo(orig.author))
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.edit(data)
				}else throw new WhatAreYouDoingException("Class ${obj.class} cant be edited")
			},
			delete: { Message orig, obj ->
				if (!orig){ obj.delete(); return }
				def ap = orig.authorPermissions
				Closure hm = { ap[it] }
				if (obj instanceof Invite || obj instanceof Integration){
					if (!hm('manageServer'))
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.delete()
				}else if (obj instanceof Role){
					if (!hm('manageRoles') || obj.isLockedFor(orig.author))
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.delete()
				}else if (obj instanceof PermissionOverwrite){
					if (!obj.channel.permissionsFor(orig.author)['managePermissions'])
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.delete()
				}else if (obj instanceof Channel){
					if (!obj.permissionsFor(orig.author)['manageChannels'])
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.delete()
				}else if (obj instanceof Message){
					if (!hm('manageMessages') || obj.member.isSuperiorTo(orig.author))
						throw new AuthorOfThisScriptDoesntHavePermissionsException()
					else obj.delete()
				}else throw new WhatAreYouDoingException("Class ${obj.class} cant be deleted")
			},
			send_message: { Message orig, channel, con ->
				channel.sendMessage(con)
			},
			server_data: { Message orig, server ->
				KarmaFields.guildData[DiscordObject.id(server)].kismet_data
			},
			modify_server_data: { Message orig, server, Map data ->
				KarmaFields.guildData[DiscordObject.id(server)].modify(kismet_data: data)
			},
			check_perms: { Message orig, message, perm, defaul = false ->
				KarmaFields.checkPerms(message, perm, defaul)
			},
			split_args: { Message orig, text, max = 0, keepQuotes = false ->
				Arguments.splitArgs(text, max, keepQuotes)
			},
			emulate_message: { Message orig, message, content ->
				def x = message.object.clone()
				x.content = content
				KarmaFields.client.dispatchEvent('MESSAGE_CREATE',
					KarmaFields.fabricateEventFromMessageObject(x))
			}
		].collectEntries { k, v ->
			[(k): KismetInner.macro { Expression... exprs ->
				def x = exprs*.evaluate().collect { it instanceof SandboxedDiscordObject ? it.inner() : it }
				def m
				try {
					m = exprs[0].block.context.__original_message?.inner()
				}catch (ex){}
				Kismet.model(v(m, *x))
			}]
		}
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
	
	static String formatLongMessage(Message msg){
		LocalDateTime time = MiscUtil.dateToLDT(msg.timestamp)
		String.format("{%s|%s} [%s] <%s>: %s",
			time.toLocalDate(),
			time.toLocalTime(),
			msg.private ? 'DM' :
				"$msg.server#$msg.channel",
			formatLongUser(msg.author), msg.content)
	}
	
	static String uploadToPuush(bytes, String filename = 'a'){
		Unirest.post('https://puush.me/api/up')
			.field('k', KarmaFields.creds['puush_api_key'])
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
			int[] rgb = arg.findAll(/\d+/).collect { it.toInteger() }
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

@InheritConstructors
class AuthorOfThisScriptDoesntHavePermissionsException extends Exception {}
@InheritConstructors
class YouDontHavePermissionsException extends Exception {}
@InheritConstructors
class WhatAreYouDoingException extends Exception {}