package org.matsim.core.mobsim.nqsim;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;

public class World implements Serializable {

    private static final long serialVersionUID = 9220460296924471990L;
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

    public static void serialize(World world, String filename) throws Exception {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
        oos.writeObject(world);
        oos.close();
    }

    public static World deserialize(String filename) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename));
        World world = (World)ois.readObject();
        ois.close();
        return world;
    }
}