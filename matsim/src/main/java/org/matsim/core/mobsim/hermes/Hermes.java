package org.matsim.core.mobsim.hermes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.mobsim.framework.Mobsim;

public final class Hermes implements Mobsim {
	
	final private static Logger log = Logger.getLogger(Hermes.class);

    // Maximum number of links (limited to 24 bits in the plan)
    public static final int MAX_LINK_ID = 16777216;
    // Maximum number of stops in a link (limited to 8 bits in the plan)
    public static final int MAX_STOP_IDX = 255;
    // Maximum number of stops (limited to 20bits in the plan)
    public static final int MAX_STOP_ROUTE_ID = 65536;
    // Maximum vehicle velocity (limited to 8 bits in the plan)
    public static final int MAX_VEHICLE_VELOCITY = 255;
    // Maximum number of events per agent (limited to 16 bits in the plan)
    public static final int MAX_EVENTS_AGENT = 65536;
    // Maximum number of sim steps.
    public static final int MAX_SIM_STEPS = 7 * 60 * 60 * 24;
    // Number of simulation steps (7 day for now)
    // For test purposes, we are use 7 days instead of 36 hours
    public static final int SIM_STEPS = 60 * 60 * 36; //24 * 7; // TODO - get this from config file!
    // Number of ticks that are added to every agent advancing links.
    public static final int LINK_ADVANCE_DELAY = 1;

    public static final boolean SBB_SCENARIO = System.getProperty("scenario").equals("sbb");

    public static final boolean DEBUG_REALMS = false;
    public static final boolean DEBUG_EVENTS = false;
    public static final boolean DUMP_AGENTS = false;
    public static final boolean DUMP_SCENARIO_CONVERSION = false;

    public static int iteration = 0;
    // Inner iterations is used to run several iterations with the sample plans. Zero means disable inner iterations.
    // Any value above zero means running a number of iterations with the same plans.
    public final static int inner_its = 0;
    
    // Reamls that compose this World.
    private Realm[] realms;
    // Links within this World.
    private Link[] links;
    // Agents that circulate within the World.
    private Agent[] agents;
    // Pre-filled events created during scenario importing (events.get(agent id).get(time))
    private ArrayList<ArrayList<Event>> events;
    // Maps a hermes agent id to a matsim agent id.
    private Map<Integer, String> hermes_to_matsim_AgentId;

	private final Scenario scenario;
    private final EventsManager eventsManager;
    private final int sim_threads;
    
	public Hermes(Scenario scenario, EventsManager eventsManager) {
        this.scenario = scenario;
        this.eventsManager = eventsManager;
        this.sim_threads = scenario.getConfig().qsim().getNumberOfThreads();
    }
	
	private void importScenario() throws Exception {
		ScenarioImporter si = ScenarioImporter.instance(scenario, sim_threads);
		
		si.generate();
		this.realms = si.qsim_realms;
		this.links = si.qsim_links;
		this.agents = si.nqsim_agents;
		this.events = si.getEvents();
		this.hermes_to_matsim_AgentId = si.nqsim_to_matsim_Agent;
		
		if (DUMP_AGENTS) {
			WorldDumper.dumpAgents(agents);
		}
		if (DUMP_SCENARIO_CONVERSION) {
			si.dump_conversion();
		}
	}
	
	private void processEvents() {
		long time = System.currentTimeMillis();
		ArrayList<Event> sortedEvents = new ArrayList<>();
		
		// Patching time for events with zero timestamp
		for (ArrayList<Event> agent_events : events) {
			if (agent_events.isEmpty()) {
				continue;
			}
			int timestamp = (int) agent_events.get(agent_events.size() - 1).getTime();
			for (int j = agent_events.size() - 1; j >= 0; j--) {
				if (agent_events.get(j).getTime() == 0) {
					agent_events.get(j).setTime(timestamp);
				} else {
					timestamp = (int)agent_events.get(j).getTime();
				}
			}
		}
		// Fix the delay field in pt interactions
		for (int i = 0; i < events.size(); i++) {
			ArrayList<Event> agent_events = events.get(i);
			Agent agent = agents[i];
			for (Event event : agent_events) {
	            if (event instanceof VehicleArrivesAtFacilityEvent) {
					VehicleArrivesAtFacilityEvent vaafe = (VehicleArrivesAtFacilityEvent) event;
					vaafe.setDelay(vaafe.getTime() - vaafe.getDelay());
				}
				if (event instanceof VehicleDepartsAtFacilityEvent) {
					VehicleDepartsAtFacilityEvent vdafe = (VehicleDepartsAtFacilityEvent) event;
					vdafe.setDelay(vdafe.getTime() - vdafe.getDelay());
				}
			}
			// This remove actend that is not issued by qsim.
			if (agent_events.get(agent_events.size() - 1) instanceof ActivityEndEvent) {
				agent_events.get(agent_events.size() - 1).setTime(0);
			}
			if (!agent.finished()) {
				String agentId = hermes_to_matsim_AgentId.get(agent.id());
				agent_events.add(new PersonStuckEvent(
					Hermes.SIM_STEPS, Id.createPersonId(agentId), Id.createLinkId("0"), "zero"));
			}
		}
		// Sorting events by time
		for (ArrayList<Event> agent_events : events) {
			for (Event agent_time_event : agent_events) {
				if (agent_time_event.getTime() != 0) {
					sortedEvents.add(agent_time_event);
				}
			}
		}
		Collections.sort(sortedEvents, new Comparator<Event>() {
			@Override
			public int compare(Event a, Event b) {
				return Double.compare(a.getTime(), b.getTime());
			}
		});

		log.info(String.format("ETHZ hermes event processing took %d ms", System.currentTimeMillis() - time));

		time = System.currentTimeMillis();
		eventsManager.initProcessing();
        for (Event event : sortedEvents) {
            eventsManager.processEvent(event);
        }
        eventsManager.finishProcessing();
        log.info(String.format("ETHZ matsim event processing took %d ms", System.currentTimeMillis() - time));
	}

	@Override
	public void run() {
		long time;
		try {			
			time = System.currentTimeMillis();
			importScenario();
			log.info(String.format("ETHZ importing hermes scenario took %d ms", System.currentTimeMillis() - time));

			time = System.currentTimeMillis();
			realms[0].run(sim_threads);
			log.info(String.format(
					"ETHZ hermes (%d threads) took %d ms", sim_threads, System.currentTimeMillis() - time));

			processEvents();
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			Hermes.iteration += 1;
		}
	}
}
