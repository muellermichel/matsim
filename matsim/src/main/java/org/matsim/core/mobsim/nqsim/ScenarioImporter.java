package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.population.routes.AbstractRoute;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.facilities.ActivityFacility;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;

public class ScenarioImporter {

    // Scenario loaded by matsim;
    private final Scenario scenario;

    private final int sim_threads;

    // Maps a mastim link to a qsim link and vice versa.
    private Map<String, Integer> matsim_to_nqsim_Link;
    private Map<Integer, String> nqsim_to_matsim_Link;

    // Maps a matsim id to a qsim agent and vice versa.
    private Map<Integer, String> nqsim_to_matsim_Agent;
    private Map<String, Integer> matsim_to_qsim_Agent;

        // Maps a matsim id to a qsim route and vice versa.
    private Map<Integer, String> nqsim_to_matsim_Route;
    private Map<String, Integer> matsim_to_nqsim_Route;

    // localStopIds.get(matsim route id).get(matsim stop id) -> local stop id
    private Map<String, Map<String, Integer>> localStopIds;

    // These are data structures that are actually used during the sim.
    private Link[] qsim_links;
    private Agent[] qsim_agents;
    private Realm[] qsim_realms;
    // pt stops indexed by route id and stop id
    private ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> qsim_stops;
    // matsim events indexed by nqsim agent id and by event id
    private ArrayList<ArrayList<Event>> matsim_events;

    public ScenarioImporter(Scenario scenario, int sim_threads) {
        this.scenario = scenario;
        this.sim_threads = sim_threads;
    }

    public World generate() throws Exception {
        generateLinks();
        genereteRoutes();
        genereteStops();
        generateAgents();
        generateRealms();
        return new World(sim_threads, qsim_realms, qsim_links, qsim_agents);
    }

    private void generateLinks() {
        Network network = scenario.getNetwork();
        Collection<? extends org.matsim.api.core.v01.network.Link> matsim_links =
            network.getLinks().values();
        int counter = 0;
        qsim_links = new Link[matsim_links.size()];
        matsim_to_nqsim_Link = new HashMap<>(matsim_links.size());
        nqsim_to_matsim_Link = new HashMap<>(matsim_links.size());

        for (org.matsim.api.core.v01.network.Link matsim_link : matsim_links) {
            int capacity = (int) Math.round(matsim_link.getCapacity());
            int length = Math.max(1, (int) Math.round(matsim_link.getLength()));
            int speed = Math.max(1, (int) Math.round(matsim_link.getFreespeed()));
            // TODO - what about the flow and lanes?
            int flow = (int) Math.round(matsim_link.getFlowCapacityPerSec());
            int lanes = (int) Math.round(matsim_link.getNumberOfLanes());
            String matsim_id = matsim_link.getId().toString();
            int qsim_id = counter++;

            if (qsim_id > World.MAX_LINK_ID) {
                throw new RuntimeException("exceeded maximum number of links");
            }

            Link qsim_link = new Link(qsim_id, capacity, length, speed);
            qsim_links[qsim_id] = qsim_link;
            matsim_to_nqsim_Link.put(matsim_id, qsim_id);
            nqsim_to_matsim_Link.put(qsim_id, matsim_id);
        }
    }

    private void genereteStops() {
        localStopIds = new HashMap<>();
        TransitSchedule ts = scenario.getTransitSchedule();
        for (TransitLine tl: ts.getTransitLines().values()) {
            for (TransitRoute tr : tl.getRoutes().values()) {
                int nstops = tr.getStops().size();
                int stopcounter = 0;
                Map<String, Integer> stopids = new HashMap<>(nstops);
                for (TransitRouteStop trs : tr.getStops()) {
                    String mid = trs.getStopFacility().getId().toString();
                    int qid = stopcounter;
                    stopids.put(mid, qid);
                    stopcounter += 1;
                }
                localStopIds.put(tr.getId().toString(), stopids);
            }
        }
    }

