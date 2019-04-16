package org.matsim.core.mobsim.hermes;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
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
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
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
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;

public class ScenarioImporter {

    // Scenario loaded by matsim;
    private final Scenario scenario;

    // Parameters that come from the config file.
    // Number of sim threads.
    protected final int sim_threads;

    // Maps a mastim link to a qsim link and vice versa.
    private Map<String, Integer> matsim_to_nqsim_Link;
    private Map<Integer, String> nqsim_to_matsim_Link;

    // Maps a matsim id to a qsim agent and vice versa.
    protected Map<Integer, String> nqsim_to_matsim_Agent; // TODO - transform into array!
    private Map<String, Integer> matsim_to_nqsim_Agent;

    // Maps a matsim id to a qsim route and vice versa.
    private Map<Integer, String> nqsim_to_matsim_Route;
    private Map<String, Integer> matsim_to_nqsim_Route;

    // Maps a matsim id to an nqsim station and vice versa.
    private Map<Integer, String> nqsim_to_matsim_Station;
    private Map<String, Integer> matsim_to_nqsim_Station;

    // nqsim stop ids per route id. Shold be used as follows:
    // bla.get(route id).get(station index) -> station id
    protected ArrayList<ArrayList<Integer>> route_stops_by_index;

    // nqsim line id of a particular route. Should be used as follows:
    // line_of_route[route id] -> line id.
    protected ArrayList<Integer> line_of_route;

    // Array of links that define the network.
    protected Link[] qsim_links;

    // Array of agents that participate in the simulation.
    protected Agent[] nqsim_agents;

    // Array of realms. TODO - delete realms!
    protected Realm[] qsim_realms;

    // Agents waiting in pt stations. Should be used as follows: 
    // nqsim_stops.get(curr station id).get(line id).get(dst station id) -> queue of agents
    protected ArrayList<ArrayList<Map<Integer, ConcurrentLinkedQueue<Agent>>>> agent_stops;

    // matsim events indexed by nqsim agent id and by event id
    protected ArrayList<ArrayList<Event>> matsim_events;

    public ScenarioImporter(Scenario scenario, int sim_threads) {
        this.scenario = scenario;
        this.sim_threads = sim_threads;
    }

