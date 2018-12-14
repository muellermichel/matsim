package org.matsim.core.utils.quickevents;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class QuickEvents {
    private List<ByteBuffer> list_of_buffers;
    private int curr_buff_idx = -1;
    private ByteBuffer curr_buff;

    // Java arrays, and thus buffers, can only have MAXINT number of entries.
    // We anticipate that for a whole day of Swiss population events we will have more than MAXINT bytes of data (~2 billion)
    // -> need to deal with a list of buffers to handle that case, which would grow till MAXINT squared, i.e. more than
    // we could ever have memory for the next couple of hundred years.
    private void prepare_next_buffer() {
        curr_buff_idx += 1;
        while (curr_buff_idx >= list_of_buffers.size()) {
            list_of_buffers.add(ByteBuffer.allocate(1000*1000*1000));
        }
        curr_buff = list_of_buffers.get(curr_buff_idx);
    }

    public QuickEvents() {
        list_of_buffers = new ArrayList<>();
        this.prepare_next_buffer();
    }

    private void putInt(int number) {
        try {
            curr_buff.putInt(number);
        }
        catch (BufferOverflowException e) {
            prepare_next_buffer();
            curr_buff.putInt(number);
        }
    }

    public void tick() {
        this.putInt(-1);
    }

    public void clear() {
        for (ByteBuffer buf:this.list_of_buffers) {
            buf.clear();
        }
        curr_buff_idx = 0;
    }

    public void registerPlannedEvent(int agentId, int planIndex) {
        this.putInt(agentId);
        this.putInt(planIndex);
    }

    public List<byte[]> getData() {
        List<byte[]> result = new ArrayList<>();
        for(ByteBuffer buf:this.list_of_buffers) {
            result.add(buf.array());
        }
        return result;
    }
}
