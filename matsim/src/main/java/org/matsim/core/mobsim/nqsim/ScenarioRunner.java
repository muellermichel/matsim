package org.matsim.core.mobsim.nqsim;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup.TrafficDynamics;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.quickevents.QuickEvents;
import org.matsim.core.utils.quickevents.StringlyEventlogTool;

public class ScenarioRunner {
    public static void main(String[] args) throws Exception {
        String worldpath = args[0];
        String configpath = args[1];
        Config config = prepareConfig(configpath) ;
        Scenario scenario = ScenarioUtils.loadScenario(config);
        ScenarioDumper.dumpScenario(scenario);
        World world = new ScenarioImporter(scenario, 1).generate();
        WorldDumper.dumpWorld(worldpath, world);
		QuickEvents eventLogger = new QuickEvents();
		//TODO: run nqsim, register events in QuickEvents object; the StringlyEventTool will expand NQSIM events to MATSim events
		// ....
		// this should create a runtime exception if timed reference output is not matched exactly:
		StringlyEventlogTool.testTimedEventsGeneration(scenario.getPopulation(), eventLogger, "berlin-1agent-output.xml");
    }
    
    public static Config prepareConfig(String configFileName) {
		OutputDirectoryLogging.catchLogEntries();
        
        Config config = ConfigUtils.loadConfig( configFileName ) ;
		
		config.controler().setRoutingAlgorithmType( FastAStarLandmarks );
		
		config.subtourModeChoice().setProbaForRandomSingleTripMode( 0.5 );
		
		// vsp defaults
		config.plansCalcRoute().setInsertingAccessEgressWalk( true );
		config.qsim().setUsingTravelTimeCheckInTeleportation( true );
		config.qsim().setTrafficDynamics( TrafficDynamics.kinematicWaves );
		
		for ( long ii = 600 ; ii <= 97200; ii+=600 ) {
			final ActivityParams params = new ActivityParams( "home_" + ii + ".0" ) ;
			params.setTypicalDuration( ii );
			config.planCalcScore().addActivityParams( params );
		}
		for ( long ii = 600 ; ii <= 97200; ii+=600 ) {
			final ActivityParams params = new ActivityParams( "work_" + ii + ".0" ) ;
			params.setTypicalDuration( ii );
			config.planCalcScore().addActivityParams( params );
		}
		for ( long ii = 600 ; ii <= 97200; ii+=600 ) {
			final ActivityParams params = new ActivityParams( "leisure_" + ii + ".0" ) ;
			params.setTypicalDuration( ii );
			config.planCalcScore().addActivityParams( params );
		}
		for ( long ii = 600 ; ii <= 97200; ii+=600 ) {
			final ActivityParams params = new ActivityParams( "shopping_" + ii + ".0" ) ;
			params.setTypicalDuration( ii );
			config.planCalcScore().addActivityParams( params );
		}
		for ( long ii = 600 ; ii <= 97200; ii+=600 ) {
			final ActivityParams params = new ActivityParams( "other_" + ii + ".0" ) ;
			params.setTypicalDuration( ii );
			config.planCalcScore().addActivityParams( params );
		}
		{
			final ActivityParams params = new ActivityParams( "freight" ) ;
			params.setTypicalDuration( 12.*3600. );
			config.planCalcScore().addActivityParams( params );
		}
		return config ;
	}
}

