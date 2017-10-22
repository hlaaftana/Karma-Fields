package hlaaftana.karmafields.relics

import groovy.transform.CompileStatic

@CompileStatic
abstract class CasingType {
	static final CasingType CAMEL = new CasingType() {
		@Override @CompileStatic
		List<String> toWords(String words) {
			MiscUtil.splitWhen(words.toString().toCharArray()){ char t -> Character.isUpperCase(t) }
					.collect { List<Character> it -> it.join('').uncapitalize() }
		}

		@Override @CompileStatic
		String fromWords(List<String> words) {
			words[0] + words.drop(1).collect { it.capitalize() }.join('')
		}
	}
	static final CasingType SNAKE = new CasingType() {
		@Override @CompileStatic
		List<String> toWords(String words) {
			words.split('_') as List<String>
		}

		@Override @CompileStatic
		String fromWords(List<String> words) {
			words.join('_')
		}
	}
	static final CasingType CONSTANT = new CasingType() {
		@Override @CompileStatic
		List<String> toWords(String words) {
			words.toLowerCase().split('_') as List<String>
		}

		@Override @CompileStatic
		String fromWords(List<String> words) {
			words.join('_').toUpperCase()
		}
	}
	static final CasingType PASCAL = new CasingType() {
		@CompileStatic List<String> toWords(String words) {
			MiscUtil.splitWhen(words.toString().toCharArray()){ char t -> Character.isUpperCase(t) }
					.drop(1).collect { List<Character> it -> it.join('').uncapitalize() }
		}

		@CompileStatic String fromWords(List<String> it) { it.collect { it.capitalize() }.join('') }
	}
	static final CasingType KEBAB = new CasingType() {
		@CompileStatic List<String> toWords(String it) {
			it.split('-') as List<String>
		}
		@CompileStatic String fromWords(List<String> it) {
			it.join('-')
		}
	}

	static Map<String, CasingType> defaultCases = [camel: CAMEL, snake: SNAKE,
		constant: CONSTANT, pascal: PASCAL, kebab: KEBAB]

	abstract List<String> toWords(String words)
	abstract String fromWords(List<String> words)

	String convert(String text, CasingType casing){
		casing.fromWords(toWords(text))
	}
}