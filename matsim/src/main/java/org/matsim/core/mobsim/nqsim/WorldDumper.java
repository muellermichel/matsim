package org.matsim.core.mobsim.nqsim;

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
        log.info(String.format("<realm time=%d >", realm.time()));
        log.info("\t<delayed_links>");
        for (int time = 0; time < World.SIM_STEPS; time++) {
            for (int thread = 0; thread < World.NUM_REALMS; thread++) {
                for (Link link : realm.delayedLinks().get(thread).get(time)) {
                    log.info(String.format("\t [time = %d] link %d", time, link.id()));
                }
            }
        }
        log.info("\t</delayed_links>");
        log.info("\t<delayed_agents>");
        for (int time = 0; time < World.SIM_STEPS; time++) {
            for (int thread = 0; thread < World.NUM_REALMS; thread++) {
                for (Agent agent : realm.delayedAgents().get(thread).get(time)) {
                    log.info(String.format("\t [time = %d] agent %d", time, agent.id()));
                }
            }
        }
        log.info("\t</delayed_agents>");
        // TODO - print agents in stops?
        log.info("</realm>");
    }

    public static void dumpLinks(Link[] links) {
        log.info("<links>");
        for (Link link : links) {
            log.info(String.format("\t\t<link id=%d length=%d velocity=%d capacity=%d>",
                link.id(), link.length(), link.velocity(), 
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