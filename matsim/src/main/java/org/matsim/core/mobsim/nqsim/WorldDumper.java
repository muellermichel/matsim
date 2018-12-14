package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;

import org.apache.log4j.Logger;

public class WorldDumper {
    
    final private static Logger log = Logger.getLogger(WorldDumper.class);

    public static boolean dumpWorld(World world) {
        log.info("Dumping world...");
        for (Realm realm : world.realms()) {
            dumpRealm(realm);
        }
        dumpAgents(world.agents());
        log.info("Finished dumping world.");
        return true;
    }

    public static void dumpRealm(Realm realm) {
        log.info(String.format("<realm time=%d id=%d>",
            realm.time(), realm.id()));
        log.info("\t<links>");
        for (int i = 0; i < realm.links().length; i++) {
            dumpInternalLink(i, realm.links()[i]);
        }
        log.info("\t</links>");
        log.info("\t<inlinks>");
        for (LinkBoundary link : realm.inLinks()) {
            dumpBoundaryLink(link);
        }
        log.info("\t</inlinks>");
        log.info("\t<outlinks>");
        for (LinkBoundary link : realm.outLinks()) {
            dumpBoundaryLink(link);
        }
        log.info("\t</outlinks>");
        log.info("\t<activities>");
        int index = 0;
        for (ArrayList<Agent> activity : realm.delayedAgents()) {
            for (Agent agent : activity) {
                log.info(String.format("\t [time = %d] agent %d", index, agent.id()));
            }
            index++;
        }
        log.info("\t</activities>");
        log.info("</realm>");
    }

    public static void dumpInternalLink(int id, LinkInternal link) {
        log.info(String.format("\t\t<ilink id=%d nextTime=%d timeToPass=%d currentCapacity=%d>",
            id, link.nexttime(), link.timeToPass(), link.currentCapacity()));
        log.info("\t\t\t<agents>");
        for (Agent a : link.queue()) {
            log.info(String.format("\t\t\t\t%d ", a.id()));   
        }
        log.info("\t\t\t</agents>");
        log.info("\t\t</ilink>");
    }

    public static void dumpBoundaryLink(LinkBoundary link) {
        log.info(String.format("\t\t<blink id=%d from=%d to=%d>",
               link.id(), link.fromrealm(), link.torealm()));

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