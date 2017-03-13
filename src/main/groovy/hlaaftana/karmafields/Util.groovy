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
import hlaaftana.karmafields.kismet.KismetClass
import hlaaftana.karmafields.kismet.KismetInner
import hlaaftana.karmafields.kismet.Expression

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.time.LocalDateTime
import javax.imageio.ImageIO

@CompileStatic
class Util {
	static String CHANNEL_ARG_REGEX = /<?#?([\d\w\-]+?)>?/
	static Map discordKismetContext
	
	@CompileDynamic
	private static __$dynamicLoad(){
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
		discordKismetContext = [
			DiscordObject: new KismetClass().object
		]
		discordKismetContext += [
			has_permission: { Message orig, ...a ->
				if (a.size() != 3){
					if (a[0] instanceof Member) user.permissions[a[2]]
					else throw new WhatAreYouDoingException('No server or channel supplied')
				}else if (a[1] instanceof Channel)
					a[1].permissionsFor(a[0])[a[2]]
				else if (a[1] instanceof Server)
					a[1].member(a[0]).permissions[a[2]]
				else throw new WhatAreYouDoingException('Permissions in WHAT? ' + a[1].class)
			},
			permissions_for: { Message orig, ...a ->
				a[1].permissionsFor(a[0])
			},
			locked: { Message orig, ...a ->
				a[1].isLockedFor(a[0])
			},
			role: { Message orig, ...a ->
				a[0].role(a[1])
			},
			channel: { Message orig, ...a ->
				a[0].channel(a[1])
			},
			member: { Message orig, ...a ->
				a[0].member(a[1])
			},
			permission_overwrite: { Message orig, ...a ->
				a[0].overwrite(a[1])
			},
			kick: { Message orig, ...a ->
				if (orig && !orig.authorPermissions['kick'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				if (a[0] instanceof Member) a[0].kick()
				else a[0].kick(a[1])
			},
			ban: { Message orig, ...a ->
				if (orig && !orig.authorPermissions['ban'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				if (a[0] instanceof Member) a[0].ban()
				else a[0].ban(a[1])
			},
			unban: { Message orig, ...a ->
				if (orig && !orig.authorPermissions['ban'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				if (a[0] instanceof Member) a[0].ban()
				else a[0].unban(a[1])
			},
			add_role: { Message orig, ...a ->
				def s = a[0] instanceof Member ? a[0].server : a[0]
				def u = a[0] instanceof Member ? a[0] : a[1]
				def r = s.role(a[0] instanceof Member ? a[1] : a[2])
				if (orig && !orig.authorPermissions['manageRoles'] || r.isLockedFor(orig.author))
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				s.addRole(u, r)
			},
			remove_role: { Message orig, ...a ->
				def s = a[0] instanceof Member ? a[0].server : a[0]
				def u = a[0] instanceof Member ? a[0] : a[1]
				def r = s.role(a[0] instanceof Member ? a[1] : a[2])
				if (orig && !orig.authorPermissions['manageRoles'] || r.isLockedFor(orig.author))
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				s.removeRole(u, r)
			},
			get_bans: { Message orig, ...a ->
				if (orig && !orig.authorPermissions['ban'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				a[0].requestBans()
			},
			get_regions: { Message orig, ...a ->
				if (orig && !orig.authorPermissions['manageServer'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				a[0].requestRegions()
			},
			get_invites: { Message orig, ...a ->
				if (orig && !orig.authorPermissions['manageServer'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				a[0].requestInvites()
			},
			get_integrations: { Message orig, ...a ->
				if (orig && !orig.authorPermissions['manageServer'])
					throw new AuthorOfThisScriptDoesntHavePermissionsException()
				a[0].requestIntegrations()
			},
			edit: { Message orig, ...a ->
				def (obj, data) = a.toList()
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
			send_message: { Message orig, ...a ->
				a[0].sendMessage(a[1])
			},
			server_data: { Message orig, ...a ->
				def x = KarmaFields.guildData[DiscordObject.id(a[0])].kismet_data
				if (x == null){
					KarmaFields.guildData[DiscordObject.id(a[0])].modify(kismet_data: [:])
					x = [:]
				}
				x
			},
			modify_server_data: { Message orig, ...a ->
				KarmaFields.guildData[DiscordObject.id(a[0])].modify(kismet_data: a[1])
			},
			check_perms: { Message orig, ...a ->
				def (msg, perm, defaul) = a.toList() + [false]
				KarmaFields.checkPerms(msg, perm, defaul)
			},
			split_args: { Message orig, ...a ->
				def (text, max, keepQuotes) = a.toList()
				if (max == null) max = 0
				if (keepQuotes == null) keepQuotes = false
				Arguments.splitArgs(text, max, keepQuotes)
			},
			emulate_message: { Message orig, ...a ->
				def (message, content) = a
				def x = message.object.clone()
				x.content = content
				KarmaFields.client.dispatchEvent('MESSAGE_CREATE',
					KarmaFields.fabricateEventFromMessageObject(x))
			}
		].collectEntries { k, v ->
			[(k): Kismet.model(KismetInner.macro { ...exprs ->
				def x = exprs*.evaluate()*.inner()
				def m = null
				try {
					m = exprs[0].block.context.__original_message?.inner()
				}catch (ex){}
				Kismet.model(v(m, *x))
			})]
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