/* *********************************************************************** *
 * project: org.matsim.*
 * SingleHandlerEventsManager.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.core.events;

import java.util.HashSet;
import java.util.Set;

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
import org.matsim.api.core.v01.events.VehicleAbortsEvent;
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
import org.matsim.api.core.v01.events.handler.VehicleAbortsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.events.handler.EventHandler;

/**
 * Implementation of an EventsManager that serves exactly one EventHandler.
 * Events that are not handled by that handler are ignored.
 * 
 * @author cdobler
 */
public final class SingleHandlerEventsManager implements EventsManager {

	private static final Logger log = Logger.getLogger(SingleHandlerEventsManager.class);
	
	private final EventHandler eventHandler;
	
	private final boolean[] isHandlerForEvent;
	
	private long counter = 0;
	private long nextCounterMsg = 1;

	private boolean isActive = true;
	
	public SingleHandlerEventsManager(EventHandler eventHandler) {
		this.eventHandler = eventHandler;
		this.isHandlerForEvent = new boolean[getMaxEventIdForHandler() + 1];
		
		if (this.eventHandler instanceof LinkLeaveEventHandler) isHandlerForEvent[LinkLeaveEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof LinkEnterEventHandler) isHandlerForEvent[LinkEnterEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof VehicleEntersTrafficEventHandler) isHandlerForEvent[VehicleEntersTrafficEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof PersonArrivalEventHandler) isHandlerForEvent[PersonArrivalEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof PersonDepartureEventHandler) isHandlerForEvent[PersonDepartureEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof ActivityEndEventHandler) isHandlerForEvent[ActivityEndEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof ActivityStartEventHandler) isHandlerForEvent[ActivityStartEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof TeleportationArrivalEventHandler) isHandlerForEvent[TeleportationArrivalEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof TransitDriverStartsEventHandler) isHandlerForEvent[TransitDriverStartsEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof PersonStuckEventHandler) isHandlerForEvent[PersonStuckEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof PersonMoneyEventHandler) isHandlerForEvent[PersonMoneyEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof AgentWaitingForPtEventHandler) isHandlerForEvent[AgentWaitingForPtEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof PersonEntersVehicleEventHandler) isHandlerForEvent[PersonEntersVehicleEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof PersonLeavesVehicleEventHandler) isHandlerForEvent[PersonLeavesVehicleEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof VehicleDepartsAtFacilityEventHandler) isHandlerForEvent[VehicleDepartsAtFacilityEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof VehicleArrivesAtFacilityEventHandler) isHandlerForEvent[VehicleArrivesAtFacilityEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof VehicleLeavesTrafficEventHandler) isHandlerForEvent[VehicleLeavesTrafficEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof VehicleAbortsEventHandler) isHandlerForEvent[VehicleAbortsEvent.EVENT_ID] = true;
		if (this.eventHandler instanceof BasicEventHandler) isHandlerForEvent[Event.EVENT_ID] = true;

		// identify the implemented Handler Interfaces
		Set<Class<?>> addedHandlers = new HashSet<Class<?>>();
		Class<?> test = eventHandler.getClass();
		log.info("adding Event-Handler: " + test.getName());
		while (test != Object.class) {
			for (Class<?> theInterface: test.getInterfaces()) {
				if (!addedHandlers.contains(theInterface)) {
					log.info("  " + theInterface.getName());
				}
			}
			test = test.getSuperclass();
		}
		log.info("");
	}
	