    private void genereteRoutes() {
        nqsim_to_matsim_Route = new HashMap<>();
        matsim_to_nqsim_Route = new HashMap<>();
        qsim_stops = new ArrayList<>();
        TransitSchedule ts = scenario.getTransitSchedule();
        for (TransitLine tl: ts.getTransitLines().values()) {
            for (TransitRoute tr : tl.getRoutes().values()) {
                String matsim_rid = tr.getId().toString();
                int qsim_rid = nqsim_to_matsim_Route.size();
                ArrayList<ConcurrentLinkedQueue<Agent>> route = new ArrayList<>();
                nqsim_to_matsim_Route.put(qsim_rid, matsim_rid);
                matsim_to_nqsim_Route.put(matsim_rid, qsim_rid);
                for (int i = 0; i < tr.getStops().size(); i++) {
                    route.add(new ConcurrentLinkedQueue<>());
                }
                qsim_stops.add(route);
            }
        }
    }

    private void generateRealms() throws Exception {
        ArrayList<ConcurrentLinkedQueue<Link>> delayedLinksByWakeupTime = 
            new ArrayList<>();
        ArrayList<ConcurrentLinkedQueue<Agent>> delayedAgentsByWakeupTime = 
            new ArrayList<>();
        qsim_realms = new Realm[1];
        qsim_realms[0] = new Realm(
                qsim_links, 
                delayedLinksByWakeupTime, 
                delayedAgentsByWakeupTime, 
                qsim_stops,
                matsim_events);

        for (int i = 0; i < World.ACT_SLOTS + 1; i++) {
            delayedLinksByWakeupTime.add(new ConcurrentLinkedQueue<>());
            delayedAgentsByWakeupTime.add(new ConcurrentLinkedQueue<>());
        }

        // Put agents in their initial location (link or activity center)
        for (Agent agent : qsim_agents) {
            // Some agents might not have plans.
            if (agent.plan.length == 0) {
                continue;
            }
            long planentry = agent.plan()[0];
            int type = Agent.getPlanHeader(planentry);
            int element = Agent.getPlanPayload(planentry);
            switch (type) {
                case Agent.LinkType:
                    int linkid = Agent.getLinkPlanEntry(element);
                    int velocity = Agent.getVelocityPlanEntry(element);
                    Link link = qsim_links[linkid];
                    agent.linkFinishTime = link.length() / Math.min(velocity, link.velocity());
                    link.push(agent);
                    break;
                case Agent.SleepForType:
                case Agent.SleepUntilType:
                    qsim_realms[0].delayedAgents().get(element).add(agent);
                    break;
                case Agent.AccessType:
                case Agent.StopType:
                case Agent.EgressType:
                case Agent.RouteType:
                default:
                    Realm.log(0, String.format("ERROR -> unknow plan element type %d",type));
            }
        }

        for (int i = 0; i < qsim_links.length; i++) {
            int nextwakeup = qsim_links[i].nexttime();
            if (nextwakeup > 0) {
                qsim_realms[0].delayedLinks().get(nextwakeup).add(qsim_links[i]);
            }
        }
    }

    private void processPlanActivity(
            Id<Person> id,
            ArrayList<Long> flatplan,
            ArrayList<Event> events,
            Activity act) {
        int time = 0;
        Id<ActivityFacility> facid = act.getFacilityId();
        String type = act.getType();

        if (Double.isFinite(act.getEndTime())) {
            time = (int) Math.round(act.getEndTime());
        } else if (Double.isFinite(act.getMaximumDuration())) {
            time = (int) Math.round(act.getMaximumDuration());
        }
        events.add(new ActivityStartEvent(0, id, act.getLinkId(), facid, type));
        flatplan.add(Agent.prepareSleepForEntry(events.size() - 1, (int)time));
        events.add(new ActivityEndEvent(0, id, act.getLinkId(), facid, type));
    }

