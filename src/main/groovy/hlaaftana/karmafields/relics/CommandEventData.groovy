package hlaaftana.karmafields.relics

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import sx.blah.discord.handle.obj.IChannel
import sx.blah.discord.handle.obj.IMessage

@CompileStatic
class CommandEventData {
	Command command
	CommandPattern alias
	CommandPattern trigger
	String arguments
	List<String> captures
	List<String> allCaptures
	@Delegate IMessage message
	Map<String, Object> extra = [:]

	CommandEventData(Map<String, Object> props = [:], Command command, CommandPattern alias, CommandPattern trigger,
	                 String arguments, IMessage message) {
		for (e in props)
			this[e.key] = e.value
		this.command = command
		this.alias = alias
		this.trigger = trigger
		this.arguments = arguments
		this.message = message
	}

	def propertyMissing(String name) {
		if (extra.containsKey(name)) extra[name]
		else throw new MissingPropertyException(name, CommandEventData)
	}

	def propertyMissing(String name, value) {
		extra.put name, value
	}

	@CompileDynamic
	IMessage formatted(IChannel chan = channel, String content) {
		chan.sendMessage(command.parent.formatter.call(content))
	}

	@CompileDynamic
	IMessage sendMessage(IChannel chan = channel, ...arguments) {
		chan.sendMessage(*arguments)
	}

	@CompileDynamic
	IMessage sendFile(IChannel chan = channel, ...arguments) {
		chan.sendFile(*arguments)
	}

	CommandEventData clone() {
		new CommandEventData(extra, command, alias, trigger, arguments, message)
	}
}
