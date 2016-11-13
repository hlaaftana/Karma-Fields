package hlaaftana.karmafields

import hlaaftana.discordg.util.JSONPath
import java.util.regex.Pattern
import static hlaaftana.discordg.util.WhatIs.whatis
import org.codehaus.groovy.runtime.typehandling.GroovyCastException

class PermissionParser {
	private static Pattern ltrim = ~/^\s+/
	List<Requirement> requirements = []

	boolean apply(entity, context){
		new AndRequirement(requirements: requirements)
			.worksFor(entity, context)
	}

	static PermissionParser from(String set){
		new PermissionParser().parse(set)
	}

	def parse(String set){
		set.eachLine(this.&parseLine)
		this
	}

	def parseLine(String line){
		whatis(line[0..(line.indexOf(" "))]){
			String args = ltrim.matcher(line.substring(value.size()))
				.replaceFirst("")
			when("is"){
				def aa = args.split(/\s+/, 2)
				requirements += new VariableEqualityRequirement(
					jsonPath: aa[0], parsableValue: aa[1])
			}
			when("can"){
				requirements += new PermissionRequirement(
					permissionName: args.replaceAll(/\s+(\w)/){ full, c ->
						c.toUpperCase()
					})
			}
			for (c in [AndRequirement, OrRequirement,
				NotRequirement]){
				when(c.string){
					int back = args ? args.toInteger() :
						c.defaultNum
					List a = requirements.takeRight(back)
					requirements = requirements.dropRight(back)
					requirements += c.newInstance(
						requirements: a)
				}
			}
		}
		this
	}
}

abstract class Requirement {
	abstract boolean worksFor(entity, context)
}

class PermissionRequirement extends Requirement {
	String permissionName

	boolean worksFor(entity, context){
		context.channel.fullPermissionsFor(entity)[permissionName]
	}
}

class VariableEqualityRequirement extends Requirement {
	String jsonPath
	String parsableValue

	String setParsableValue(String newVal){
		this.@parsableValue = newVal.replaceAll(/\\n/, "\n")
	}

	boolean worksFor(entity, context){
		def variable = JSONPath.parse(jsonPath).apply(context)
		Class type = variable.class
		try{
			variable == parsableValue.asType(type)
		}catch (GroovyCastException ex){
			variable.inspect() == parsableValue
		}catch (ex){
			false
		}
	}
}

class AndRequirement extends Requirement {
	static string = "and"
	static defaultNum = 2
	List<Requirement> requirements

	boolean worksFor(entity, context){
		requirements.every { it.worksFor(entity, context) }
	}
}

class OrRequirement extends Requirement {
	static string = "or"
	static defaultNum = 2
	List<Requirement> requirements

	boolean worksFor(entity, context){
		requirements.any { it.worksFor(entity, context) }
	}
}

class NotRequirement extends Requirement {
	static string = "not"
	static defaultNum = 1
	List<Requirement> requirements

	boolean worksFor(entity, context){
		requirements.every { !it.worksFor(entity, context) }
	}
}