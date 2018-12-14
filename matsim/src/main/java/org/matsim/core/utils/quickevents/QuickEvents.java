package org.matsim.core.utils.quickevents;

import java.nio.ByteBuffer;

public class QuickEvents {
    private ByteBuffer buf;

    public QuickEvents(int maxNumPlanElements, int maxNumTicks) {
        buf = ByteBuffer.allocate(4 * (2 * maxNumPlanElements + maxNumTicks));
    }

    public void tick() {
        buf.putInt(-1);
    }

    public void clear() {
        buf.clear();
    }

    public void registerPlannedEvent(int agentId, int planIndex) {
        buf.putInt(agentId);
        buf.putInt(planIndex);
    }

    public byte[] getData() {
        return buf.array();
    }
}
