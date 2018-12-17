package org.matsim.core.mobsim.nqsim;

import java.util.ArrayDeque;
import java.util.Queue;

public class LinkInternal {

    // Timestamp of the next agent to arrive.
    private int nextTime;
    // Queue of agents on this link.
    private final Queue<Agent> queue;
    // Length of the link in meters.
    private final int length;
    // Max velocity within the link (meters per second).
    private final int velocity;
    // Number of free slots in the queue.
    private int currentCapacity;

    public LinkInternal(int capacity, int length, int velocity) {
        this.queue = new ArrayDeque<>(capacity);
        this.length = length;
        this.velocity = velocity;
        this.currentCapacity = capacity;
    }

    public boolean push(int time, Agent agent, int velocity) {
        if (currentCapacity > 0) { 
            queue.add(agent);
            currentCapacity--;
            agent.linkFinishTime = time + length / Math.min(velocity, this.velocity);
            nextTime = queue.peek().linkFinishTime;
            return true;
        } else {
            return false;
        }
    }

    public void pop() {
        queue.poll();
        currentCapacity++;
    }

    public int nexttime () {
        return this.nextTime;
    }

    public int nexttime (int nexttime) {
        return this.nextTime = nexttime;
    }

    public int length() {
        return this.length;
    }

    public int velocity() {
        return this.velocity;
    }

    public int currentCapacity() {
        return this.currentCapacity;
    }

    public Queue<Agent> queue() {
        return this.queue;
    }

}