    private void processPlanNetworkRoute(
            Id<Person> id,
            ArrayList<Long> flatplan,
            ArrayList<Event> events,
            Leg leg,
            NetworkRoute netroute) {
        Id<org.matsim.api.core.v01.network.Link> startLId = netroute.getStartLinkId();
        Id<org.matsim.api.core.v01.network.Link> endLId = netroute.getEndLinkId();
        Map<Id<Vehicle>, Vehicle> vehicles = scenario.getVehicles().getVehicles();
        Vehicle v = vehicles.get(netroute.getVehicleId());
        Id<Vehicle> vid = v == null ?
            Id.createVehicleId(id.toString()) : v.getId();
        int velocity = v == null ?
            World.MAX_VEHICLE_VELOCITY : (int) Math.round(v.getType().getMaximumVelocity());
        int egressId = matsim_to_nqsim_Link.get(endLId.toString());
        events.add(new PersonEntersVehicleEvent(0, id, vid));
        events.add(new VehicleEntersTrafficEvent(0, id, startLId, vid, leg.getMode(), 1));
        events.add(new LinkLeaveEvent(0, vid, startLId));
        for (Id<org.matsim.api.core.v01.network.Link> linkid : netroute.getLinkIds()) {
            int linkId = matsim_to_nqsim_Link.get(linkid.toString());
            events.add(new LinkEnterEvent(0, vid, linkid));
            flatplan.add(Agent.prepareLinkEntry(events.size() - 1, linkId, velocity));
            events.add(new LinkLeaveEvent(0, vid, linkid));
        }
        events.add(new LinkEnterEvent(0, vid, endLId));
        events.add(new VehicleLeavesTrafficEvent(0, id, endLId, vid, leg.getMode(), 1));
        events.add(new PersonLeavesVehicleEvent(0, id, vid));
        flatplan.add(Agent.prepareLinkEntry(events.size() - 1, egressId, velocity));
    }

    private void processPlanTransitRoute(
            Id<Person> id,
            ArrayList<Long> flatplan,
            ArrayList<Event> events,
            ExperimentalTransitRoute troute) {
        String access = troute.getAccessStopId().toString();
        String egress = troute.getEgressStopId().toString();
        String routeid = troute.getRouteId().toString();
        int routeId = matsim_to_nqsim_Route.get(routeid);
        int accessid = localStopIds.get(routeid).get(access);
        int egressid = localStopIds.get(routeid).get(egress);
        // Add public transport access
        // TODO - fix this!
        flatplan.add(Agent.prepareAccessEntry(-1, routeId, accessid));
        flatplan.add(Agent.prepareEgressEntry(-1, egressid));
    }

    private void processPlanElement(
            Id<Person> id,
            ArrayList<Long> flatplan,
            ArrayList<Event> events,
            PlanElement element) {
        if (element instanceof Leg) {
            Leg leg = (Leg) element;
            Route route = leg.getRoute();
            if (route == null) return;
            events.add(new PersonDepartureEvent(0, id, route.getStartLinkId(), leg.getMode()));
            // Network circuit
            if (route instanceof NetworkRoute) {
                processPlanNetworkRoute(id, flatplan, events, leg, (NetworkRoute) route);
            }
            // Public transport (vehicles)
            else if (route instanceof ExperimentalTransitRoute) {
                processPlanTransitRoute(id, flatplan, events, (ExperimentalTransitRoute) route);
            }
            // walking, cycling, riding, teleporting
            else if (route instanceof AbstractRoute) {
                int time = (int) Math.round(route.getTravelTime());
                flatplan.add(Agent.prepareSleepForEntry(events.size() - 1, time));
                events.add(new TeleportationArrivalEvent(0, id, route.getDistance()));
            }
            else {
                throw new RuntimeException ("Unknown route " + route.getClass().toString());
            }

            events.add(new PersonArrivalEvent(0, id, route.getEndLinkId(), leg.getMode()));
        } else if (element instanceof Activity) {
            processPlanActivity(id, flatplan, events, (Activity) element);
        } else {
            throw new RuntimeException ("Unknown plan element " + element);
        }
    }

