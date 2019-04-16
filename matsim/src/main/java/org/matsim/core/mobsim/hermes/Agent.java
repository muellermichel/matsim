package org.matsim.core.mobsim.hermes;

import java.util.ArrayList;

import org.matsim.core.mobsim.hermes.Hermes;

public class Agent {

    // Types of plan headers.
    // agent sleeps for some time.
    public static final int SleepForType   =  0;
    // agent sleeps until a specific time of the day
    public static final int SleepUntilType =  1;
    // agent goes through some link
    public static final int LinkType       =  2;
    // non-vehicle agent enters a vehicle agent
    public static final int AccessType     =  3;
    // non-vehicle agent leaves a vehicle agent
    public static final int EgressType     =  4;
    // vehicle agent arrives at a PT stop
    public static final int StopArriveType =  5;
    // vehicle agent leaves at a PT stop
    public static final int StopDepartType =  6;
    // non-vehicle agent waits for vehicle agent at PT stop
    public static final int WaitType       =  7;
    // vehicle agent waits at the PT stop until it can leave the stop
    public static final int StopDelayType  =  8;

    // Id of the link (index for World.agents).
    protected final int id;

    // Array of plan elements. A plan element has the following structure:
    // <8 bit header><16 bit event id><40 bit payload>
    // Possible headers (binary format) and corresponding payload:
    // <0000> SleepForType    | 16 bit event id | 8 bits unused   | 32 bit sleep for a number of second
    // <0001> SleepUntilType  | 16 bit event id | 8 bits unused   | 32 bit speep until a specific time
    // <0010> LinkType        | 16 bit event id | 32 bit link id  | 8 bit velocity
    // <0111> WaitType        | 16 bit event id | 8 bits unused   | 16 bit route id | 16 station id
    // <0011> AccessType      | 16 bit event id | 8 bits unused   | 16 bit route id | 16 station id
    // <0100> EgressType      | 16 bit event id | 8 bits unused   | 16 bit route id | 16 station id
    // <0101> StopArriveType  | 16 bit event id | 8 station idx   | 16 bit route id | 16 station id
    // <1000> StopDelayType   | 16 bit event id | 8 station idx   | 16 bit route id | 16 station id
    // <0110> StopDepartType  | 16 bit event id | 8 station idx   | 16 bit route id | 16 station id
    protected final long[] plan;

