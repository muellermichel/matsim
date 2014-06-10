package playground.balac.retailers.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.facilities.ActivityFacility;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.controler.Controler;
import org.matsim.core.facilities.ActivityFacilityImpl;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordUtils;

import playground.balac.retailers.data.LinkRetailersImpl;
import playground.balac.retailers.data.PersonPrimaryActivity;
import playground.balac.retailers.utils.Utils;



public class MinTravelCostRoadPriceModel extends RetailerModelImpl
{
  private static final Logger log = Logger.getLogger(MaxActivityModel.class);

  private TreeMap<Id, LinkRetailersImpl> availableLinks = new TreeMap<Id, LinkRetailersImpl>();

  public MinTravelCostRoadPriceModel(Controler controler, Map<Id, ActivityFacilityImpl> retailerFacilities)
  {
    this.controler = controler;
    this.retailerFacilities = retailerFacilities;
    this.controlerFacilities = this.controler.getFacilities();
    this.shops = findScenarioShops(this.controlerFacilities.getFacilities().values());

    for (Person p : controler.getPopulation().getPersons().values()) {
      PersonImpl pi = (PersonImpl)p;
      this.persons.put(pi.getId(), pi);
    }
  }

  public void init(TreeMap<Integer, String> first)
  {
    this.first = first;

    setInitialSolution(this.first.size());
    log.info("Initial solution = " + getInitialSolution());
    findScenarioShops(this.controlerFacilities.getFacilities().values());
    Gbl.printMemoryUsage();
   /* for (PersonImpl pi : this.persons.values()) {
      PersonRetailersImpl pr = new PersonRetailersImpl(pi);
      this.retailersPersons.put(pr.getId(), pr);
    }*/
    Utils.setPersonPrimaryActivityQuadTree(Utils.createPersonPrimaryActivityQuadTree(this.controler));
    Utils.setShopsQuadTree(Utils.createShopsQuadTree(this.controler));
    
    Utils.setInsideShopsQuadTree(Utils.createInsideShopsQuadTreeWIthoutRetailers(this.controler, this.retailerFacilities));
    Utils.setOutsideShopsQuadTree(Utils.createOutsideShopsQuadTreeWIthoutRetailers(this.controler, this.retailerFacilities));
    
    //TODO: kick out retailers stores
    for(ActivityFacility af: retailerFacilities.values()) {
    	 Utils.removeShopFromShopsQuadTree(af.getCoord().getX(), af.getCoord().getY(), af);
    	 Utils.removeInsideShopFromShopsQuadTree(af.getCoord().getX(), af.getCoord().getY(), af);
    	 Utils.removeOutsideShopFromShopsQuadTree(af.getCoord().getX(), af.getCoord().getY(), af);
    }
    
    for (Integer i = Integer.valueOf(0); i.intValue() < first.size(); i = Integer.valueOf(i.intValue() + 1)) {
      String linkId = this.first.get(i);
      double scoreSum = 0.0D;
      LinkRetailersImpl link = new LinkRetailersImpl(this.controler.getNetwork().getLinks().get(new IdImpl(linkId)), this.controler.getNetwork(), Double.valueOf(0.0D), Double.valueOf(0.0D));
      double centerX = 683217.0; 
      double centerY = 247300.0;
      CoordImpl coord = new CoordImpl(centerX, centerY);
      Collection<PersonPrimaryActivity> primaryActivities;
      if (CoordUtils.calcDistance(link.getCoord(), coord) < 5000) {
    	  
	      primaryActivities = Utils.getPersonPrimaryActivityQuadTree().get(link.getCoord().getX(), link.getCoord().getY(), 2700.0D);

      }
      else
	      primaryActivities = Utils.getPersonPrimaryActivityQuadTree().get(link.getCoord().getX(), link.getCoord().getY(), 5000.0D);

      
      scoreSum = primaryActivities.size();
     
      
      link.setScoreSum(scoreSum);
      link.setPotentialCustomers(scoreSum);
      this.availableLinks.put(link.getId(), link);
    }
  }