    private void generateAgent(
            String matsim_id,
            int capacity,
            ArrayList<Long> flatplan,
            ArrayList<Event> events) {
        if (events.size() >= World.MAX_EVENTS_AGENT) {
            throw new RuntimeException("exceeded maximum number of agent events");
        }

        // Convert flat plan to long (native type) plan. // TODO - is this really necessary?
        long[] longplan = new long[flatplan.size()];
        for (int i = 0; i < longplan.length; i++) {
            longplan[i] = flatplan.get(i);
        }
        Agent agent = new Agent(matsim_to_qsim_Agent.size(), capacity, longplan);
        matsim_to_qsim_Agent.put(matsim_id, agent.id);
        nqsim_to_matsim_Agent.put(agent.id, matsim_id);
        matsim_events.add(events);
        qsim_agents[agent.id] = agent;
    }

    private void generateVehicleTrip(
            Map<Vehicle, ArrayList<Long>> plans,
            Map<Vehicle, ArrayList<Event>> events,
            TransitLine tl,
            TransitRoute tr,
            Departure depart) {
        List<TransitRouteStop> trs = tr.getStops();
        TransitRouteStop next = trs.get(0);
        int lstopid = 0;
        int rid = matsim_to_nqsim_Route.get(tr.getId().toString());
        Vehicle v = scenario.getTransitVehicles().getVehicles().get(depart.getVehicleId());
        VehicleType vt = v.getType();
        if (!plans.containsKey(v)) {
            plans.put(v, new ArrayList<>());
            events.put(v, new ArrayList<>());
        }
        ArrayList<Long> flatplan = plans.get(v);
        ArrayList<Event> flatevents = events.get(v);
        int velocity = (int) Math.round(v.getType().getMaximumVelocity());
        NetworkRoute nr = tr.getRoute();
        int startid = matsim_to_nqsim_Link.get(nr.getStartLinkId().toString());
        int endid = matsim_to_nqsim_Link.get(nr.getEndLinkId().toString());
        Id<Person> driverid = Id.createPersonId("pt_" + v.getId() + "_" + vt.getId());
        String legmode = "car"; // TODO - get a real leg mode!

        // Sleep until the time of departure
        flatplan.add(Agent.prepareSleepUntilEntry(-1, (int) Math.round(depart.getDepartureTime())));

        // Prepare to leave
        flatevents.add(new TransitDriverStartsEvent(
            0, driverid, v.getId(), tl.getId(), tr.getId(), depart.getId()));
        flatevents.add(new PersonDepartureEvent(
            0, driverid, nr.getStartLinkId(), legmode));
        flatevents.add(new PersonEntersVehicleEvent(
            0, driverid, v.getId()));
        flatevents.add(new VehicleEntersTrafficEvent(
            0, driverid, nr.getStartLinkId(), v.getId(), legmode, 1));
        flatplan.add(Agent.prepareRouteEntry(events.size() - 1, rid));

        // Adding first link and possibly the first stop.
        if (next.getStopFacility().getLinkId().equals(nr.getStartLinkId())) {
            flatevents.add(new VehicleArrivesAtFacilityEvent(
                0, v.getId(), next.getStopFacility().getId(), next.getArrivalOffset()));
            flatevents.add(new VehicleDepartsAtFacilityEvent(
                0, v.getId(), next.getStopFacility().getId(), next.getDepartureOffset()));
            flatevents.add(new LinkLeaveEvent(
                0, v.getId(), nr.getStartLinkId()));
            flatplan.add(Agent.prepareStopEntry(-1, lstopid++));
            next = trs.get(lstopid);
        }
        flatplan.add(Agent.prepareLinkEntry(events.size() - 1, startid, velocity));

        // For each link (exclucing the first and the last)
        for (Id<org.matsim.api.core.v01.network.Link> link : nr.getLinkIds()) {
            int linkid = matsim_to_nqsim_Link.get(link.toString());
            flatevents.add(new LinkEnterEvent(0, v.getId(), link));
            // Adding link and possibly a stop.
            if (next.getStopFacility().getLinkId().equals(link)) {
                flatevents.add(new VehicleArrivesAtFacilityEvent(
                    0, v.getId(), next.getStopFacility().getId(), next.getArrivalOffset()));
                flatevents.add(new VehicleDepartsAtFacilityEvent(
                    0, v.getId(), next.getStopFacility().getId(), next.getDepartureOffset()));
                flatplan.add(Agent.prepareStopEntry(- 1, lstopid++));
                next = trs.get(lstopid);
            }
            flatplan.add(Agent.prepareLinkEntry(events.size() - 1, linkid, velocity));
            flatevents.add(new LinkLeaveEvent(0, v.getId(), link));
        }

        // Adding last link and possibly the last stop.
        flatevents.add(new LinkEnterEvent(0, v.getId(), nr.getEndLinkId()));
        flatplan.add(Agent.prepareLinkEntry(events.size() - 1, endid, velocity));
        if (next.getStopFacility().getLinkId().equals(nr.getEndLinkId())) {
            flatevents.add(new VehicleArrivesAtFacilityEvent(
                0, v.getId(), next.getStopFacility().getId(), next.getArrivalOffset()));
            flatevents.add(new VehicleDepartsAtFacilityEvent(
                0, v.getId(), next.getStopFacility().getId(), next.getDepartureOffset()));
            flatplan.add(Agent.prepareStopEntry(events.size() - 1, lstopid++));
        }
        flatevents.add(new VehicleLeavesTrafficEvent(
            0, driverid, nr.getEndLinkId(), v.getId(), legmode, 1));
        flatevents.add(new PersonLeavesVehicleEvent(0, driverid, v.getId()));
        flatevents.add(new PersonArrivalEvent(
            0, driverid, nr.getEndLinkId(), legmode));
    }

