package hlaaftana.karmafields

class InfiniteList extends AbstractList {
	def defaultValue
	int maxSize
	Map changedValues = [:]
	InfiniteList(defaultValue, int maxSize = Integer.MAX_VALUE){
		this.defaultValue = defaultValue
		this.maxSize = Math.max(maxSize, 0)
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
			(a.join() ?: '') + ']'
	}

	boolean isEmpty(){ false }

	boolean contains(o){ o == defaultValue || o in changedValues.values() }

	boolean remove(o) {
		changedValues.remove(
			changedValues.find { k, v -> v == o }.key)
	}

	boolean containsAll(Collection c) {
		c.every { contains it }
	}

	boolean removeAll(Collection c) {
		changedValues.findAll { k, v -> v in c }*.key
			.each(changedValues.&remove)
	}

	boolean retainAll(Collection c) {
		removeAll(changedValues.values().findAll { !(it in c) })
	}

	int lastIndexOf(o) {
		o == defaultValue ? maxSize
			: changedValues.find { k, v -> o == v }.key
	}

	List subList(int fromIndex, int toIndex) {
		def a = new InfiniteList(defaultValue, toIndex - fromIndex)
		changedValues.each { k, v -> if (fromIndex <= k && k < toIndex)
			a[k - fromIndex] = v
		}
		a
	}
}