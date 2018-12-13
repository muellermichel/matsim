package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.routes.AbstractRoute;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;

public class ScenarioImporter {

    // Scenario loaded by matsim;
    private final Scenario scenario;

    // Number of realms to use.
    private final int nrealms;
    
    // Maps a mastim link to a qsim link and vice versa.
    private Map<String, Integer> matsim_to_qsim_Link;
    private Map<Integer, String> qsim_to_matsim_Link;

    // Maps a matsim id to a qsim agent and vice versa.
    private Map<Agent, String> qsim_to_matsim_Agent;
    private Map<String, Agent> matsim_to_qsim_Agent;

        // Maps a matsim id to a qsim route and vice versa.
    private Map<Integer, String> qsim_to_matsim_Route;
    private Map<String, Integer> matsim_to_qsim_Route;       
    
    // localStopIds.get(matsim route id).get(matsim stop id) -> local stop id
    private Map<String, Map<String, Integer>> localStopIds;

    // qsim links and agents.
    private LinkInternal[] qsim_links;
    private Agent[] qsim_agents;
    private Realm[] qsim_realms;

    public ScenarioImporter(Scenario scenario, int nrealms) {
        this.scenario = scenario;
        this.nrealms = nrealms;
    }

    public World generate() throws Exception {
        generateLinks();
        genereteRoutes();
        genereteStops();
        generateAgents();
        generateRealms();
        return new World(qsim_realms, qsim_agents, null);
    }

    private void generateLinks() {
        Network network = scenario.getNetwork();
        Collection<? extends Link> matsim_links = network.getLinks().values();
        int counter = 0;
        qsim_links = new LinkInternal[matsim_links.size()];
        matsim_to_qsim_Link = new HashMap<>(matsim_links.size());
        qsim_to_matsim_Link = new HashMap<>(matsim_links.size());

        for (Link matsim_link : matsim_links) {
            int capacity = (int)matsim_link.getCapacity();
            int length = Math.max(1, (int) matsim_link.getLength());
            int speed = Math.max(1, (int) matsim_link.getFreespeed());
            // TODO - what about the flow and lanes?
            int flow = (int)matsim_link.getFlowCapacityPerSec();
            int lanes = (int) matsim_link.getNumberOfLanes();
            LinkInternal qsim_link = new LinkInternal(
                capacity, length, speed);
            String matsim_id = matsim_link.getId().toString();
            int qsim_id = counter++;
            qsim_links[qsim_id] = qsim_link;
            matsim_to_qsim_Link.put(matsim_id, qsim_id);
            qsim_to_matsim_Link.put(qsim_id, matsim_id);
        }
    }

    private void genereteStops() {
        localStopIds = new HashMap<>();
        TransitSchedule ts = scenario.getTransitSchedule();
        for (TransitLine tl: ts.getTransitLines().values()) {
            for (TransitRoute tr : tl.getRoutes().values()) {
                int nstops = tr.getStops().size();
                ArrayList<ArrayList<Agent>> stops = new ArrayList<>(nstops);
                Map<String, Integer> stopids = new HashMap<>(nstops);
                for (TransitRouteStop trs : tr.getStops()) {
                    String mid = trs.getStopFacility().getId().toString();
                    int qid = stops.size();
                    // TODO - make something fail if qid > 2^8
                    stopids.put(mid, qid);
                    stops.add(new ArrayList<>());
                }
                localStopIds.put(tr.getId().toString(), stopids);
            }
        }
    }

    private void genereteRoutes() {
        qsim_to_matsim_Route = new HashMap<>();
        matsim_to_qsim_Route = new HashMap<>();
        TransitSchedule ts = scenario.getTransitSchedule();
        for (TransitLine tl: ts.getTransitLines().values()) {
            for (TransitRoute tr : tl.getRoutes().values()) {
                String matsim_rid = tr.getId().toString();
                int qsim_rid = qsim_to_matsim_Route.size();
                qsim_to_matsim_Route.put(qsim_rid, matsim_rid);
                matsim_to_qsim_Route.put(matsim_rid, qsim_rid);
            }
        }
    }

    private void generateRealms() throws Exception {
        // TODO - split by nodes, use in and out links to build boundary links
        // TODO - rewrite plans to cope with link local ids.
        qsim_realms = new Realm[1];
        qsim_realms[0] = new Realm(
            0, qsim_links, new LinkBoundary[0], new LinkBoundary[0]);
    }

