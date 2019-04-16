package org.matsim.core.mobsim.hermes;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.matsim.api.core.v01.events.Event;

public class WorldDumper {

    public static String outputPrefix;
    private static BufferedWriter events_hermes = null;
    private static BufferedWriter events_qsim = null;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                close();
            }
        });
    }

    private static void close() {
        try {
            if (events_hermes != null) {
                events_hermes.close();
            }
            if (events_qsim != null) {
                events_qsim.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setup(String folder) {
        try {
            outputPrefix = folder + "/" + "ITERS" + "/it." + Hermes.iteration;

            close();

            events_hermes = new BufferedWriter(new FileWriter(outputPrefix + "/hermes_events"));
            events_qsim = new BufferedWriter(new FileWriter(outputPrefix + "/qsim_events"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void dumpRealm(Realm realm) throws Exception {
        BufferedWriter log = new BufferedWriter(new FileWriter(outputPrefix + "/hermes_realm"));
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
        log.close();
    }

    public static void dumpLinks(Link[] links) throws Exception {
        BufferedWriter log = new BufferedWriter(new FileWriter(outputPrefix + "/hermes_links"));
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
        log.close();
    }

    public static void dumpAgents(Agent[] agents) throws Exception {
        BufferedWriter log = new BufferedWriter(new FileWriter(outputPrefix + "/hermes_agents"));
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
        log.close();
    }

    public static void dumpQsimEvents(Event event) {
        try {
            events_qsim.write(String.format("%s\n", event.toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void dumpHermesEvents(ArrayList<Event> events) {
        if (events_hermes == null) {

        }
        for (Event event : events) {
            // This if ignores events that hermes produces and qsim dones not.
            if (event.getTime() == 0) {
                continue;
            }
            try {
                events_hermes.write(String.format("%s\n", event.toString()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
