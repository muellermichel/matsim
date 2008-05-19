/* *********************************************************************** *
 * project: org.matsim.*
 * LegHistogram.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package playground.yu.analysis;

import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.matsim.config.Config;
import org.matsim.events.AgentEvent;
import org.matsim.events.EventAgentArrival;
import org.matsim.events.EventAgentDeparture;
import org.matsim.events.EventAgentStuck;
import org.matsim.events.Events;
import org.matsim.events.MatsimEventsReader;
import org.matsim.events.handler.EventHandlerAgentArrivalI;
import org.matsim.events.handler.EventHandlerAgentDepartureI;
import org.matsim.events.handler.EventHandlerAgentStuckI;
import org.matsim.gbl.Gbl;
import org.matsim.network.MatsimNetworkReader;
import org.matsim.network.NetworkLayer;
import org.matsim.plans.Plan;
import org.matsim.plans.Plans;
import org.matsim.utils.charts.XYLineChart;
import org.matsim.utils.io.IOUtils;
import org.matsim.utils.misc.Time;

/**
 * @author mrieser
 * 
 * Counts the number of vehicles departed, arrived or got stuck per time bin
 * based on events.
 */
public class OnRouteModalSplit implements EventHandlerAgentDepartureI,
		EventHandlerAgentArrivalI, EventHandlerAgentStuckI {

	private int iteration = 0;
	private final int binSize;
	private final int[] countsDep;
	private final int[] countsArr;
	private final int[] countsStuck;
	private final int[] carCountsDep;
	private final int[] carCountsArr;
	private final int[] carCountsStuck;
	private final int[] ptCountsDep;
	private final int[] ptCountsArr;
	private final int[] onRoute, carOnRoute, ptOnRoute;
	private final NetworkLayer network;
	private final Plans plans;

	/**
	 * Creates a new LegHistogram with the specified binSize and the specified
	 * number of bins.
	 * 
	 * @param binSize
	 *            The size of a time bin in seconds.
	 * @param nofBins
	 *            The number of time bins for this analysis.
	 */
	public OnRouteModalSplit(final int binSize, final int nofBins,
			NetworkLayer network, Plans plans) {
		super();
		this.binSize = binSize;
		this.countsDep = new int[nofBins + 1]; // +1 for all times out of our
		// range
		this.countsArr = new int[nofBins + 1];
		this.countsStuck = new int[nofBins + 1];
		this.carCountsArr = new int[nofBins + 1];
		this.carCountsDep = new int[nofBins + 1];
		this.carCountsStuck = new int[nofBins + 1];
		this.ptCountsArr = new int[nofBins + 1];
		this.ptCountsDep = new int[nofBins + 1];
		this.onRoute = new int[nofBins + 1];
		this.carOnRoute = new int[nofBins + 1];
		this.ptOnRoute = new int[nofBins + 1];
		reset(0);
		this.network = network;
		this.plans = plans;
	}

	/**
	 * Creates a new LegHistogram with the specified binSize and a default
	 * number of bins, such that 30 hours are analyzed.
	 * 
	 * @param binSize
	 *            The size of a time bin in seconds.
	 */
	public OnRouteModalSplit(final int binSize, NetworkLayer network,
			Plans plans) {
		this(binSize, 30 * 3600 / binSize + 1, network, plans);
	}

	/* Implementation of eventhandler-Interfaces */

	public void handleEvent(final EventAgentDeparture event) {
		internHandleEvent(event, this.countsDep, this.carCountsDep,
				this.ptCountsDep);
	}

	public void handleEvent(final EventAgentArrival event) {
		internHandleEvent(event, this.countsArr, this.carCountsArr,
				this.ptCountsArr);
	}

	public void handleEvent(final EventAgentStuck event) {
		internHandleEvent(event, this.countsStuck, this.carCountsStuck, null);
	}

	private void internHandleEvent(AgentEvent ae, int[] allCount,
			int[] carCount, int[] ptCount) {
		int binIdx = getBinIndex(ae.time);
		allCount[binIdx]++;
		ae.rebuild(this.plans, this.network);
		Plan.Type planType = ae.agent.getSelectedPlan().getType();
		if ((planType == null) || (planType == Plan.Type.UNDEFINED)) {
			carCount[binIdx]++;
		} else {
			if (planType.equals(Plan.Type.CAR)) {
				carCount[binIdx]++;
			} else if (planType.equals(Plan.Type.PT)) {
				if (ptCount != null) {
					ptCount[binIdx]++;
				}
			}
		}
	}

	/* output methods */

	/**
	 * Writes the gathered data tab-separated into a text file.
	 * 
	 * @param filename
	 *            The name of a file where to write the gathered data.
	 */
	public void write(final String filename) {
		BufferedWriter bw;
		try {
			bw = IOUtils.getBufferedWriter(filename);
			write(bw);
			bw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void calcOnRoute() {
		this.onRoute[0] = this.countsDep[0] - this.countsArr[0]
				- this.countsStuck[0];
		this.carOnRoute[0] = this.carCountsDep[0] - this.carCountsArr[0]
				- this.carCountsStuck[0];
		this.ptOnRoute[0] = this.ptCountsDep[0] - this.ptCountsArr[0];
		for (int i = 1; i < this.countsDep.length; i++) {
			this.onRoute[i] = this.onRoute[i - 1] + this.countsDep[i]
					- this.countsArr[i] - this.countsStuck[i];
			this.carOnRoute[i] = this.carOnRoute[i - 1] + this.carCountsDep[i]
					- this.carCountsArr[i] - this.carCountsStuck[i];
			this.ptOnRoute[i] = this.ptOnRoute[i - 1] + this.ptCountsDep[i]
					- this.ptCountsArr[i];
		}
	}

	/**
	 * Writes the gathered data tab-separated into a text stream.
	 * 
	 * @param bw
	 *            The data stream where to write the gathered data.
	 */
	public void write(final BufferedWriter bw) {
		calcOnRoute();
		try {
			bw.write("time\ttimeBin\tdepartures\tarrivals\tstuck\ton_route"
					+ "\tcarDepartures\tcarArrivals\tcarStuck\tcarOnRoute"
					+ "\tptDepartures\tptArrivals\tptStuck\tptOnRoute\n");
			for (int i = 0; i < this.countsDep.length; i++) {
				bw.write(Time.writeTime(i * this.binSize) + "\t"
						+ i * this.binSize + "\t" + this.countsDep[i] + "\t"
						+ this.countsArr[i] + "\t" + this.countsStuck[i] + "\t"
						+ this.onRoute[i] + "\t" + this.carCountsDep[i] + "\t"
						+ this.carCountsArr[i] + "\t" + this.carCountsStuck[i]
						+ "\t" + this.carOnRoute[i] + "\t"
						+ this.ptCountsDep[i] + "\t" + this.ptCountsArr[i]
						+ "\t" + 0 + "\t" + this.ptOnRoute[i] + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public JFreeChart getGraphic() {

		final XYSeriesCollection data = new XYSeriesCollection();
		final XYSeries departuresSerie = new XYSeries("departures", false, true);
		final XYSeries arrivalsSerie = new XYSeries("arrivals", false, true);
		final XYSeries onRouteSerie = new XYSeries("on route", false, true);
		int onRoute = 0;
		for (int i = 0; i < this.countsDep.length; i++) {
			onRoute = onRoute + this.countsDep[i] - this.countsArr[i]
					- this.countsStuck[i];
			double hour = i * this.binSize / 60.0 / 60.0;
			departuresSerie.add(hour, this.countsDep[i]);
			arrivalsSerie.add(hour, this.countsArr[i]);
			onRouteSerie.add(hour, onRoute);
		}

		data.addSeries(departuresSerie);
		data.addSeries(arrivalsSerie);
		data.addSeries(onRouteSerie);

		final JFreeChart chart = ChartFactory.createXYStepChart(
				"Leg Histogram, it." + this.iteration, "time", "# vehicles",
				data, PlotOrientation.VERTICAL, true, // legend
				false, // tooltips
				false // urls
				);

		XYPlot plot = chart.getXYPlot();

		final CategoryAxis axis1 = new CategoryAxis("hour");
		axis1.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 7));
		plot.setDomainAxis(new NumberAxis("time"));
		return chart;
	}

	public void writeGraphic(final String filename) {
		try {
			ChartUtilities.saveChartAsPNG(new File(filename), getGraphic(),
					1024, 768);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* private methods */

	private int getBinIndex(final double time) {
		int bin = (int) (time / this.binSize);
		if (bin >= this.countsDep.length) {
			return this.countsDep.length - 1;
		}
		return bin;
	}

	public void reset(final int iteration) {
		this.iteration = iteration;
		for (int i = 0; i < this.countsDep.length; i++) {
			this.countsDep[i] = 0;
			this.countsArr[i] = 0;
			this.countsStuck[i] = 0;
		}
	}

	public void writeCharts(final String filename) {
		double[] category = new double[this.countsDep.length + 1];
		for (int j = 0; j < category.length; j++) {
			category[j] = ((double) j) * (double) this.binSize / 3600.0;
		}
		XYLineChart onRouteChart = new XYLineChart("Leg Histogramm", "time",
				"vehicles");
		int length = this.onRoute.length;
		double[] onRoute = new double[length];
		double[] carOnRoute = new double[length];
		double[] ptOnRoute = new double[length];
		for (int i = 0; i < length; i++) {
			onRoute[i] = this.onRoute[i];
			carOnRoute[i] = this.carOnRoute[i];
			ptOnRoute[i] = this.ptOnRoute[i];
		}
		onRouteChart.addSeries("all agents on route", category, onRoute);
		onRouteChart.addSeries("drivers on route", category, carOnRoute);
		onRouteChart.addSeries("public transit users on route", category,
				ptOnRoute);
		onRouteChart.saveAsPng(filename, 1024, 768);
	}
}
