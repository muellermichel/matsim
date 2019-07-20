package org.matsim.core.mobsim.hermes;

import java.util.Arrays;
import java.util.Iterator;

public class Link {

	// The whole purpose of this implementation is to have a dynamically sized queue that never goes over the capacity
	// restriction. This becomes a big memory waste when large scenarios are used. This implementation is inspired in
	// Java's implementation of ArrayDequeue.
	public static class AgentQueue implements Iterable<Agent> {
		// the storage
		private Agent[] array;
		// the max capacity of the queue
		private final int capacity;
		// Pop/peak from head
		private int head;
		// Push to tail
		private int tail;

		public AgentQueue(int capacity) {
			this.capacity = capacity;
			this.array = new Agent[1];
		}

		private int inc(int number) {
			if (++number == array.length) {
				number = 0;
			}
			return number;
		}

		public boolean push(Agent agent) {
			if (head == inc(tail)) {
				if (array.length == capacity) {
					return false;
				}
				array = Arrays.copyOf(array, Math.min(capacity, array.length * 2));
			}
			array[tail = inc(tail)] = agent;
			return true;
		}

		public Agent peek() {
			return head == tail ? null : array[head];
		}

		public void pop() {
			if (head != tail) {
				head = inc(head);
			}
		}

		public int size() {
			return tail < head ? head - tail + array.length : tail - head;
		}

		public void clear() {
			for (int i = head; i < size(); i = inc(i)) {
				array[i] = null;
			}
			head = tail = 0;
		}

		@Override
		public Iterator<Agent> iterator() {
			return new Iterator<Agent>() {

				private int idx = head;

				@Override
				public boolean hasNext() {
					if (idx == tail) {
						return false;
					} else {
						return true;
					}
				}

				@Override
				public Agent next() {
					Agent agent = array[idx];
					idx = inc(idx);
					return agent;
				}
			};
		}
	}

    // Id of the link.
    private int id;
    // Length of the link in meters.
    private final int length;
    // Max velocity within the link (meters per second).
    private final int velocity;
    // Maximum number of free slots in the queue.
    private final int capacity;
    // Queues of agents on this link. Boundary links use both queues.
    private final AgentQueue queue;

    public Link(int id, int capacity, int length, int velocity) {
        this.id = id;
        this.length = length;
        this.velocity = velocity;
        this.capacity = capacity;
        // We do not preallocate using the capacity because it leads to huge memory waste.
        this.queue = new AgentQueue(Math.max(1, capacity));
    }

    public void reset() {
    	queue.clear();
    }

    public boolean push(Agent agent) {
    	return queue.push(agent);
    }

    public void pop() {
        queue.pop();
    }

    public int nexttime () {
        if (queue.size() == 0) {
            return 0;
        } else {
            return queue.peek().linkFinishTime;
        }
    }

    public int length() {
        return this.length;
    }

    public int velocity() {
        return this.velocity;
    }

    public AgentQueue queue() {
        return this.queue;
    }

    public int capacity() {
        return this.capacity;
    }

    public int id() {
        return this.id;
    }

}
