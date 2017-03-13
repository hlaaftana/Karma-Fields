package hlaaftana.karmafields.kismet

import groovy.transform.InheritConstructors
import hlaaftana.discordg.util.JSONPath

class KismetInner {
	static Map defaultContext = [
		Class: KismetClass.meta.clone().with { name = 'Class'; it }.object,
		Null: new KismetClass(name: 'Null').object,
		Integer: new KismetClass(orig: BigInteger, name: 'Integer').object,
		Decimal: new KismetClass(orig: BigDecimal, name: 'Decimal').object,
		String: new KismetClass(orig: String, name: 'String').object,
		Boolean: new KismetClass(orig: boolean, name: 'Boolean').object,
		Int8: new KismetClass(orig: byte, name: 'Int8').object,
		Int16: new KismetClass(orig: short, name: 'Int16').object,
		Int32: new KismetClass(orig: int, name: 'Int32').object,
		Int64: new KismetClass(orig: long, name: 'Int64').object,
		Dec32: new KismetClass(orig: float, name: 'Dec32').object,
		Dec64: new KismetClass(orig: double, name: 'Dec64').object,
		Character: new KismetClass(orig: char, name: 'Character').object,
		Path: new KismetClass(orig: JSONPath, name: 'Path').object,
		List: new KismetClass(orig: List, name: 'List').object,
		Map: new KismetClass(orig: Map, name: 'Map').object,
		Expression: new KismetClass(orig: Expression, name: 'Expression').object,
		Function: new KismetClass(orig: Function, name: 'Function').object,
		Macro: new KismetClass(orig: Macro, name: 'Macro').object,
		Native: new KismetClass(orig: Object, name: 'Native').object,
		null: null, true: true, false: false,
		class: new GroovyFunction(convert: false, x: { ...a -> a[0].kclass() }),
		as: new GroovyFunction(convert: false, x: { ...a -> a[0].asType(a[1].kclass()) }),
		eq: func { ...args -> args.inject { a, b -> a == b } },
		is: func { ...a -> a[0].is(a[1]) },
		in: func { ...a -> a[0] in a[1] },
		not: func { ...a -> !(a[0]) },
		and: macro { ...exprs ->
			for (it in exprs){
				if (!it()) return false
			}
			true
		},
		or: macro { ...exprs ->
			for (it in exprs){
				if (it()) return true
			}
			false
		},
		elvis: macro { ...exprs ->
			for (it in exprs){
				def x = it()
				if (x) return x
			}
		},
		xor: func { ...args -> args.inject { a, b -> a ^ b } },
		bool: func { ...a -> (a[0]).asBoolean() },
		bnot: func { ...a -> ~(a[0]) },
		band: func { ...args -> args.inject { a, b -> a & b } },
		bor: func { ...args -> args.inject { a, b -> a | b } },
		bxor: func { ...args -> args.inject { a, b -> a ^ b } },
		lsh: func { ...args -> args.inject { a, b -> a << b } },
		rsh: func { ...args -> args.inject { a, b -> a >> b } },
		ursh: func { ...args -> args.inject { a, b -> a >>> b } },
		lt: func { ...args -> args.inject { a, b -> a < b } },
		gt: func { ...args -> args.inject { a, b -> a > b } },
		lte: func { ...args -> args.inject { a, b -> a <= b } },
		gte: func { ...args -> args.inject { a, b -> a >= b } },
		pos: func { ...a -> +(a[0]) },
		neg: func { ...a -> -(a[0]) },
		abs: func { ...a -> Math.abs(a[0]) },
		plus: func { ...args -> args.sum() },
		minus: func { ...args -> args.inject { a, b -> a - b } },
		multiply: func { ...args -> args.inject { a, b -> a * b } },
		div: func { ...args -> args.inject { a, b -> a / b } },
		mod: func { ...args -> args.inject { a, b -> a % b } },
		pow: func { ...args -> args.inject { a, b -> a ** b } },
		sum: func { ...args -> args[0].sum() },
		product: func { ...args -> args[0].inject { a, b -> a * b } },
		regex: func { ...a -> ~(a[0]) },
		set: func { ...a -> a[0][a[1]] = a[2] },
		get: func { ...a -> a[0][a[1]] },
		string: func { ...a -> a[0] as String },
		int: func { ...a -> a[0] as BigInteger },
		int8: func { ...a -> a[0] as byte },
		int16: func { ...a -> a[0] as short },
		int32: func { ...a -> a[0] as int },
		int64: func { ...a -> a[0] as long },
		char: func { ...a -> a[0] as char },
		decimal: func { ...a -> a[0] as BigDecimal },
		decimal32: func { ...a -> a[0] as float },
		decimal64: func { ...a -> a[0] as double },
		list: func { ...args -> args.toList() },
		map: func { ...args -> args.toList().collate(2).collectEntries {
			it.size() == 2 ? [(it[0]): it[1]] : [:] } },
		size: func { ...a -> a[0].size() },
		keys: func { ...a -> a[0].keySet().toList() },
		values: func { ...a -> a[0].values() },
		reverse: func { ...a -> a[0].reverse() },
		format: func { ...args -> String.format(*args) },
		apply_path: macro { ...x ->
			(x[0] instanceof ValueExpression && x[0].value instanceof JSONPath ?
				x[0].value : x[0]()).apply(x[1]())
		},
		define: macro { ...x ->
			x[0].block.context.define(x[0].value instanceof JSONPath ?
				x[0].raw : x[0].value, x[1].call())
		},
		change: macro { ...x ->
			x[0].block.context.change(x[0].value instanceof JSONPath ?
				x[0].raw : x[0].value, x[1]())
		},
		function: macro { ...exprs ->
			Block b = new Block(expressions: exprs.toList(),
				block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			new KismetFunction(b: Kismet.model(b))
		},
		macro: macro { ...exprs ->
			Block b = new Block(expressions: exprs.toList(),
					block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			new KismetMacro(b: Kismet.model(b))
		},
		block: macro { ...exprs ->
			Block b = new Block(expressions: exprs.toList(),
				block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			b
		},
		let: macro { ...exprs ->
			def m = exprs[0]()
			Block b = new Block(expressions: exprs.toList().drop(1))
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b, data: m)
			b
		},
		eval: macro { ...a -> evaluate(a[0]().toString(), a.block) },
		quote: macro { ...exprs -> exprs.toList() },
		if: macro { ...exprs ->
			Block b = new Block(expressions: exprs.toList().drop(1),
					block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			def j
			if (exprs[0]()) j = b()
			j
		},
		ifelseif: macro { ...ab ->
			for (x in ab.toList().collate(2)) {
				if (x.size() == 1) return x[0]()
				else if (x[0]()) return x[1]()
			}
		},
		ifelse: macro { ...x -> x[0]() ? x[1]() : x[2]() },
		while: macro { ...exprs ->
			Block b = new Block(expressions: exprs.toList().drop(1),
					block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			def j
			while (exprs[0]()){
				j = b()
			}
			j
		},
		foreach: macro { ...exprs ->
			def n = exprs[0].value instanceof JSONPath ? exprs[0].raw : exprs[0].value
			Block b = new Block(expressions: exprs.toList().drop(2),
				block: exprs[0].block)
			Block.changeBlock(b.expressions, b)
			b.context = new Context(block: b)
			def a
			for (x in exprs[1]().inner()){
				Block y = b.anonymousClone()
				y.context.directSet(n, Kismet.model(x))
				a = y()
			}
			a
		},
		each: func { ...args -> args[0].each { args[1](Kismet.model(it)) } },
		collect: func { ...args -> args[0].collect { args[1](Kismet.model(it)) } },
		any: func { ...args -> args[0].any { args[1](Kismet.model(it)) } },
		every: func { ...args -> args[0].every { args[1](Kismet.model(it)) } },
		find: func { ...args -> args[0].find { args[1](Kismet.model(it)) } },
		find_all: func { ...args -> args[0].findAll { args[1](Kismet.model(it)) } },
		join: func { ...args -> args[0].join(args[1]) },
		inject: func { ...args -> args[0].inject { a, b -> args[1](Kismet.model(a), Kismet.model(b)) } }
	]

	static {
		defaultContext = defaultContext.collectEntries { k, v -> [(k): Kismet.model(v)] }.asImmutable()
	}

	static List<String> separateLines(String code){
		def currentQuote
		boolean escaped
		int parentheses = 0
		List<String> a = ['']
		for (int i = 0; i < code.length(); ++i){
			def x = code[i]
			if (!currentQuote){
				if (x in ['\'', '"']) currentQuote = x
				if (x == '(') ++parentheses
				if (x == ')') --parentheses
				if (parentheses == 0){
					if (x == '\\') escaped = true
					else if (x in [';', '\r', '\n']) {
						if (!escaped){
							if (code.length() <= i + 1){ ++i }
							else {
								if (code[i + 1] in ['\r', '\n'])
									++i
								a << ''
							}
						}else{
							if (code.length() <= i + 1){ ++i }
							else {
								if (code[i + 1] in ['\r', '\n'])
									++i
							}
							escaped = false
						}
					}else{
						a[-1] += x
						escaped = false
					}
				}else{
					a[-1] += x
				}
			}else{
				if (x == '\\') escaped = true
				else if (!escaped && x == currentQuote) currentQuote = null
				else escaped = false
				a[-1] += x
			}
		}
		a*.trim() - ''
	}

	static Expression compile(String code, Block source = null){
		def lines = separateLines(code)
		if (lines.size() <= 1)
			parseExpression(source, lines[0])
		else
			new Block(raw: code).with { b ->
				context = new Context(block: b)
				if (source) block = source
				expressions = lines.collect { parseExpression(b, it) }
				b
			}
	}

	static evaluate(String code, Block source = null){
		compile(code, source)()
	}
	
	static Expression parseExpression(Block block, String code){
		Expression ex
		if (null == code || !code.trim())
			ex = new NoExpression(block: block)
		else if (code.isBigInteger())
			ex = new ValueExpression(block: block, raw: code,
					type: ValueExpression.Type.INTEGER)
		else if (code.isBigDecimal())
			ex = new ValueExpression(block: block, raw: code,
					type: ValueExpression.Type.DECIMAL)
		else if (code.size() >= 2 && code[0] in ['\'', '"'] && code[0] == code[-1])
			ex = new ValueExpression(block: block, raw: code,
				type: ValueExpression.Type.STRING)
		else if ((code.contains('(') && code.contains(')')) ||
				code.toCharArray().any(Character.&isWhitespace))
			ex = new CallExpression(block: block, raw: code)
		else
			ex = new ValueExpression(block: block, raw: code,
				type: ValueExpression.Type.PATH)
		ex.parse()
		ex
	}
	
	static macro(Closure c){
		new GroovyMacro(x: c)
	}

	static func(Closure c){
		new GroovyFunction(x: c)
	}
}


class Block extends Expression {
	LinkedList<Expression> expressions
	Context context
	
	KismetObject evaluate(){
		def x
		for (e in expressions){
			x = e()
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
	Map<String, KismetObject> data = [:]
	
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
	
	abstract KismetObject evaluate()

	KismetObject call(){ evaluate() }
}

class NoExpression extends Expression {
	def parse(){}

	KismetObject evaluate(){
		Kismet.model(null)
	}
}

class ValueExpression extends Expression {
	Type type
	def value
	
	def parse(){
		if (!raw) value = null
		else if (type == Type.INTEGER) value = new BigInteger(raw)
		else if (type == Type.DECIMAL) value = new BigDecimal(raw)
		else if (type == Type.STRING){
			def x = ''
			boolean escaped
			def usize
			def u = ''
			for (a in raw.trim()[1..-2].toList()){
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
		}else if (type == Type.PATH) value = JSONPath.parse(raw)
		else throw new InvalidSyntaxException('Unknown value type')
	}
	
	KismetObject evaluate(){
		Kismet.model(value instanceof JSONPath ? value.apply(block.context) : value)
	}

	enum Type {
		STRING,
		INTEGER,
		DECIMAL,
		PATH
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
				if (!quoteEscaped && raw[index] == currentQuote) currentQuote = null
				else if (!quoteEscaped && raw[index] == '\\') quoteEscaped = true
				if (quoteEscaped) quoteEscaped = false
				if (!inBetween) args[-1] += raw[index]
			}
			++index
		}
		if (parantheses > 0)
			throw new InvalidSyntaxException("$parantheses missing closing parantheses\n$raw")
		expressions = args.collect { KismetInner.compile(it, block) }
	}
	
	KismetObject evaluate(){
		def x = expressions[0]()
		if (expressions.size() == 1) return x
		def args = expressions.drop(1).findAll { !(it instanceof NoExpression) }
		boolean y = args
		if (!(x.inner() instanceof Macro)) args = args*.evaluate().collect(Kismet.&model)
		else if (y) args = args.collect(Kismet.&model)
		if (y) Kismet.model(x(*args))
		else Kismet.model(x())
	}
}