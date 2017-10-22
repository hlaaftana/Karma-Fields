package hlaaftana.karmafields.relics

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import java.time.LocalDateTime

/**
 * A groovy way to log messages.
 * @author Hlaaftana
 */
@CompileStatic
class Log {
	static class Level {
		String name
		boolean enabled = true

		Level enable(){ enabled = true; this }
		Level disable(){ enabled = false; this }
		boolean equals(Level other){ name == other.name }
	}

	static class Message {
		String by
		Level level
		String content
		LocalDateTime time = LocalDateTime.now()
		Map info = [:]

		String toString(){ toString(defaultFormatter) }
		String toString(Log log){ toString(log.formatter) }
		String toString(Closure formatter){ formatter(this) }
	}

	static List<Level> defaultLevels = [
			new Level(name: 'info'),
			new Level(name: 'error'),
			new Level(name: 'warn'),
			new Level(name: 'debug').disable(),
			new Level(name: 'trace').disable()
	]

	static Closure<String> defaultFormatter = { Message message ->
		String.format('<%s|%s> [%s] [%s]: %s',
			message.time.toLocalDate(),
			message.time.toLocalTime(),
			message.level.name.toUpperCase(),
			message.by, message.content)
	}

	Closure<String> formatter = defaultFormatter

	List<Level> levels = defaultLevels

	List<Message> messages = []

	List<Closure> listeners = [{ Message it -> if (it.level.enabled) println formatter.call(it) }]

	String name

	Log(String name){ this.name = name }

	Log(Log parent){
		formatter = parent.formatter
		name = parent.name
	}

	def listen(Closure ass){
		listeners.add ass
	}

	@CompileDynamic
	def call(Message message){
		listeners.each { it.call(message) }
	}

	Level level(String name){
		Level ass = null
		for (l in levels) if (l.name == name) ass = l
		if (!ass){
			ass = new Level(name: name)
			levels.add ass
		}
		ass
	}

	Level level(Level level){
		if (level in levels) level
		else {
			levels.add level
			level
		}
	}

	def propertyMissing(String name){
		level(name)
	}

	@CompileDynamic
	def methodMissing(String name, arguments){
		Level level = propertyMissing(name)
		boolean argsIsMultiple = arguments instanceof Collection || arguments.class.array
		if (arguments instanceof Message || (argsIsMultiple && arguments[0] instanceof Message))
			log(arguments)
		else {
			List ahh = [level] + (argsIsMultiple ? arguments as List : arguments)
			log(*ahh)
		}
	}

	def log(String level, content, String by = name){
		Message ass = new Message(level: this.level(level), content: content.toString(), by: by)
		log(ass)
	}

	def log(Level level, content, String by = name){
		Message ass = new Message(level: level, content: content.toString(), by: by)
		log(ass)
	}

	def log(Message message){
		messages.add message
		call(message)
	}
}
