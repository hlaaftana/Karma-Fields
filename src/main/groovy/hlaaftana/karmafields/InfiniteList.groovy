package hlaaftana.karmafields

class InfiniteList extends AbstractList {
	def defaultValue
	int maxSize
	Map changedValues = [:]
	InfiniteList(defaultValue, int maxSize = Integer.MAX_VALUE){
		this.defaultValue = defaultValue
		this.maxSize = [maxSize, 0].max()
	}

	def get(int index){
		changedValues.containsKey(index) ?
			changedValues[index] :
			defaultValue
	}

	def set(int index, element){ changedValues[index] = element }

	int indexOf(element){
		changedValues.any { k, v -> v == element } ?
			changedValues.find { k, v -> v == element }.key :
			defaultValue == element ? 0 : -1
	}

	int size(){ maxSize }

	def remove(int index){ changedValues.remove(index) }

	void clear(){ changedValues = [:] }

	String toString(){
		def a = changedValues.collect { k, v -> ", $k: $v" }
		"InfiniteList[default: $defaultValue" +
			(a.join("") ?: "") + "]"
	}
}