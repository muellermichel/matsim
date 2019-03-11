package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;

public class Realm {
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
    // TODO - change to stop and line -> agents
    private final ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> agentsInStops;
    // Matsim events.
    private final ScenarioImporter scenario;

    // Current timestamp
    private int secs;

    public Realm(
            Link[] links,
            ArrayList<ConcurrentLinkedQueue<Link>> delayedLinksByWakeupTime,
            ArrayList<ConcurrentLinkedQueue<Agent>> delayedAgentsByWakeupTime,
            ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> agentsInStops,
            ScenarioImporter scenario) throws Exception {
        this.links = links;
        // The plus one is necessary because we peek into the next slot on each tick.
        this.delayedLinksByWakeupTime = delayedLinksByWakeupTime;
        this.delayedAgentsByWakeupTime = delayedAgentsByWakeupTime;
        this.agentsInStops = agentsInStops;
        this.scenario = scenario;
    }

    public static void log(int time, String s) {
        if (World.DEBUG_REALMS) {
            System.out.println(String.format("ETHZ [ time = %d ] %s", time, s));
        }
    }

    private void add_delayed_agent(Agent agent, int until) {
        log(secs, String.format("agent %d delayed until %d", agent.id, until));
        delayedAgentsByWakeupTime.get(until).add(agent);
    }

    private void add_delayed_link(Link link, int until) {
        log(secs, String.format("link %d delayed until %d", link.id(), until));
        delayedLinksByWakeupTime.get(until).add(link);
    }

    private void advanceAgent(Agent agent) {
        long centry = agent.currPlan();
        log(secs, String.format("agent %d finished %s", agent.id, Agent.toString(centry)));
        agent.planIndex++;
        long nentry = agent.currPlan();
        log(secs, String.format("agent %d starting %s", agent.id, Agent.toString(nentry)));
        // set time in agent's event.
        scenario.setEventTime(agent.id, Agent.getPlanEvent(nentry), secs);
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
        int routeid = Agent.getRoutePlanEntry(planentry);
        int stopid = Agent.getStopPlanEntry(planentry);
        agentsInStops.get(routeid).get(stopid).add(agent);
        advanceAgent(agent);
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

        // consume stop delay
        advanceAgent(agent);

        // drop agents
        for (Agent out : agent.egress(stopid)) {
            add_delayed_agent(out, secs + 1);
            // consume access, activate egress
            advanceAgent(out);
            // set driver in agent's event
            scenario.setEventVehicle(out.id, Agent.getPlanEvent(out.currPlan()), agent.id);

        }

        // take agents
        ConcurrentLinkedQueue<Agent> agents = agentsInStops.get(routeid).get(stopid);
        ArrayList<Agent> removed = new ArrayList<>();
        for (Agent in : agents ) {
            if (!agent.access(in)) {
                break;
            }
            removed.add(in);
            // consume wait in stop, activate access
            advanceAgent(in);
            // set driver in agent's event
            scenario.setEventVehicle(in.id, Agent.getPlanEvent(in.currPlan()), agent.id);
        }
        agents.removeAll(removed);

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
            scenario.setLastEventTime(agent.id, secs);
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
                scenario.setLastEventTime(agent.id, secs);
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
                        log(secs, String.format("Processing agent %d", agent.id));
                        routed += processAgentActivities(agent);
                    }
                    while ((link = delayedLinksByWakeupTime.get(secs).poll()) != null) {
                        log(secs, String.format("Processing link %d", link.id()));
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