	private int getMaxEventIdForHandler() {
		int max = 0;
		max = LinkLeaveEvent.EVENT_ID > max ? LinkLeaveEvent.EVENT_ID : max;
		max = LinkEnterEvent.EVENT_ID > max ? LinkEnterEvent.EVENT_ID : max; 
		max = VehicleEntersTrafficEvent.EVENT_ID > max ? VehicleEntersTrafficEvent.EVENT_ID : max;
		max = PersonArrivalEvent.EVENT_ID > max ? PersonArrivalEvent.EVENT_ID : max;
		max = PersonDepartureEvent.EVENT_ID > max ? PersonDepartureEvent.EVENT_ID : max;
		max = ActivityEndEvent.EVENT_ID > max ? ActivityEndEvent.EVENT_ID : max;
		max = ActivityStartEvent.EVENT_ID > max ? ActivityStartEvent.EVENT_ID : max;
		max = TeleportationArrivalEvent.EVENT_ID > max ? TeleportationArrivalEvent.EVENT_ID : max;
		max = TransitDriverStartsEvent.EVENT_ID > max ? TransitDriverStartsEvent.EVENT_ID : max;
		max = PersonStuckEvent.EVENT_ID > max ? PersonStuckEvent.EVENT_ID : max;
		max = PersonMoneyEvent.EVENT_ID > max ? PersonMoneyEvent.EVENT_ID : max;
		max = AgentWaitingForPtEvent.EVENT_ID > max ? AgentWaitingForPtEvent.EVENT_ID : max;
		max = PersonEntersVehicleEvent.EVENT_ID > max ? PersonEntersVehicleEvent.EVENT_ID : max;
		max = PersonLeavesVehicleEvent.EVENT_ID > max ? PersonLeavesVehicleEvent.EVENT_ID : max;
		max = VehicleDepartsAtFacilityEvent.EVENT_ID > max ? VehicleDepartsAtFacilityEvent.EVENT_ID : max;
		max = VehicleArrivesAtFacilityEvent.EVENT_ID > max ? VehicleArrivesAtFacilityEvent.EVENT_ID : max;
		max = VehicleLeavesTrafficEvent.EVENT_ID > max ? VehicleLeavesTrafficEvent.EVENT_ID : max;
		max = VehicleAbortsEvent.EVENT_ID > max ? VehicleAbortsEvent.EVENT_ID : max;
		max = Event.EVENT_ID > max ? Event.EVENT_ID : max;		
		return max;
	}
	
	public void deactivate() {
		this.isActive = false;
	}
	
	@Override
	public void processEvent(final Event event) {
		
		if (!this.isActive) return;
		
		this.counter++;
		if (this.counter == this.nextCounterMsg) {
			this.nextCounterMsg *= 2;
			log.info(" event # " + this.counter);
		}
		callHandlerFast(event);
	}

	@Override
	public void addHandler(final EventHandler handler) {
		throw new UnsupportedOperationException("This implementation supports only a single EventHandler which "
				+ "has to be provided upon creation. Aborting!");
	}

	@Override
	public void removeHandler(final EventHandler handler) {
		throw new UnsupportedOperationException("This implementation supports only a single EventHandler which "
				+ "has to be provided upon creation. Aborting!");
	}

	@Override
	public void resetHandlers(final int iteration) {
		log.info("resetting Event-Handler");
		this.counter = 0;
		this.nextCounterMsg = 1;
		this.eventHandler.reset(iteration);
	}

	@Override
	public void initProcessing() {
		// nothing to do in this implementation
	}

	@Override
	public void afterSimStep(double time) {
		// Maybe we can move stuff from the ParallelEventsManager here?
	}

	@Override
	public void finishProcessing() {
		// nothing to do in this implementation
	}

	public EventHandler getEventHandler() {
		return this.eventHandler;
	}
	
	public String getEventHandlerClassName() {
		return this.eventHandler.getClass().toString();
	}
	
