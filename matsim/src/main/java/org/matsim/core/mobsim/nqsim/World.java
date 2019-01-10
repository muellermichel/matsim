package org.matsim.core.mobsim.nqsim;

import java.util.Map;

public class World {

    // Maximum number of stops in a link (limited to 8 bits in the plan)
    public static final int MAX_LOCAL_STOPID = 255;
    // Maximum vehicle velocity (limited to 8 bits in the plan)
    public static final int MAX_VEHICLE_VELOCITY = 255;
    // Number of simulation steps (7 day for now)
    public static final int ACT_SLOTS = 7 * 60 * 60 * 24;
    public static final int SIM_STEPS = 60 * 60 * 24;

    // Reamls that compose this World.
    private final Realm[] realms;
    // Agents that circulate within the World.
    private final Agent[] agents;
    // Conversion between LinkInternal and the global id of the link. Debug only.
    private final Map<LinkInternal, Integer> globalIdByLink;

    public World(
            Realm[] realms,
            Agent[] agents,
            Map<LinkInternal, Integer> globalIdByLink) {
        this.realms = realms;
        this.agents = agents;
        this.globalIdByLink = globalIdByLink;
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

    public Map<LinkInternal, Integer> globalIdByLink() {
        return globalIdByLink;
    }
}