    // Current position in plan. Using this index in the plan will yield what
    // the agent is doing currently. Note that we trigger the corresponding
    // events when the plan entry is activated.
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
        this.passagersByStop = new ArrayList<>(Hermes.MAX_STOP_IDX + 1);
        for(int i = 0; i < Hermes.MAX_STOP_IDX  + 1; i++) {
            this.passagersByStop.add(new ArrayList<>());
        }
    }

    public int id() { return this.id; }
    public int linkFinishTime() { return this.linkFinishTime; }
    public int planIndex() { return this.planIndex; }
    public long[] plan() { return this.plan; }
    public long currPlan() { return this.plan[planIndex]; }
    public boolean finished() { return planIndex >= (plan.length - 1); }
    public int capacity() { return this.capacity; }

    public ArrayList<Agent> egress(int stopidx) {
        ArrayList<Agent> ret = passagersByStop.get(stopidx);
        passagersInside -= ret.size();
        passagersByStop.set(stopidx, new ArrayList<>());
        return ret;
    }

    public boolean access(int stopidx, Agent agent) {
        if (passagersInside == capacity) {
            return false;
        } else {
            passagersByStop.get(stopidx).add(agent);
            passagersInside++;
            return true;
        }
    }

    public int getNextStopPlanEntry() {
        // TODO - install assert checking if the next entry is an egress?
        // +2 is used to peek where the agent wants to leave the vehicle.
        // +1 is the access plan element which was not yet consumed.
        return getStopPlanEntry(plan[planIndex + 2]);
    }

    public static int getPlanHeader         (long plan) { return (int)((plan >> 56) & 0x00000000000000FFl); }
    public static int getPlanEvent          (long plan) { return (int)((plan >> 40) & 0x000000000000FFFFl); }
    public static int getLinkPlanEntry      (long plan) { return (int)((plan >>  8) & 0x00000000FFFFFFFFl); }
    public static int getVelocityPlanEntry  (long plan) { return (int)( plan        & 0x00000000000000FFl); }
    public static int getRoutePlanEntry     (long plan) { return (int)((plan >> 16) & 0x000000000000FFFFl); }
    public static int getStopPlanEntry      (long plan) { return (int)( plan        & 0x000000000000FFFFl); }
    public static int getStopIndexPlanEntry (long plan) { return (int)( plan >> 32  & 0x00000000000000FFl); }
    public static int getSleepPlanEntry     (long plan) { return (int)( plan        & 0x00000000FFFFFFFFl); }

    private static void validatePlanEntry(long planEntry) {
        int event = Agent.getPlanEvent(planEntry);
        int type = Agent.getPlanHeader(planEntry);
        switch (type) {
            case Agent.LinkType:
            case Agent.SleepForType:
            case Agent.SleepUntilType:
            case Agent.AccessType:
            case Agent.StopArriveType:
            case Agent.StopDelayType:
            case Agent.StopDepartType:
            case Agent.EgressType:
            case Agent.WaitType:
                break; // TODO - add more verification for each field!
            default:
                throw new RuntimeException("planEntry does not validate " + planEntry);
        }
    }

    public static long preparePlanEntry(long type, long eventid, long element) {
        long planEntry = (type << 56) | (eventid << 40) | element;
        if (Hermes.DEBUG_EVENTS) {
            validatePlanEntry(planEntry);
        }
        return planEntry;
    }

    private static long prepapreLinkEntryElement(long linkid, long velocity) {
        if (linkid > Hermes.MAX_LINK_ID) {
            throw new RuntimeException("exceeded maximum number of links");
        }

        // Checking for velocities that are too high.
        velocity = Math.min(velocity, Hermes.MAX_VEHICLE_VELOCITY);

        // Checking for velocities that are too low.
        velocity = velocity < 0 ? Hermes.MAX_VEHICLE_VELOCITY : velocity;

        return (linkid << 8) | velocity;
    }

    private static long prepareRouteStopEntry(long routeid, long stopid, long stopidx) {
        if (stopid > Hermes.MAX_STOP_ROUTE_ID) {
            throw new RuntimeException(String.format("stopid above limit: %d", stopid));
        }
        if (routeid > Hermes.MAX_STOP_ROUTE_ID) {
            throw new RuntimeException(String.format("routeid above limit: %d", routeid));
        }
        if (stopidx > Hermes.MAX_STOP_IDX) {
            throw new RuntimeException(String.format("station index above limit: %d", stopidx));
        }
        return (stopidx << 32) | (routeid << 16) | stopid;
    }

    public static long prepareLinkEntry(int eventid, int linkid, int velocity) {
        return preparePlanEntry(LinkType, eventid, prepapreLinkEntryElement(linkid, velocity));
    }

    public static long prepareSleepForEntry(int eventid, int element) {
        return preparePlanEntry(SleepForType, eventid, element);
    }

    public static long prepareSleepUntilEntry(int eventid, int element) {
        return preparePlanEntry(SleepUntilType, eventid, element);
    }

    public static long prepareAccessEntry(int eventid, int routeid, int stopid) {
        return preparePlanEntry(AccessType, eventid, prepareRouteStopEntry(routeid, stopid, 0));
    }

    public static long prepareEgressEntry(int eventid, int routeid, int stopid) {
        return preparePlanEntry(EgressType, eventid, prepareRouteStopEntry(routeid, stopid, 0));
    }

    public static long prepareWaitEntry(int eventid, int routeid, int stopid) {
        return preparePlanEntry(WaitType, eventid, prepareRouteStopEntry(routeid, stopid, 0));
    }

    public static long prepareStopArrivalEntry(int eventid, int routeid, int stopid, int stopidx) {
        return preparePlanEntry(StopArriveType, eventid, prepareRouteStopEntry(routeid, stopid, stopidx));
    }

    public static long prepareStopDelayEntry(int eventid, int routeid, int stopid, int stopidx) {
        return preparePlanEntry(StopDelayType, eventid, prepareRouteStopEntry(routeid, stopid, stopidx));
    }

    public static long prepareStopDepartureEntry(int eventid, int routeid, int stopid, int stopidx) {
        return preparePlanEntry(StopDepartType, eventid, prepareRouteStopEntry(routeid, stopid, stopidx));
    }

    public static String toString(long planEntry) {
        int event = Agent.getPlanEvent(planEntry);
        int type = Agent.getPlanHeader(planEntry);
        switch (type) {
            case Agent.LinkType:
                return String.format("type=link; event=%d; link=%d; vel=%d",
                    event, getLinkPlanEntry(planEntry), getVelocityPlanEntry(planEntry));
            case Agent.SleepForType:
                return String.format("type=sleepfor; event=%d; sleep=%d",
                    event, getSleepPlanEntry(planEntry));
            case Agent.SleepUntilType:
                return String.format("type=sleepuntil; event=%d; sleep=%d",
                    event, getSleepPlanEntry(planEntry));
            case Agent.AccessType:
                return String.format("type=access; event=%d; route=%d stopid=%d stopidx=%d",
                    event, getRoutePlanEntry(planEntry), getStopPlanEntry(planEntry), getStopIndexPlanEntry(planEntry));
            case Agent.StopArriveType:
                return String.format("type=stoparrive; event=%d; route=%d stopid=%d stopidx=%d",
                    event, getRoutePlanEntry(planEntry), getStopPlanEntry(planEntry), getStopIndexPlanEntry(planEntry));
            case Agent.StopDelayType:
                return String.format("type=stopdelay; event=%d; route=%d stopid=%d stopidx=%d",
                    event, getRoutePlanEntry(planEntry), getStopPlanEntry(planEntry), getStopIndexPlanEntry(planEntry));
            case Agent.StopDepartType:
                return String.format("type=stopdepart; event=%d; route=%d stopid=%d stopidx=%d",
                    event, getRoutePlanEntry(planEntry), getStopPlanEntry(planEntry), getStopIndexPlanEntry(planEntry));
            case Agent.EgressType:
                return String.format("type=egress; event=%d; route=%d stopid=%d stopidx=%d",
                    event, getRoutePlanEntry(planEntry), getStopPlanEntry(planEntry), getStopIndexPlanEntry(planEntry));
            case Agent.WaitType:
                return String.format("type=wait; event=%d; route=%d stopid=%d stopidx=%d",
                    event, getRoutePlanEntry(planEntry), getStopPlanEntry(planEntry), getStopIndexPlanEntry(planEntry));
            default:
                return String.format("unknow plan type %d", type);
        }

    }

}
