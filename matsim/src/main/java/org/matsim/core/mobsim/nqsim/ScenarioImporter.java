package org.matsim.core.mobsim.nqsim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
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

    // qsim links and agents.
    private Link[] qsim_links;
    private Agent[] qsim_agents;
    private Realm[] qsim_realms;
    private ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> qsim_stops;

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
            int capacity = (int)matsim_link.getCapacity();
            int length = Math.max(1, (int) matsim_link.getLength());
            int speed = Math.max(1, (int) matsim_link.getFreespeed());
            // TODO - what about the flow and lanes?
            int flow = (int)matsim_link.getFlowCapacityPerSec();
            int lanes = (int) matsim_link.getNumberOfLanes();
            String matsim_id = matsim_link.getId().toString();
            int qsim_id = counter++;

            if (qsim_id > World.MAX_LINK_ID) {
                throw new RuntimeException("exceeded maximum number of links");
            }

            Link qsim_link = new Link(
                    qsim_id, sim_threads == 1 ?
                        0 :
                        qsim_id % (sim_threads - 1), capacity, length, speed);
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
                ArrayList<ArrayList<Agent>> stops = new ArrayList<>(nstops); // TODO - this is not necessary!
                Map<String, Integer> stopids = new HashMap<>(nstops);
                for (TransitRouteStop trs : tr.getStops()) {
                    String mid = trs.getStopFacility().getId().toString();
                    int qid = stops.size();
                    stopids.put(mid, qid);
                    stops.add(new ArrayList<>());
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
        ArrayList<ArrayList<ConcurrentLinkedQueue<Link>>> delayedLinksByWakeupTime =
            new ArrayList<>(sim_threads);
        ArrayList<ArrayList<ConcurrentLinkedQueue<Agent>>> delayedAgentsByWakeupTime =
            new ArrayList<>(sim_threads);
        qsim_realms = new Realm[1];
        qsim_realms[0] = new Realm(
                qsim_links,
                delayedLinksByWakeupTime,
                delayedAgentsByWakeupTime,
                qsim_stops);
        for (int i = 0; i < sim_threads + 1; i++) {
                delayedLinksByWakeupTime.add(new ArrayList<>(World.ACT_SLOTS));
                delayedAgentsByWakeupTime.add(new ArrayList<>(World.ACT_SLOTS));
            for (int j = 0; j < World.ACT_SLOTS + 1; j++) {
                delayedLinksByWakeupTime.get(i).add(new ConcurrentLinkedQueue<>());
                delayedAgentsByWakeupTime.get(i).add(new ConcurrentLinkedQueue<>());
            }
        }
        // Put agents in their initial location (link or activity center)
        for (Agent agent : qsim_agents) {
            // Some agents might not have plans.
            if (agent.plan.length == 0) {
                continue;
            }
            long planentry = agent.plan()[0];
            int type = Agent.getPlanHeader(planentry);
            int element = Agent.getPlanElement(planentry);
            switch (type) {
                case Agent.LinkType:
                    int linkid = Agent.getLinkPlanElement(element);
                    int velocity = Agent.getVelocityPlanElement(element);
                    Link link = qsim_links[linkid];
                    agent.linkFinishTime = link.length() / Math.min(velocity, link.velocity());
                    link.push(agent);
                    break;
                case Agent.SleepForType:
                case Agent.SleepUntilType:
                    qsim_realms[0].delayedAgents().get(agent.realm).get(element).add(agent);
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
                qsim_realms[0].delayedLinks().get(qsim_links[i].realm).get(nextwakeup).add(qsim_links[i]);
            }
        }
    }

    private void processPlanElement(ArrayList<Long> flatplan, PlanElement element) {
        Map<Id<Vehicle>, Vehicle> vehicles = scenario.getVehicles().getVehicles();
        if (element instanceof Leg) {
            Leg leg = (Leg) element;
            Route route = leg.getRoute();
            if (route == null) return;
            // Network circuit
            if (route instanceof NetworkRoute) {
                flatplan.add(Agent.prepareSleepForElement(0));
                NetworkRoute netroute = (NetworkRoute) route;
                Vehicle v = vehicles.get(netroute.getVehicleId());
                int velocity = World.MAX_VEHICLE_VELOCITY; // Bound by link speed.
                if (v != null) {
                    velocity = (int)v.getType().getMaximumVelocity();
                }
                int accessId = matsim_to_nqsim_Link.get(route.getStartLinkId().toString());
                int egressId = matsim_to_nqsim_Link.get(route.getEndLinkId().toString());
                flatplan.add(Agent.prepareLinkElement(accessId, velocity));
                for (Id<org.matsim.api.core.v01.network.Link> linkid : netroute.getLinkIds()) {
                    int linkId = matsim_to_nqsim_Link.get(linkid.toString());
                    flatplan.add(Agent.prepareLinkElement(linkId, velocity));
                }
                flatplan.add(Agent.prepareLinkElement(egressId, velocity));
            }
            // Public transport (vehicles)
            else if (route instanceof ExperimentalTransitRoute) {
                ExperimentalTransitRoute troute =
                    (ExperimentalTransitRoute) route;
                String access = troute.getAccessStopId().toString();
                String egress = troute.getEgressStopId().toString();
                String routeid = troute.getRouteId().toString();
                int routeId = matsim_to_nqsim_Route.get(routeid);
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
                if (time == 0.0) {
                    return;
                }
                flatplan.add(Agent.prepareSleepUntilElement((int)time));
            } else if (Double.isFinite(activity.getMaximumDuration())) {
                double time = activity.getMaximumDuration();
                if (time == 0.0) {
                    return;
                }
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
        Agent agent = new Agent(
            matsim_to_qsim_Agent.size(),
            matsim_to_qsim_Agent.size() % sim_threads,
            capacity,
            longplan);
        matsim_to_qsim_Agent.put(matsim_id, agent.id);
        nqsim_to_matsim_Agent.put(agent.id, matsim_id);
        qsim_agents[agent.id] = agent;
    }

    private void generateVehicleTrip(
            Map<Vehicle, ArrayList<Long>> plans,
            TransitRoute tr,
            Departure depart) {
        List<TransitRouteStop> trs = tr.getStops();
        TransitRouteStop next = trs.get(0);
        int lstopid = 0;
        int rid = matsim_to_nqsim_Route.get(tr.getId().toString());
        Vehicle v = scenario.getTransitVehicles().getVehicles().get(depart.getVehicleId());
        if (!plans.containsKey(v)) {
            plans.put(v, new ArrayList<>());
        }
        ArrayList<Long> flatplan = plans.get(v);
        int velocity = (int)v.getType().getMaximumVelocity();
        NetworkRoute nr = tr.getRoute();
        int startid = matsim_to_nqsim_Link.get(nr.getStartLinkId().toString());
        int endid = matsim_to_nqsim_Link.get(nr.getEndLinkId().toString());
        flatplan.add(Agent.prepareSleepUntilElement((int)depart.getDepartureTime()));
        flatplan.add(Agent.prepareRouteElement(rid));
        // Adding first link and possibly the first stop.
        if (next.getStopFacility().getLinkId().equals(nr.getStartLinkId())) {
            flatplan.add(Agent.prepareStopElement(lstopid++));
            next = trs.get(lstopid);
        }
        flatplan.add(Agent.prepareLinkElement(startid, velocity));
        for (Id<org.matsim.api.core.v01.network.Link> link : nr.getLinkIds()) {
            int linkid = matsim_to_nqsim_Link.get(link.toString());
            // Adding link and possibly a stop.
            if (next.getStopFacility().getLinkId().equals(link)) {
                flatplan.add(Agent.prepareStopElement(lstopid++));
                next = trs.get(lstopid);
            }
            flatplan.add(Agent.prepareLinkElement(linkid, velocity));
        }
        // Adding last link and possibly the last stop.
        if (next.getStopFacility().getLinkId().equals(nr.getEndLinkId())) {
            flatplan.add(Agent.prepareStopElement(lstopid++));
        }
        flatplan.add(Agent.prepareLinkElement(endid, velocity));
    }

    private void generateVehicles() {
        Map<Id<Vehicle>, Vehicle> vehicles = scenario.getTransitVehicles().getVehicles();
        Map<Vehicle, ArrayList<Long>> plans = new HashMap<>(vehicles.size());
        TransitSchedule ts = scenario.getTransitSchedule();
        // Create plans for vehicles.
        for (TransitLine tl: ts.getTransitLines().values()) {
            for (TransitRoute tr : tl.getRoutes().values()) {
                for (Departure depart : tr.getDepartures().values()) {
                    generateVehicleTrip(plans, tr, depart);
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
        nqsim_to_matsim_Agent = new HashMap<>();
        qsim_agents = new Agent[
            scenario.getPopulation().getPersons().size() +
            scenario.getTransitVehicles().getVehicles().size()];
        generatePersons();
        generateVehicles();
    }

    public Map<Integer, String> getNqsimToMatsimAgent() {
        return nqsim_to_matsim_Agent;
    }
}
