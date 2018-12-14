package org.matsim.core.utils.quickevents;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import org.apache.commons.io.IOUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.routes.NetworkRoute;

import java.io.*;
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

    public static void dumpPopulation(Population population) {
        System.out.println(String.format("Population Name:%s", population.getName()));
        for (Person person : population.getPersons().values()) {
            System.out.println(String.format(
                    "Person (%s) %s", person.getClass().getName(), person));
            for (Plan plan : person.getPlans()) {
                System.out.println(String.format(
                        "\tPlan (%s) %s", plan.getClass().getName(), plan));
                for (PlanElement element: plan.getPlanElements()) {
                    if (element instanceof Leg) {
                        Leg leg = (Leg) element;
                        Route route = leg.getRoute();
                        System.out.println("Mode=" + leg.getMode());
                        if (route != null) {
                            System.out.println("Route=" + leg.getRoute().getClass().getName());
                            if (route instanceof NetworkRoute) {
                                NetworkRoute nr = (NetworkRoute) route;
                                System.out.println("VehicleId=" + nr.getVehicleId());;
                            }
                        }
                    }
                    System.out.println(String.format(
                            "\t\tStep (%s) %s", element.getClass().getName(), element));
                }
            }
        }
    }

    private static StringlyEvents generateDummyEvents(Population population) {
        StringlyEvents events = new StringlyEvents();
        for (Person person : population.getPersons().values()) {
            String personStr = person.getId().toString();
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
                            events.events.add(StringlyEvent.Departure("0", personStr, route.getStartLinkId().toString(), leg.getMode()));
                            if (route instanceof NetworkRoute) {
                                NetworkRoute nr = (NetworkRoute) route;
                                String vehicleId;
                                if (nr.getVehicleId() != null) {
                                    vehicleId = nr.getVehicleId().toString();
                                }
                                else {
                                    vehicleId = personStr;
                                }
                                events.events.add(StringlyEvent.PersonEntersVehicle("0", personStr, vehicleId));
                                events.events.add(StringlyEvent.VehicleEntersTraffic("0", personStr, nr.getStartLinkId().toString(), vehicleId, leg.getMode(), "1.0"));
                                events.events.add(StringlyEvent.LeaveLink("0", vehicleId, nr.getStartLinkId().toString()));
                                for (Id<Link> linkId: nr.getLinkIds()) {
                                    events.events.add(StringlyEvent.EnterLink("0", vehicleId, linkId.toString()));
                                    events.events.add(StringlyEvent.LeaveLink("0", vehicleId, linkId.toString()));
                                }
                                events.events.add(StringlyEvent.EnterLink("0", vehicleId, nr.getEndLinkId().toString()));
                                events.events.add(StringlyEvent.VehicleLeavesTraffic("0", personStr, nr.getEndLinkId().toString(), vehicleId, leg.getMode(), "1.0"));
                                events.events.add(StringlyEvent.PersonLeavesVehicle("0", personStr, vehicleId));
                            }
                            else {
                                events.events.add(StringlyEvent.Travelled("0", personStr, String.valueOf(route.getDistance())));
                            }
                            events.events.add(StringlyEvent.Arrival("0", personStr, route.getEndLinkId().toString(), leg.getMode()));
                        }
                    }
                    else if (element instanceof Activity) {
                        Activity act = (Activity) element;
                        if (element != firstElement) {
                            events.events.add(StringlyEvent.ActivityStart("0", personStr, act.getLinkId().toString(), act.getType()));
                        }
                        if (element != lastElement) {
                            events.events.add(StringlyEvent.ActivityEnd("0", personStr, act.getLinkId().toString(), act.getType()));
                        }
                    }
                }
            }
        }
        return events;
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
}
