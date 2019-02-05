package org.matsim.core.mobsim.nqsim;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class Link {

    // Id of the link.
    private int id;
    // Id of the realm that this link belongs.
    protected final int realm;
    // Length of the link in meters.
    private final int length;
    // Max velocity within the link (meters per second).
    private final int velocity;
    // Maximum number of free slots in the queue.
    private final int capacity;
    // Queues of agents on this link. Boundary links use both queues.
    private final LinkedBlockingQueue<Agent> queue;

    public Link(int id, int realm, int capacity, int length, int velocity) {
        this.id = id;
        this.realm = realm;
        this.length = length;
        this.velocity = velocity;
        this.capacity = capacity;
        // TODO - this happens with the SBB scenario, they have capacity as zero.
        this.queue = new LinkedBlockingQueue<>(Math.max(capacity, 1));
    }

    public boolean push(Agent agent) {
        return queue.offer(agent);
    }

    public void pop() {
        queue.poll();
    }

    public int nexttime () {
        if (queue.isEmpty()) {
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

    public Queue<Agent> queue() {
        return this.queue;
    }

    public int capacity() {
        return this.capacity;
    }

    public int id() {
        return this.id;
    }

}