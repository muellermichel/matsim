package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.events.Event;

public class Realm {
	final private static Logger log = Logger.getLogger(Realm.class);

    // Global array of links.
    // Note: the id of the link is its index in the array.
    private final Link[] links;
    // Internal realm links on hold until a specific timestamp (in seconds). 
    // Internal means that the source and destination realm of are the same.
    private final ArrayList<ConcurrentLinkedQueue<Link>> delayedLinksByWakeupTime;
    // Agents on hold until a specific timestamp (in seconds).
    private final ArrayList<ConcurrentLinkedQueue<Agent>> delayedAgentsByWakeupTime;
    // Agents on hold waiting for a vehicle to arrive.
    // agentsInStops.get(route id).get(local stop id) -> arary of agents
    private final ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> agentsInStops;
    // Matsim events.
    private final ArrayList<ArrayList<Event>> events;

    // Current timestamp
    private int secs;

    public Realm(
            Link[] links, 
            ArrayList<ConcurrentLinkedQueue<Link>> delayedLinksByWakeupTime, 
            ArrayList<ConcurrentLinkedQueue<Agent>> delayedAgentsByWakeupTime,
            ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> agentsInStops,
            ArrayList<ArrayList<Event>> events) throws Exception {
        this.links = links;
        // The plus one is necessary because we peek into the next slot on each tick.
        this.delayedLinksByWakeupTime = delayedLinksByWakeupTime;
        this.delayedAgentsByWakeupTime = delayedAgentsByWakeupTime;
        this.agentsInStops = agentsInStops;
        this.events = events;
    }

    public static void log(int time, String s) {
        if (World.DEBUG_REALMS) {
            log.info(String.format("ETHZ [ time = %d ] %s", time, s));
        }
    }

    private void advanceAgent(Agent agent) {
        long centry = agent.currPlan();
        log(secs, String.format("agent=%d finished %s", agent.id, Agent.toString(centry)));
        agent.planIndex++;
        long nentry = agent.currPlan();
        log(secs, String.format("agent=%d starting %s", agent.id, Agent.toString(nentry)));
        if (Agent.getPlanEvent(nentry) != 0) {
            events.get(agent.id).get(Agent.getPlanEvent(nentry)).setTime(secs);
        }
    }

    protected boolean processAgentLink(Agent agent, int element, int currLinkId) {
        int linkid = Agent.getLinkPlanEntry(element);
        int velocity = Agent.getVelocityPlanEntry(element);
        Link next = links[linkid];
        int prev_finishtime = agent.linkFinishTime;
        int prev_starttime = agent.linkStartTime;
        // this ensures that if no velocity is provided for the vehicle, we use the link
        velocity = velocity == 0 ? next.velocity() : velocity;
        // the max(1, ...) ensures that a link hop takes at least on step.
        agent.linkFinishTime =
            secs +
            World.LINK_ADVANCE_DELAY +
            Math.max(1, next.length() / Math.min(velocity, next.velocity()));
        // TODO - protecd this?
        System.out.println(String.format("ETHZ traveltime hermes %d len %d vel %d",
            Math.max(1, next.length() / Math.min(velocity, next.velocity())),
            next.length(),
            Math.min(velocity, next.velocity())));
        agent.linkStartTime = secs;
        if (next.push(agent)) {
            advanceAgent(agent);
            // If the agent we just added is the head, add to delayed links
            if (currLinkId != next.id() && next.queue().peek() == agent) {
                delayedLinksByWakeupTime.get(Math.max(agent.linkFinishTime, secs + 1)).add(next);
            }
            return true;
        } else {
            agent.linkFinishTime = prev_finishtime;
            agent.linkStartTime = prev_starttime;
            return false;
        }
    }

    protected boolean processAgentSleepFor(Agent agent, int sleep) {
        return processAgentSleepUntil(agent, secs + Math.max(1, sleep));
    }

    protected boolean processAgentSleepUntil(Agent agent, int sleep) {
        delayedAgentsByWakeupTime.get(Math.max(sleep, secs + 1)).add(agent);
        advanceAgent(agent);
        return true;
    }

    protected boolean processAgentAccess(Agent agent, int routestop) {
        int routeid = Agent.getRoutePlanEntry(routestop);
        int stopid = Agent.getStopPlanEntry(routestop);
        agentsInStops.get(routeid).get(stopid).add(agent);
        return true;
    }

