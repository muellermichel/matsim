package org.matsim.core.utils.quickevents;

import java.nio.ByteBuffer;

public class QuickEvents {
    private ByteBuffer buf;

    public QuickEvents(int maxNumPlanElements, int maxNumTicks) {
        buf = ByteBuffer.allocate(4 * 2 * maxNumPlanElements + maxNumTicks);
    }

    public void tick() {
        buf.put((byte) -1);
    }

    public void clear() {
        buf.clear();
    }

    public void registerEvent(int agentId, int planIndex) {
        buf.putInt(agentId);
        buf.putInt(planIndex);
    }

    public byte[] getData() {
        return buf.array();
    }
}
