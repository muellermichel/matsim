/*
 * Copyright 2018 Gunnar Flötteröd
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * contact: gunnar.flotterod@gmail.com
 *
 */
package org.matsim.contrib.pseudosimulation.searchacceleration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.pseudosimulation.searchacceleration.datastructures.CountIndicatorUtils;
import org.matsim.contrib.pseudosimulation.searchacceleration.datastructures.ScoreUpdater;
import org.matsim.contrib.pseudosimulation.searchacceleration.datastructures.SpaceTimeIndicators;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.router.util.TravelTime;

import floetteroed.utilities.DynamicData;
import floetteroed.utilities.TimeDiscretization;

/**
 * 
 * @author Gunnar Flötteröd
 *
 */
class ReplannerIdentifier {

	// -------------------- MEMBERS --------------------

	private final ReplanningParameterContainer replanningParameters;
	private final double meanLambda; // somewhat redundant
	private final double delta; // somewhat redundant
	private final TravelTime travelTimes;

	private final Map<Id<Person>, SpaceTimeIndicators<Id<Link>>> driverId2physicalLinkUsage;
	private final Map<Id<Person>, SpaceTimeIndicators<Id<Link>>> driverId2pSimLinkUsage;
	private final Population population;

	private final DynamicData<Id<Link>> currentTotalCounts;
	private final DynamicData<Id<Link>> currentWeightedCounts;
	private final DynamicData<Id<Link>> upcomingWeightedCounts;

	private final double sumOfCurrentWeightedCounts2;
	private final double sumOfWeightedCountDifferences2;
	private final double w;

	private Double shareOfScoreImprovingReplanners = null;
	private Double finalObjectiveFunctionValue = null;

	// -------------------- CONSTRUCTION --------------------

	ReplannerIdentifier(final ReplanningParameterContainer replanningParameters,
			final TimeDiscretization timeDiscretization, final int iteration,
			final Map<Id<Person>, SpaceTimeIndicators<Id<Link>>> driverId2physicalLinkUsage,
			final Map<Id<Person>, SpaceTimeIndicators<Id<Link>>> driverId2pSimLinkUsage, final Population population,
			final TravelTime travelTimes) {

		this.replanningParameters = replanningParameters;
		this.meanLambda = replanningParameters.getMeanLambda(iteration);
		this.travelTimes = travelTimes;

		this.driverId2physicalLinkUsage = driverId2physicalLinkUsage;
		this.driverId2pSimLinkUsage = driverId2pSimLinkUsage;
		this.population = population;

		this.currentTotalCounts = CountIndicatorUtils.newUnweightedCounts(timeDiscretization,
				driverId2physicalLinkUsage.values());
		this.currentWeightedCounts = CountIndicatorUtils.newWeightedCounts(timeDiscretization,
				driverId2physicalLinkUsage.values(), this.currentTotalCounts, replanningParameters, travelTimes);
		this.upcomingWeightedCounts = CountIndicatorUtils.newWeightedCounts(timeDiscretization,
				driverId2pSimLinkUsage.values(), this.currentTotalCounts, replanningParameters, travelTimes);

		this.sumOfCurrentWeightedCounts2 = CountIndicatorUtils.sumOfEntries2(this.currentWeightedCounts);
		if (this.sumOfCurrentWeightedCounts2 < 1e-6) {
			throw new RuntimeException(
					"There is no (weighted) traffic on the network: " + this.sumOfCurrentWeightedCounts2);
		}
		this.sumOfWeightedCountDifferences2 = CountIndicatorUtils.sumOfDifferences2(this.currentWeightedCounts,
				this.upcomingWeightedCounts);
		this.delta = replanningParameters.getDelta(iteration, this.sumOfWeightedCountDifferences2);
		this.w = this.meanLambda / (1.0 - this.meanLambda) * (this.sumOfWeightedCountDifferences2 + this.delta)
				/ this.sumOfCurrentWeightedCounts2;
	}

	ReplannerIdentifier(final ReplannerIdentifier parent) {
		this.replanningParameters = parent.replanningParameters;
		this.meanLambda = parent.meanLambda;
		this.delta = parent.delta;
		this.travelTimes = parent.travelTimes;

		this.driverId2physicalLinkUsage = parent.driverId2physicalLinkUsage;
		this.driverId2pSimLinkUsage = parent.driverId2pSimLinkUsage;
		this.population = parent.population;

		this.currentTotalCounts = parent.currentTotalCounts;
		this.currentWeightedCounts = parent.currentWeightedCounts;
		this.upcomingWeightedCounts = parent.upcomingWeightedCounts;

		this.sumOfCurrentWeightedCounts2 = parent.sumOfCurrentWeightedCounts2;
		this.sumOfWeightedCountDifferences2 = parent.sumOfWeightedCountDifferences2;
		this.w = parent.w;
	}

	// -------------------- IMPLEMENTATION --------------------

	public double getUniformReplanningObjectiveFunctionValue() {
		return this.meanLambda * (this.sumOfWeightedCountDifferences2 + this.delta);
	}

