package org.matsim.core.mobsim.hermes;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.vehicles.Vehicle;

public class Realm {
    // Global array of links.
    // Note: the id of the link is its index in the array.
    private final Link[] links;
    // Internal realm links on hold until a specific timestamp (in seconds).
    // Internal means that the source and destination realm of are the same.
    private final ArrayList<ConcurrentLinkedQueue<Link>> delayedLinksByWakeupTime;
    // Agents on hold until a specific timestamp (in seconds).
    private final ArrayList<ConcurrentLinkedQueue<Agent>> delayedAgentsByWakeupTime;
    // Agents waiting in pt stations. Should be used as follows:
    // nqsim_stops.get(curr station id).get(line id).get(dst station id) -> queue of agents
    private final ArrayList<ArrayList<Map<Integer, ConcurrentLinkedQueue<Agent>>>> agent_stops;
    // Get the matsim id for an agent. Should be indexed by agent id.
    private final Map<Integer, String> matsim_agent_id; // TODO - transform into array!
    // stop ids per route id
    private final ArrayList<ArrayList<Integer>> stops_in_route;
    // line id of a particular route
    private final ArrayList<Integer> line_of_route;
    // events indexed by agent id and by event id
    private final ArrayList<ArrayList<Event>> events;

    // Current timestamp
    private int secs;

    public Realm(ScenarioImporter scenario) throws Exception {
        this.links = scenario.qsim_links;
        // The plus one is necessary because we peek into the next slot on each tick.
        this.delayedLinksByWakeupTime = new ArrayList<>();
        this.delayedAgentsByWakeupTime = new ArrayList<>();
        this.agent_stops = scenario.agent_stops;
        this.stops_in_route = scenario.route_stops_by_index;
        this.line_of_route = scenario.line_of_route;
        this.events = scenario.matsim_events;
        this.matsim_agent_id = scenario.nqsim_to_matsim_Agent;

        for (int i = 0; i < Hermes.MAX_SIM_STEPS + 1; i++) {
            delayedLinksByWakeupTime.add(new ConcurrentLinkedQueue<>());
            delayedAgentsByWakeupTime.add(new ConcurrentLinkedQueue<>());
        }
    }

    public static void log(int time, String s) {
        if (Hermes.DEBUG_REALMS) {
            System.out.println(String.format("ETHZ [ time = %d ] %s", time, s));
        }
    }

    private void add_delayed_agent(Agent agent, int until) {
        if (Hermes.DEBUG_REALMS) log(secs, String.format("agent %d delayed until %d", agent.id, until));
        delayedAgentsByWakeupTime.get(until).add(agent);
    }

    private void add_delayed_link(Link link, int until) {
        if (Hermes.DEBUG_REALMS) log(secs, String.format("link %d delayed until %d", link.id(), until));
        delayedLinksByWakeupTime.get(until).add(link);
    }

    private void advanceAgent(Agent agent) {
        long centry = agent.currPlan();
        if (Hermes.DEBUG_REALMS) log(secs, String.format("agent %d finished %s", agent.id, Agent.toString(centry)));
        agent.planIndex++;
        long nentry = agent.currPlan();
        if (Hermes.DEBUG_REALMS) log(secs, String.format("agent %d starting %s", agent.id, Agent.toString(nentry)));
        // set time in agent's event.
        setEventTime(agent.id, Agent.getPlanEvent(nentry), secs);
    }

