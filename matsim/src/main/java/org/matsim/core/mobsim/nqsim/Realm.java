package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;

import org.apache.log4j.Logger;
import org.matsim.core.utils.quickevents.QuickEvents;

public class Realm {
	final private static Logger log = Logger.getLogger(Realm.class);

    // Global array of links.
    // Note: the id of the link is its index in the array.
    private final Link[] links;
    // Internal realm links on hold until a specific timestamp (in seconds).
    // Internal means that the source and destination realm of are the same.
    // delayedLinksByWakeupTime.get(realm).get(secs) -> queue of agents
    private final ArrayList<ArrayList<ConcurrentLinkedQueue<Link>>> delayedLinksByWakeupTime;
    // Agents on hold until a specific timestamp (in seconds).
    // delayedAgentsByWakeupTime.get(realm).get(secs) -> queue of agents
    private final ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> delayedAgentsByWakeupTime;
    // Agents on hold waiting for a vehicle to arrive.
    // agentsInStops.get(route id).get(local stop id) -> arary of agents
    private final ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> agentsInStops;
    // Event generation helper.
    private final QuickEvents events;
    // If true, will enable per tick and per thread logs
    private static final boolean debug = true;

    // Current timestamp
    private int secs;

    public Realm(
            Link[] links,
            ArrayList<ArrayList<ConcurrentLinkedQueue<Link>>> delayedLinksByWakeupTime,
            ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> delayedAgentsByWakeupTime,
            ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> agentsInStops) throws Exception {
        this.links = links;
        // The plus one is necessary because we peek into the next slot on each tick.
        this.delayedLinksByWakeupTime = delayedLinksByWakeupTime;
        this.delayedAgentsByWakeupTime = delayedAgentsByWakeupTime;
        this.agentsInStops = agentsInStops;
        events = new QuickEvents();
    }

    public static boolean log(int time, String s) {
        log.info(String.format("[ time = %d ] %s", time, s));
        return true;
    }

    private void advanceAgent(Agent agent) {
//        log(secs, String.format(
//            "agent=%d finished %s", agent.id, Agent.toString(agent.currPlan())));
        agent.planIndex++;
//        log(secs, String.format(
//            "agent=%d starting %s", agent.id, Agent.toString(agent.currPlan())));
        events.registerPlannedEvent(agent.id, agent.planIndex, agent.currPlan());
    }

    protected boolean processAgentLink(Agent agent, int element, int currLinkId) {
        int linkid = Agent.getLinkPlanElement(element);
        int velocity = Agent.getVelocityPlanElement(element);
        Link next = links[linkid];
        int prev_finishtime = agent.linkFinishTime;
        // the max(1, ...) ensures that a link hop takes at least on step.
        agent.linkFinishTime =
            secs + Math.max(1, next.length() / Math.min(velocity, next.velocity()));
        if (next.push(secs, agent, velocity)) {
            advanceAgent(agent);
            // If the agent we just added is the head, add to delayed links
            // the old peek is used to prevent loops
            if (next.id() != currLinkId && next.queue().peek() == agent) {
            }
            return true;
        } else {
            agent.linkFinishTime = prev_finishtime;
            return false;
        }
    }

    protected boolean processAgentSleepFor(Agent agent, int sleep) {
        return processAgentSleepUntil(agent, secs + Math.max(1, sleep));
    }

    protected boolean processAgentSleepUntil(Agent agent, int sleep) {
        delayedAgentsByWakeupTime.get(agent.realm).get(Math.max(sleep, secs + 1)).add(agent);
        advanceAgent(agent);
        return true;
    }

    protected boolean processAgentAccess(Agent agent, int routestop) {
        int routeid = Agent.getRoutePlanElement(routestop);
        int stopid = Agent.getStopPlanElement(routestop);
        agentsInStops.get(routeid).get(stopid).add(agent);
        return true;
    }

    protected boolean processAgentStop(Agent agent, int stopid) {
        int routeid = agent.route;
        advanceAgent(agent);

        for (Agent out : agent.egress(stopid)) {
            delayedAgentsByWakeupTime.get(out.realm).get(secs + 1).add(out);
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
        int element = Agent.getPlanElement(agent.plan[agent.planIndex + 1]);
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
        // -1 is used in the processAgent because the agent is not in a link currently.
        if (agent.planIndex < (agent.plan.length - 1) && !processAgent(agent, -1)) {
            delayedAgentsByWakeupTime.get(agent.realm).get(secs + 1).add(agent);
            return 0;
        }
        return 1;
    }

    protected int processLinks(Link link) {
        int routed = 0;
        Agent agent = link.queue().peek();

        while (agent.linkFinishTime <= secs) {
            if (agent.planIndex >= (agent.plan.length - 1) || processAgent(agent, link.id())) {
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
        if (agent != null) {
            delayedLinksByWakeupTime.get(link.realm).get(Math.max(agent.linkFinishTime, secs + 1)).add(link);
        }
        return routed;
    }

    public void run() throws Exception {
        int nthreads = World.NUM_REALMS;
        Thread[] workers = new Thread[nthreads];
        CyclicBarrier cb = new CyclicBarrier(nthreads, new Runnable(){

            @Override
            public void run() {
                secs += 1;
                events.tick();
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
                    int realm = id;
                    Agent agent = null;
                    Link link = null;

                    while ((agent = delayedAgentsByWakeupTime.get(realm).get(secs).poll()) != null) {
                        routed += processAgentActivities(agent);
                    }
                    while ((link = delayedLinksByWakeupTime.get(realm).get(secs).poll()) != null) {
                        routed += processLinks(link);
                    }

                    if (debug) {
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
    public ArrayList<ArrayList<ConcurrentLinkedQueue<Link>>> delayedLinks() { return this.delayedLinksByWakeupTime; }
    public ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> delayedAgents() { return this.delayedAgentsByWakeupTime; }
    public QuickEvents events() { return events; }
}
