package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.core.utils.quickevents.QuickEvents;

public class Realm {
	final private static Logger log = Logger.getLogger(Realm.class);

    // Identifier of the realm.
    private final int id;
    // Array of links onwer by this realm. 
    // Note 1: that outgoing are onwer by the source realm. 
    // Note 2: the id of the link is its index in the array.
    private final LinkInternal[] links;
    // Array of internal links onwer by this realm. Does not include outgoing
    // links owned by this realm.
    private final LinkInternal[] internalLinks;
    // A LinkBoundary is either an incomming or outgoing link. Each boundary
    // link contains the id of the link in the source realm. These are used to
    // regulate the communication between realms.
    private final LinkBoundary[] inLinks;
    private final LinkBoundary[] outLinks;
    // Agents on hold until a specific timestamp (in seconds).
    private final ArrayList<ArrayList<Agent>> delayedAgentsByWakeupTime;
    // Agents on hold waiting for a vehicle to arrive.
    // agentsInStops.get(route id).get(local stop id) -> arary of agents
    private final ArrayList<ArrayList<ArrayList<Agent>>> agentsInStops;
    // Event generation helper.
    private final QuickEvents events;

    // Current timestamp
    private int secs;
    private int routed;

    public Realm(int id, LinkInternal[] links, LinkBoundary[] inLinks, 
            LinkBoundary[] outLinks) throws Exception {
        this.id = id;
        this.links = links;
        this.inLinks = inLinks;
        this.outLinks = outLinks;
        this.internalLinks = setupInternalLinks();
        // The plus one is necessary because we peek into the next slot on each tick.
        this.delayedAgentsByWakeupTime = 
            new ArrayList<>(Collections.nCopies(World.SIM_STEPS + 1, null));
        this.agentsInStops = new ArrayList<>();
        events = new QuickEvents();
    }

    private LinkInternal[] setupInternalLinks() {
        Set<Integer> outLinkIds = new HashSet<>(outLinks.length);
        LinkInternal[] internalLinks = new LinkInternal[links.length - outLinks.length];
        int idx = 0;

        for (LinkBoundary lb: outLinks) {
            outLinkIds.add(lb.id());
        }

        for (int i = 0; i < links.length; i++) {
            if (!outLinkIds.contains(i)) {
                internalLinks[idx++] = links[i];
            }
        }

        return internalLinks;
    }

    public static boolean log(int time, int realmid, String s) {
        log.info(String.format("[ time = %d realm = %d ] %s", time, realmid, s));
        return true;
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
        LinkInternal next = links[linkid];
        if (next.push(secs, agent, velocity)) {
            events.registerPlannedEvent(
                agent.id, agent.planIndex + 1, agent.plan[agent.planIndex + 1]);
            agent.planIndex++;
            return true;
        } else {
            return false;
        }
    }

    protected boolean processAgentSleepFor(Agent agent, int sleep) {
        return processAgentSleepUntil(agent, secs + Math.max(1, sleep));
    }
    
    protected boolean processAgentSleepUntil(Agent agent, int sleep) {
        getDelayedAgents(sleep).add(agent);
        events.registerPlannedEvent(
                agent.id, agent.planIndex+1, agent.plan[agent.planIndex + 1]);
        agent.planIndex++;
        return true;
    }

    protected boolean processAgentAccess(Agent agent, int routestop) {
        int routeid = Agent.getRoutePlanElement(routestop);
        int stopid = Agent.getStopPlanElement(routestop);
        agentsInStops.ensureCapacity(routeid - 1);
        ArrayList<ArrayList<Agent>> route = agentsInStops.get(routeid);
        if (route == null) {
            route = new ArrayList<>();
            agentsInStops.set(routeid, route);
        }
        route.ensureCapacity(stopid - 1);
        ArrayList<Agent> stop = route.get(stopid);
        if (stop == null) {
            stop = new ArrayList<>();
            route.set(stopid, stop);
        }
        stop.add(agent);
        return true;
    }

    protected boolean processAgentStop(Agent agent, int stopid) {
        int routeid = agent.route;
        for (Agent out : agent.egress(stopid)) {
            events.registerPlannedEvent(
                out.id, out.planIndex + 1, out.plan[out.planIndex + 1]);
            out.planIndex++;
            getDelayedAgents(secs + 1).add(out);
        }
        if (agentsInStops.size() <= routeid) {
            return true;
        }
        ArrayList<ArrayList<Agent>> route = agentsInStops.get(routeid);
        if (route == null) {
            return true;
        }
        if (route.size() <= stopid) {
            return true;
        }
        ArrayList<Agent> stop = route.get(stopid);
        if (stop == null) {
            return true;
        }
        for (Agent in : stop) {
            if (!agent.access(stopid, in)) {
                break;
            }
            events.registerPlannedEvent(
                in.id, in.planIndex + 1, in.plan[in.planIndex + 1]);
            in.planIndex++;
        }
        return true;
    }

