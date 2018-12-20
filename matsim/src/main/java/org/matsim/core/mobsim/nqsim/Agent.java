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
    // <32 bit header><32 bit payload>
    // Possible headers (binary format) and corresponding payload:
    // <000> Link            | 24 bit link id  | 8 bit velocity
    // <001> Sleep for       | 32 bit sleep for a number of second
    // <010> Sleep until     | 32 bit speep until a specific time
    // <011> Vehicle Access  | 24 bit route id | 8 bit local station id
    // <100> Vehicle Egress  | 32 bit local station id.
    // <101> Set Route       | 32 bit route id.
    // <110> Stop            | 32 bit local stop id.
    protected final long[] plan;

    // Current position in plan.
    protected int planIndex;

     // Timestamp of when the agent will be ready to exit link.
    protected int linkFinishTime;

    // Number of passagers that this agent can take (zero for personal vehicles)
    private int capacity;

    // Number of passegers that are currently being transported.
    private int passagersInside;

    // Array of passagers on this vehicle.
    private ArrayList<ArrayList<Agent>> passagersByStop;

    // TODO - implement sharing cars through vehicle tokens

    public Agent(int id, long[] plan) { 
        this.id = id;
        this.plan = plan;
    }
    public Agent(int id, int capacity, long[] plan) {
        this(id, plan);
        this.capacity = capacity;
        this.passagersByStop = new ArrayList<>(World.MAX_LOCAL_STOPID);
        for(int i = 0; i < World.MAX_LOCAL_STOPID; i++) {
            this.passagersByStop.add(new ArrayList<>());
        }
    }

    public int id() { return this.id; }
    public int capacity() { return this.capacity; }
    public int route() { return this.route; }
    public void route(int route) { this.route = route; }
    public int linkFinishTime() { return this.linkFinishTime; }
    public int planIndex() { return this.planIndex; }
    public long[] plan() { return this.plan; }
    public long currPlan() { return this.plan[planIndex]; }
    public int getPlanElementHeader() { return getPlanHeader(this.plan[this.planIndex]); }
    public int getPlanElement() { return getPlanElement(this.plan[this.planIndex]); }
    public static int getPlanElement(long plan) { return (int)plan; }
    public static int getPlanHeader(long plan) { return ((int)(plan >> 32)); }
    public void planIndex(int index) { this.planIndex = index; }

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
                getStopPlanElement(getPlanElement(agent.plan[agent.planIndex + 2]));
            passagersByStop.get(stopid).add(agent);
            passagersInside++;
            return true;
        }
    }

    public static int getLinkPlanElement(int element) {
        return element >> 8;
    }

    public static int getVelocityPlanElement(int element) {
        return element & 0x0000000FF;
    }

    public static int getRoutePlanElement(int element) {
        return element >> 8;
    }
    
    public static int getStopPlanElement(int element) {
        return element & 0x0000000FF;
    }

    public static long prepareLinkElement(int linkid, int velocity) {
        if (velocity > World.MAX_VEHICLE_VELOCITY &&
            velocity != Integer.MAX_VALUE) {
            throw new RuntimeException(
                String.format("velocity above limit: %d", velocity));
        }
        int element = (linkid << 8) | Math.min(velocity, World.MAX_VEHICLE_VELOCITY);
        //int element = linkid;
        return preparePlanElement(LinkType, element);
    }

    public static long prepareSleepForElement(int element) {
        return preparePlanElement(SleepForType, element);
    }

    public static long prepareSleepUntilElement(int element) {
        return preparePlanElement(SleepUntilType, element);
    }
    public static long prepareAccessElement(int routeid, int stopid) {
        if (stopid > World.MAX_LOCAL_STOPID) {
            throw new RuntimeException(
                String.format("stopid above limit: %d", stopid));
        }
        int element = (routeid << 8) | stopid;
        return preparePlanElement(AccessType, element);
    }

    public static long prepareEgressElement(int stopid) {
        return preparePlanElement(EgressType, stopid);
    }

    public static long prepareRouteElement(int routeid) {
        return preparePlanElement(RouteType, routeid);
    }

    public static long prepareStopElement(int stopid) {
        return preparePlanElement(StopType, stopid);
    }

    public static long preparePlanElement(long type, long element) {
        return (type << 32) | element;
    }

    public static String toString(long planEntry) {
        int element = Agent.getPlanElement(planEntry);
        int type = Agent.getPlanHeader(planEntry);
        switch (type) {
            case Agent.LinkType:        
                return String.format("type=link; link=%d; vel=%d", 
                    getLinkPlanElement(element), getVelocityPlanElement(element));
            case Agent.SleepForType:
                return String.format("type=sleepfor; sleep=%d", element);
            case Agent.SleepUntilType:
                return String.format("type=sleepuntil; sleep=%d", element);
            case Agent.AccessType:
                return String.format("type=access; route=%d stopid=%d", 
                    getRoutePlanElement(element), getStopPlanElement(element));
            case Agent.StopType:
                return String.format("type=stop; stopid=%d", element);
            case Agent.EgressType:
                return String.format("type=egress; stopid=%d", element);
            case Agent.RouteType:
                return String.format("type=route; routeid=%d", element);
            default:
                return String.format("unknow plan type %d", type);
        }

    }

}