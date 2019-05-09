package org.matsim.core.events;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;
import org.matsim.api.core.v01.events.handler.PersonStuckEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.events.handler.EventHandler;

// TODO - we will need a two barriers, one for the simstep end and one for the iteration end barrier.

public class ConcurrentEventsManager  implements EventsManager {

    private static final Logger log = Logger.getLogger(ConcurrentEventsManager.class);

    public enum EventCode {
        LinkLeaveEvent,
        LinkEnterEvent,
        VehicleEntersTrafficEvent,
        VehicleLeavesTrafficEvent,
        PersonArrivalEvent,
        PersonDepartureEvent,
        ActivityEndEvent,
        ActivityStartEvent,
        TransitDriverStartsEvent,
        PersonStuckEvent,
        PersonMoneyEvent,
        AgentWaitingForPtEvent,
        PersonEntersVehicleEvent,
        PersonLeavesVehicleEvent,
        VehicleDepartsAtFacilityEvent,
        VehicleArrivesAtFacilityEvent,
        UnknownEvent
    }

    /**
     * Event_queues.get('event code') returns a list of queues that should receive events with the 'event code'.
     */
    private final ArrayList<ArrayList<LinkedBlockingDeque<Event>>> event_queues;

    /**
     * Threads that are waiting for events targeting a specific event handler.
     */
    private final ArrayList<HandlerThread> handlerThreads;

    /**
     * Runnable that handles a blocking queue filled with events going to a specific event handler.
     */
    class HandlerThread extends Thread {

        private final EventHandler handler;
        private final LinkedBlockingDeque<Event> queue;

        public HandlerThread(EventHandler handler, LinkedBlockingDeque<Event> queue) {
            this.handler = handler;
            this.queue = queue;

        }

        public void handle(Event event) {
            switch(event.getEventCode()) {
            case LinkLeaveEvent:
                ((LinkLeaveEventHandler)handler).handleEvent((LinkLeaveEvent)event);
                break;
            case LinkEnterEvent:
                ((LinkEnterEventHandler)handler).handleEvent((LinkEnterEvent)event);
                break;
            case VehicleEntersTrafficEvent:
                ((VehicleEntersTrafficEventHandler)handler).handleEvent((VehicleEntersTrafficEvent)event);
                break;
            case VehicleLeavesTrafficEvent:
                ((VehicleLeavesTrafficEventHandler)handler).handleEvent((VehicleLeavesTrafficEvent)event);
                break;
            case PersonArrivalEvent:
                ((PersonArrivalEventHandler)handler).handleEvent((PersonArrivalEvent)event);
                break;
            case PersonDepartureEvent:
                ((PersonDepartureEventHandler)handler).handleEvent((PersonDepartureEvent)event);
                break;
            case ActivityEndEvent:
                ((ActivityEndEventHandler)handler).handleEvent((ActivityEndEvent)event);
                break;
            case ActivityStartEvent:
                ((ActivityStartEventHandler)handler).handleEvent((ActivityStartEvent)event);
                break;
            case TransitDriverStartsEvent:
                ((TransitDriverStartsEventHandler) handler).handleEvent((TransitDriverStartsEvent) event);
                break;
            case PersonStuckEvent:
                ((PersonStuckEventHandler)handler).handleEvent((PersonStuckEvent)event);
                break;
            case PersonMoneyEvent:
                ((PersonMoneyEventHandler)handler).handleEvent((PersonMoneyEvent)event);
                break;
            case AgentWaitingForPtEvent:
                ((AgentWaitingForPtEventHandler)handler).handleEvent((AgentWaitingForPtEvent)event);
                break;
            case PersonEntersVehicleEvent:
                ((PersonEntersVehicleEventHandler)handler).handleEvent((PersonEntersVehicleEvent)event);
                break;
            case PersonLeavesVehicleEvent:
                ((PersonLeavesVehicleEventHandler)handler).handleEvent((PersonLeavesVehicleEvent)event);
                break;
            case VehicleDepartsAtFacilityEvent:
                ((VehicleDepartsAtFacilityEventHandler) handler).handleEvent((VehicleDepartsAtFacilityEvent) event);
                break;
            case VehicleArrivesAtFacilityEvent:
                ((VehicleArrivesAtFacilityEventHandler) handler).handleEvent((VehicleArrivesAtFacilityEvent) event);
                break;
            case UnknownEvent:
            default:
                // TODO - blow up, unknown
                break;
            }
        }

