package org.matsim.core.utils.quickevents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.matsim.core.mobsim.nqsim.Agent;

public class StringlyEvent {

    private static StringlyEvent ActivityOfType(String type, String person, String link, String actType) {
        StringlyEvent event = new StringlyEvent();
        event.nqSimEventType = Agent.SleepForType;
        event.type = type;
        event.person = person;
        event.link = link;
        event.actType = actType;
        return event;
    }

    public static StringlyEvent ActivityStart(String person, String link, String actType) {
        return ActivityOfType("actstart", person, link, actType);
    }

    public static StringlyEvent ActivityEnd(String person, String link, String actType) {
        return ActivityOfType("actend", person, link, actType);
    }

    private static StringlyEvent LegEventOfType(String type, String person, String link, String legMode) {
        StringlyEvent event = new StringlyEvent();
        event.nqSimEventType = Agent.LinkType;
        event.type = type;
        event.person = person;
        event.link = link;
        event.legMode = legMode;
        return event;
    }

    public static StringlyEvent Departure(String person, String link, String legMode) {
        return LegEventOfType("departure", person, link, legMode);
    }

    public static StringlyEvent Arrival(String person, String link, String legMode) {
        return LegEventOfType("arrival", person, link, legMode);
    }

    public static StringlyEvent Travelled(String person, String distance) {
        StringlyEvent event = new StringlyEvent();
        event.nqSimEventType = Agent.SleepForType;
        event.type = "travelled";
        event.person = person;
        event.distance = distance;
        return event;
    }

    private static StringlyEvent VehicleInteraction(String type, String person, String vehicle) {
        StringlyEvent event = new StringlyEvent();
        event.nqSimEventType = Agent.LinkType;
        event.type = type;
        event.person = person;
        event.vehicle = vehicle;
        return event;
    }

    public static StringlyEvent PersonEntersVehicle(String person, String vehicle) {
        return VehicleInteraction("PersonEntersVehicle", person, vehicle);
    }

    public static StringlyEvent PersonLeavesVehicle(String person, String vehicle) {
        return VehicleInteraction("PersonLeavesVehicle", person, vehicle);
    }

    public static StringlyEvent TrafficInteraction(String type, String person, String link, String vehicle, String networkMode, String relativePosition) {
        StringlyEvent event = new StringlyEvent();
        event.nqSimEventType = Agent.LinkType;
        event.type = type;
        event.person = person;
        event.link = link;
        event.vehicle = vehicle;
        event.networkMode = networkMode;
        event.relativePosition = relativePosition;
        return event;
    }

    public static StringlyEvent VehicleEntersTraffic(String person, String link, String vehicle, String networkMode, String relativePosition) {
        return TrafficInteraction("vehicle enters traffic", person, link, vehicle, networkMode, relativePosition);
    }

    public static StringlyEvent VehicleLeavesTraffic(String person, String link, String vehicle, String networkMode, String relativePosition) {
        return TrafficInteraction("vehicle leaves traffic", person, link, vehicle, networkMode, relativePosition);
    }

    private static StringlyEvent LinkInteraction(String type, String vehicle, String link) {
        StringlyEvent event = new StringlyEvent();
        event.nqSimEventType = Agent.LinkType;
        event.type = type;
        event.vehicle = vehicle;
        event.link = link;
        return event;
    }

    public static StringlyEvent EnterLink(String vehicle, String link) {
        return LinkInteraction("entered link", vehicle, link);
    }

    public static StringlyEvent LeaveLink(String vehicle, String link) {
        return LinkInteraction("left link", vehicle, link);
    }

    private static StringlyEvent FacilityInteraction(String type, String vehicle, String facility, String delay) {
        StringlyEvent event = new StringlyEvent();
        event.nqSimEventType = Agent.SleepForType;
        event.type = type;
        event.vehicle = vehicle;
        event.facility = facility;
        event.delay = delay;
        return event;
    }

    public static StringlyEvent FacilityArrival(String vehicle, String facility, String delay) {
        return FacilityInteraction("VehicleArrivesAtFacility", vehicle, facility, delay);
    }

    public static StringlyEvent FacilityDeparture(String vehicle, String facility, String delay) {
        return FacilityInteraction("VehicleDepartsAtFacility", vehicle, facility, delay);
    }

    public static boolean isEquivalent(String one, String two) {
        if (one == null && two == null) {
            return true;
        }
        if (one == null || two == null) {
            return false;
        }
        return one.equals(two);
    }

