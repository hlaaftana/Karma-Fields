package hlaaftana.karmafields.registers

import hlaaftana.discordg.Client
import hlaaftana.discordg.exceptions.NoPermissionException
import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.Role
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.discordg.util.bot.CommandBot
import hlaaftana.karmafields.KarmaFields
import hlaaftana.karmafields.Util

class ServerCommands {
	static register(KarmaFields kf){
		CommandBot bot = kf.bot
		Client client = kf.client

		bot.command("color",
			group: "Server",
			description: "Creates/reuses a role that has a color and no other special aspect to it.",
			usages: [
				" (hexadecimal number)": "Uses a hexadecimal number as the color",
				" (rgb number tuple)": "Uses numbers separated by commas as RGB numbers for the color.",
				" (named color)": "Uses a human readable color (spaces ignored) as the color. (list: \"http://www.december.com/html/spec/colorsvg.html\")",
				" random": "Uses a random color between 0x000000 and 0xFFFFFF. That's the infamous 16.7 million colors."
			],
			examples: [
				" fb4bf4",
				" 44, 43, 103",
				" red",
				" navy blue"
			],
			allowsPermissions: true){ d ->
			if (!message.server){
				decorate("We aren't in a server.")
				return
			}
			def r = Util.resolveColor(d)
			if (r instanceof String){
				decorate(r)
				return
			}
			int color = r
			if (color > 0xFFFFFF){
				decorate("Color is bigger than #FFFFFF.")
				return
			}
			try{
				def a = message.server.defaultRole.permissionValue
				Map groupedRoles = author.roles.groupBy {
					it.hoist || !(it.name ==~ /#?[0-9a-fA-F]+/) ||
					it.name.contains(" ") ||
					it.permissionValue > a ||
					it.permissionValue != 0 ? 1 : 0 }
				// 0 is color roles, 1 is the others
				boolean created
				Role role
				if (color){
					role = message.server.roles.find {
						!it.hoist && it.permissionValue <= a &&
							it.name ==~ /#?[0-9a-fA-F]+/ &&
							it.colorValue == color
					} ?: { ->
						created = true
						message.server.createRole(name:
							Long.toHexString(color).padLeft(6, "0"),
							color: color,
							permissions: 0)
					}()
				}
				author.editRoles((groupedRoles[1] ?: []) + (color ? role : []))
				def m = color ? "Color ${created ? "created" : "found"} and added." :
					"That color is the default color, so I just removed your other colors."
				if (groupedRoles[0])
					m += "\nRemoved roles: ${groupedRoles[0]*.name.join(", ")}"
				decorate(m)
			}catch (NoPermissionException ex){
				decorate("I don't have sufficient permissions. I need Manage Roles.")
			}
		}
	}
}