        public void flush() {
            Event event;
            while ((event = queue.poll()) != null) {
                handle(event);
            }
        }

        public void run() {
            while (true) {
                try {
                    handle(queue.take());
                } catch (InterruptedException e) {
                    flush();
                    break;
                }
            }
        }

        public EventHandler getEventHandler() {
            return this.handler;
        }

        public LinkedBlockingDeque<Event> getQueue() {
            return this.queue;
        }

    }

    public ConcurrentEventsManager() {
        this.event_queues = new ArrayList<>();
        this.handlerThreads = new ArrayList<>();

        for (int i = 0; i < EventCode.values().length; i++) {
            this.event_queues.add(new ArrayList<>());
        }
    }

    @Override
    public void processEvent(Event event) {
        EventCode code = event.getEventCode();
        // TODO - I could do this in the background!
        try {
            for (LinkedBlockingDeque<Event> queue : event_queues.get(code.ordinal())) {
                queue.put(event);
            }
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void addHandler(EventHandler handler) {
        Set<EventCode> registeredCodes = new HashSet<>();
        LinkedBlockingDeque<Event> queue = new LinkedBlockingDeque<>();

        Class<?> klass = handler.getClass();
        log.info("adding Event-Handler: " + klass.getName());
        while (klass != Object.class) {
            // for each interface of the current class
            for (Class<?> iface: klass.getInterfaces()) {
                log.info("  " + iface.getName());
                // for each method of the current interface
                for (Method method : iface.getMethods()) {
                    // if the method signature is handleEvent(?) ...
                    if (method.getName().equals("handleEvent") && method.getParameterTypes().length == 1) {
                        Class<?> eventClass = method.getParameterTypes()[0];
                        EventCode code = getEventCodeFromHandler(eventClass);
                        // if this handler is not already registered for this code
                        if (!registeredCodes.contains(code)) {
                            log.info("    > " + eventClass.getName());
                            registeredCodes.add(code);
                            event_queues.get(code.ordinal()).add(queue);
                        }
                    }
                }
            }
            klass = klass.getSuperclass();
        }
        log.info("");

        // creating the thread to handle this event handler.
        HandlerThread thread = new HandlerThread(handler, queue);
        handlerThreads.add(thread);
        thread.start();
    }

    private EventCode getEventCodeFromHandler(Class<?> klass) {
        if (klass ==  LinkLeaveEventHandler.class) {
            return EventCode.LinkLeaveEvent;
        }
        // TODO - implement the dam method!
        return null;

    }

    @Override
    public void removeHandler(EventHandler handler) {
        log.info("removing Event-Handler: " + handler.getClass().getName());
        HandlerThread target = null;
        for (HandlerThread thread : handlerThreads) {
            if (thread.getEventHandler() == handler) {
                target = thread;
                break;
            }
        }

        if (target == null) {
            // TODO - blow up!
        }

        // Remove from threads and queues.
        handlerThreads.remove(target);
        for (ArrayList<LinkedBlockingDeque<Event>> queues : event_queues) {
            queues.remove(target.getQueue());
        }

        // TODO - insert remove handler event into queue.

        try {
            target.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    @Override
    public void resetHandlers(int iteration) {
        log.info("resetting Event-Handlers");
        for (HandlerThread thread : handlerThreads) {
            // TODO - insert reset event
        }

    }

    @Override
    public void initProcessing() {
        // TODO Auto-generated method stub

    }

    @Override
    public void afterSimStep(double time) {
        // TODO Auto-generated method stub

    }

    @Override
    public void finishProcessing() {
        // TODO Auto-generated method stub

    }

}
