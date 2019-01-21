package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;

import org.apache.log4j.Logger;

public class WorldDumper {
    
    final private static Logger log = Logger.getLogger(WorldDumper.class);

    public static boolean dumpWorld(World world) {
        log.info("Dumping world...");
        dumpRealms(world.realms());
        dumpLinks(world.links());
        dumpAgents(world.agents());
        log.info("Finished dumping world.");
        return true;
    }

    public static void dumpRealms(Realm[] realms) {
        for (Realm realm : realms) {
            dumpRealm(realm);
        }
    }

    public static void dumpRealm(Realm realm) {
        log.info(String.format("<realm time=%d id=%d>",
            realm.time(), realm.id()));
        log.info("\t<delayed_links>");
        int time = 0;
        for (ArrayList<Link> links : realm.delayedLinks()) {
            for (Link link : links) {
                log.info(String.format("\t [time = %d] link %d", time, link.id()));
            }
            time++;
        }
        log.info("\t</delayed_links>");
        log.info("\t<delayed_agents>");
        time = 0;
        for (ArrayList<Agent> activity : realm.delayedAgents()) {
            for (Agent agent : activity) {
                log.info(String.format("\t [time = %d] agent %d", time, agent.id()));
            }
            time++;
        }
        log.info("\t</delayed_agents>");
        log.info("\t\t<incomming links>");
        for (Link link : realm.inLinks()) {
            log.info(String.format("\t\t\t%d ", link.id()));   
        }
        log.info("\t\t\t</incomming links>");
        // TODO - print agents in stops?
        log.info("</realm>");
    }

    public static void dumpLinks(Link[] links) {
        log.info("<links>");
        for (Link link : links) {
            log.info(String.format("\t\t<link id=%d boundary=%s length=%d velocity=%d capacity=%d>",
                link.id(), link.boundary() ? "T":"F", link.length(), link.velocity(), 
                link.capacity()));
            log.info("\t\t\t<agents>");
            for (Agent a : link.queue()) {
                log.info(String.format("\t\t\t\t%d ", a.id()));   
            }
            log.info("\t\t\t</agents>");
        }
        log.info("</links>");
    }

    public static void dumpAgents(Agent[] agents) {
        log.info("<agents>");
        for (Agent agent : agents) {
            log.info(String.format("\t<agent id=%d linkFinishTime=%d planIndex=%d>",
                agent.id(), agent.linkFinishTime(), agent.planIndex()));
            log.info("\t<plan>");
            for (long edge : agent.plan()) {
                log.info(String.format("\t\t%s", Agent.toString(edge)));   
            }
            log.info("\t</plan>");
        }
        log.info("</agents>");
    }
}