package org.matsim.core.utils.quickevents;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import org.apache.commons.io.IOUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.routes.NetworkRoute;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.GZIPInputStream;

class EventValidator {
    private Map<String, List<StringlyEvent>> eventsByPerson;

    EventValidator(StringlyEvents events) throws ValidationException {
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

    void validate(EventValidator reference, boolean exactTimingRequired) throws ValidationException {
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

public final class StringlyEventlogTool {

    public static byte[] decompress(byte[] contentBytes){
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try{
            IOUtils.copy(new GZIPInputStream(new ByteArrayInputStream(contentBytes)), out);
        }
        catch(IOException e){
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    public static StringlyEvents readXML(byte[] input) {
        StringlyEvents events;
        try {
            events = new XmlMapper().readValue(input, StringlyEvents.class);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return events;
    }

    public static StringlyEvents readGzipXMLFile(String filePath) {
        try {
            return readXML(decompress(IOUtils.toByteArray(new FileInputStream(new File(filePath)))));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static StringlyEvents readXMLFile(String filePath) {
        try {
            return readXML(IOUtils.toByteArray(new FileInputStream(new File(filePath))));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeXMLFile(String filePath, StringlyEvents events) {
        try {
            XmlMapper mapper = new XmlMapper();
            mapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
            mapper.writeValue(new File(filePath), events);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Map<String, StringlyEvent> extractEventExamplesByHeader(StringlyEvents events) {
        Map<String, StringlyEvent> result = new TreeMap<>();
        for (StringlyEvent event:events.events) {
            result.putIfAbsent(event.getStringHeader(), event);
        }
        return result;
    }

    public static void printEventTypesAndExamples(StringlyEvents events) {
        Map<String,StringlyEvent> sampleEventsByHeader = extractEventExamplesByHeader(events);
        int typeIdx = 0;
        for (Map.Entry<String, StringlyEvent> e:sampleEventsByHeader.entrySet()) {
            System.out.println(String.format(
                "%d: %s  ->  %s",
                typeIdx,
                e.getKey(),
                e.getValue().toString()
            ));
            typeIdx++;
        }
    }

    public static void validate(StringlyEvents events, StringlyEvents refEvents, boolean exactTimingRequired) throws ValidationException {
        EventValidator eventValidator = new EventValidator(events);
        eventValidator.validate(new EventValidator(refEvents), exactTimingRequired);
    }

    private static void addEvent(List<List<StringlyEvent>> eventsForPerson, boolean usesSeparateStepInNQSIM, StringlyEvent event) {
        List<StringlyEvent> currEvents;
        if (usesSeparateStepInNQSIM) {
            currEvents = new ArrayList<>();
            eventsForPerson.add(currEvents);
        }
        else {
            currEvents = eventsForPerson.get(eventsForPerson.size() - 1);
        }
        currEvents.add(event);
    }

    private static List<StringlyEvent> setEventTimingAndGetRespectiveEvents(Map<Integer, List<List<StringlyEvent>>> eventsByPerson, int agentId, int planStepForAgent, String time) {
        List<List<StringlyEvent>> eventsForPerson = eventsByPerson.get(agentId);
        if (eventsForPerson == null) {
            throw new RuntimeException("no events found for agent " + agentId);
        }
        List<StringlyEvent> eventsForStep = eventsForPerson.get(planStepForAgent);
        for (StringlyEvent event:eventsForStep) {
            event.time = time;
        }
        return eventsForStep;
    }

    public static StringlyEvents generateStringlyEventsFromSimResults(Population population, List<byte[]> quickEventData) {
        Map<Integer, List<List<StringlyEvent>>> eventsByPerson = new HashMap<>();

        for (Person person : population.getPersons().values()) {
            String personStr = person.getId().toString();
            List<List<StringlyEvent>> eventsPerNQSIMStep = eventsByPerson.computeIfAbsent(Integer.parseInt(personStr), k -> new ArrayList<>());
            for (Plan plan : person.getPlans()) {
                if (plan.getPlanElements().size() == 0) {
                    continue;
                }
                PlanElement firstElement = plan.getPlanElements().get(0);
                PlanElement lastElement = plan.getPlanElements().get(plan.getPlanElements().size() - 1);
                for (PlanElement element: plan.getPlanElements()) {
                    if (element instanceof Leg) {
                        Leg leg = (Leg) element;

                        Route route = leg.getRoute();
                        if (route != null) {
                            addEvent(eventsPerNQSIMStep, true, StringlyEvent.Departure(personStr, route.getStartLinkId().toString(), leg.getMode()));
                            if (route instanceof NetworkRoute) {
                                NetworkRoute nr = (NetworkRoute) route;
                                String vehicleId;
                                if (nr.getVehicleId() != null) {
                                    vehicleId = nr.getVehicleId().toString();
                                }
                                else {
                                    vehicleId = personStr;
                                }
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.PersonEntersVehicle(personStr, vehicleId));
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.VehicleEntersTraffic(personStr, nr.getStartLinkId().toString(), vehicleId, leg.getMode(), "1.0"));
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.LeaveLink(vehicleId, nr.getStartLinkId().toString()));
                                ListIterator<Id<Link>> linkIdIterator = nr.getLinkIds().listIterator();
                                while (linkIdIterator.hasNext()) {
                                    int idx = linkIdIterator.nextIndex();
                                    Id<Link> linkId = linkIdIterator.next();
                                    addEvent(eventsPerNQSIMStep, idx > 0, StringlyEvent.EnterLink(vehicleId, linkId.toString()));
                                    addEvent(eventsPerNQSIMStep, false, StringlyEvent.LeaveLink(vehicleId, linkId.toString()));
                                }
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.EnterLink(vehicleId, nr.getEndLinkId().toString()));
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.VehicleLeavesTraffic(personStr, nr.getEndLinkId().toString(), vehicleId, leg.getMode(), "1.0"));
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.PersonLeavesVehicle(personStr, vehicleId));
                            }
                            else {
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.Travelled(personStr, String.valueOf(route.getDistance())));
                            }
                            addEvent(eventsPerNQSIMStep, true, StringlyEvent.Arrival(personStr, route.getEndLinkId().toString(), leg.getMode()));
                        }
                    }
                    else if (element instanceof Activity) {
                        Activity act = (Activity) element;
                        if (element != firstElement) {
                            addEvent(eventsPerNQSIMStep, true, StringlyEvent.ActivityStart(personStr, act.getLinkId().toString(), act.getType()));
                        }
                        if (element != lastElement) {
                            addEvent(eventsPerNQSIMStep, true, StringlyEvent.ActivityEnd(personStr, act.getLinkId().toString(), act.getType()));
                        }
                    }
                }
            }
        }

        StringlyEvents events = new StringlyEvents();
        if (quickEventData != null) {
            int tick = 0;
            String time = String.valueOf((double) tick);
            for (byte[] quickEventBytes : quickEventData) {
                ByteBuffer currBuf = ByteBuffer.wrap(quickEventBytes);
                while (true) {
                    long payload;
                    try {
                        payload = currBuf.getInt();
                    } catch (BufferUnderflowException e) {
                        break;
                    }
                    if (payload == -1) {
                        tick += 1;
                        time = String.valueOf((double) tick);
                        continue;
                    }
                    int agentId = (int) (payload >> 32);
                    int planStepForAgent = (int) (payload >> 32);
                    events.events.addAll(setEventTimingAndGetRespectiveEvents(eventsByPerson, agentId, planStepForAgent, time));
                }
            }
        }
        else {
            for (List<List<StringlyEvent>> eventsForPerson: eventsByPerson.values()) {
                for (List<StringlyEvent> eventsForPlanStep:eventsForPerson) {
                    events.events.addAll(eventsForPlanStep);
                }
            }
        }
        return events;
    }

    public static StringlyEvents generateDummyEvents(Population population) {
        return generateStringlyEventsFromSimResults(population, null);
    }

    public static void testEventGeneration(Population population, String refXMLFile) {
        StringlyEvents events = generateDummyEvents(population);
        try {
            writeXMLFile("dummy_" + refXMLFile, events);
            validate(events, readXMLFile(refXMLFile), false);
        }
        catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void testTimedEventsGeneration(Population population, QuickEvents quickEvents, String refXMLFile) {
        StringlyEvents events = generateStringlyEventsFromSimResults(population, quickEvents.getData());
        try {
            writeXMLFile("timed_" + refXMLFile, events);
            validate(events, readXMLFile(refXMLFile), true);
        }
        catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }
}
