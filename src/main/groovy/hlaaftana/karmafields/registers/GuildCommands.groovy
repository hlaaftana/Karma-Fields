package hlaaftana.karmafields.registers

import groovy.transform.CompileStatic
import hlaaftana.karmafields.CommandRegister
import hlaaftana.karmafields.relics.MiscUtil
import sx.blah.discord.handle.obj.IMessage
import sx.blah.discord.handle.obj.IRole
import sx.blah.discord.handle.obj.Permissions
import sx.blah.discord.util.PermissionUtils
import static groovyx.gpars.GParsPool.withPool
import static java.lang.System.currentTimeMillis as now

@CompileStatic
class GuildCommands extends CommandRegister {
	{ group = 'Guild' }
	
	static Map<String, Closure<Boolean>> roleOptions = [
		color: { IRole r ->
			!r.hoisted && !r.permissions && r.color.RGB &&
				r.name ==~ /#?[A-Fa-f0-9]+/
		},
		unused: { IRole r ->
			!r.guild.getUsersByRole(r)
		},
		no_overwrites: { IRole r ->
			!r.guild.channels*.roleOverridesLong*.containsKey(r.longID).any()
		},
		no_permissions: { IRole r ->
			!r.permissions
		},
		color_ignore_perms: { IRole r ->
			!r.hoisted && r.color.RGB && r.name ==~ /#?[A-Fa-f0-9]+/
		}
	]

	static {
		roleOptions.colour = roleOptions.color
		roleOptions.colour_ignore_perms = roleOptions.color_ignore_perms
	}

	def register(){
		command(['filterroles', 'roles',
			~/(?:filter)?roles([\-!]+)/],
			id: '23',
			description: 'Finds roles with specific filters. If no filters are given, all filters will be used.\n\n' +
				'List of filters: ' + roleOptions.keySet().join(', '),
			usages: [
				'': 'Uses all filters.',
				' (filter1) (filter2)...': 'Uses the given filters. Note: space separated.',
				'- ...': 'Removes the filtered roles.',
			],
			examples: [
				'',
				' color',
				' unused no_overwrites',
				' unused ! color'
			],
			guildOnly: true){
			def params = captures[0]?.toList() ?: []
			List<IRole> roles = guild.roles
			roles.remove(guild.everyoneRole)
			List<Closure<Boolean>> options = []
			if (arguments){
				boolean neg = false
				for (o in arguments.tokenize())
					if (o == '!') neg = true
					else {
						if (roleOptions[o])
							options.add(neg ? { IRole x -> !roleOptions[o](x) } : roleOptions[o])
						else {
							sendMessage("Unknown filter: $o.\nList of filters: " +
								roleOptions.keySet().join(", "))
							return
						}
						if (neg) neg = false
					}
			} else { options = roleOptions.values() as List<Closure<Boolean>> }
			for (x in options) roles = roles.findAll(x)
			if (params.contains('-')){
				if (!PermissionUtils.hasPermissions(guild, author, Permissions.MANAGE_ROLES))
					return formatted('You don\'t have permissions.')
				IMessage a = formatted("Deleting ${roles.size()} roles in about ${roles.size() / 2} seconds...")
				long s = now()
				if (roles) {
					for (r in roles.dropRight(1)) {
						r.delete()
						Thread.sleep 500
					}
					roles.last().delete()
				}
				a.edit(MiscUtil.block("> Deleted all ${roles.size()} roles in ${(now() - s) / 1000} seconds.", 'accesslog'))
			} else formatted("${roles.join(", ")}\n${roles.size()} total")
		}
	}
}
