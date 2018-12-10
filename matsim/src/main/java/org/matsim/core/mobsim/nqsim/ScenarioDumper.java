package org.matsim.core.mobsim.nqsim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.households.Household;
import org.matsim.households.Households;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

public class ScenarioDumper {

    public static void dumpScenario(Scenario scenario) {
        dumpNetwork(scenario.getNetwork());
        dumpLanes(scenario.getLanes());
        dumpVehicles(scenario.getVehicles());
        dumpVehicles(scenario.getTransitVehicles()); // TODO - diff wrt transit vehicles.
        dumpTransitSchedule(scenario.getTransitSchedule());
        dumpPopulation(scenario.getPopulation());
        dumpHouseholds(scenario.getHouseholds());
        dumpActivities(scenario.getActivityFacilities());
        // TODO - dump config?
    }

    public static void dumpTransitSchedule(TransitSchedule ts) {
        for (TransitLine tl: ts.getTransitLines().values()) {
            System.out.println(String.format(
                "Transit Line (%s) %s", tl.getClass().getName(), tl));
            for (TransitRoute tr : tl.getRoutes().values()) {
                System.out.println(String.format(
                    "\tTransit Route (%s) %s", tr.getClass().getName(), tr));
                for (Id<Link> link : tr.getRoute().getLinkIds()) {
                    System.out.println("\t\tLink id " + link.toString());
                }
                for (TransitRouteStop trs : tr.getStops()) {
                    TransitStopFacility tsf = trs.getStopFacility();
                    System.out.println(String.format(
                        "\t\tTransit Stop Facility (%s) Link %s %s", 
                        tsf.getClass().getName(),
                        tsf.getLinkId().toString(),
                        tsf));
                }
            }
        }
        for (TransitStopFacility tsf : ts.getFacilities().values()) {
            System.out.println(String.format(
                "Transit Stop Facility (%s) %s", tsf.getClass().getName(), tsf));
        }
        System.out.println(ts.getMinimalTransferTimes());
    }

    // TODO - check if we actually have activities and if so, how are they setup.
    public static void dumpActivities(ActivityFacilities facilities) {
        for (ActivityFacility facility : facilities.getFacilities().values()) {
            System.out.println(String.format(
                "Activity Facility (%s) %s", facility.getClass().getName(), facility));
        }
    }

    // TODO - check if we actually have households and if so, how are they setup.
    public static void dumpHouseholds(Households households) {
        for (Household household: households.getHouseholds().values()) {
            System.out.println(String.format(
                "Household (%s) %s", household.getClass().getName(), household));
        }
    }

    private static void dumpVehicles(Vehicles vehicles) {
        for (VehicleType vt : vehicles.getVehicleTypes().values()) {
            System.out.println(String.format(
                "Vehicle Type (%s) id:%s desc:%s len:%f width:%f", 
                vt.getClass().getName(),
                vt.getId(),
                vt.getDescription(),
                vt.getLength(),
                vt.getWidth()));
            // TODO - complete print out?
        }
        for (Vehicle vehicle : vehicles.getVehicles().values()) {
            System.out.println(String.format(
                "Vehicle (%s) id:%s type:%s", 
                vehicle.getClass().getName(), 
                vehicle.getId(),
                vehicle.getType().getId()));
        }
    }

    private static void dumpPopulation(Population population) {
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

    // TODO - check if we actually have lanes and if so, how are they setup.
    private static void dumpLanes(Lanes lanes) {
        System.out.println("Lanes " + lanes);
         for (LanesToLinkAssignment ltla : lanes.getLanesToLinkAssignments().values()) {
            for (Lane lane : ltla.getLanes().values()) {
                System.out.println(String.format(
                    "Lane (%s) %s", lane.getClass().getName(), lane));

            }
         }
    }

    private static void dumpNetwork(Network network) {
        System.out.println(String.format(
            "Network (%s) Name:%s", 
            network.getClass().getName(),
            network.getName()));
        System.out.println(String.format("Network Effective Cell Size:%f", 
            network.getEffectiveCellSize()));
        System.out.println(String.format("Network Effective Lane Width:%f", 
            network.getEffectiveLaneWidth()));
        System.out.println(String.format("Network Capacity Pedior:%f", 
            network.getCapacityPeriod()));
        for (Link link : network.getLinks().values()) {
            dumpLink(link);
        }
        for (Node node : network.getNodes().values()) {
            dumpNode(node);
        }
    }

    private static void dumpNode(Node node) {
        System.out.println(String.format(
            "Node (%s) %s", node.getClass().getName(), node));
        for (Id<Link> in : node.getInLinks().keySet()) {
            System.out.println(String.format("\tIn Link id:%s", in));
        }
        for (Id<Link> out : node.getOutLinks().keySet()) {
            System.out.println(String.format("\tOut Link id:%s", out));
        }
    }

    private static void dumpLink(Link link) {
        System.out.println(String.format(
            "Link (%s) %s", link.getClass().getName(), link));
    }
	
}

