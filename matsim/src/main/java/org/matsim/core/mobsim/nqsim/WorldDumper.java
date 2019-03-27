package org.matsim.core.mobsim.nqsim;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.matsim.api.core.v01.events.Event;

public class WorldDumper {
    
    private final BufferedWriter log;

    public WorldDumper(String output) throws Exception {
        log = new BufferedWriter(new FileWriter(output + "/hermes_dump"));

    }

    public boolean dumpWorld(World world) throws Exception {
        log.write("Dumping world...\n");
        dumpRealms(world.realms());
        dumpLinks(world.links());
        dumpAgents(world.agents());
        log.write("Finished dumping world.\n");
        return true;
    }

    public void dumpRealms(Realm[] realms) throws Exception {
        for (Realm realm : realms) {
            dumpRealm(realm);
        }
    }

    public void dumpRealm(Realm realm) throws Exception {
        log.write(String.format("<realm time=%d >\n", realm.time()));
        log.write("\t<delayed_links>\n");
        int time = 0;
        for (ConcurrentLinkedQueue<Link> links : realm.delayedLinks()) {
            for (Link link : links) {
                log.write(String.format("\t [time = %d] link %d\n", time, link.id()));
            }
            time++;
        }
        log.write("\t</delayed_links>\n");
        log.write("\t<delayed_agents>\n");
        time = 0;
        for (ConcurrentLinkedQueue<Agent> activity : realm.delayedAgents()) {
            for (Agent agent : activity) {
                log.write(String.format("\t [time = %d] agent %d\n", time, agent.id()));
            }
            time++;
        }
        log.write("\t</delayed_agents>\n");
        // TODO - print agents in stops?
        log.write("</realm>\n");
    }

    public void dumpLinks(Link[] links) throws Exception {
        log.write("<links>\n");
        for (Link link : links) {
            log.write(String.format("\t\t<link id=%d length=%d velocity=%d capacity=%d>\n",
                link.id(), link.length(), link.velocity(), 
                link.capacity()));
            log.write("\t\t\t<agents>\n");
            for (Agent a : link.queue()) {
                log.write(String.format("\t\t\t\t%d \n", a.id()));   
            }
            log.write("\t\t\t</agents>\n");
        }
        log.write("</links>\n");
    }

    public void dumpAgents(Agent[] agents) throws Exception {
        log.write("<agents>\n");
        for (Agent agent : agents) {
            log.write(String.format("\t<agent id=%d linkFinishTime=%d planIndex=%d capacity=%d>\n",
                agent.id(), agent.linkFinishTime(), agent.planIndex(), agent.capacity()));
            log.write("\t<plan>\n");
            for (long edge : agent.plan()) {
                log.write(String.format("\t\t%s\n", Agent.toString(edge)));   
            }
            log.write("\t</plan>\n");
        }
        log.write("</agents>\n");
    }

    public static void dumpEvent(final Event event) {
        if (	World.DEBUG_EVENTS &&
				event != null &&
				event.getEventType() != null &&
				!event.getEventType().equals("simstepend")) {
            System.out.println("ETHZ qsim event " + event.toString());
            (new Throwable()).printStackTrace();
		}
    }
}