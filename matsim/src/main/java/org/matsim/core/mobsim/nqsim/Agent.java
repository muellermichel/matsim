package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;

import org.matsim.core.mobsim.nqsim.World;

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
    // <16 bit header><16 bit event id><32 bit payload>
    // Possible headers (binary format) and corresponding payload:
    // <0000> SleepForType    | 16 bit event id | 32 bit sleep for a number of second
    // <0001> SleepUntilType  | 16 bit event id | 32 bit speep until a specific time
    // <0010> LinkType        | 16 bit event id | 24 bit link id  | 8 bit velocity
    // <0011> AccessType      | 16 bit event id | 24 bit route id | 8 bit local station id
    // <0100> EgressType      | 16 bit event id | 24 bit route id | 8 bit local station id
    // <0101> StopArriveType  | 16 bit event id | 24 bit route id | 8 bit local station id
    // <0110> StopDepartType  | 16 bit event id | 24 bit route id | 8 bit local station id
    // <0111> WaitType        | 16 bit event id | 24 bit route id | 8 bit local station id
    // <1000> StopDelayType   | 16 bit event id | 24 bit route id | 8 bit local station id
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
        this.passagersByStop = new ArrayList<>(World.MAX_LOCAL_STOPID + 1);
        for(int i = 0; i < World.MAX_LOCAL_STOPID  + 1; i++) {
            this.passagersByStop.add(new ArrayList<>());
        }
    }

    public int id() { return this.id; }
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
        if (linkid > World.MAX_LINK_ID) {
            throw new RuntimeException("exceeded maximum number of links");
        }
        velocity = Math.min(velocity, World.MAX_VEHICLE_VELOCITY); // TODO - check we can get rid of this
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

    public static long prepareEgressEntry(int eventid, int routeid, int stopid) {
        if (stopid > World.MAX_LOCAL_STOPID) {
            throw new RuntimeException(
                String.format("stopid above limit: %d", stopid));
        }
        int element = (routeid << 8) | stopid;
        return preparePlanEntry(EgressType, eventid, element);
    }

    public static long prepareWaitEntry(int eventid, int routeid, int stopid) {
        if (stopid > World.MAX_LOCAL_STOPID) {
            throw new RuntimeException(
                String.format("stopid above limit: %d", stopid));
        }
        int element = (routeid << 8) | stopid;
        return preparePlanEntry(WaitType, eventid, element);
    }

    public static long prepareStopArrivalEntry(int eventid, int routeid, int stopid) {
        if (stopid > World.MAX_LOCAL_STOPID) {
            throw new RuntimeException(
                String.format("stopid above limit: %d", stopid));
        }
        int element = (routeid << 8) | stopid;
        return preparePlanEntry(StopArriveType, eventid, element);
    }

    public static long prepareStopDelayEntry(int eventid, int routeid, int stopid) {
        if (stopid > World.MAX_LOCAL_STOPID) {
            throw new RuntimeException(
                String.format("stopid above limit: %d", stopid));
        }
        int element = (routeid << 8) | stopid;
        return preparePlanEntry(StopDelayType, eventid, element);
    }

    public static long prepareStopDepartureEntry(int eventid, int routeid, int stopid) {
        if (stopid > World.MAX_LOCAL_STOPID) {
            throw new RuntimeException(
                String.format("stopid above limit: %d", stopid));
        }
        int element = (routeid << 8) | stopid;
        return preparePlanEntry(StopDepartType, eventid, element);
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
                return String.format("type=sleepfor; event=%d; sleep=%d",
                    event, element);
            case Agent.SleepUntilType:
                return String.format("type=sleepuntil; event=%d; sleep=%d",
                    event, element);
            case Agent.AccessType:
                return String.format("type=access; event=%d; route=%d stopid=%d",
                    event, getRoutePlanEntry(element), getStopPlanEntry(element));
            case Agent.StopArriveType:
                return String.format("type=stoparrive; event=%d; route=%d stopid=%d",
                    event, getRoutePlanEntry(element), getStopPlanEntry(element));
            case Agent.StopDelayType:
                return String.format("type=stopdelay; event=%d; route=%d stopid=%d",
                    event, getRoutePlanEntry(element), getStopPlanEntry(element));
            case Agent.StopDepartType:
                return String.format("type=stopdepart; event=%d; route=%d stopid=%d",
                    event, getRoutePlanEntry(element), getStopPlanEntry(element));
            case Agent.EgressType:
                return String.format("type=egress; event=%d; route=%d stopid=%d",
                    event, getRoutePlanEntry(element), getStopPlanEntry(element));
            case Agent.WaitType:
                return String.format("type=wait; event=%d; route=%d stopid=%d",
                    event, getRoutePlanEntry(element), getStopPlanEntry(element));
            default:
                return String.format("unknow plan type %d", type);
        }

    }

}