	// this method is purely for performance reasons and need not be implemented
	private void callHandlerFast(final Event ev) {

		// TODO - add a debug method to make sure no unexpected event is received
		if (ev.getEventTypeId() >= isHandlerForEvent.length || !isHandlerForEvent[ev.getEventTypeId()] ) {
			return;
		}
		
		switch(ev.getEventTypeId()) {
		case LinkLeaveEvent.EVENT_ID:
			if (isHandlerForEvent[LinkLeaveEvent.EVENT_ID])
				((LinkLeaveEventHandler) this.eventHandler).handleEvent((LinkLeaveEvent)ev);
			return;
		case LinkEnterEvent.EVENT_ID:
			if (isHandlerForEvent[LinkEnterEvent.EVENT_ID])
				((LinkEnterEventHandler) this.eventHandler).handleEvent((LinkEnterEvent)ev);
			return;
		case VehicleEntersTrafficEvent.EVENT_ID:
			if (isHandlerForEvent[VehicleEntersTrafficEvent.EVENT_ID])
				((VehicleEntersTrafficEventHandler) this.eventHandler).handleEvent((VehicleEntersTrafficEvent)ev);
			return;
		case PersonArrivalEvent.EVENT_ID:
			if (isHandlerForEvent[PersonArrivalEvent.EVENT_ID])
				((PersonArrivalEventHandler) this.eventHandler).handleEvent((PersonArrivalEvent)ev);
			return;
		case PersonDepartureEvent.EVENT_ID:
			if (isHandlerForEvent[PersonDepartureEvent.EVENT_ID])
				((PersonDepartureEventHandler) this.eventHandler).handleEvent((PersonDepartureEvent)ev);
			return;
		case ActivityEndEvent.EVENT_ID:
			if (isHandlerForEvent[ActivityEndEvent.EVENT_ID])
				((ActivityEndEventHandler) this.eventHandler).handleEvent((ActivityEndEvent)ev);
			return;
		case ActivityStartEvent.EVENT_ID:
			if (isHandlerForEvent[ActivityStartEvent.EVENT_ID])
				((ActivityStartEventHandler) this.eventHandler).handleEvent((ActivityStartEvent)ev);
			return;
		case TeleportationArrivalEvent.EVENT_ID:
			if (isHandlerForEvent[TeleportationArrivalEvent.EVENT_ID])
				((TeleportationArrivalEventHandler) this.eventHandler).handleEvent((TeleportationArrivalEvent)ev);
			return;
		case TransitDriverStartsEvent.EVENT_ID:
			if (isHandlerForEvent[TransitDriverStartsEvent.EVENT_ID])
				((TransitDriverStartsEventHandler) this.eventHandler).handleEvent((TransitDriverStartsEvent) ev);
			return;
		case PersonStuckEvent.EVENT_ID:
			if (isHandlerForEvent[PersonStuckEvent.EVENT_ID])
				((PersonStuckEventHandler) this.eventHandler).handleEvent((PersonStuckEvent)ev);
			return;
		case PersonMoneyEvent.EVENT_ID:
			if (isHandlerForEvent[PersonMoneyEvent.EVENT_ID])
				((PersonMoneyEventHandler) this.eventHandler).handleEvent((PersonMoneyEvent)ev);
			return;
		case AgentWaitingForPtEvent.EVENT_ID:
			if (isHandlerForEvent[AgentWaitingForPtEvent.EVENT_ID])
				((AgentWaitingForPtEventHandler) this.eventHandler).handleEvent((AgentWaitingForPtEvent)ev);
			return;
		case PersonEntersVehicleEvent.EVENT_ID:
			if (isHandlerForEvent[PersonEntersVehicleEvent.EVENT_ID])
				((PersonEntersVehicleEventHandler) this.eventHandler).handleEvent((PersonEntersVehicleEvent)ev);
			return;
		case PersonLeavesVehicleEvent.EVENT_ID:
			if (isHandlerForEvent[PersonLeavesVehicleEvent.EVENT_ID])
				((PersonLeavesVehicleEventHandler) this.eventHandler).handleEvent((PersonLeavesVehicleEvent)ev);
			return;
		case VehicleDepartsAtFacilityEvent.EVENT_ID:
			if (isHandlerForEvent[VehicleDepartsAtFacilityEvent.EVENT_ID])
				((VehicleDepartsAtFacilityEventHandler) this.eventHandler).handleEvent((VehicleDepartsAtFacilityEvent) ev);
			return;
		case VehicleArrivesAtFacilityEvent.EVENT_ID:
			if (isHandlerForEvent[VehicleArrivesAtFacilityEvent.EVENT_ID])
				((VehicleArrivesAtFacilityEventHandler) this.eventHandler).handleEvent((VehicleArrivesAtFacilityEvent) ev);
			return;
		case VehicleLeavesTrafficEvent.EVENT_ID:
			if (isHandlerForEvent[VehicleLeavesTrafficEvent.EVENT_ID])
				((VehicleLeavesTrafficEventHandler) this.eventHandler).handleEvent((VehicleLeavesTrafficEvent) ev);
			return;
		case VehicleAbortsEvent.EVENT_ID:
			if (isHandlerForEvent[VehicleAbortsEvent.EVENT_ID])
				((VehicleAbortsEventHandler) this.eventHandler).handleEvent((VehicleAbortsEvent) ev);
			return;
		case Event.EVENT_ID:
			if (isHandlerForEvent[Event.EVENT_ID])
				((BasicEventHandler) this.eventHandler).handleEvent(ev);
			return;
		}
	}
}
