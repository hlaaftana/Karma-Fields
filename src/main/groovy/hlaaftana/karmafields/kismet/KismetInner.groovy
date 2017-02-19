package hlaaftana.karmafields.kismet

import groovy.transform.InheritConstructors
import hlaaftana.discordg.util.JSONPath
import hlaaftana.discordg.util.JSONUtil
import hlaaftana.discordg.util.MiscUtil
import hlaaftana.karmafields.Arguments;

import java.util.regex.Pattern
import static hlaaftana.discordg.util.WhatIs.whatis
import org.codehaus.groovy.runtime.typehandling.GroovyCastException

class KismetInner {
	static final Map defaultContext = [
		null: null,
		true: true,
		false: false,
		eq: { ...args -> args.inject { a, b -> a == b } },
		is: { a, b -> a.is(b) },
		in: { a, b -> a in b },
		not: { a -> !a },
		and: macro { Expression... exprs ->
			for (it in exprs){
				if (!it.evaluate()) return false
			}
			true
		},
		or: macro { Expression... exprs ->
			for (it in exprs){
				if (it.evaluate()) return true
			}
			false
		},
		elvis: macro { Expression... exprs ->
			for (it in exprs){
				def x = it.evaluate()
				if (x) return x
			}
		},
		xor: { ...args -> args.inject { a, b -> a ^ b } },
		bool: { a -> a.asBoolean() },
		bnot: { a -> ~a },
		band: { ...args -> args.inject { a, b -> a & b } },
		bor: { ...args -> args.inject { a, b -> a | b } },
		bxor: { ...args -> args.inject { a, b -> a ^ b } },
		lsh: { ...args -> args.inject { a, b -> a << b } },
		rsh: { ...args -> args.inject { a, b -> a >> b } },
		ursh: { ...args -> args.inject { a, b -> a >>> b } },
		lt: { ...args -> args.inject { a, b -> a < b } },
		gt: { ...args -> args.inject { a, b -> a > b } },
		lte: { ...args -> args.inject { a, b -> a <= b } },
		gte: { ...args -> args.inject { a, b -> a >= b } },
		pos: { a -> +a },
		neg: { a -> -a },
		abs: { a -> Math.abs(a) },
		plus: { ...args -> args.sum() },
		minus: { ...args -> args.inject { a, b -> a - b } },
		multiply: { ...args -> args.inject { a, b -> a * b } },
		div: { ...args -> args.inject { a, b -> a / b } },
		mod: { ...args -> args.inject { a, b -> a % b } },
		pow: { ...args -> args.inject { a, b -> a ** b } },
		regex: { String a -> ~a },
		set: { a, b, c -> a[b] = c },
		get: { a, b -> a[b] },
		string: { a -> a as String },
		int: { a -> a as BigInteger },
		int8: { a -> a as byte },
		int16: { a -> a as short },
		int32: { a -> a as int },
		int64: { a -> a as long },
		char: { a -> a as char },
		decimal: { a -> a as BigDecimal },
		decimal32: { a -> a as float },
		decimal64: { a -> a as double },
		list: { ...args -> args.toList() },
		map: { ...args -> args.toList().collate(2).collectEntries {
			it.size() == 2 ? [(it[0]): it[1]] : [:] } },
		size: { a -> a.size() },
		keys: { a -> a.keySet() },
		values: { a -> a.values() },
		reverse: { a -> a.reverse() },
		format: { ...args -> String.format(*args) },
		apply_path: macro { ValueExpression path, value ->
			path.value.apply(value.evaluate())
		},
		define: macro { ValueExpression name, Expression value ->
			name.block.context.define(name.value instanceof JSONPath ?
				name.raw : name.value, value.evaluate())
		},
		change: macro { ValueExpression name, Expression value ->
			name.block.context.change(name.value instanceof JSONPath ?
				name.raw : name.value, value.evaluate())
		},
		function: macro { Expression... exprs ->
			Block b = new Block(expressions: exprs.toList(),
				block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			return { ...args ->
				Block x = b.anonymousClone()
				x.context.directSet('$all', args.toList())
				args.size().times {
					x.context.directSet("\$$it", args[it])
				}
				x.evaluate()
			}
		},
		macro: { Closure x -> macro x },
		make_special: { Map s = null -> s ? new Special(s) : new Special() },
		block: macro { Expression... exprs ->
			Block b = new Block(expressions: exprs.toList(),
				block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			b
		},
		let: macro { Expression map, Expression... exprs ->
			def m = map.evaluate()
			if (!(m instanceof Map)) throw new IllegalArgumentException('Isolate block ' +
				'context not a map')
			Block b = new Block(expressions: exprs.toList())
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b, data: m)
			b
		},
		eval: { Expression ex -> ex.evaluate() },
		quote: macro { Expression... exprs -> exprs.toList() },
		if: macro { Expression cond, Expression... exprs ->
			Block b = new Block(expressions: exprs.toList(),
					block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			def j
			if (cond.evaluate()){
				j = b.evaluate()
			}
			j
		},
		ternary: macro { a, b, c -> a.evaluate() ? b.evaluate() : c.evaluate() },
		while: macro { Expression cond, Expression... exprs ->
			Block b = new Block(expressions: exprs.toList(),
					block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			def j
			while (cond.evaluate()){
				j = b.evaluate()
			}
			j
		},
		foreach: macro { ValueExpression name, Expression list, Expression... exprs ->
			def n = name.value instanceof JSONPath ? name.raw : name.value
			Block b = new Block(expressions: exprs.toList(),
				block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			def a
			for (x in list.evaluate()){
				Block y = b.anonymousClone()
				y.context.directSet(n, x)
				a = y.evaluate()
			}
			a
		}
	]
	
	static List<String> separateLines(String code){
		List a = code.readLines()
		List<List> b = []
		boolean abc = false
		for (x in a){
			def yaya = x
			boolean jajajaja = yaya.endsWith('\\') && !yaya.endsWith('\\\\')
			if (jajajaja)
				yaya = yaya[0..-2]
			if (abc){
				b[-1].add(yaya)
				abc = false
			}else b << [yaya]
			if (jajajaja)
				abc = true
		}
		b*.join('')
	}
	
	static Expression parseExpression(Block block, String code){
		Expression ex
		if (code && !(code[0] in ['\'', '"'] && code[0] == code[-1]) && (
				(code.contains('(') && code.contains(')')) ||
				code.toCharArray().any(Character.&isWhitespace)
			))
			ex = new CallExpression(block: block, raw: code)
		else
			ex = new ValueExpression(block: block, raw: code)
		ex.parse()
		ex
	}
	
	static macro(Closure c){
		new ClosureMacro(x: c)
	}
}

abstract class Macro {
	abstract call(Expression... expressions)
}

class ClosureMacro extends Macro {
	Closure x
	
	def call(Expression... expressions){
		x(*expressions)
	}
}

class Special {
	def caller
	def getter = { this."get${it.capitalize()}"() }
	def setter = { x, y -> this."set${x.capitalize()}"(y) }

	def getProperty(String name){
		getter(name)
	}

	void setProperty(String name, value){
		setter(name, value)
	}

	def call(...args){
		caller(*args)
	}
}

class Block extends Expression {
	LinkedList<Expression> expressions
	Context context
	
	def evaluate(){
		def x
		for (e in expressions){
			x = e.evaluate()
		}
		x
	}
	
	Block anonymousClone(){
		Block b = new Block(block: block, expressions: expressions)
		b.context = new Context(block: b, data: (Map) context.getData().clone())
		changeBlock(b.expressions, b)
		b
	}
	
	static changeBlock(List<Expression> exprs, Block block){
		for (x in exprs){
			if (x instanceof CallExpression)
				changeBlock(((CallExpression) x).expressions, block)
			if (x instanceof Block)
				changeBlock(((Block) x).expressions, block)
			x.block = block
		}
	}
}

class Context {
	Block block
	Map data = [:]
	
	def getProperty(String name){
		if (data.containsKey(name)) data[name]
		else if (block?.block)
			block.block.context.getProperty(name)
		else throw new UndefinedVariableException(name)
	}
	
	void directSet(String name, value){
		data[name] = value
	}
	
	void define(String name, value){
		if (data.containsKey(name))
			throw new VariableExistsException("Variable $name already exists")
		data[name] = value
	}
	
	void change(String name, value){
		if (data.containsKey(name))
			data[name] = value
		else if (block?.block)
			block.block.context.change(name, value)
		else throw new UndefinedVariableException(name)
	}
	
	def clone(){
		new Context(block: block, data: (Map) data.clone())
	}
}

@InheritConstructors
class KismetException extends Exception {}
@InheritConstructors
class InvalidSyntaxException extends KismetException {}
@InheritConstructors
class UndefinedVariableException extends KismetException {}
@InheritConstructors
class VariableExistsException extends KismetException {}
@InheritConstructors
class WrongMethodUseException extends KismetException {}

abstract class Expression {
	Block block
	String raw
	
	abstract evaluate()
}

class ValueExpression extends Expression {
	def value
	
	def parse(){
		if (!raw) value = null
		else if (raw.isBigInteger()) value = new BigInteger(raw)
		else if (raw.isBigDecimal()) value = new BigDecimal(raw)
		else if (raw[0] in ['\'', '"'] && raw[0] == raw[-1]){
			def x = ''
			boolean escaped
			def usize
			def u = ''
			for (a in raw[1..-2].toList()){
				if (!escaped){
					if (a == '\\') escaped = true
					else if (a == raw[0]) throw new InvalidSyntaxException('Unescaped quote\n' + raw)
					else x += a
				}else{
					if (usize){
						u += a
						if (u.size() == usize){
							escaped = false
							usize = 0
							x += new String(Character.toChars(Integer.parseInt(u, 16)))
							u = ''
						}
						continue
					}
					else if (a == 'u'){ usize = 4; continue }
					else if (a == 'U'){ usize = 8; continue }
					else if (a in ['\\', '\'', '"', '/']) x += a
					else if (a == 'r') x += '\r'
					else if (a == 'n') x += '\n'
					else if (a == 't') x += '\t'
					else if (a == 'f') x += '\f'
					else if (a == 'b') x += '\b'
					else x += '\\' + a
					escaped = false
				}
			}
			value = x
		}else value = JSONPath.parse(raw)
	}
	
	def evaluate(){
		value instanceof JSONPath ? value.apply(block.context) : value
	}
}

class CallExpression extends Expression {
	List<Expression> expressions
	
	def parse(){
		int index = 0
		List<String> args = []
		def currentQuote
		boolean quoteEscaped
		boolean inBetween = true
		boolean inSpace = true
		int parantheses = 0
		while (index < raw.size()){
			if (parantheses < 0)
				throw new InvalidSyntaxException('Too many closing parantheses\n' + raw)
			if (inBetween){
				if (raw[index] == '('){
					parantheses++
					args.add('')
					inBetween = false
					inSpace = false
				}else if (Character.isWhitespace(raw[index] as char)){
					inSpace = true
				}else if (inSpace){
					--index
					args.add('')
					inBetween = false
					inSpace = false
				}
			}else if (!currentQuote){
				if (parantheses == 0 && Character.isWhitespace(raw[index] as char)){
					inSpace = true
					inBetween = true
				}else if (raw[index] == '('){
					parantheses++
					args[-1] += raw[index]
				}else if (raw[index] == ')'){
					parantheses--
					if (parantheses == 0) inBetween = true
					else args[-1] += raw[index]
				}else{
					if (raw[index] in ['\'', '"']) currentQuote = raw[index]
					if (!inBetween) args[-1] += raw[index]
				}
			}else{
				if (!quoteEscaped && raw[index] in ['\'', '"']) currentQuote = null
				else if (!quoteEscaped && raw[index] == '\\') quoteEscaped = true
				if (quoteEscaped) quoteEscaped = false
				if (!inBetween) args[-1] += raw[index]
			}
			++index
		}
		if (parantheses > 0)
			throw new InvalidSyntaxException("$parantheses missing closing parantheses\n$raw")
		expressions = args.collect { KismetInner.parseExpression(block, it) }
	}
	
	def evaluate(){
		def x = expressions[0].evaluate()
		if (expressions.size() == 1) return x
		def args = expressions.drop(1)
		if (args.size() == 1 && !args[0].raw) args = []
		if (!(x instanceof Macro)) args = args*.evaluate()
		try{
			x(*args)
		}catch (MissingMethodException ex){
			throw new WrongMethodUseException(raw)
		}
	}
}