    protected boolean processAgentLink(Agent agent, long planentry, int currLinkId) {
        int linkid = Agent.getLinkPlanEntry(planentry);
        int velocity = Agent.getVelocityPlanEntry(planentry);
        Link next = links[linkid];
        int prev_finishtime = agent.linkFinishTime;
        int prev_starttime = agent.linkStartTime;
        // this ensures that if no velocity is provided for the vehicle, we use the link
        velocity = velocity == 0 ? next.velocity() : velocity;
        // the max(1, ...) ensures that a link hop takes at least on step.
        agent.linkFinishTime =
            secs +
            Hermes.LINK_ADVANCE_DELAY +
            Math.max(1, next.length() / Math.min(velocity, next.velocity()));
        agent.linkStartTime = secs;
        if (next.push(agent)) {
            advanceAgent(agent);
            // If the agent we just added is the head, add to delayed links
            if (currLinkId != next.id() && next.queue().peek() == agent) {
                add_delayed_link(next, Math.max(agent.linkFinishTime, secs + 1));
            }
            return true;
        } else {
            agent.linkFinishTime = prev_finishtime;
            agent.linkStartTime = prev_starttime;
            return false;
        }
    }

    protected boolean processAgentSleepFor(Agent agent, long planentry) {
        int sleep = Agent.getSleepPlanEntry(planentry);
        return processAgentSleepUntil(agent, secs + Math.max(1, sleep));
    }

    protected boolean processAgentSleepUntil(Agent agent, long planentry) {
        int sleep = Agent.getSleepPlanEntry(planentry);
        add_delayed_agent(agent, Math.max(sleep, secs + 1));
        advanceAgent(agent);
        return true;
    }

    protected boolean processAgentWait(Agent agent, long planentry) {
        advanceAgent(agent);
        int routeid = Agent.getRoutePlanEntry(planentry);
        int accessStop = Agent.getStopPlanEntry(planentry);
        // Note: getNextStop needs to be called after advanveAgent.
        int egressStop = agent.getNextStopPlanEntry();
        int lineid = line_of_route.get(routeid);
        agent_stops.get(accessStop).get(lineid).get(egressStop).add(agent);
        return true;
    }

    protected boolean processAgentStopArrive(Agent agent, long planentry) {
        add_delayed_agent(agent, secs + 1);
        advanceAgent(agent);
        // Although we want the agent to be processed in the next tick, we
        // return true to remove the vehicle from the link that it is currently.
        return true;
    }

    protected boolean processAgentStopDelay(Agent agent, long planentry) {
        int routeid = Agent.getRoutePlanEntry(planentry);
        int stopid = Agent.getStopPlanEntry(planentry);
        int stopidx = Agent.getStopIndexPlanEntry(planentry);
        int lineid = line_of_route.get(routeid);
        ArrayList<Integer> next_stops = stops_in_route.get(routeid);
        Map<Integer, ConcurrentLinkedQueue<Agent>> agents_next_stops =
            agent_stops.get(stopid).get(lineid);

        // consume stop delay
        advanceAgent(agent);

        // drop agents
        for (Agent out : agent.egress(stopidx)) {
            add_delayed_agent(out, secs + 1);
            // consume access, activate egress
            advanceAgent(out);
            // set driver in agent's event
            setEventVehicle(out.id, Agent.getPlanEvent(out.currPlan()), agent.id);
        }

        // take agents
        for (int idx = stopidx; idx < next_stops.size(); idx++) {
            ConcurrentLinkedQueue<Agent> in_agents = agents_next_stops.get(next_stops.get(idx));

            if (in_agents == null) {
                continue;
            }

            ArrayList<Agent> removed = new ArrayList<>();
            for (Agent in : in_agents) {
                if (!agent.access(idx, in)) {
                    break;
                }
                removed.add(in);
                // consume wait in stop, activate access
                advanceAgent(in);
                // set driver in agent's event
                setEventVehicle(in.id, Agent.getPlanEvent(in.currPlan()), agent.id);
            }
            in_agents.removeAll(removed);
        }

        // False is returned to force this agent to be processed in the next tick.
        // This will mean that the vehicle will be processed in the next tick.
        return false;
    }

    protected boolean processAgentStopDepart(Agent agent, long planentry) {
        advanceAgent(agent);
        // False is returned to force this agent to be processed in the next tick.
        // This will mean that the vehicle will be processed in the next tick.
        return false;
    }

