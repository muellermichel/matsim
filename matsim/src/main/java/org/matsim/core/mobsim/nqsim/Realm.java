package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;
import java.util.Collections;

import org.apache.log4j.Logger;
import org.matsim.core.utils.quickevents.QuickEvents;

public class Realm {
	final private static Logger log = Logger.getLogger(Realm.class);

    // The world that contains the realm.
    private final World world;
    // Identifier of the realm.
    private final int id;
    // Global array of links.
    // Note: the id of the link is its index in the array.
    private final Link[] links;
    // Internal realm links on hold until a specific timestamp (in seconds). 
    // Internal means that the source and destination realm of are the same.
    private final ArrayList<ArrayList<Link>> delayedLinksByWakeupTime; // TODO - use the concurrent linked queue
    // Agents on hold until a specific timestamp (in seconds).
    private final ArrayList<ArrayList<Agent>> delayedAgentsByWakeupTime; // TODO - use the concurrent linked queue
    // Agents on hold waiting for a vehicle to arrive.
    // agentsInStops.get(route id).get(local stop id) -> arary of agents
    private final ArrayList<ArrayList<ArrayList<Agent>>> agentsInStops;
    // Event generation helper.
    private final QuickEvents events;

    // Current timestamp
    private int secs;
    private int routed;

    // TODO - need to understand which way to go:
    // option 1: have all links with synchronized queues (expensive). Close to
    // zero inter-thread synchronization. Delayed data structures implemented
    // as concurrent queues (somewhat expensive). No locality through.
    // option 2; have all links with synchronized queues (expensive). Keep
    // realms but push to external links directly. Each thread has its onw
    // delayed data structures.
    // option 3: array dequeue (fast) for internal links. Synchronized queus
    // for boundary links. Broker for activities.

    public Realm(World world, int id, Link[] links, Link[] inLinks) throws Exception {
        this.world = world;
        this.id = id;
        this.links = links;
        // The plus one is necessary because we peek into the next slot on each tick.
        this.delayedLinksByWakeupTime =
            new ArrayList<>(Collections.nCopies(World.ACT_SLOTS + 1, null));
        this.delayedAgentsByWakeupTime =
            new ArrayList<>(Collections.nCopies(World.ACT_SLOTS + 1, null));
        this.agentsInStops = new ArrayList<>();
        setupDelayedLinks();
        events = new QuickEvents();
    }

    // TODO - where is this being done for agents!, do this in the scenario importer!
    private void setupDelayedLinks() {
        for (int i = 0; i < links.length; i++) {
            int nextwakeup = links[i].nexttime();
            if (nextwakeup > 0) {
                getDelayedLinks(nextwakeup).add(links[i]);
            }
        }
    }

    public static boolean log(int time, int realmid, String s) {
        log.info(String.format("[ time = %d realm = %d ] %s", time, realmid, s));
        return true;
    }

    private void advanceAgent(Agent agent) {
//        log(secs, id, String.format(
//            "agent=%d finished %s", agent.id, Agent.toString(agent.currPlan())));
        agent.planIndex++;
//        log(secs, id, String.format(
//            "agent=%d starting %s", agent.id, Agent.toString(agent.currPlan())));
        events.registerPlannedEvent(agent.id, agent.planIndex, agent.currPlan());
    }

    public ArrayList<Link> getDelayedLinks(int wakeupTime) {
        ArrayList<Link> act = delayedLinksByWakeupTime.get(wakeupTime);
        if (act == null) {
            act = new ArrayList<>();
            delayedLinksByWakeupTime.set(wakeupTime, act);
        }
        return act;
    }

    public ArrayList<Agent> getDelayedAgents(int wakeupTime) {
        ArrayList<Agent> act = delayedAgentsByWakeupTime.get(wakeupTime);
        if (act == null) {
            act = new ArrayList<>();
            delayedAgentsByWakeupTime.set(wakeupTime, act);
        }
        return act;
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
                getDelayedLinks(Math.max(agent.linkFinishTime, secs + 1)).add(next);
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
        getDelayedAgents(Math.max(sleep, secs + 1)).add(agent);
        advanceAgent(agent);
        return true;
    }

    // Helper method that returns an empty array list is no element is present.
    // It also fills the array with nulls if the size of the array is < index.
    private ArrayList<ArrayList<Agent>> get_route(
      ArrayList<ArrayList<ArrayList<Agent>>> arr, int index) {
        if (arr.size() <= index) {
            arr.ensureCapacity(index + 1);
            for (int i = arr.size(); i <= index; i++) {
                arr.add(null);
            }
        }
        if (arr.get(index) == null) {
            arr.set(index, new ArrayList<>());
        }
        return arr.get(index);
    }

    // Helper method that returns an empty array list is no element is present.
    // It also fills the array with nulls if the size of the array is < index.
    private ArrayList<Agent> get_stop(ArrayList<ArrayList<Agent>> arr, int index) {
        if (arr.size() <= index) {
            arr.ensureCapacity(index + 1);
            for (int i = arr.size(); i <= index; i++) {
                arr.add(null);
            }
        }
        if (arr.get(index) == null) {
            arr.set(index, new ArrayList<>());
        }
        return arr.get(index);
    }
    protected boolean processAgentAccess(Agent agent, int routestop) {
        int routeid = Agent.getRoutePlanElement(routestop);
        int stopid = Agent.getStopPlanElement(routestop);
        get_stop(get_route(agentsInStops, routeid), stopid).add(agent);
        return true;
    }

    protected boolean processAgentStop(Agent agent, int stopid) {
        int routeid = agent.route;
        advanceAgent(agent);

        for (Agent out : agent.egress(stopid)) {
            getDelayedAgents(secs + 1).add(out);
            advanceAgent(out);
        }

        ArrayList<Agent> agents = get_stop(get_route(agentsInStops, routeid), stopid);
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
        ArrayList<Agent> next = getDelayedAgents(secs + 1);
        for (Agent agent : getDelayedAgents(secs)) {
            if (agent.planIndex < (agent.plan.length - 1) && !processAgent(agent)) {
                next.add(agent);
            }
        }
    }

    protected void processLinks(ArrayList<Link> arrlinks) {
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
                    getDelayedLinks(Math.max(agent.linkFinishTime, secs + 1)).add(link);
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
        processAgentActivities();

        factivities = System.nanoTime();

        // Process links.
        processLinks(getDelayedLinks(secs));

        frouting = System.nanoTime();

        log(secs, id, String.format(
                "Processed %d agents in %d ns (activities = %d ns; routing = %d ns)",
                routed,
                frouting - start,
                factivities - start,
                frouting - factivities));
        return routed;
    }

    public int time() { return this.secs; }
    public int id() { return this.id; }
    public Link[] links() { return this.links; }
    public ArrayList<ArrayList<Link>> delayedLinks() { return this.delayedLinksByWakeupTime; }
    public ArrayList<ArrayList<Agent>> delayedAgents() { return this.delayedAgentsByWakeupTime; }
    public QuickEvents events() { return events; }
}
