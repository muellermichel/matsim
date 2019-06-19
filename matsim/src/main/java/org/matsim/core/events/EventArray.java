package org.matsim.core.events;

import java.util.ArrayList;
import java.util.Arrays;

import org.matsim.api.core.v01.events.Event;

public class EventArray {
	Event[] array;
	int size;

	public EventArray(int capacity) {
		this.array = new Event[capacity];
	}
	
	public EventArray() {
		this(32);
	}

	public void add(Event element) {
		if (size == array.length) {
			array = Arrays.copyOf(array, array.length * 2);
		}
		array[size] = element;
		size++;
	}
	
	public void removeLast() {
		array[size - 1] = null;
		size--;
	}

	public int size() {
		return size;
	}
	public Event get(int index) {
		assert index < size;
		assert array[index] != null;
		return array[index];
	}

	public void clear() {
		for (int i = 0; i < size; i++) {
			array[i] = null;
		}
		size = 0;
	}
	
	public Event[] array() {
		return array;
	}

	// This should be avoided as it introduces a lot of overhead.
	public ArrayList<Event> asArrayList() {
		return new ArrayList<>(Arrays.asList(Arrays.copyOf(array, size)));
	}

	public boolean contains(Event event) {
		for (int i = 0; i < size; i++) {
			if (array[i].equals(event)) {
				return true;
			}
		}
		return false;
	}
}