    protected boolean processAgent(Agent agent, int currLinkId) {
        // Peek the next plan element and try to execute it.
        long planentry = agent.plan[agent.planIndex + 1];
        int type = Agent.getPlanHeader(planentry);
        switch (type) {
            case Agent.LinkType:        return processAgentLink(agent, planentry, currLinkId);
            case Agent.SleepForType:    return processAgentSleepFor(agent, planentry);
            case Agent.SleepUntilType:  return processAgentSleepUntil(agent, planentry);
            case Agent.StopArriveType:  return processAgentStopArrive(agent, planentry);
            case Agent.StopDelayType:   return processAgentStopDelay(agent, planentry);
            case Agent.StopDepartType:  return processAgentStopDepart(agent, planentry);
            case Agent.WaitType:        return processAgentWait(agent, planentry);
            case Agent.AccessType:      // The access event is consumed in the stop.
            case Agent.EgressType:      // The egress event is consumed in the stop.
            default:
                throw new RuntimeException(String.format(
                    "unknow plan element type %d, agent %d plan index %d",
                    type, agent.id, agent.planIndex + 1));
        }
    }

    protected int processAgentActivities(Agent agent) {
        boolean finished = agent.finished();
        // if finished, install times on last event.
        if (finished) {
            setLastEventTime(agent.id, secs);
        }
        // -1 is used in the processAgent because the agent is not in a link currently.
        if (!finished && !processAgent(agent, -1)) {
            add_delayed_agent(agent, secs + 1);
            return 0;
        }
        return 1;
    }

    protected int processLinks(Link link) {
        int routed = 0;
        Agent agent = link.queue().peek();

        while (agent.linkFinishTime <= secs) {
            boolean finished = agent.finished();
            // if finished, install times on last event.
            if (finished) {
                setLastEventTime(agent.id, secs);
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
        if (agent != null) {
            add_delayed_link(link, Math.max(agent.linkFinishTime, secs + 1));
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
                        if (Hermes.DEBUG_REALMS) log(secs, String.format("Processing agent %d", agent.id));
                        routed += processAgentActivities(agent);
                    }
                    while ((link = delayedLinksByWakeupTime.get(secs).poll()) != null) {
                        if (Hermes.DEBUG_REALMS) log(secs, String.format("Processing link %d", link.id()));
                        routed += processLinks(link);
                    }

                    if (routed > 0) {
                        if (Hermes.DEBUG_REALMS) log(secs, String.format("Thread %s Processed %d agents", id, routed));
                    }
                    routed = 0;
                }

                @Override
                public void run() {
                    try {
                        while (secs != Hermes.SIM_STEPS) {
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

    public void setEventTime(int agentid, int eventid, int time) {
        if (eventid != 0) {
            events.get(agentid).get(eventid).setTime(time);
        }
    }

    public void setLastEventTime(int agentid, int time) {
        int nevents = events.get(agentid).size();
        setEventTime(agentid, nevents - 1, time);
    }

    public void setEventVehicle(int agentid, int eventid, int vehicleid) {
        if (eventid != 0) {
            Event event = events.get(agentid).get(eventid);
            Id<Vehicle> vid = Id.createVehicleId(matsim_agent_id.get(vehicleid));
            if (event instanceof PersonEntersVehicleEvent) {
                ((PersonEntersVehicleEvent)event).setVehicleId(vid);
            } else if (event instanceof PersonLeavesVehicleEvent) {
                ((PersonLeavesVehicleEvent)event).setVehicleId(vid);
            } else {
                throw new RuntimeException(
                    String.format("vehicle id could not be set for event: %d", eventid));
            }
        }
    }

    public int time() { return this.secs; }
    public Link[] links() { return this.links; }
    public ArrayList<ConcurrentLinkedQueue<Link>> delayedLinks() { return this.delayedLinksByWakeupTime; }
    public ArrayList<ConcurrentLinkedQueue<Agent>> delayedAgents() { return this.delayedAgentsByWakeupTime; }
}
