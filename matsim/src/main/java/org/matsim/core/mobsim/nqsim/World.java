package org.matsim.core.mobsim.nqsim;

import java.util.Map;

public class World {

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