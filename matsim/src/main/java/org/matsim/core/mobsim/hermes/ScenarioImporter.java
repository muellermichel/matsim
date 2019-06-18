package org.matsim.core.mobsim.hermes;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
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
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.gbl.Gbl;
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
	
	final private static Logger log = Logger.getLogger(Hermes.class);
	
	private static ScenarioImporter instance;
	
	private Thread resetThread = null;
	
    // Scenario loaded by matsim;
    private final Scenario scenario;

    // Parameters that come from the config file.
    // Number of sim threads.
    protected final int sim_threads;
    
    protected int agent_persons;

    // hermes stop ids per route id. Shold be used as follows:
    // bla.get(route id).get(station index) -> station id
    protected ArrayList<ArrayList<Integer>> route_stops_by_index;

    // hermes line id of a particular route. Should be used as follows:
    // line_of_route[route id] -> line id.
    protected int[] line_of_route;

    // Array of links that define the network.
    protected Link[] hermes_links;

    // Array of agents that participate in the simulation.
    protected Agent[] hermes_agents;

    protected Realm realm;

    // Agents waiting in pt stations. Should be used as follows: 
    // agent_stops.get(curr station id).get(line id).get(dst station id) -> queue of agents
    protected ArrayList<ArrayList<Map<Integer, ArrayDeque<Agent>>>> agent_stops;

    // matsim events indexed by agent id and by event id
    protected ArrayList<ArrayList<Event>> matsim_events;

    protected final EventsManager eventsManager;

    private ScenarioImporter(Scenario scenario, EventsManager eventsManager, int sim_threads) {
        this.scenario = scenario;
        this.eventsManager = eventsManager;
        this.sim_threads = sim_threads;
        generateLinks();
        generetePT();
        generateAgents();
    }
    
    public static ScenarioImporter instance(Scenario scenario, EventsManager eventsManager, int sim_threads) {
    	if (instance == null) {
            instance = new ScenarioImporter(scenario, eventsManager, sim_threads);
    	}
		return instance;
    }

    public void generate() throws Exception {
    	long time = System.currentTimeMillis();
    	
    	if (resetThread != null) {
    		resetThread.join();
    	}
    	
    	log.info(String.format("ETHZ reset took %d ms", System.currentTimeMillis() - time));
    	time = System.currentTimeMillis();
    	generatePlans();
    	log.info(String.format("ETHZ generatePlans took %d ms", System.currentTimeMillis() - time));
    	time = System.currentTimeMillis();
        generateRealms();
        log.info(String.format("ETHZ generateRealms took %d ms", System.currentTimeMillis() - time));
    }
    
    public void reset() {
    	resetThread = new Thread() {
    		
    		@Override
    		public void run() {
    	    	// reset links
    	    	for (int i = 0; i < hermes_links.length; i++) {
    	    		hermes_links[i].reset();
    	    	}
    	    	// reset agent plans and events
    	        for (int i = 0; i < hermes_agents.length; i++) {
    	            hermes_agents[i].reset();
    	            matsim_events.get(i).clear();
    	    	}
    	    	// reset agent_stops
    	        for (ArrayList<Map<Integer, ArrayDeque<Agent>>>  station_id : agent_stops) {
    	            for (Map<Integer, ArrayDeque<Agent>> line_id : station_id) {
    	                for (Map.Entry<Integer, ArrayDeque<Agent>> entry : line_id.entrySet()) {
    	    				entry.getValue().clear();
    	    			}
    	    		}
    	    	}
    		}
    	};
    	
    	resetThread.start();
    }

    private void generateLinks() {
        Network network = scenario.getNetwork();
        Collection<? extends org.matsim.api.core.v01.network.Link> matsim_links =
            network.getLinks().values();
        hermes_links = new Link[matsim_links.size()];

        for (org.matsim.api.core.v01.network.Link matsim_link : matsim_links) {
            int length = Math.max(1, (int) Math.round(matsim_link.getLength()));
            int speed = Math.max(1, (int) Math.round(matsim_link.getFreespeed()));
            int flow = (int) Math.round(matsim_link.getFlowCapacityPerSec());
            int lanes = (int) Math.round(matsim_link.getNumberOfLanes());
            int capacity = (int) (matsim_link.getLength() / 7.5 * lanes);
            int link_id  = matsim_link.getId().hashCode();

            if (link_id > Hermes.MAX_LINK_ID) {
                throw new RuntimeException("exceeded maximum number of links");
            }

            hermes_links[link_id] = new Link(link_id, capacity, length, speed);
        }
    }

    private void initRoutesStations() {
    	route_stops_by_index = new ArrayList<>();
    	TransitSchedule ts = scenario.getTransitSchedule();
        for (TransitLine tl: ts.getTransitLines().values()) {
            for (TransitRoute tr : tl.getRoutes().values()) {
            	route_stops_by_index.add(new ArrayList<>());
            }
        }
        line_of_route = new int[route_stops_by_index.size()];
        
    }
    
    private void generetePT() {
        initRoutesStations();
    	Set<Integer> stopIds = new HashSet<>();
        TransitSchedule ts = scenario.getTransitSchedule();
        int transit_line_counter = 0;

        for (TransitLine tl: ts.getTransitLines().values()) {
        	int tid = tl.getId().hashCode();
        	transit_line_counter += 1;
            for (TransitRoute tr : tl.getRoutes().values()) {
            	int rid = tr.getId().hashCode();
            	
                // Initialize line of route
                line_of_route[rid] = tid;
                // Initialize stops in route
                route_stops_by_index.set(rid, new ArrayList<>(tr.getStops().size()));
                for (TransitRouteStop trs : tr.getStops()) {
                	int sid = trs.getStopFacility().getId().hashCode();
                	if (stopIds.contains(sid)) {
                	} else {
                		stopIds.add(sid);
                	}
                	
                    // Initialize stops in route
                    route_stops_by_index.get(rid).add(sid);
                }
            }
        }

        // Initialize agent_stops.
        agent_stops = new ArrayList<>(stopIds.size());
        for (int i = 0; i < stopIds.size(); i++) {
            ArrayList<Map<Integer, ArrayDeque<Agent>>> agent_lines = new ArrayList<>(transit_line_counter);
            for (int j = 0; j < transit_line_counter; j++) {
                agent_lines.add(new HashMap<>());
            }
            agent_stops.add(agent_lines);
        }
    }

    private void generateRealms() throws Exception {
        realm = new Realm(this, eventsManager);

        // Put agents in their initial location (link or activity center)
        for (Agent agent : hermes_agents) {
            // Some agents might not have plans.
            if (agent.plan.size() == 0) {
                continue;
            }
            long planentry = agent.plan().get(0);
            int type = Agent.getPlanHeader(planentry);
            switch (type) {
                case Agent.LinkType:
                    int linkid = Agent.getLinkPlanEntry(planentry);
                    int velocity = Agent.getVelocityPlanEntry(planentry);
                    Link link = hermes_links[linkid];
                    agent.linkFinishTime = link.length() / Math.min(velocity, link.velocity());
                    link.push(agent);
                    break;
                case Agent.SleepForType:
                case Agent.SleepUntilType:
                    int sleep = Agent.getSleepPlanEntry(planentry);
                    realm.delayedAgents().get(sleep).add(agent);
                    break;
                default:
                    Realm.log(0, String.format("ERROR -> unknow plan element type %d",type));
            }
        }

        for (int i = 0; i < hermes_links.length; i++) {
            int nextwakeup = hermes_links[i].nexttime();
            if (nextwakeup > 0) {
                realm.delayedLinks().get(nextwakeup).add(hermes_links[i]);
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

        // hack to avoid a actstart as first event (hermes does not have it).
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
        int egressId = endLId.hashCode();
        events.add(new PersonEntersVehicleEvent(0, id, vid));
        events.add(new VehicleEntersTrafficEvent(0, id, startLId, vid, leg.getMode(), 1));
        if (startLId != endLId) {
            events.add(new LinkLeaveEvent(0, vid, startLId));
        }
        for (Id<org.matsim.api.core.v01.network.Link> linkid : netroute.getLinkIds()) {
            int linkId = linkid.hashCode();
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
        Map<Integer, ArrayDeque<Agent>> agents = agent_stops.get(srcStopId).get(lineId);

        if (!agents.containsKey(dstStopId)) {
            agents.put(dstStopId, new ArrayDeque<>());
        }
    }

    private void processPlanTransitRoute(
            Id<Person> id,
            ArrayList<Long> flatplan,
            ArrayList<Event> events,
            ExperimentalTransitRoute troute) {
        Id<TransitStopFacility> access = troute.getAccessStopId();
        Id<TransitStopFacility> egress = troute.getEgressStopId();
        int routeid = troute.getRouteId().hashCode();
        int lineid = line_of_route[routeid];
        int accessid = access.hashCode();
        int egressid = egress.hashCode();

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
            int agent_id,
            int capacity,
            ArrayList<Long> flatplan,
            ArrayList<Event> events) {
    	
        if (events.size() >= Hermes.MAX_EVENTS_AGENT) {
            throw new RuntimeException("exceeded maximum number of agent events");
        }

        Agent agent = new Agent(agent_id, capacity, flatplan);
        matsim_events.add(events);
        hermes_agents[agent_id] = agent;
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
            ArrayList<Long> flatplan,
            ArrayList<Event> flatevents,
            TransitLine tl,
            TransitRoute tr,
            Departure depart) {
        List<TransitRouteStop> trs = tr.getStops();
        TransitRouteStop next = trs.get(0);
        int stopidx = 0;
        int rid = tr.getId().hashCode();
        ArrayList<Integer> stop_ids = route_stops_by_index.get(rid);
        Vehicle v = scenario.getTransitVehicles().getVehicles().get(depart.getVehicleId());
        VehicleType vt = v.getType();
        int velocity = (int)Math.min( Math.round(v.getType().getMaximumVelocity()), Hermes.MAX_VEHICLE_VELOCITY);
        NetworkRoute nr = tr.getRoute();
        int startid = nr.getStartLinkId().hashCode();
        int endid = nr.getEndLinkId().hashCode();

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
            int linkid = link.hashCode();
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

    private void generateVehiclePlans() {
        Map<Id<Vehicle>, Vehicle> vehicles = scenario.getTransitVehicles().getVehicles();
        scenario.getTransitSchedule().getTransitLines().values().parallelStream().forEach((tl) -> {
            for (TransitRoute tr : tl.getRoutes().values()) {
                for (Departure depart : tr.getDepartures().values()) {
                	Vehicle v = vehicles.get(depart.getVehicleId());
                	int hermes_id = hermes_id(v.getId().hashCode(), true); 
                	ArrayList<Long> plan = hermes_agents[hermes_id].plan();
                    ArrayList<Event> events = matsim_events.get(hermes_id);
                    generateVehicleTrip(plan, events, tl, tr, depart);
                }
            }
        });
    }

    private void generatePersonPlans() {
        Population population = scenario.getPopulation();
        population.getPersons().values().parallelStream().forEach((person) -> {
        	int hermes_id = hermes_id(person.getId().hashCode(), false); 
            ArrayList<Long> plan = hermes_agents[hermes_id].plan();
            ArrayList<Event> events = matsim_events.get(hermes_id);
            for (PlanElement element: person.getSelectedPlan().getPlanElements()) {
                processPlanElement(person.getId(), plan, events, element);
            }
        });
    }

    private void generateAgents() {
    	Population population = scenario.getPopulation();
    	Map<Id<Vehicle>, Vehicle> vehicles = scenario.getTransitVehicles().getVehicles();
    	agent_persons = population.getPersons().size();
    	int nagents = agent_persons + vehicles.size();
        matsim_events = new ArrayList<>();
        hermes_agents = new Agent[nagents];
        
        // Generate persons
        for (Person person : population.getPersons().values()) {
        	int hermes_id = hermes_id(person.getId().hashCode(), false);
            generateAgent(hermes_id, 0, new ArrayList<>(), new ArrayList<>());
        }
        
        // Generate vehicles
        for (Vehicle vehicle : vehicles.values()) {
            VehicleCapacity vc = vehicle.getType().getCapacity();
            int capacity = vc.getSeats() + vc.getStandingRoom();
            int hermes_id = hermes_id(vehicle.getId().hashCode(), true);
            generateAgent(hermes_id, capacity, new ArrayList<>(), new ArrayList<>());
        }
    }
    
    public int matsim_id(int hermes_id, boolean is_vehicle) {
    	if (is_vehicle) {
    		return hermes_id - agent_persons; 
    	} else {
    		return hermes_id;
    	}
    }
    
    public int hermes_id(int matsim_id, boolean is_vehicle) {
    	if (is_vehicle) {
    		return matsim_id + agent_persons; 
    	} else {
    		return matsim_id;
    	}
    }
    
    private void generatePlans() {
        generatePersonPlans();
        generateVehiclePlans();
    }

    public ArrayList<ArrayList<Event>> getEvents() {
        return this.matsim_events;
    }

    public Agent[] getAgents() {
        return this.hermes_agents;
    }

    public void dump_conversion() throws Exception {
        BufferedWriter log = new BufferedWriter(new FileWriter(WorldDumper.outputPrefix + "/hermes_conversion"));
        dump_line_of_route(log);
        dump_route_stops(log);
        log.close();
    }

    public void dump_line_of_route(BufferedWriter log) throws Exception {
        for(int i = 0; i < line_of_route.length; i++) {
            log.write(String.format("ETHZ Route %d is in Line %d\n",
                i, line_of_route[i]));
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

}