    public void generate() throws Exception {
        generateLinks();
        generetePT();
        generateAgents();
        generateRealms();
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
            int length = Math.max(1, (int) Math.round(matsim_link.getLength()));
            int speed = Math.max(1, (int) Math.round(matsim_link.getFreespeed()));
            int flow = (int) Math.round(matsim_link.getFlowCapacityPerSec());
            int lanes = (int) Math.round(matsim_link.getNumberOfLanes());
            int capacity = (int) (matsim_link.getLength() / 7.5 * lanes);
            String matsim_id = matsim_link.getId().toString();
            int qsim_id = counter++;

            if (qsim_id > Hermes.MAX_LINK_ID) {
                throw new RuntimeException("exceeded maximum number of links");
            }

            Link qsim_link = new Link(qsim_id, capacity, length, speed);
            qsim_links[qsim_id] = qsim_link;
            matsim_to_nqsim_Link.put(matsim_id, qsim_id);
            nqsim_to_matsim_Link.put(qsim_id, matsim_id);
        }
    }

    private void generetePT() {
        nqsim_to_matsim_Route = new HashMap<>();
        matsim_to_nqsim_Route = new HashMap<>();
        matsim_to_nqsim_Station = new HashMap<>();
        nqsim_to_matsim_Station = new HashMap<>();
        route_stops_by_index = new ArrayList<>();
        line_of_route = new ArrayList<>();
        int transit_line_counter = 0;
        TransitSchedule ts = scenario.getTransitSchedule();
        for (TransitLine tl: ts.getTransitLines().values()) {
            for (TransitRoute tr : tl.getRoutes().values()) {
                String matsim_rid = tr.getId().toString();
                int qsim_rid = nqsim_to_matsim_Route.size();
                // Initialize route ids
                nqsim_to_matsim_Route.put(qsim_rid, matsim_rid);
                matsim_to_nqsim_Route.put(matsim_rid, qsim_rid);
                // Initialize line of route
                line_of_route.add(transit_line_counter);
                // Initialize stops in route
                route_stops_by_index.add(new ArrayList<>(tr.getStops().size()));
                for (TransitRouteStop trs : tr.getStops()) {
                    String matsim_sid = trs.getStopFacility().getId().toString();
                    int qsim_sid;

                    // If not already initialized station
                    if (!matsim_to_nqsim_Station.containsKey(matsim_sid)) {
                        qsim_sid = nqsim_to_matsim_Station.size();
                        nqsim_to_matsim_Station.put(qsim_sid, matsim_sid);
                        matsim_to_nqsim_Station.put(matsim_sid, qsim_sid);
                    } else {
                        qsim_sid = matsim_to_nqsim_Station.get(matsim_sid);
                    }

                    // Initialize stops in route
                    route_stops_by_index.get(qsim_rid).add(qsim_sid);
                }
            }
            transit_line_counter += 1;
        }
        // Initialize nqsim_stops.
        int nstops = matsim_to_nqsim_Station.size();
        agent_stops = new ArrayList<>(nstops);
        for (int i = 0; i < nstops; i++) {
            ArrayList<Map<Integer, ConcurrentLinkedQueue<Agent>>> agent_lines = 
                new ArrayList<>(transit_line_counter);
            for (int j = 0; j < transit_line_counter; j++) {
                agent_lines.add(new HashMap<>());
            }
            agent_stops.add(agent_lines);
        }
    }

    private void generateRealms() throws Exception {
        qsim_realms = new Realm[1];
        qsim_realms[0] = new Realm(this);

        // Put agents in their initial location (link or activity center)
        for (Agent agent : nqsim_agents) {
            // Some agents might not have plans.
            if (agent.plan.length == 0) {
                continue;
            }
            long planentry = agent.plan()[0];
            int type = Agent.getPlanHeader(planentry);
            switch (type) {
                case Agent.LinkType:
                    int linkid = Agent.getLinkPlanEntry(planentry);
                    int velocity = Agent.getVelocityPlanEntry(planentry);
                    Link link = qsim_links[linkid];
                    agent.linkFinishTime = link.length() / Math.min(velocity, link.velocity());
                    link.push(agent);
                    break;
                case Agent.SleepForType:
                case Agent.SleepUntilType:
                    int sleep = Agent.getSleepPlanEntry(planentry);
                    qsim_realms[0].delayedAgents().get(sleep).add(agent);
                    break;
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
        int eventid = 0;
        Id<ActivityFacility> facid = act.getFacilityId();
        String type = act.getType();

        // hack to avoid a actstart as first event (qsim does not have it).
        if (!flatplan.isEmpty()) {
            eventid = events.size() - 1;
            events.add(new ActivityStartEvent(0, id, act.getLinkId(), facid, type));
        } else {
            eventid = 0;
        }

        if (Double.isFinite(act.getEndTime())) {
            time = (int) Math.round(act.getEndTime());
            flatplan.add(Agent.prepareSleepUntilEntry(eventid, (int)time));
        } else if (Double.isFinite(act.getMaximumDuration())) {
            time = (int) Math.round(act.getMaximumDuration());
            flatplan.add(Agent.prepareSleepForEntry(eventid, (int)time));
        } else {
            // TODO - better way to handle this?
            flatplan.add(Agent.prepareSleepForEntry(eventid, 0));
        }
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
                leg.getMode().equals("freight") ?
                    Id.createVehicleId(id.toString() + "_" + leg.getMode()) :
                    Id.createVehicleId(id.toString()) :
                v.getId();
        int velocity = v == null ?
            Hermes.MAX_VEHICLE_VELOCITY : (int) Math.round(v.getType().getMaximumVelocity());
        int egressId = matsim_to_nqsim_Link.get(endLId.toString());
        events.add(new PersonEntersVehicleEvent(0, id, vid));
        events.add(new VehicleEntersTrafficEvent(0, id, startLId, vid, leg.getMode(), 1));
        if (startLId != endLId) {
            events.add(new LinkLeaveEvent(0, vid, startLId));
        }
        for (Id<org.matsim.api.core.v01.network.Link> linkid : netroute.getLinkIds()) {
            int linkId = matsim_to_nqsim_Link.get(linkid.toString());
            events.add(new LinkEnterEvent(0, vid, linkid));
            events.add(new LinkLeaveEvent(0, vid, linkid));
            flatplan.add(Agent.prepareLinkEntry(events.size() - 1, linkId, velocity));
        }
        if (startLId != endLId) {
            events.add(new LinkEnterEvent(0, vid, endLId));
        }
        events.add(new VehicleLeavesTrafficEvent(0, id, endLId, vid, leg.getMode(), 1));
        events.add(new PersonLeavesVehicleEvent(0, id, vid));
        flatplan.add(Agent.prepareLinkEntry(events.size() - 1, egressId, velocity));
    }

    private void populateStops(int srcStopId, int lineId, int dstStopId) {
        Map<Integer, ConcurrentLinkedQueue<Agent>> agents = agent_stops.get(srcStopId).get(lineId);

        if (!agents.containsKey(dstStopId)) {
            agents.put(dstStopId, new ConcurrentLinkedQueue<>());
        }
    }

    private void processPlanTransitRoute(
            Id<Person> id,
            ArrayList<Long> flatplan,
            ArrayList<Event> events,
            ExperimentalTransitRoute troute) {
        Id<TransitStopFacility> access = troute.getAccessStopId();
        Id<TransitStopFacility> egress = troute.getEgressStopId();
        String route = troute.getRouteId().toString();
        int routeid = matsim_to_nqsim_Route.get(route);
        int lineid = line_of_route.get(routeid);
        int accessid = matsim_to_nqsim_Station.get(access.toString());
        int egressid = matsim_to_nqsim_Station.get(egress.toString());

        populateStops(accessid, lineid, egressid);

        // this will be replaced dynamically
        Id<Vehicle> vid = Id.createVehicleId("tr_X");
        // Add public transport access
        events.add(new AgentWaitingForPtEvent(0, id, access, egress));
        flatplan.add(Agent.prepareWaitEntry(events.size() - 1, routeid, accessid));
        events.add(new PersonEntersVehicleEvent(0, id, vid));
        flatplan.add(Agent.prepareAccessEntry(events.size() - 1, routeid, accessid));
        events.add(new PersonLeavesVehicleEvent(0, id, vid));
        flatplan.add(Agent.prepareEgressEntry(events.size() - 1, routeid, egressid));
    }

    private void processPlanElement(
            Id<Person> id,
            ArrayList<Long> flatplan,
            ArrayList<Event> events,
            PlanElement element) {
        if (element instanceof Leg) {
            Leg leg = (Leg) element;
            Route route = leg.getRoute();
            String mode = leg.getMode();

            if (route == null) return;

            events.add(new PersonDepartureEvent(0, id, route.getStartLinkId(), leg.getMode()));

            switch (mode) {
                case TransportMode.car:
                case TransportMode.motorcycle:
                case TransportMode.truck:
                case "freight":
                    assert route instanceof NetworkRoute;
                    processPlanNetworkRoute(id, flatplan, events, leg, (NetworkRoute) route);
                    break;
                case TransportMode.pt:
                    assert route instanceof ExperimentalTransitRoute;
                    processPlanTransitRoute(id, flatplan, events, (ExperimentalTransitRoute) route);
                    break;
                case TransportMode.ride:
                case TransportMode.walk:
                case TransportMode.transit_walk:
                case TransportMode.train:
                case TransportMode.ship:
                case TransportMode.airplane:
                case TransportMode.access_walk:
                case TransportMode.egress_walk:
                case "bike":
                case "bicycle":
                    int time = (int) Math.round(route.getTravelTime());
                    flatplan.add(Agent.prepareSleepForEntry(events.size() - 1, time));
                    events.add(new TeleportationArrivalEvent(0, id, route.getDistance()));
                    break;
                default:
                    throw new RuntimeException ("Unknown leg mode " + leg.toString());
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
        if (events.size() >= Hermes.MAX_EVENTS_AGENT) {
            throw new RuntimeException("exceeded maximum number of agent events");
        }

        // Convert flat plan to long (native type) plan. // TODO - is this really necessary?
        long[] longplan = new long[flatplan.size()];
        for (int i = 0; i < longplan.length; i++) {
            longplan[i] = flatplan.get(i);
        }
        Agent agent = new Agent(matsim_to_nqsim_Agent.size(), capacity, longplan);
        matsim_to_nqsim_Agent.put(matsim_id, agent.id);
        nqsim_to_matsim_Agent.put(agent.id, matsim_id);
        matsim_events.add(events);
        nqsim_agents[agent.id] = agent;
    }

    private boolean isGoodDouble(double value) {
        if (!Double.isNaN(value) && !Double.isInfinite(value)) {
            return true;
        } else {
            return false;
        }
    }

    private double delay_helper(double expected, double delay_a, double delay_b) {
        if (isGoodDouble(delay_a)) {
            return expected + delay_a;
        } else if (isGoodDouble(delay_b)) {
            return expected + delay_b;
        } else {
            return expected;
        }
    }

    private double arrivalOffsetHelper(Departure depart, TransitRouteStop trs) {
        return delay_helper(
            depart.getDepartureTime(), trs.getArrivalOffset(), trs.getDepartureOffset());
    }

    private double departureOffsetHelper(Departure depart, TransitRouteStop trs) {
        return delay_helper(
            depart.getDepartureTime(), trs.getDepartureOffset(), trs.getArrivalOffset());
    }

    private void generateVehicleTrip(
            Map<Vehicle, ArrayList<Long>> plans,
            Map<Vehicle, ArrayList<Event>> events,
            TransitLine tl,
            TransitRoute tr,
            Departure depart) {
        List<TransitRouteStop> trs = tr.getStops();
        TransitRouteStop next = trs.get(0);
        int stopidx = 0;
        int rid = matsim_to_nqsim_Route.get(tr.getId().toString());
        ArrayList<Integer> stop_ids = route_stops_by_index.get(rid);
        Vehicle v = scenario.getTransitVehicles().getVehicles().get(depart.getVehicleId());
        VehicleType vt = v.getType();
        if (!plans.containsKey(v)) {
            plans.put(v, new ArrayList<>());
            events.put(v, new ArrayList<>());
        }
        ArrayList<Long> flatplan = plans.get(v);
        ArrayList<Event> flatevents = events.get(v);
        int velocity = (int)Math.min(
            Math.round(v.getType().getMaximumVelocity()),
            Hermes.MAX_VEHICLE_VELOCITY);
        NetworkRoute nr = tr.getRoute();
        int startid = matsim_to_nqsim_Link.get(nr.getStartLinkId().toString());
        int endid = matsim_to_nqsim_Link.get(nr.getEndLinkId().toString());

        Id<Person> driverid = null;
        String legmode = null;

        if (Hermes.SBB_SCENARIO) {
            driverid = Id.createPersonId(
                "pt_" + tl.getId().toString() + "_" + tr.getId().toString() + "_" + depart.getId().toString());
            legmode = "detPt";
        } else {
            driverid = Id.createPersonId("pt_" + v.getId() + "_" + vt.getId());
            legmode = "car";
        }

        // Sleep until the time of departure
        flatplan.add(Agent.prepareSleepUntilEntry(0, (int) Math.round(depart.getDepartureTime())));

        // Prepare to leave
        flatevents.add(new TransitDriverStartsEvent(
            0, driverid, v.getId(), tl.getId(), tr.getId(), depart.getId()));
        flatevents.add(new PersonDepartureEvent(
            0, driverid, nr.getStartLinkId(), legmode));
        flatevents.add(new PersonEntersVehicleEvent(
            0, driverid, v.getId()));
        flatevents.add(new VehicleEntersTrafficEvent(
            0, driverid, nr.getStartLinkId(), v.getId(), legmode, 1));

        // Adding first link and possibly the first stop.
        if (next.getStopFacility().getLinkId().equals(nr.getStartLinkId())) {
            flatevents.add(new VehicleArrivesAtFacilityEvent(
                0, v.getId(), next.getStopFacility().getId(), arrivalOffsetHelper(depart, next)));
            flatplan.add(Agent.prepareStopArrivalEntry(
                flatevents.size() - 1, rid, stop_ids.get(stopidx), stopidx));
            // no event associated to stop delay
            flatplan.add(Agent.prepareStopDelayEntry(0, rid, 
                stop_ids.get(stopidx), stopidx));
            flatevents.add(new VehicleDepartsAtFacilityEvent(
                0, v.getId(), next.getStopFacility().getId(), departureOffsetHelper(depart, next)));
            flatplan.add(Agent.prepareStopDepartureEntry(
                flatevents.size() - 1, rid, stop_ids.get(stopidx), stopidx));
            flatevents.add(new LinkLeaveEvent(
                0, v.getId(), nr.getStartLinkId()));

            stopidx += 1;

            // We don't add a flatplan event here on purpose.
            next = trs.get(stopidx);
        }

        // For each link (exclucing the first and the last)
        for (Id<org.matsim.api.core.v01.network.Link> link : nr.getLinkIds()) {
            int linkid = matsim_to_nqsim_Link.get(link.toString());
            flatevents.add(new LinkEnterEvent(0, v.getId(), link));
            flatplan.add(Agent.prepareLinkEntry(flatevents.size() - 1, linkid, velocity));
            // Adding link and possibly a stop.
            if (next.getStopFacility().getLinkId().equals(link)) {
                flatevents.add(new VehicleArrivesAtFacilityEvent(
                    0, v.getId(), next.getStopFacility().getId(), arrivalOffsetHelper(depart, next)));
                flatplan.add(Agent.prepareStopArrivalEntry(
                    flatevents.size() - 1, rid, stop_ids.get(stopidx), stopidx));
                // no event associated to stop delay
                flatplan.add(Agent.prepareStopDelayEntry(
                    0, rid, stop_ids.get(stopidx), stopidx));
                flatevents.add(new VehicleDepartsAtFacilityEvent(
                    0, v.getId(), next.getStopFacility().getId(), departureOffsetHelper(depart, next)));
                flatplan.add(Agent.prepareStopDepartureEntry(
                    flatevents.size() - 1, rid, stop_ids.get(stopidx), stopidx));

                stopidx += 1;
                next = trs.get(stopidx);
            }
            flatevents.add(new LinkLeaveEvent(0, v.getId(), link));
        }

        // Adding last link and possibly the last stop.
        flatevents.add(new LinkEnterEvent(0, v.getId(), nr.getEndLinkId()));
        flatplan.add(Agent.prepareLinkEntry(flatevents.size() - 1, endid, velocity));
        if (next.getStopFacility().getLinkId().equals(nr.getEndLinkId())) {
            flatevents.add(new VehicleArrivesAtFacilityEvent(
                0, v.getId(), next.getStopFacility().getId(), arrivalOffsetHelper(depart, next)));
            flatplan.add(Agent.prepareStopArrivalEntry(
                flatevents.size() - 1, rid, stop_ids.get(stopidx), stopidx));
            // no event associated to stop delay
            flatplan.add(Agent.prepareStopDelayEntry(
                0, rid, stop_ids.get(stopidx), stopidx));
            flatevents.add(new VehicleDepartsAtFacilityEvent(
                0, v.getId(), next.getStopFacility().getId(), departureOffsetHelper(depart, next)));
            flatplan.add(Agent.prepareStopDepartureEntry(
                flatevents.size() - 1, rid, stop_ids.get(stopidx), stopidx));
            stopidx += 1;
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
        matsim_to_nqsim_Agent = new HashMap<>();
        nqsim_to_matsim_Agent = new HashMap<>();
        matsim_events = new ArrayList<>();
        nqsim_agents = new Agent[
            scenario.getPopulation().getPersons().size() +
            scenario.getTransitVehicles().getVehicles().size()];
        generatePersons();
        generateVehicles();
    }

    public ArrayList<ArrayList<Event>> getEvents() {
        return this.matsim_events;
    }

    public Agent[] getAgents() {
        return this.nqsim_agents;
    }

    public String getMatsimAgentId(int nqsimAgentId) {
        return this.nqsim_to_matsim_Agent.get(nqsimAgentId);
    }



    public void dump_conversion() throws Exception {
        BufferedWriter log = new BufferedWriter(new FileWriter(WorldDumper.outputPrefix + "/hermes_conversion"));
        dump_agents_conversion(log);
        dump_links_conversion(log);
        dump_routes_conversion(log);
        dump_station_conversion(log);
        dump_line_of_route(log);
        dump_route_stops(log);
        log.close();
    }

    public void dump_line_of_route(BufferedWriter log) throws Exception {
        for(int i = 0; i < line_of_route.size(); i++) {
            log.write(String.format("ETHZ Route %d is in Line %d\n",
                i, line_of_route.get(i)));
        }

    }

    public void dump_route_stops(BufferedWriter log) throws Exception {
        for(int i = 0; i < route_stops_by_index.size(); i++) {
            ArrayList<Integer> stops_by_index = route_stops_by_index.get(i);
            for (int j = 0; j < stops_by_index.size(); j++) {
                log.write(String.format("ETHZ Route %d Contains Stop idx = %d id = %d\n",
                    i, j, stops_by_index.get(j)));
            }
        }
    }

    public void dump_agents_conversion(BufferedWriter log) throws Exception {
        for (Map.Entry<String, Integer> entry : matsim_to_nqsim_Agent.entrySet()) {
            log.write(String.format("ETHZ Agent matsim to hermes: %s %d\n",
                entry.getKey(), entry.getValue()));
        }
    }

    public void dump_links_conversion(BufferedWriter log) throws Exception {
        for (Map.Entry<String, Integer> entry : matsim_to_nqsim_Link.entrySet()) {
            log.write(String.format("ETHZ Link matsim to hermes: %s %d\n",
                entry.getKey(), entry.getValue()));
        }
    }

    public void dump_routes_conversion(BufferedWriter log) throws Exception {
        for (Map.Entry<String, Integer> entry : matsim_to_nqsim_Route.entrySet()) {
            log.write(String.format("ETHZ Route matsim to hermes: %s %d\n",
                entry.getKey(), entry.getValue()));
        }
    }

    public void dump_station_conversion(BufferedWriter log) throws Exception {
        for (Map.Entry<String, Integer> entry : matsim_to_nqsim_Station.entrySet()) {
            log.write(String.format("ETHZ Stop matsim to hermes: %s %d\n",
                entry.getKey(), entry.getValue()));
        }
    }
}