    private void generateVehicles() {
        Map<Id<Vehicle>, Vehicle> vehicles = scenario.getTransitVehicles().getVehicles();
        Map<Vehicle, ArrayList<Long>> plans = new HashMap<>(vehicles.size());
        Map<Vehicle, ArrayList<Event>> events = new HashMap<>(vehicles.size());
        TransitSchedule ts = scenario.getTransitSchedule();
        // Create plans for vehicles.
        for (TransitLine tl: ts.getTransitLines().values()) {
            for (TransitRoute tr : tl.getRoutes().values()) {
                for (Departure depart : tr.getDepartures().values()) {
                    generateVehicleTrip(plans, events, tl, tr, depart);
                }
            }
        }
        // Create agents from vehicles.
        for (Map.Entry<Vehicle, ArrayList<Long>> entry : plans.entrySet()) {
            Vehicle v = entry.getKey();
            VehicleCapacity vc = v.getType().getCapacity();
            String vid = v.getId().toString();
            int capacity = vc.getSeats() + vc.getStandingRoom();
            generateAgent(vid, capacity, entry.getValue(), events.get(v));
        }
    }

    private void generatePersons() {
        Population population = scenario.getPopulation();
        for (Person person : population.getPersons().values()) {
            // Convert matsim plan to flat plan.
            ArrayList<Long> flatplan = new ArrayList<>();
            ArrayList<Event> events = new ArrayList<>();
            for (PlanElement element: person.getSelectedPlan().getPlanElements()) {
                processPlanElement(person.getId(), flatplan, events, element);
            }
            generateAgent(person.getId().toString(), 0, flatplan, events);
        }
    }

    private void generateAgents() {
        matsim_to_qsim_Agent = new HashMap<>();
        nqsim_to_matsim_Agent = new HashMap<>();
        matsim_events = new ArrayList<>();
        qsim_agents = new Agent[
            scenario.getPopulation().getPersons().size() +
            scenario.getTransitVehicles().getVehicles().size()];
        generatePersons();
        generateVehicles();
    }

    public Map<Integer, String> getNqsimToMatsimAgent() {
        return nqsim_to_matsim_Agent;
    }

    public ArrayList<ArrayList<Event>> getEvents() {
        return this.matsim_events;
    }
}