	public double getUniformityExcess() {
		return this.sumOfWeightedCountDifferences2 * this.meanLambda / this.delta;
	}

	public Double getShareOfScoreImprovingReplanners() {
		return this.shareOfScoreImprovingReplanners;
	}

	public Double getFinalObjectiveFunctionValue() {
		return this.finalObjectiveFunctionValue;
	}

	Set<Id<Person>> drawReplanners() {

		// Initialize score residuals.

		final DynamicData<Id<Link>> interactionResiduals = CountIndicatorUtils
				.newWeightedDifference(this.upcomingWeightedCounts, this.currentWeightedCounts, this.meanLambda);

		final DynamicData<Id<Link>> inertiaResiduals = new DynamicData<>(this.currentWeightedCounts.getStartTime_s(),
				this.currentWeightedCounts.getBinSize_s(), this.currentWeightedCounts.getBinCnt());
		for (Id<Link> locObj : this.currentWeightedCounts.keySet()) {
			for (int bin = 0; bin < this.currentWeightedCounts.getBinCnt(); bin++) {
				inertiaResiduals.put(locObj, bin,
						(1.0 - this.meanLambda) * this.currentWeightedCounts.getBinValue(locObj, bin));
			}
		}
		double regularizationResidual = this.meanLambda * this.sumOfCurrentWeightedCounts2;

		// Go through all vehicles and decide which driver gets to re-plan.

		final Set<Id<Person>> replanners = new LinkedHashSet<>();
		double score = this.getUniformReplanningObjectiveFunctionValue();

		final List<Id<Person>> allPersonIdsShuffled = new ArrayList<>(this.population.getPersons().keySet());
		Collections.shuffle(allPersonIdsShuffled);

		int scoreImprovingReplanners = 0;

		for (Id<Person> driverId : allPersonIdsShuffled) {

			// this.log(" driver " + driverId);

			final ScoreUpdater<Id<Link>> scoreUpdater = new ScoreUpdater<>(
					this.driverId2physicalLinkUsage.get(driverId), this.driverId2pSimLinkUsage.get(driverId),
					this.meanLambda, this.currentWeightedCounts, this.sumOfCurrentWeightedCounts2, this.w, this.delta,
					interactionResiduals, inertiaResiduals, regularizationResidual, this.replanningParameters,
					this.currentTotalCounts, this.travelTimes);

			// this.log(" scoreChange if one = " + (totalScoreChange +
			// scoreUpdater.getScoreChangeIfOne()));
			// this.log(" scoreChange if zero = " + (totalScoreChange +
			// scoreUpdater.getScoreChangeIfZero()));

			final double newLambda;

			if (Math.min(scoreUpdater.getScoreChangeIfOne(), scoreUpdater.getScoreChangeIfZero()) < 0) {
				if (scoreUpdater.getScoreChangeIfOne() < scoreUpdater.getScoreChangeIfZero()) {
					newLambda = 1.0;
					replanners.add(driverId);
					score += scoreUpdater.getScoreChangeIfOne();
					// this.personId2lastReplanIteration.put(driverId, event.getIteration());
				} else {
					newLambda = 0.0;
					score += scoreUpdater.getScoreChangeIfZero();
				}
				scoreImprovingReplanners++;
			} else {
				newLambda = 0.1;
				if (MatsimRandom.getRandom().nextDouble() < newLambda) {
					replanners.add(driverId);
					score += scoreUpdater.getScoreChangeIfOne();
				} else {
					score += scoreUpdater.getScoreChangeIfZero();
				}
			}

			scoreUpdater.updateResiduals(newLambda);
			// Interaction- and inertiaResiduals are updated by reference.
			regularizationResidual = scoreUpdater.getUpdatedRegularizationResidual();
		}
		this.shareOfScoreImprovingReplanners = ((double) scoreImprovingReplanners) / allPersonIdsShuffled.size();
		this.finalObjectiveFunctionValue = score;

		return replanners;
	}

	List<Double> bootstrap(final int replications) {

		final Map<Id<Person>, Integer> personId2replanCnt = new LinkedHashMap<>();
		for (Id<Person> personId : this.population.getPersons().keySet()) {
			personId2replanCnt.put(personId, 0);
		}

		for (int repl = 0; repl < replications; repl++) {
			final ReplannerIdentifier identifier = new ReplannerIdentifier(this);
			final Set<Id<Person>> replanners = identifier.drawReplanners();
			for (Id<Person> replannerId : replanners) {
				personId2replanCnt.put(replannerId, personId2replanCnt.get(replannerId) + 1);
			}
		}

		final List<Double> stats = new ArrayList<Double>();
		for (int i = 0; i <= replications; i++) {
			stats.add(0.0);
		}
		for (Integer cnt : personId2replanCnt.values()) {
			stats.set(cnt, stats.get(cnt) + 1.0 / this.population.getPersons().size());
		}
		return Collections.unmodifiableList(stats);
	}
}
