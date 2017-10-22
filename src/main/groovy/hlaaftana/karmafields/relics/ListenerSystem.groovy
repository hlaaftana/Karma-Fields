package hlaaftana.karmafields.relics

class ListenerSystem<E, L> {
	Map<E, List<L>> listeners = [:]

	L addListener(E event, L listener) {
		if (listeners.containsKey(event)) listeners[event].add(listener)
		else listeners[event] = [listener]
		listener
	}

	boolean removeListener(E event, L listener) {
		listeners[event]?.remove(listener)
	}

	List<L> removeListenersFor(E event){
		listeners.remove(event)
	}

	void removeAllListeners(){ listeners.clear() }

	def dispatchEvent(E event, data){
		for (l in listeners[event])
			l(data)
	}
}