    @JsonIgnore
    public int nqSimEventType;

    @JacksonXmlProperty(isAttribute = true)
    public String time; // --> payload 0

    @JacksonXmlProperty(isAttribute = true)
    public String type; // --> header

    @JacksonXmlProperty(isAttribute = true)
    public String person; // --> payload 1

    @JacksonXmlProperty(isAttribute = true)
    public String link; // --> payload 2

    @JacksonXmlProperty(isAttribute = true)
    public String actType; // --> header + payload 3

    @JacksonXmlProperty(isAttribute = true)
    public String legMode; // --> header

    @JacksonXmlProperty(isAttribute = true)
    public String vehicle; // --> payload 4

    @JacksonXmlProperty(isAttribute = true)
    public String facility; // --> payload 5

    @JacksonXmlProperty(isAttribute = true)
    public String delay; // --> payload 6

    @JacksonXmlProperty(isAttribute = true)
    public String networkMode; // --> header

    @JacksonXmlProperty(isAttribute = true)
    public String relativePosition; // --> payload 7

    @JacksonXmlProperty(isAttribute = true)
    public String distance; // --> payload 8

    @JacksonXmlProperty(isAttribute = true)
    public String driverId; // --> payload 9

    @JacksonXmlProperty(isAttribute = true)
    public String vehicleId; // --> payload 10

    @JacksonXmlProperty(isAttribute = true)
    public String transitLineId; // --> payload 11

    @JacksonXmlProperty(isAttribute = true)
    public String transitRouteId; // --> payload 12

    @JacksonXmlProperty(isAttribute = true)
    public String departureId; // --> payload 13

    @JacksonXmlProperty(isAttribute = true)
    public String agent; // --> payload 14

    @JacksonXmlProperty(isAttribute = true)
    public String atStop; // --> payload 15

    @JacksonXmlProperty(isAttribute = true)
    public String destinationStop; // --> payload 16

    @JsonIgnore
    public boolean equals(StringlyEvent ref, boolean exactTimeRequired) {
        if (exactTimeRequired && !isEquivalent(time, ref.time)) {
            return false;
        }
        return isEquivalent(type, ref.type)
            && isEquivalent(person, ref.person)
            && isEquivalent(link, ref.link)
            && isEquivalent(actType, ref.actType)
            && isEquivalent(legMode, ref.legMode)
            && isEquivalent(vehicle, ref.vehicle)
            && isEquivalent(facility, ref.facility)
            && isEquivalent(delay, ref.delay)
            && isEquivalent(networkMode, ref.networkMode)
            && isEquivalent(relativePosition, ref.relativePosition)
            && isEquivalent(distance, ref.distance)
            && isEquivalent(driverId, ref.driverId)
            && isEquivalent(vehicleId, ref.vehicleId)
            && isEquivalent(transitLineId, ref.transitLineId)
            && isEquivalent(transitRouteId, ref.transitRouteId)
            && isEquivalent(departureId, ref.departureId)
            && isEquivalent(agent, ref.agent)
            && isEquivalent(atStop, ref.atStop)
            && isEquivalent(destinationStop, ref.destinationStop);
    }

    @JsonIgnore
    public String getStringHeader() {
        return String.format(
            "t=%s:a=%s:l=%s:n=%s",
            this.type,
            this.actType,
            this.legMode,
            this.networkMode
        );
    }

    @JsonIgnore
    public String toString() {
        if (transitLineId == null) {
            if (facility == null) {
                if (agent == null) {
                    return String.format(
                            "t=%s:type=%s:p=%s:l=%s(%s,%s,%s,%s,%s,%s)",
                            time,
                            type,
                            person,
                            link,
                            actType,
                            legMode,
                            vehicle,
                            networkMode,
                            relativePosition,
                            distance
                    );
                }
                return String.format(
                        "t=%s:type=%s:agent=%s:atStop=%s:destStop=%s",
                        time,
                        type,
                        agent,
                        atStop,
                        destinationStop
                );
            }
            return String.format(
                    "t=%s:type=%s:v=%s:f=%s:delay=%s",
                    time,
                    type,
                    vehicle,
                    facility,
                    delay
            );
        }
        return String.format(
                "t=%s:type=%s:d=%s:v=%s:line=%s:route=%s:departure=%s",
                time,
                type,
                driverId,
                vehicleId,
                transitLineId,
                transitRouteId,
                departureId
        );

    }
}
