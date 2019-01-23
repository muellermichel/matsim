package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;
import org.matsim.core.utils.quickevents.QuickEvents;

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
    // Event generation helper.
    private final QuickEvents events;

    // Current timestamp
    private int secs;
    private int routed;

    public Realm(
            Link[] links, 
            ArrayList<ConcurrentLinkedQueue<Link>> delayedLinksByWakeupTime, 
            ArrayList<ConcurrentLinkedQueue<Agent>> delayedAgentsByWakeupTime,
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

    protected boolean processAgentLink(Agent agent, int element) {
        int linkid = Agent.getLinkPlanElement(element);
        int velocity = Agent.getVelocityPlanElement(element);
        Link next = links[linkid];
        if (next.push(secs, agent, velocity)) {
            // the max(1, ...) ensures that a link hop takes at least on step.
            agent.linkFinishTime = 
                secs + Math.max(1, next.length() / Math.min(velocity, next.velocity()));
            advanceAgent(agent);
            // If the link was empty before
            if (next.queue().size() == 1) {
                delayedLinksByWakeupTime.get(Math.max(agent.linkFinishTime, secs + 1)).add(next);
            }
            return true;
        } else {
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
        int routeid = Agent.getRoutePlanElement(routestop);
        int stopid = Agent.getStopPlanElement(routestop);
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

    protected boolean processAgent(Agent agent) {
        // Peek the next plan element and try to execute it.
        int element = Agent.getPlanElement(agent.plan[agent.planIndex + 1]);
        int type = Agent.getPlanHeader(agent.plan[agent.planIndex + 1]);
        switch (type) {
            case Agent.LinkType:        return processAgentLink(agent, element);
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

    protected void processAgentActivities() {
        ConcurrentLinkedQueue<Agent> next = delayedAgentsByWakeupTime.get(secs + 1);
        for (Agent agent : delayedAgentsByWakeupTime.get(secs)) {
            if (agent.planIndex < (agent.plan.length - 1) && !processAgent(agent)) {
                next.add(agent);
            }
        }
    }

    protected void processLinks(ConcurrentLinkedQueue<Link> arrlinks) {
        for (Link link : arrlinks) {
            if (!link.queue().isEmpty() && secs >= link.nexttime()) {
                Agent agent = link.queue().peek();
                while (agent.linkFinishTime <= secs) {
                    if (agent.planIndex >= (agent.plan.length - 1) || processAgent(agent)) {
                        link.pop();
                        routed++;
                        if ((agent = link.queue().peek()) == null) {
                            break;
                        }
                    } else {
                        break;
                    }
                }
                // If there is at least one agent in the link
                if (agent != null) {
                    delayedLinksByWakeupTime.get(Math.max(agent.linkFinishTime, secs + 1)).add(link);
                }
            }
        }
    }

    // Updates all links and agents. Returns the number of routed agents.
    public int tick(int delta) throws Exception {
        long start, factivities = 0, frouting = 0;
        routed = 0;
        secs += delta;

        start = System.nanoTime();

        events.tick();

        // Process agents waiting for something.
        processAgentActivities(); // TODO - parallize with processing delayed links

        factivities = System.nanoTime();

        // Process links.
        processLinks(delayedLinksByWakeupTime.get(secs)); // TODO - parallelize

        frouting = System.nanoTime();

        log(secs, String.format(
                "Processed %d agents in %d ns (activities = %d ns; routing = %d ns)",
                routed,
                frouting - start,
                factivities - start,
                frouting - factivities));
        return routed;
    }

    public int time() { return this.secs; }
    public Link[] links() { return this.links; }
    public ArrayList<ConcurrentLinkedQueue<Link>> delayedLinks() { return this.delayedLinksByWakeupTime; }
    public ArrayList<ConcurrentLinkedQueue<Agent>> delayedAgents() { return this.delayedAgentsByWakeupTime; }
    public QuickEvents events() { return events; }
}
