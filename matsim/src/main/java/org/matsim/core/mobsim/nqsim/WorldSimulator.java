package org.matsim.core.mobsim.nqsim;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Map;

import mpi.MPI;

public class WorldSimulator {

    // Logging and Debugging information
    private static PrintWriter logfile;
    public static Map<LinkInternal, Integer> globalIdByLink;

    public static boolean log(int time, int realmid, String s) {
        logfile.write(String.format(
            "[ time = %d ] %s\n", time, s));
        return true;
    }

    public static void main(String[] args) throws Exception {
        MPI.Init(new String[0]);
        
        Communicator comm;
        Realm realm;
        String worldpath = args[0];
        int timestep = Integer.parseInt(args[1]);
        int numsteps = Integer.parseInt(args[2]);
        int realmid = MPI.COMM_WORLD.getRank();
        logfile = new PrintWriter(new FileWriter(String.format("%s-realm-%d.log", worldpath, realmid)));

        System.out.println("Deerializing world to " + worldpath + ".ser");
        World world = World.deserialize(worldpath + ".ser");
        System.out.println("Finished deserializing world.");

        realm = world.realm(realmid);
        globalIdByLink = world.globalIdByLink(); // TODO - do only behing an assert
        comm = new Communicator(realm, world.agents());
        world = null;

        System.out.println("Running world");
        for (int i = 0; i < numsteps; i++) {
            realm.tick(timestep, comm);
        }
        System.out.println("Running world finished");
        MPI.Finalize();
        logfile.close();
        
        System.out.println("Dumping realm");
        WorldDumper.dumpRealm(worldpath, realm);
        System.out.println("Finished dumping realm.");
    }
}