    protected boolean processAgentRoute(Agent agent, int routeid) {
        agent.route(routeid);
        return true;
    }

    protected boolean processAgent(Agent agent) {
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
                log(secs, id, String.format("ERROR -> unknow plan element type %d",type));
        }
        //TODO: move plan incrementation here
        return false;

    }

    protected void processAgentActivities() {
        ArrayList<Agent> next = getDelayedAgents(secs + 1);
        for (Agent agent : getDelayedAgents(secs)) {
            if (agent.planIndex < (agent.plan.length - 1) && !processAgent(agent)) {
                next.add(agent);
            }
        }
    }

    protected void processInternalLinks() {
        for (LinkInternal link : internalLinks) {
            if (link.nexttime() > 0 && secs >= link.nexttime()) {
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
                link.nexttime(agent == null ? 0 : agent.linkFinishTime);
            }
        }
    }

    protected Map<LinkBoundary, ArrayList<Agent>> processOutgoingLinks() {
        Map<LinkBoundary, ArrayList<Agent>> outAgentsByBoundary = new HashMap<>();
        for (LinkBoundary blink : outLinks) {
            LinkInternal ilink = links[blink.id()];
            ArrayList<Agent> outgoing = new ArrayList<>();
            for (Agent agent : ilink.queue()) {
                if (agent.linkFinishTime > secs) {
                    break;
                } else {
                    outgoing.add(agent);
                }
            }
            outAgentsByBoundary.put(blink, outgoing);
        }
        return outAgentsByBoundary;
    }

    protected Map<Integer, Integer> processIngoingLinks(Map<Integer, ArrayList<Agent>> inAgentsByLinkId) {
        Map<Integer, Integer> routedAgentsByLinkId = new HashMap<>();
        for (Map.Entry<Integer, ArrayList<Agent>> entry : inAgentsByLinkId.entrySet()) {
            int localrouted = 0;
            for (Agent agent : entry.getValue()) {
                if (agent.planIndex >= (agent.plan.length - 1) || processAgent(agent)) {
                    localrouted++;
                    routed++;
                } else {
                    break;
                }
            }
            routedAgentsByLinkId.put(entry.getKey(), localrouted);
        }
        return routedAgentsByLinkId;
    }

    protected void processRemotellyRoutedAgents(Map<Integer, Integer> routedAgentsByLinkId) {
        for (Integer linkid : routedAgentsByLinkId.keySet()) {
            int counter = routedAgentsByLinkId.get(linkid);
            for (int i = 0; i < counter; i++) {
                links[linkid].pop();
            }
        }
    }

    // Updates all links and agents. Returns the number of routed agents.
    public int tick(int delta, Communicator comm) throws Exception {
        Map<Integer, Integer> routedAgentsByLinkId = new HashMap<>();
        long start, frouting = 0, fcomm = 0;
        routed = 0;
        secs += delta;

        start = System.currentTimeMillis();

        // Process agents waiting for something.
        processAgentActivities();

        // Process internal links.
        processInternalLinks();

        frouting = System.currentTimeMillis();

        // If we are running with 1 process only.
        if (comm != null) {

            // Send outgoing agents.
            comm.sendAgents(processOutgoingLinks());

            // Receive incomming agents.
            routedAgentsByLinkId = processIngoingLinks(comm.receiveAgents());

            // Wait for all sends to be complete.
            comm.waitSends();

            // Send locally rounted agents counters.
            comm.sendRoutedCounters(routedAgentsByLinkId);

            // Receive number of agents routed remotelly.
            processRemotellyRoutedAgents(comm.receiveRoutedCounters());

            // Wait for all sends to be complete.
            comm.waitSends();
        }

        events.tick();

        fcomm = System.currentTimeMillis();

//        log(secs, id, String.format(
//                "Processed %d agents in %d ms (routing = %d ms; comm = %d ms)",
//                routed,
//                fcomm - start,
//                frouting - start,
//                fcomm - frouting));
        return routed;
    }

    public int time() { return this.secs; }
    public int id() { return this.id; }
    public LinkInternal[] links() { return this.links; }
    public LinkBoundary[] inLinks() { return this.inLinks; }
    public LinkBoundary[] outLinks() { return this.outLinks; }
    public ArrayList<ArrayList<Agent>> delayedAgents() { return this.delayedAgentsByWakeupTime; }
    public QuickEvents events() { return events; }
}