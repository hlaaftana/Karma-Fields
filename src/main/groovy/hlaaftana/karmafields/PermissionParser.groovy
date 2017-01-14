package hlaaftana.karmafields

import hlaaftana.discordg.objects.Member
import hlaaftana.discordg.objects.Role
import hlaaftana.discordg.objects.User
import hlaaftana.discordg.util.JSONPath
import hlaaftana.discordg.util.MiscUtil
import java.util.regex.Pattern
import static hlaaftana.discordg.util.WhatIs.whatis
import org.codehaus.groovy.runtime.typehandling.GroovyCastException

class PermissionParser {
	private static Pattern ltrim = ~/^\s+/
	Map variables = [:]
	List<Requirement> requirements = []

	boolean apply(entity, context){
		new AndRequirement(requirements: requirements)
			.worksFor(entity, context)
	}

	static PermissionParser from(String set){
		new PermissionParser().parse(set)
	}

	PermissionParser parse(String set){
		set.eachLine(this.&parseLine)
		this
	}

	def parseLine(String line){
		int li = (line.contains(' ') ? line.indexOf(' ') : line.size()) - 1
		whatis(line[0..li]){ x ->
			String args = ltrim.matcher(line.substring(li + 1))
				.replaceFirst('')
			when('is'){
				def aa = Arguments.splitArgs(args, 2)
				requirements.add(new VariableEqualityRequirement(
					jsonPath: aa[0], parsableValue: aa[1].trim(), parser: this))
			}
			when('regex'){
				def aa = Arguments.splitArgs(args, 2)
				requirements.add(new RegexRequirement(
					jsonPath: aa[0], regex: aa[1], parser: this))
			}
			when(['rolelocked', 'lockedrole']){
				def aa = Arguments.splitArgs(args, 2)
				requirements.add(new LockedRoleRequirement(role: aa[0], user: aa[1], parser: this))
			}
			when('can'){
				requirements.add(new PermissionRequirement(
					permissionName: args.replaceAll(/[_\- ](\w)/){ f, w -> w.toUpperCase() }, parser: this))
			}
			when(['true', 'false']){
				requirements.add(new BooleanRequirement(value: Boolean.parseBoolean(x), parser: this))
			}
			when('set'){
				def aa = Arguments.splitArgs(args, 2)
				requirements.add(new VariableSetRequirement(name: aa[0],
					value: aa[1], parser: this))
			}
			for (c in [AndRequirement, OrRequirement,
				NotRequirement]){
				when(c.string){
					int back = args ? args.toInteger() :
						c.defaultNum
					List a = requirements.takeRight(back)
					requirements = requirements.dropRight(back)
					requirements.add(c.newInstance(requirements: a))
				}
			}
		}
		this
	}
}

abstract class Requirement {
	PermissionParser parser
	
	abstract boolean worksFor(entity, context)
}

class PermissionRequirement extends Requirement {
	String permissionName

	boolean worksFor(entity, context){
		context.channel.permissionsFor(entity)[permissionName]
	}
}

class VariableEqualityRequirement extends Requirement {
	String jsonPath
	String parsableValue

	boolean worksFor(entity, context){
		def variable = JSONPath.parse(jsonPath).apply(context)
		Class type = variable.class
		try{
			variable == parsableValue.asType(type)
		}catch (GroovyCastException ex){
			variable.inspect() == parsableValue
		}catch (NullPointerException ex){
			if (variable == null)
				parsableValue in [null, '', 'null']
			else
				variable == null
		}catch (ex){
			false
		}
	}
}

class VariableSetRequirement extends Requirement {
	String name
	String value
	
	boolean worksFor(entity, context){
		parser.variables[name] = JSONPath.parse(value).apply(context)
		true
	}
}

class RegexRequirement extends Requirement {
	String jsonPath
	String regex

	boolean worksFor(entity, context){
		def variable = JSONPath.parse(jsonPath).apply(context)
		try{
			variable ==~ regex
		}catch (ex){
			false
		}
	}
}

class LockedRoleRequirement extends Requirement {
	String role
	String user
	
	boolean worksFor(entity, context){
		Role r = context.server.role(parser.variables[role] ?: role)
		Member u = user ? context.server.member(parser.variables[user] ?: user) : entity
		r?.isLockedFor(u)
	}
}

class AndRequirement extends Requirement {
	static string = 'and'
	static defaultNum = 2
	List<Requirement> requirements

	boolean worksFor(entity, context){
		requirements.every { it.worksFor(entity, context) }
	}
}

class OrRequirement extends Requirement {
	static string = 'or'
	static defaultNum = 2
	List<Requirement> requirements

	boolean worksFor(entity, context){
		requirements.any { it.worksFor(entity, context) }
	}
}

class NotRequirement extends Requirement {
	static string = 'not'
	static defaultNum = 1
	List<Requirement> requirements

	boolean worksFor(entity, context){
		requirements.every { !it.worksFor(entity, context) }
	}
}

class BooleanRequirement extends Requirement {
	boolean value
	
	boolean worksFor(entity, context){ value }
}