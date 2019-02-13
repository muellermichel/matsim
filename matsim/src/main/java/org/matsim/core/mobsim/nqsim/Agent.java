package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;

import org.matsim.core.mobsim.nqsim.World;

public class Agent {

    // Types of plan headers.
    public static final int LinkType       =  0;
    public static final int SleepForType   =  1;
    public static final int SleepUntilType =  2;
    public static final int AccessType     =  3;
    public static final int EgressType     =  4;
    public static final int RouteType      =  5;
    public static final int StopType       =  6;

    // Id of the link (index for World.agents).
    protected final int id;

    // Route number. Used if agent is vehicle driver.
    protected int route;

    // Array of plan elements. A plan element has the following structure:
    // <16 bit header><16 bit event id><32 bit payload>
    // Possible headers (binary format) and corresponding payload:
    // <000> Link            | 16 bit event id | 24 bit link id  | 8 bit velocity
    // <001> Sleep for       | 16 bit event id | 32 bit sleep for a number of second
    // <010> Sleep until     | 16 bit event id | 32 bit speep until a specific time
    // <011> Vehicle Access  | 16 bit event id | 24 bit route id | 8 bit local station id
    // <100> Vehicle Egress  | 16 bit event id | 32 bit local station id.
    // <101> Set Route       | 16 bit event id | 32 bit route id.
    // <110> Stop            | 16 bit event id | 32 bit local stop id.
    protected final long[] plan;

    // Current position in plan.
    protected int planIndex;

     // Timestamp of when the agent will be ready to exit link.
    protected int linkFinishTime;

    // Timestamp of when the agent entered the link.
    protected int linkStartTime;

    // Number of passagers that this agent can take (zero for personal vehicles)
    private int capacity;

    // Number of passegers that are currently being transported.
    private int passagersInside;

    // Array of passagers on this vehicle.
    private ArrayList<ArrayList<Agent>> passagersByStop;

    public Agent(int id, long[] plan) {
        this.id = id;
        this.plan = plan;
    }
    public Agent(int id, int capacity, long[] plan) {
        this(id, plan);
        this.capacity = capacity;
        this.passagersByStop = new ArrayList<>(World.MAX_LOCAL_STOPID + 1);
        for(int i = 0; i < World.MAX_LOCAL_STOPID  + 1; i++) {
            this.passagersByStop.add(new ArrayList<>());
        }
    }

    public int id() { return this.id; }
    public void route(int route) { this.route = route; }
    public int linkFinishTime() { return this.linkFinishTime; }
    public int planIndex() { return this.planIndex; }
    public long[] plan() { return this.plan; }
    public long currPlan() { return this.plan[planIndex]; }
    public static int getPlanPayload(long plan) { return (int)plan; }
    public static int getPlanHeader(long plan) { return ((int)(plan >> 48)); }
    public static int getPlanEvent(long plan) { return ((short)(plan >> 32)); }

    public ArrayList<Agent> egress(int stopid) {
        ArrayList<Agent> ret = passagersByStop.get(stopid);
        passagersInside -= ret.size();
        passagersByStop.set(stopid, new ArrayList<>());
        return ret;
    }

    public boolean access(Agent agent) {
        if (passagersInside == capacity) {
            return false;
        } else {
            // +2 is used to peek where the agent wants to leave the vehicle.
            // +1 is the access plan element which was not yet consumed.
            int stopid =
                getStopPlanEntry(getPlanPayload(agent.plan[agent.planIndex + 2]));
            passagersByStop.get(stopid).add(agent);
            passagersInside++;
            return true;
        }
    }

    public static int getLinkPlanEntry(int element) {
        return element >> 8;
    }

    public static int getVelocityPlanEntry(int element) {
        return element & 0x0000000FF;
    }

    public static int getRoutePlanEntry(int element) {
        return element >> 8;
    }

    public static int getStopPlanEntry(int element) {
        return element & 0x0000000FF;
    }

    public static long prepareLinkEntry(int eventid, int linkid, int velocity) {
        // TODO - fix this! It happens in the SBB scenario (Bus with 10Km/s)
        /*
        if (velocity > World.MAX_VEHICLE_VELOCITY &&
            velocity != Integer.MAX_VALUE) {
            throw new RuntimeException(
                String.format("velocity above limit: %d", velocity));
        }
        */
        if (linkid > World.MAX_LINK_ID) {
            throw new RuntimeException("exceeded maximum number of links");
        }
        velocity = Math.min(velocity, World.MAX_VEHICLE_VELOCITY);
        int element = (linkid << 8) | velocity;
        return preparePlanEntry(LinkType, eventid, element);
    }

    public static long prepareSleepForEntry(int eventid, int element) {
        return preparePlanEntry(SleepForType, eventid, element);
    }

    public static long prepareSleepUntilEntry(int eventid, int element) {
        return preparePlanEntry(SleepUntilType, eventid, element);
    }
    public static long prepareAccessEntry(int eventid, int routeid, int stopid) {
        if (stopid > World.MAX_LOCAL_STOPID) {
            throw new RuntimeException(
                String.format("stopid above limit: %d", stopid));
        }
        int element = (routeid << 8) | stopid;
        return preparePlanEntry(AccessType, eventid, element);
    }

    public static long prepareEgressEntry(int eventid, int stopid) {
        return preparePlanEntry(EgressType, eventid, stopid);
    }

    public static long prepareRouteEntry(int eventid, int routeid) {
        return preparePlanEntry(RouteType, eventid, routeid);
    }

    public static long prepareStopEntry(int eventid, int stopid) {
        return preparePlanEntry(StopType, eventid, stopid);
    }

    public static long preparePlanEntry(long type, long eventid, long element) {
        return (type << 48) | (eventid << 32) | element;
    }

    public static String toString(long planEntry) {
        int element = Agent.getPlanPayload(planEntry);
        int event = Agent.getPlanEvent(planEntry);
        int type = Agent.getPlanHeader(planEntry);
        switch (type) {
            case Agent.LinkType:
                return String.format("type=link; event=%d; link=%d; vel=%d",
                    event, getLinkPlanEntry(element), getVelocityPlanEntry(element));
            case Agent.SleepForType:
                return String.format("type=sleepfor; event=%d; sleep=%d", event, element);
            case Agent.SleepUntilType:
                return String.format("type=sleepuntil; event=%d; sleep=%d", event, element);
            case Agent.AccessType:
                return String.format("type=access; event=%d; route=%d stopid=%d",
                    event, getRoutePlanEntry(element), getStopPlanEntry(element));
            case Agent.StopType:
                return String.format("type=stop; event=%d; stopid=%d", event, element);
            case Agent.EgressType:
                return String.format("type=egress; event=%d; stopid=%d", event, element);
            case Agent.RouteType:
                return String.format("type=route; event=%d; routeid=%d", event, element);
            default:
                return String.format("unknow plan type %d", type);
        }

    }

}
