package hlaaftana.karmafields.relics

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.MessageChannel

@CompileStatic
class CommandEventData {
	Command command
	CommandPattern alias
	CommandPattern trigger
	String arguments
	List<String> captures
	List<String> allCaptures
	@Delegate Message message
	Map<String, Object> extra = [:]

	CommandEventData(Map<String, Object> props = [:], Command command,
	                 CommandPattern alias, CommandPattern trigger,
	                 String arguments, Message message) {
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
	Message formatted(boolean care = false, MessageChannel chan = message.channel, String content) {
		def x = chan.sendMessage(command.parent.formatter.call(content))
		care ? x.complete() : x.queue()
	}

	@CompileDynamic
	Message sendMessage(boolean care = false, MessageChannel chan = message.channel, ...arguments) {
		def x = chan.sendMessage(*arguments)
		care ? x.complete() : x.queue()
	}

	@CompileDynamic
	Message sendFile(boolean care = false, MessageChannel chan = message.channel, ...arguments) {
		def x = chan.sendFile(*arguments)
		care ? x.complete() : x.queue()
	}

	CommandEventData clone() {
		new CommandEventData(extra, command, alias, trigger, arguments, message)
	}
}
