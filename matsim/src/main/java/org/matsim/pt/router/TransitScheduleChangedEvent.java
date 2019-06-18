package org.matsim.pt.router;

import org.matsim.api.core.v01.events.Event;

public class TransitScheduleChangedEvent extends Event {

	public static final int EVENT_ID = genEventTypeId();

    public TransitScheduleChangedEvent(double time) {
        super(time);
    }

    @Override
    public String getEventType() {
        return "transit_schedule_changed";
    }

	@Override
	public int getEventTypeId() {
		return EVENT_ID;
	}
}
