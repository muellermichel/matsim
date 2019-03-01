package org.matsim.core.mobsim.nqsim;

public class World {

    // Maximum number of links (limited to 24 bits in the plan)
    public static final int MAX_LINK_ID = 16777216;
    // Maximum number of stops in a link (limited to 8 bits in the plan)
    public static final int MAX_LOCAL_STOPID = 255;
    // Maximum vehicle velocity (limited to 8 bits in the plan)
    public static final int MAX_VEHICLE_VELOCITY = 255;
    // Maximum number of events per agent (limited to 16 bits in the plan)
    public static final int MAX_EVENTS_AGENT = 65536;
    // Maximum number of sim steps.
    public static final int MAX_SIM_STEPS = 7 * 60 * 60 * 24;
    // Number of simulation steps (7 day for now)
    // For test purposes, we are use 7 days instead of 36 hours
    public static final int SIM_STEPS = 60 * 60 * 24 * 7;
    // Number of ticks that are added to every agent advancing links.
    public static final int LINK_ADVANCE_DELAY = 1;

    public static final boolean DEBUG_REALMS = false;
    public static final boolean DEBUG_EVENTS = true;
    public static final boolean DUMP_AGENTS = false;

    // Number of threads used to run the simulator.
    public final int nrealms;
    // Reamls that compose this World.
    private final Realm[] realms;
    // Links within this World.
    private final Link[] links;
    // Agents that circulate within the World.
    private final Agent[] agents;

    public World(int nrealms, Realm[] realms, Link[] links, Agent[] agents) {
        this.nrealms = nrealms;
        this.realms = realms;
        this.links = links;
        this.agents = agents;
    }

    public int nrealms() {
        return this.nrealms;
    }

    public Realm realm(int id) {
        return realms[id];
    }

    public Realm[] realms() {
        return realms;
    }

    public Agent[] agents() {
        return agents;
    }

    public Link[] links() {
        return this.links;
    }
}