    protected boolean processAgentStop(Agent agent, int stopid) {
        int routeid = agent.route;
        advanceAgent(agent);

        for (Agent out : agent.egress(stopid)) {
            delayedAgentsByWakeupTime.get(secs + 1).add(out);
            advanceAgent(out);
        }

        ConcurrentLinkedQueue<Agent> agents = agentsInStops.get(routeid).get(stopid);
        ArrayList<Agent> removed = new ArrayList<>();
        for (Agent in : agents ) {
            if (!agent.access(in)) {
                break;
            }
            removed.add(in);
            advanceAgent(in);
        }
        agents.removeAll(removed);
        // False is returned to force this agent to be processed in the next tick.
        // This will mean that the vehicle will be processed in the next tick.
        return false;
    }

    protected boolean processAgentRoute(Agent agent, int routeid) {
        agent.route(routeid);
        advanceAgent(agent);
        // False is returned to force this agent to be processed in the next tick.
        // This will mean that the vehicle will be processed in the next tick.
        return false;
    }

    protected boolean processAgent(Agent agent, int currLinkId) {
        // Peek the next plan element and try to execute it.
        int element = Agent.getPlanPayload(agent.plan[agent.planIndex + 1]);
        int type = Agent.getPlanHeader(agent.plan[agent.planIndex + 1]);
        switch (type) {
            case Agent.LinkType:        return processAgentLink(agent, element, currLinkId);
            case Agent.SleepForType:    return processAgentSleepFor(agent, element);
            case Agent.SleepUntilType:  return processAgentSleepUntil(agent, element);
            case Agent.AccessType:      return processAgentAccess(agent, element);
            case Agent.StopType:        return processAgentStop(agent, element);
            case Agent.RouteType:       return processAgentRoute(agent, element);
            case Agent.EgressType:      // The egress event is consumed in the stop.
            default:
                throw new RuntimeException(String.format(
                    "unknow plan element type %d, agent %d plan index %d",
                    type, agent.id, agent.planIndex + 1));
        }
    }

    protected int processAgentActivities(Agent agent) {
        boolean finished = agent.planIndex >= (agent.plan.length - 1);
        // if finished, install times on last event.
        if (finished) {
            events.get(agent.id).get(events.get(agent.id).size() - 1).setTime(secs);
        }
        // -1 is used in the processAgent because the agent is not in a link currently.
        if (!finished && !processAgent(agent, -1)) {
            delayedAgentsByWakeupTime.get(secs + 1).add(agent);
            return 0;
        }
        return 1;
    }

    protected int processLinks(Link link) {
        int routed = 0;
        Agent agent = link.queue().peek();

        while (agent.linkFinishTime <= secs) {
            boolean finished = agent.planIndex >= (agent.plan.length - 1);
            // if finished, install times on last event.
            if (finished) {
                events.get(agent.id).get(events.get(agent.id).size() - 1).setTime(secs);
            }
            if (finished || processAgent(agent, link.id())) {
                link.pop();
                routed += 1;
                if ((agent = link.queue().peek()) == null) {
                    break;
                }
            } else {
                break;
            }
        }
        // If there is at least one agent in the link that could not be processed
        // In addition we check if this agent was not added in this tick.
        if (agent != null && agent.linkStartTime != secs) {
            delayedLinksByWakeupTime.get(Math.max(agent.linkFinishTime, secs + 1)).add(link);
        }
        return routed;
    }

    public void run(int nthreads) throws Exception {
        Thread[] workers = new Thread[nthreads];
        CyclicBarrier cb = new CyclicBarrier(nthreads, new Runnable(){

            @Override
            public void run() {
                secs += 1;
            }
        });

        // Create and start worker threads
        for (int i = 0; i < nthreads; i++) {
            workers[i] = new Thread() {

                private int id;

                public Thread initialize(int id) {
                    this.id = id;
                    return this;
                }

                public void tick() {
                    int routed = 0;
                    Agent agent = null;
                    Link link = null;

                    while ((agent = delayedAgentsByWakeupTime.get(secs).poll()) != null) {
                        routed += processAgentActivities(agent);
                    }
                    while ((link = delayedLinksByWakeupTime.get(secs).poll()) != null) {
                        routed += processLinks(link);
                    }

                    if (routed > 0) {
                        log(secs, String.format("Thread %s Processed %d agents", 
                            id, routed));
                    }
                    routed = 0;
                }

                @Override
                public void run() {
                    try {
                        while (secs != World.SIM_STEPS) {
                            tick();
                            cb.await();
                        }

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

            }.initialize(i);
            workers[i].start();
        }

        // Join threads
        for (int i = 0; i < nthreads; i++) {
            workers[i].join();
        }
    }

    public int time() { return this.secs; }
    public Link[] links() { return this.links; }
    public ArrayList<ConcurrentLinkedQueue<Link>> delayedLinks() { return this.delayedLinksByWakeupTime; }
    public ArrayList<ConcurrentLinkedQueue<Agent>> delayedAgents() { return this.delayedAgentsByWakeupTime; }
}
