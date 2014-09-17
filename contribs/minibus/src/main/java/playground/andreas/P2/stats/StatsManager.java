/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.andreas.P2.stats;

import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import playground.andreas.P2.PConfigGroup;
import playground.andreas.P2.PConstants;
import playground.andreas.P2.hook.PBox;
import playground.andreas.P2.stats.abtractPAnalysisModules.lineSetter.PtMode2LineSetter;
import playground.andreas.P2.stats.gexfPStats.GexfPStat;
import playground.andreas.P2.stats.gexfPStats.GexfPStatLight;
import playground.andreas.P2.stats.gexfPStats.Line2GexfPStat;
import playground.andreas.P2.stats.pStatsOverview.PStatsOverview;

import java.io.File;

/**
 * 
 * Registers all stats modules with MATSim
 * 
 * @author aneumann
 *
 */
public final class StatsManager implements StartupListener {
	
	public StatsManager(Controler controler, PConfigGroup pConfig, PBox pBox, PtMode2LineSetter lineSetter) {
		controler.addControlerListener(new PStatsOverview(pBox, pConfig));
		controler.addControlerListener(new POperatorLogger(pBox, pConfig));
		controler.addControlerListener(new GexfPStat(pConfig, false));
		controler.addControlerListener(new GexfPStatLight(pConfig));
		controler.addControlerListener(new Line2GexfPStat(pConfig));
		controler.addControlerListener(new PAnalysisManager(pConfig, lineSetter));
		controler.addControlerListener(new ActivityLocationsParatransitUser(pConfig));
	}

	@Override
	public void notifyStartup(StartupEvent event) {
		String outFilename = event.getControler().getControlerIO().getOutputPath() + PConstants.statsOutputFolder;
		new File(outFilename).mkdir();
	}

}
