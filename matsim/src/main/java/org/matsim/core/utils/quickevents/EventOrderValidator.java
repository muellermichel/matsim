package org.matsim.core.utils.quickevents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventOrderValidator {
    private Map<String, List<StringlyEvent>> eventsByPerson;

    public EventOrderValidator(StringlyEvents events) throws ValidationException {
        this.eventsByPerson = new HashMap<>();
        for (StringlyEvent event:events.events) {
            String agentIdentifier = event.person;
            if (agentIdentifier == null) {
                agentIdentifier = event.vehicle;
            }
            if (agentIdentifier == null) {
                agentIdentifier = event.driverId;
            }
            if (agentIdentifier == null) {
                agentIdentifier = event.agent;
            }
            if (agentIdentifier == null) {
                throw new ValidationException("event cannot be represented as there is no agent identifier: " + event.toString());
            }
            List<StringlyEvent> eventsForPerson = eventsByPerson.computeIfAbsent(
                    agentIdentifier,
                    k -> new ArrayList<>()
            );
            eventsForPerson.add(event);
        }
    }

    public void validate(EventOrderValidator reference, boolean exactTimingRequired) throws ValidationException {
        for (Map.Entry<String, List<StringlyEvent>> e:this.eventsByPerson.entrySet()) {
            String agentIdentifier = e.getKey();
            List<StringlyEvent> events = e.getValue();
            List<StringlyEvent> referenceEvents = reference.eventsByPerson.get(agentIdentifier);
            if (referenceEvents == null) {
                throw new ValidationException("no events found in reference for agent " + agentIdentifier);
            }
            if (referenceEvents.size() != events.size()) {
                throw new ValidationException(String.format(
                        "event list size (%d) does not match reference (%d) for agent %s",
                        events.size(),
                        referenceEvents.size(),
                        agentIdentifier
                ));
            }
            for (int event_idx = 0; event_idx < events.size(); event_idx++) {
                if (!events.get(event_idx).equals(referenceEvents.get(event_idx), exactTimingRequired)) {
                    throw new ValidationException(String.format(
                            "event %d for agent %s does not match reference:\n%s\nvs\n%s",
                            event_idx,
                            agentIdentifier,
                            events.get(event_idx).toString(),
                            referenceEvents.get(event_idx).toString()
                    ));
                }
            }
        }
    }
}