    private void processPlanElement(ArrayList<Long> flatplan, PlanElement element) {
        Map<Id<Vehicle>, Vehicle> vehicles = scenario.getVehicles().getVehicles();
        if (element instanceof Leg) {
            Leg leg = (Leg) element;
            Route route = leg.getRoute();
            if (route == null) return;
            // Network circuit
            if (route instanceof NetworkRoute) {
                NetworkRoute netroute = (NetworkRoute) route;
                int velocity = 1; // TODO - vehicle is null!?
                // Vehicle v = vehicles.get(netroute.getVehicleId());
                //int velocity = (int)v.getType().getMaximumVelocity();
                int exitId = Integer.parseInt(route.getEndLinkId().toString());
                for (Id<Link> linkid : netroute.getLinkIds()) {
                    int linkId = Integer.parseInt(linkid.toString());
                    // Add routing link
                    flatplan.add(Agent.prepareLinkElement(linkId, velocity));
                }
                // Add routing link
                flatplan.add(Agent.prepareLinkElement(exitId, velocity));
            }
            // Public transport (vehicles)
            else if (route instanceof ExperimentalTransitRoute) {
                ExperimentalTransitRoute troute = 
                    (ExperimentalTransitRoute) route;
                String access = troute.getAccessStopId().toString();
                String egress = troute.getEgressStopId().toString();
                String routeid = troute.getRouteId().toString();
                int routeId = matsim_to_qsim_Route.get(routeid); 
                int accessid = localStopIds.get(routeid).get(access);
                int egressid = localStopIds.get(routeid).get(egress);
                // Add public transport access
                flatplan.add(Agent.prepareAccessElement(routeId, accessid));
                flatplan.add(Agent.prepareEgressElement(egressid));
            }
            // walking, cycling, riding, teleporting
            else if (route instanceof AbstractRoute) {
                double time = (int)route.getTravelTime();
                // Add activity duration
                flatplan.add(Agent.prepareSleepForElement((int)time));
            }
            else {
                throw new RuntimeException ("Unknown route " + route.getClass().toString());
            }
        } else if (element instanceof Activity) {
            Activity activity = (Activity) element;
            if (Double.isFinite(activity.getEndTime())) {
                double time = activity.getEndTime();
                flatplan.add(Agent.prepareSleepUntilElement((int)time));
            } else if (Double.isFinite(activity.getMaximumDuration())) {
                double time = activity.getMaximumDuration();
                flatplan.add(Agent.prepareSleepForElement((int)time));
            } else {
                return;
            }
            
        } else {
            throw new RuntimeException ("Unknown plan element " + element);
        }
    }

    private void generateAgent(
        String matsim_id, int capacity, ArrayList<Long> flatplan) {
            // Convert flat plan to long (native type) plan.
        long[] longplan = new long[flatplan.size()];
        for (int i = 0; i < longplan.length; i++) {
            longplan[i] = flatplan.get(i);
        }
        Agent agent = new Agent(matsim_to_qsim_Agent.size(), capacity, longplan);
        matsim_to_qsim_Agent.put(matsim_id, agent);
        qsim_to_matsim_Agent.put(agent, matsim_id);
    }

    private void generateVehicles() {
        Map<Id<Vehicle>, Vehicle> vehicles = scenario.getTransitVehicles().getVehicles();
        Map<Vehicle, ArrayList<Long>> plans = new HashMap<>(vehicles.size());
        TransitSchedule ts = scenario.getTransitSchedule();
        // Create plans for vehicles.
        for (TransitLine tl: ts.getTransitLines().values()) {
            for (TransitRoute tr : tl.getRoutes().values()) {
                int rid = matsim_to_qsim_Route.get(tr.getId().toString());
                    List<TransitRouteStop> trs = tr.getStops();
                for (Departure depart : tr.getDepartures().values()) {
                    int lstopid = 0;
                    TransitRouteStop next = trs.get(0);
                    Vehicle v = vehicles.get(depart.getVehicleId());
                    if (!plans.containsKey(v)) {
                        plans.put(v, new ArrayList<>()); 
                    }
                    ArrayList<Long> flatplan = plans.get(v);
                    flatplan.add(Agent.prepareSleepUntilElement(
                        (int)depart.getDepartureTime()));
                    flatplan.add(Agent.prepareRouteElement(rid));
                    int velocity = (int)v.getType().getMaximumVelocity();
                    for (Id<Link> link : tr.getRoute().getLinkIds()) {
                        int linkid = matsim_to_qsim_Link.get(link.toString());
                        flatplan.add(Agent.prepareLinkElement(linkid, velocity));
                        if (next.getStopFacility().getLinkId().equals(link)) {
                            flatplan.add(Agent.prepareStopElement(lstopid++));
                            next = trs.get(lstopid);
                        }
                    }
                }
            }
        }
        // Create agents from vehicles.
        for (Map.Entry<Vehicle, ArrayList<Long>> entry : plans.entrySet()) {
            Vehicle v = entry.getKey();
            VehicleCapacity vc = v.getType().getCapacity();
            String vid = v.getId().toString();
            int capacity = vc.getSeats() + vc.getStandingRoom();
            generateAgent(vid, capacity, entry.getValue());
        }
    }

    private void generatePersons() {
        Population population = scenario.getPopulation();
        for (Person person : population.getPersons().values()) {
            // Convert matsim plan to flat plan.
            ArrayList<Long> flatplan = new ArrayList<>();
            for (Plan plan : person.getPlans()) {
                for (PlanElement element: plan.getPlanElements()) {
                    processPlanElement(flatplan, element); 
                }
            }
            generateAgent(person.getId().toString(), 0, flatplan);
        }
    }

    private void generateAgents() {
        matsim_to_qsim_Agent = new HashMap<>();
        qsim_to_matsim_Agent = new HashMap<>();
        generatePersons();
        generateVehicles();
        // Preparing qsim agent array
        qsim_agents = new Agent[matsim_to_qsim_Agent.size()];
        for (Agent agent : qsim_to_matsim_Agent.keySet()) {
            qsim_agents[agent.id] = agent;
        }
    }
}