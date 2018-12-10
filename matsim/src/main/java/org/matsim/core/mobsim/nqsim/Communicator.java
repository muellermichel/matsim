package org.matsim.core.mobsim.nqsim;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import mpi.MPI;
import mpi.Request;
import mpi.Status;

public class Communicator {

    // Communication buffers. One buffer per neighbor realm.
    private final Map<Integer, ByteBuffer> rcvBufsByRealmId;
    private final Map<Integer, ByteBuffer> sndBufsByRealmId;
    private final Set<Request> sndRequests;
    // Current realm.
    private final Realm realm;
    // All the agents in the world.
    private final Agent[] agents;
    // Buffer capacity for each neighbor realm.
    private final int bufferCapacity = 128*1024*1024;

    public Communicator(Realm realm, Agent[] agents) throws Exception {
        MPI.COMM_WORLD.setErrhandler(MPI.ERRORS_ARE_FATAL);
        this.rcvBufsByRealmId = new HashMap<>();
        this.sndBufsByRealmId = new HashMap<>();
        this.sndRequests = new HashSet<>();
        this.realm = realm;
        this.agents = agents;

        for (LinkBoundary inlink : realm.inLinks()) {
            int realmid = inlink.fromrealm();
            if (!rcvBufsByRealmId.containsKey(realmid)) {
                rcvBufsByRealmId.put(realmid, MPI.newByteBuffer(bufferCapacity));
            }
        }
        for (LinkBoundary outlink : realm.outLinks()) {
            int realmid = outlink.torealm();
            if (!sndBufsByRealmId.containsKey(realmid)) {
                sndBufsByRealmId.put(realmid, MPI.newByteBuffer(bufferCapacity));
            }
        }
    }

    public void waitSends() throws Exception {
        if (!sndRequests.isEmpty()) {
            Request.waitAll(sndRequests.toArray(new Request[sndBufsByRealmId.size()]));
           sndRequests.clear();
        }
    }

    private void sendBuffers(int tag) throws Exception {
        for (Integer toid : sndBufsByRealmId.keySet()) {
            ByteBuffer bb = sndBufsByRealmId.get(toid);
            sndRequests.add(MPI.COMM_WORLD.iSend(bb, bb.position(), MPI.BYTE, toid, tag));
            bb.clear();
        }
    }

    public void recvBuffers(int tag) throws Exception {
        for (Integer fromid : rcvBufsByRealmId.keySet()) {
            ByteBuffer bb = rcvBufsByRealmId.get(fromid);
            bb.clear();
            Status status = MPI.COMM_WORLD.recv(bb, bufferCapacity, MPI.BYTE, fromid, tag);
            bb.limit(status.getCount(MPI.BYTE));
        }

    }

    public void sendAgents(Map<LinkBoundary, ArrayList<Agent>> outAgentsByLinkId) throws Exception {
        for (Map.Entry<LinkBoundary,ArrayList<Agent>> entry : outAgentsByLinkId.entrySet()) {
            LinkBoundary blink = entry.getKey();
            ArrayList<Agent> outgoing = entry.getValue();
            ByteBuffer bb = sndBufsByRealmId.get(blink.torealm());
            // Id of the link
            bb.putInt(blink.id());
            // Number of agents being sent for this link.
            bb.putInt(outgoing.size());
            for (Agent a : outgoing) {
                // Id of the agent.
                bb.putInt(a.id);
                // Plan index of the agent.
                bb.putInt(a.planIndex);
            }
        }
        sendBuffers(realm.time());
    }

    public Map<Integer, ArrayList<Agent>> receiveAgents() throws Exception {
        recvBuffers(realm.time());
        Map<Integer, ArrayList<Agent>> inAgentsByLinkId = new HashMap<>();
        for (ByteBuffer bb : rcvBufsByRealmId.values()) {
            while (bb.remaining() > 0) {
                int linkid = bb.getInt();
                int nagents = bb.getInt();
                ArrayList<Agent> recvAgents = new ArrayList<>(nagents);
                for (int j = 0; j < nagents; j++) {
                    int agentid = bb.getInt();
                    int planindex = bb.getInt();
                    agents[agentid].planIndex(planindex);
                    recvAgents.add(agents[agentid]);
                }
                inAgentsByLinkId.put(linkid, recvAgents);
            }
        }
        return inAgentsByLinkId;
    }

    public void sendRoutedCounters(Map<Integer, Integer> routedAgentsByLinkId) throws Exception {
        for (LinkBoundary link : realm.inLinks()) {
            ByteBuffer bb = sndBufsByRealmId.get(link.fromrealm());
            bb.putInt(link.id());
            bb.putInt(routedAgentsByLinkId.get(link.id()));
        }
        sendBuffers(realm.time());
    }
    
    public Map<Integer, Integer> receiveRoutedCounters() throws Exception {
        Map<Integer, Integer> routedAgentsByLinkId = new HashMap<>();
        recvBuffers(realm.time());
        for (ByteBuffer bb : rcvBufsByRealmId.values()) {
            while (bb.remaining() > 0) {
                int linkid = bb.getInt();
                int counter = bb.getInt();
                routedAgentsByLinkId.put(linkid, counter);
            }
        }
        return routedAgentsByLinkId;
    }
}