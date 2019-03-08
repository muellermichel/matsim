package org.matsim.core.utils.quickevents;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import org.apache.commons.io.IOUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.mobsim.nqsim.Agent;
import org.matsim.core.population.routes.NetworkRoute;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

class LongStreamer {
    private List<ByteBuffer> data;
    private int data_idx = 0;

    LongStreamer(List<ByteBuffer> data) {
        this.data = data;
    }

    long getLong() {
        while (this.data_idx < data.size()) {
            ByteBuffer currBuffer = this.data.get(this.data_idx);
            long payload;
            try {
                payload = currBuffer.getLong();
            } catch (BufferUnderflowException e) {
                this.data_idx += 1;
                continue;
            }
            return payload;
        }
        throw new BufferUnderflowException();
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
        EventOrderValidator eventOrderValidator = new EventOrderValidator(events);
        eventOrderValidator.validate(new EventOrderValidator(refEvents), exactTimingRequired);
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

    private static List<StringlyEvent> setEventTimingAndGetRespectiveEvents(Map<String, List<List<StringlyEvent>>> eventsByPerson, String agentId, int planStepForAgent, String time, long nqsimPlanEntry) {
        List<List<StringlyEvent>> eventsForPerson = eventsByPerson.get(agentId);
        if (eventsForPerson == null) {
            throw new RuntimeException("no events found for agent " + agentId);
        }
        List<StringlyEvent> eventsForStep = eventsForPerson.get(planStepForAgent);
        int adjustedPlanHeader = Agent.getPlanHeader(nqsimPlanEntry);
        if (adjustedPlanHeader == Agent.SleepUntilType) {
            adjustedPlanHeader = Agent.SleepForType;
        }
        for (StringlyEvent event:eventsForStep) {
            if (event.nqSimEventType != adjustedPlanHeader) {
                throw new RuntimeException(String.format(
                    "mismatch event type for plan step %d for agent %s: %d expected from scenario plan, %d logged;\nevent from scenario plan: %s\nnqsim plan header: %d\nnqsim plan payload: %d\nall events for step: %s",
                    planStepForAgent,
                    agentId,
                    event.nqSimEventType,
                    adjustedPlanHeader,
                    event.toString(),
                    Agent.getPlanHeader(nqsimPlanEntry),
                    0,
                    eventsForStep.stream().map(Object::toString).collect(Collectors.joining(",\n"))
                ));
            }
            if (!event.receivesTimingFromNextEvent) {
                event.time = time;
            }
            System.out.println("Event " + planStepForAgent + " logged: " + Agent.getPlanHeader(nqsimPlanEntry) + "; " + event.toString());
        }
        return eventsForStep;
    }

    public static StringlyEvents generateStringlyEventsFromSimResults(
        Population population, List<ByteBuffer> quickEventData, Map<Integer, String> matsimAgentIdByNqsimAgentIdx
    ) {
        Map<String, List<List<StringlyEvent>>> eventsByPerson = new HashMap<>();

        for (Person person : population.getPersons().values()) {
            String personStr = person.getId().toString();
            List<List<StringlyEvent>> eventsPerNQSIMStep = eventsByPerson.computeIfAbsent(
                personStr, k -> new ArrayList<>()
            );
            for (Plan plan : person.getPlans()) {
                if (plan.getPlanElements().size() == 0) {
                    continue;
                }
                PlanElement firstElement = plan.getPlanElements().get(0);
                PlanElement lastElement = plan.getPlanElements().get(plan.getPlanElements().size() - 1);
                boolean previousEventIsCarArrival = false;
                for (PlanElement element: plan.getPlanElements()) {
                    if (element instanceof Leg) {
                        Leg leg = (Leg) element;

                        Route route = leg.getRoute();
                        if (route != null) {
                            addEvent(
                                eventsPerNQSIMStep,
                                !leg.getMode().equals("car"), //in case of cars the car interaction is used as the start of the event list
                                StringlyEvent.Departure(personStr, route.getStartLinkId().toString(), leg.getMode())
                            );
                            if (route instanceof NetworkRoute) {
                                NetworkRoute nr = (NetworkRoute) route;
                                String vehicleId;
                                if (nr.getVehicleId() != null) {
                                    vehicleId = nr.getVehicleId().toString();
                                }
                                else {
                                    vehicleId = personStr;
                                }
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.PersonEntersVehicle(
                                    personStr, vehicleId
                                ));
                                addEvent(eventsPerNQSIMStep, true, StringlyEvent.VehicleEntersTraffic(
                                    personStr, nr.getStartLinkId().toString(), vehicleId, leg.getMode(), "1.0"
                                ));
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.LeaveLink(
                                    vehicleId, nr.getStartLinkId().toString()
                                ));
                                ListIterator<Id<Link>> linkIdIterator = nr.getLinkIds().listIterator();
                                while (linkIdIterator.hasNext()) {
                                    int idx = linkIdIterator.nextIndex();
                                    Id<Link> linkId = linkIdIterator.next();
                                    addEvent(eventsPerNQSIMStep, true, StringlyEvent.EnterLink(
                                        vehicleId, linkId.toString()
                                    ));
                                    addEvent(eventsPerNQSIMStep, false, StringlyEvent.LeaveLink(
                                        vehicleId, linkId.toString()
                                    ));
                                }
                                addEvent(eventsPerNQSIMStep, true, StringlyEvent.EnterLink(
                                    vehicleId, nr.getEndLinkId().toString()
                                ));
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.VehicleLeavesTraffic(
                                    personStr, nr.getEndLinkId().toString(), vehicleId, leg.getMode(), "1.0"
                                ));
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.PersonLeavesVehicle(
                                    personStr, vehicleId
                                ));
                            }
                            else {
                                addEvent(eventsPerNQSIMStep, false, StringlyEvent.Travelled(
                                    personStr, String.valueOf(route.getDistance())
                                ));
                            }
                            addEvent(eventsPerNQSIMStep, false, StringlyEvent.Arrival(
                                personStr, route.getEndLinkId().toString(), leg.getMode()
                            ));
                            if (leg.getMode().equals("car")) {
                                previousEventIsCarArrival = true;
                            }
                            else {
                                previousEventIsCarArrival = false;
                            }
                        }
                        else {
                            previousEventIsCarArrival = false;
                        }
                    }
                    else if (element instanceof Activity) {
                        Activity act = (Activity) element;
                        if (act.getType().equals("car interaction")) {
                            addEvent(
                                eventsPerNQSIMStep,
                                !previousEventIsCarArrival,
                                StringlyEvent.CarInteractionStart(personStr, act.getLinkId().toString())
                            );
                            addEvent(eventsPerNQSIMStep, false, StringlyEvent.CarInteractionEnd(
                                personStr, act.getLinkId().toString()
                            ));
                        }
                        else {
                            if (element != firstElement) {
                                addEvent(
                                    eventsPerNQSIMStep,
                                    element != lastElement,
                                    StringlyEvent.ActivityStart(personStr, act.getLinkId().toString(), act.getType())
                                );
                            }
                            if (element != lastElement) {
                                addEvent(
                                    eventsPerNQSIMStep,
                                    element == firstElement,
                                    StringlyEvent.ActivityEnd(personStr, act.getLinkId().toString(), act.getType())
                                );
                            }
                        }
                        previousEventIsCarArrival = false;
                    }
                    else {
                        previousEventIsCarArrival = false;
                    }
                }
            }
        }

        StringlyEvents events = new StringlyEvents();
        if (quickEventData != null && matsimAgentIdByNqsimAgentIdx != null) {
            int tick = 0;
            String time = String.valueOf((double) tick);
            LongStreamer quickEventStreamer = new LongStreamer(quickEventData);
            while (true) {
                long payload1;
                try {
                    payload1 = quickEventStreamer.getLong();
                }
                catch (BufferUnderflowException e) {
                    break;
                }
                if (payload1 == -1) {
                    tick += 1;
                    time = String.valueOf((double) tick);
                    continue;
                }
                int agentIdx = (int) (payload1 >> 32);
                int planStepForAgent = (int) payload1;
                long plan = quickEventStreamer.getLong(); //if this underflows we want the runtime exception to bubble up as it's unexpected
                String matsimAgentId = matsimAgentIdByNqsimAgentIdx.get(agentIdx);
                if (matsimAgentId == null) {
                    throw new RuntimeException("no agent mapping to MATSim agents for agent idx " + agentIdx);
                }
                if (planStepForAgent == 1) {
                    //special case for start event - this currently never fires out of the NQSim
                    events.events.addAll(setEventTimingAndGetRespectiveEvents(
                        eventsByPerson,
                        matsimAgentId,
                        0,
                        time,
                        (long)Agent.SleepUntilType << 32 | 0 & 0xFFFFFFFFL
                    ));
                }
                if (planStepForAgent > 0) {
                    //check for previous events that are still waiting to receive their timing
                    int lastCheckedEventIdx = events.events.size() - 1;
                    do {
                        StringlyEvent currEvent = events.events.get(lastCheckedEventIdx);
                        if (currEvent.receivesTimingFromNextEvent == false) {
                            break;
                        }
                        currEvent.time = time;
                        lastCheckedEventIdx -= 1;
                    } while(lastCheckedEventIdx >= 0);
                }
                events.events.addAll(setEventTimingAndGetRespectiveEvents(eventsByPerson, matsimAgentId, planStepForAgent, time, plan));
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
        return generateStringlyEventsFromSimResults(population, null, null);
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

    public static void testTimedEventsGeneration(Population population, QuickEvents quickEvents, Map<Integer, String> matsimAgentIdByNqsimAgentIdx, String refXMLFile) {
        StringlyEvents events = generateStringlyEventsFromSimResults(population, quickEvents.getData(), matsimAgentIdByNqsimAgentIdx);
        try {
            writeXMLFile("timed_" + refXMLFile, events);
            validate(events, readXMLFile(refXMLFile), true);
        }
        catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }
}
