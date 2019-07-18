package org.matsim.core.mobsim.hermes;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.ParallelEventsManager;
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
    public static final int MAX_SIM_STEPS = 7 * 60 * 60 * 24; // TODO - we should not use this, anything extra than SIM_STEPS should not be considered!
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
    public static final boolean CONCURRENT_EVENT_PROCESSING = true;

    public static int iteration = 0;
    // Inner iterations is used to run several iterations with the sample plans. Zero means disable inner iterations.
    // Any value above zero means running a number of iterations with the same plans.
    public final static int inner_its = 0;
    
    // Reamls that compose this World.
    private Realm realm;
    // Agents that circulate within the World.
    private Agent[] agents;

	private final Scenario scenario;
	private ScenarioImporter si;
    private final ParallelEventsManager eventsManager;
    
	public Hermes(Scenario scenario, EventsManager eventsManager) {
        this.scenario = scenario;
        this.eventsManager = (ParallelEventsManager) eventsManager;
    }
	
	private void importScenario() throws Exception {
		si = ScenarioImporter.instance(scenario, eventsManager);
		si.generate();
		this.realm = si.realm;
		this.agents = si.hermes_agents;
		
		if (DUMP_AGENTS) {
			WorldDumper.dumpAgents(agents);
		}
		if (DUMP_SCENARIO_CONVERSION) {
			si.dump_conversion();
		}
	}

	private void processEvents() {
        eventsManager.processEvents(realm.getSortedEvents());

		for (Agent agent : agents) {
			if (!agent.finished() && !agent.isVehicle()) {
				int matsim_id = si.matsim_id(agent.id(),  false);
				eventsManager.processEvent(
						new PersonStuckEvent(
								Hermes.SIM_STEPS, Id.get(matsim_id, Person.class), Id.createLinkId("0"), "zero"));
			}
		}
	}

	@Override
	public void run() {
		long time;
		try {			
			time = System.currentTimeMillis();
			importScenario();
			log.info(String.format("ETHZ importing hermes scenario took %d ms", System.currentTimeMillis() - time));

			eventsManager.initProcessing();

			time = System.currentTimeMillis();
			realm.run();
			log.info(String.format(
					"ETHZ hermes took %d ms", System.currentTimeMillis() - time));

			time = System.currentTimeMillis();
			processEvents();
			eventsManager.finishProcessing();
			log.info(String.format("ETHZ matsim event processing took %d ms", System.currentTimeMillis() - time));

			// Launch scenario imported in backgroud
			si.reset();
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			Hermes.iteration += 1;
		}
	}
}
