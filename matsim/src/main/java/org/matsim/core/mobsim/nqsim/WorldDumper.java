package org.matsim.core.mobsim.nqsim;

import java.io.FileWriter;
import java.io.PrintWriter;

public class WorldDumper {

    public static boolean dumpWorld(String worldpath, World world) throws Exception {
        System.out.println("Dumping world to " + worldpath);
        for (Realm realm : world.realms()) {
            dumpRealm(worldpath, realm);
        }
        dumpAgents(worldpath, world.agents());
        System.out.println("Finished dumping world.");
        return true;
    }

    public static void dumpRealm(String worldpath, Realm realm) throws Exception {
        String filepath = String.format("%s-realm-%d-%d.xml", worldpath, realm.id(), realm.time());
        PrintWriter writer = new PrintWriter(new FileWriter(filepath));
        writer.println(String.format("<realm time=%d id=%d>",
            realm.time(), realm.id()));
        writer.println("\t<links>");
        for (int i = 0; i < realm.links().length; i++) {
            dumpInternalLink(writer, i, realm.links()[i]);
        }
        writer.println("\t</links>");
        writer.println("\t<inlinks>");
        for (LinkBoundary link : realm.inLinks()) {
            dumpBoundaryLink(writer, link);
        }
        writer.println("\t</inlinks>");
        writer.println("\t<outlinks>");
        for (LinkBoundary link : realm.outLinks()) {
            dumpBoundaryLink(writer, link);
        }
        writer.println("\t</outlinks>");
        writer.println("</realm>");
        writer.close();
    }

    public static void dumpInternalLink(PrintWriter writer, int id, LinkInternal link) {
        writer.println(String.format("\t\t<ilink id=%d nextTime=%d timeToPass=%d currentCapacity=%d>",
            id, link.nexttime(), link.timeToPass(), link.currentCapacity()));
        writer.print("\t\t\t<agents>");
        for (Agent a : link.queue()) {
            writer.print(String.format("%d ", a.id()));   
        }
        writer.println("</agents>");
        writer.println("\t\t</ilink>");
    }

    public static void dumpBoundaryLink(PrintWriter writer, LinkBoundary link) {
        writer.println(String.format("\t\t<blink id=%d from=%d to=%d>",
               link.id(), link.fromrealm(), link.torealm()));

    }
   
    public static void dumpAgents(String worldpath, Agent[] agents) throws Exception {
        String filepath = String.format("%s-agents.xml", worldpath);
        PrintWriter writer = new PrintWriter(new FileWriter(filepath));
        for (Agent agent : agents) {
            writer.println("<agent>");
            writer.println(String.format("\t<agent id=%d linkFinishTime=%d planIndex=%d>",
                agent.id(), agent.linkFinishTime(), agent.planIndex()));
            writer.print("\t<plan>");
            for (long edge : agent.plan()) {
                writer.print(String.format("\n\t\t%s", Agent.toString(edge)));   
            }
            writer.println("\n\t</plan>");
            writer.println("</agent>");
        }
        writer.close();
    }
}