  private void computePotentialCustomers() {
	  for (Integer i = Integer.valueOf(0); i.intValue() < first.size(); i = Integer.valueOf(i.intValue() + 1)) {
	      String linkId = this.first.get(i);
	     
	      LinkRetailersImpl link = new LinkRetailersImpl(this.controler.getNetwork().getLinks().get(new IdImpl(linkId)), this.controler.getNetwork(), Double.valueOf(0.0D), Double.valueOf(0.0D));
	      double centerX = 683217.0; 
	      double centerY = 247300.0;
	      CoordImpl coord = new CoordImpl(centerX, centerY);
	      Collection<ActivityFacility> facilities1;
	      Collection<ActivityFacility> facilities2;

	      if (CoordUtils.calcDistance(link.getCoord(), coord) < 5000) {
		      facilities1 = Utils.getInsideShopsQuadTree().get(link.getCoord().getX(), link.getCoord().getY(), 2700.0D);
		      facilities2 = Utils.getOutsideShopsQuadTree().get(link.getCoord().getX(), link.getCoord().getY(), 2700.0D);
		      if (facilities1.size() ==0)
		      log.info("size 0");
		      
		      link.setPotentialCustomers(availableLinks.get(link.getId()).getScoreSum() / (((double)(facilities2.size() ) * 1.5) + (double)facilities1.size()));
	      }
	      else {	    	  
	    	  facilities1 = Utils.getInsideShopsQuadTree().get(link.getCoord().getX(), link.getCoord().getY(), 5000.0D);
	    	  facilities2 = Utils.getOutsideShopsQuadTree().get(link.getCoord().getX(), link.getCoord().getY(), 5000.0D);
	    	  if (facilities2.size() == 0)
			      log.info("size 0");
	    	  link.setPotentialCustomers(availableLinks.get(link.getId()).getScoreSum() / (((double)(facilities1.size() ) * 0.5) + ((double)(facilities2.size()))));

	      }
	        
	  
	      
	      link.setScoreSum(availableLinks.get(link.getId()).getScoreSum());
	      this.availableLinks.put(link.getId(), link);
	    }
	  
  }
  
 

  

  @Override
	public double computePotential(ArrayList<Integer> solution) {
	  
	  Double Fitness = 0.0D;
	  double centerX = 683217.0; 
      double centerY = 247300.0;
      CoordImpl coord = new CoordImpl(centerX, centerY);
	  ActivityFacilityImpl af = (ActivityFacilityImpl) retailerFacilities.values().toArray()[0];
	  for (int s = 0; s < this.retailerFacilities.size(); ++s) {
		  String linkId = this.first.get(solution.get(s));
		 // Coord coord = new CoordImpl(1,1);
		  if (CoordUtils.calcDistance(coord, this.availableLinks.get(new IdImpl(linkId)).getCoord()) < 5000) {
			  Utils.addInsideShopToShopsQuadTree(this.availableLinks.get(new IdImpl(linkId)).getCoord().getX(), this.availableLinks.get(new IdImpl(linkId)).getCoord().getY(), af);
		  }
		  else
			  Utils.addOutsideShopToShopsQuadTree(this.availableLinks.get(new IdImpl(linkId)).getCoord().getX(), this.availableLinks.get(new IdImpl(linkId)).getCoord().getY(), af);

		  Utils.addShopToShopsQuadTree(this.availableLinks.get(new IdImpl(linkId)).getCoord().getX(), this.availableLinks.get(new IdImpl(linkId)).getCoord().getY(), af);
	  }
	  computePotentialCustomers();
	  //log.info("computed potential");
	  for (int s = 0; s < this.retailerFacilities.size(); ++s) {
		  String linkId = this.first.get(solution.get(s));
		  Fitness +=  this.availableLinks.get(new IdImpl(linkId)).getPotentialCustomers();
	  }

	  for (int s = 0; s < this.retailerFacilities.size(); ++s) {
		  String linkId = this.first.get(solution.get(s));		
		  if (CoordUtils.calcDistance(coord, this.availableLinks.get(new IdImpl(linkId)).getCoord()) < 5000) {
			  Utils.removeInsideShopFromShopsQuadTree(this.availableLinks.get(new IdImpl(linkId)).getCoord().getX(), this.availableLinks.get(new IdImpl(linkId)).getCoord().getY(), af);
		  }
		  else
			  Utils.removeOutsideShopFromShopsQuadTree(this.availableLinks.get(new IdImpl(linkId)).getCoord().getX(), this.availableLinks.get(new IdImpl(linkId)).getCoord().getY(), af);

		  Utils.removeShopFromShopsQuadTree(this.availableLinks.get(new IdImpl(linkId)).getCoord().getX(), this.availableLinks.get(new IdImpl(linkId)).getCoord().getY(), af);
	  }
	  return Fitness;
  }

  public Map<Id, ActivityFacilityImpl> getScenarioShops() {
    return this.shops;
  }
}
