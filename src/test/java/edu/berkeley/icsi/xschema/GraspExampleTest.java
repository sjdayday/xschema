package edu.berkeley.icsi.xschema;

import static org.junit.Assert.*;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import uk.ac.imperial.pipe.dsl.ANormalArc;
import uk.ac.imperial.pipe.dsl.APetriNet;
import uk.ac.imperial.pipe.dsl.APlace;
import uk.ac.imperial.pipe.dsl.AToken;
import uk.ac.imperial.pipe.dsl.AnExternalTransition;
import uk.ac.imperial.pipe.dsl.AnImmediateTransition;
import uk.ac.imperial.pipe.exceptions.IncludeException;
import uk.ac.imperial.pipe.exceptions.PetriNetComponentException;
import uk.ac.imperial.pipe.exceptions.PetriNetComponentNotFoundException;
import uk.ac.imperial.pipe.models.petrinet.Arc;
import uk.ac.imperial.pipe.models.petrinet.InboundArc;
import uk.ac.imperial.pipe.models.petrinet.InboundNormalArc;
import uk.ac.imperial.pipe.models.petrinet.IncludeHierarchy;
import uk.ac.imperial.pipe.models.petrinet.OutboundArc;
import uk.ac.imperial.pipe.models.petrinet.OutboundNormalArc;
import uk.ac.imperial.pipe.models.petrinet.PetriNet;
import uk.ac.imperial.pipe.models.petrinet.Place;
import uk.ac.imperial.pipe.models.petrinet.Transition;
import uk.ac.imperial.pipe.runner.FiringWriter;
import uk.ac.imperial.pipe.runner.InterfaceException;
import uk.ac.imperial.pipe.runner.PetriNetRunner;
import uk.ac.imperial.pipe.runner.Runner;
import uk.ac.imperial.pipe.runner.StateReport;
/**
 * The Grasp example shows how to build an x-schema that executes other x-schemas.
 * {@link https://github.com/sjdayday/xschema/wiki}  
 * The test methods below show the steps.  
 * Note that because each test is independent, they may not execute in the order in which they appear in the file.
 * 
 * The tests assert markings at points of interest.  To see the actual results file for a given test, 
 * just run that test by itself, and then look at the report.csv file in your xschema project root directory
 * (if running eclipse, right click on the test in the Junit output, and select "Run")
 * @author stevedoubleday
 *
 */
public class GraspExampleTest {

    private static  String CLOSE_SENSED = null;
	private PetriNet net;
	private Runner runner;
	private int events;
	private StateReport report;
	private int checkCase;
	private ByteArrayOutputStream out;
	private PrintStream print;
	private BufferedReader reader;
	private File file;
	private int tokenFired;
	private boolean tokenEvent;
	private String targetPlaceId;
	private String includePath;
	String filename = "report.csv";
	private List<String> linelist;
	private IncludeHierarchy includes;
	private Map<String,String> tokenweights;

    @Before
    public void setUp() {
        cleanupFile(filename); 
        buildTokenWeights(); 
    }

	@Test
	public void basicXschemaBuilt() throws Exception {
		PetriNet basicControl = buildBasicNet(); 
		runner = new PetriNetRunner(basicControl); 
		runner.markPlace("Enabled", "Default", 1);
		run();
//		printResults();
		checkLine("", 0, "\"Round\",\"Transition\",\"Done\",\"Enabled\",\"Ongoing\",\"Ready\"");
		checkLine("after setup, Enabled is marked by client request", 
				1, "0,\"\",0,0,0,0");
		checkLine("...but not visible because Prepare transition fires before report record is generated",
				2, "1,\"Prepare\",0,0,0,1");
		checkLine("", 4, "3,\"Finish\",1,0,0,0");
	}

	@Test
	public void closeHandXschemaBuilt() throws Exception {
		PetriNet closeHand = buildCloseHand(); 
		runner = new PetriNetRunner(closeHand); 
		runner.markPlace("Enabled", "Default", 1);
		CLOSE_SENSED = "Close_sensed"; 
		runner.setTransitionContext("Close", this);
		run(); 
		checkLine("", 0, "\"Round\",\"Transition\",\"Close_sensed\",\"Closing\",\"Done\",\"Enabled\",\"Ongoing\",\"P4\",\"Ready\"");
		checkLine("external transition fired...", 4, "3,\"Close\",0,1,0,0,1,0,0");
		checkLine("...marking close_sensed place but not visible here", 5, "4,\"Finish\",0,0,1,0,0,0,0");
//		printResults();
	}
	@Test
	public void graspXschemaIncludesCloseHand() throws Exception {
		PetriNet basicControl = buildBasicNet(); 
		PetriNet closeHand = buildCloseHand(); 
		includes = new IncludeHierarchy(basicControl, "Grasp"); 
		includes.include(closeHand, "Close_hand");
//		build a "merge arc" by hand the first time...
    	includes.getInclude("Close_hand").addToInterface(closeHand.getComponent("Enabled", Place.class), true, false, false, false);    
    	includes.addAvailablePlaceToPetriNet(includes.getInterfacePlace("Close_hand.Enabled"));
    	Place graspCloseHandEnabled = includes.getInterfacePlace("Close_hand.Enabled"); 
    	Transition start = basicControl.getComponent("Start",Transition.class);
		OutboundArc arcOut = new OutboundNormalArc(start, graspCloseHandEnabled, tokenweights);
    	basicControl.add(arcOut); 
//    	...equivalent to this:
//		buildMergeArc(false, includes, "Close_hand", "Enabled", "Start", "Close_hand.Enabled"); 
    	
    	buildMergeArc(true, includes, "Close_hand", "Done", "Finish", "Close_hand.Done"); 
    	
		runner = new PetriNetRunner(includes.getPetriNet()); 
		runner.markPlace("Grasp.Enabled", "Default", 1);
		CLOSE_SENSED = "Grasp.Close_hand.Close_sensed"; 
		runner.setTransitionContext("Grasp.Close_hand.Close", this);
		run(); 
//		printResults();
		checkLine("", 0, "\"Round\",\"Transition\",\"Grasp.Close_hand.Close_sensed\",\"Grasp.Close_hand.Closing\",\"Grasp.Close_hand.Done\","
				+ "\"Grasp.Close_hand.Enabled\",\"Grasp.Close_hand.Ongoing\",\"Grasp.Close_hand.P4\",\"Grasp.Close_hand.Ready\",\"Grasp.Done\","
				+ "\"Grasp.Enabled\",\"Grasp.Ongoing\",\"Grasp.Ready\"");
		checkLine("", 2, "1,\"Grasp.Prepare\",0,0,0,0,0,0,0,0,0,0,1");
		checkLine("", 3, "2,\"Grasp.Start\",0,0,0,1,0,0,0,0,0,1,0");
		checkLine("", 4, "3,\"Grasp.Close_hand.Prepare\",0,0,0,0,0,0,1,0,0,1,0");
		checkLine("", 5, "4,\"Grasp.Close_hand.Start\",0,0,0,0,1,1,0,0,0,1,0");
		checkLine("", 6, "5,\"Grasp.Close_hand.Close\",0,1,0,0,1,0,0,0,0,1,0");
		checkLine("", 7, "6,\"Grasp.Close_hand.Finish\",0,0,1,0,0,0,0,0,0,1,0");
		checkLine("", 8, "7,\"Grasp.Finish\",0,0,0,0,0,0,0,1,0,0,0");
		
	}

	@SuppressWarnings("rawtypes")
	private void buildMergeArc(boolean inbound,
			IncludeHierarchy parent, String child, String homePlace, String transition, String awayPlace) 
					throws IncludeException, PetriNetComponentNotFoundException, PetriNetComponentException {
		parent.getInclude(child).addToInterface(parent.getInclude(child).getPetriNet().
				getComponent(homePlace, Place.class), true, false, false, false);    
		parent.addAvailablePlaceToPetriNet(parent.getInterfacePlace(awayPlace));
		Arc arc = (inbound) ? new InboundNormalArc(parent.getInterfacePlace(awayPlace), 
				parent.getPetriNet().getComponent(transition,Transition.class), tokenweights)   
			    : new OutboundNormalArc(parent.getPetriNet().getComponent(transition,Transition.class), 
						parent.getInterfacePlace(awayPlace), tokenweights)	;
		parent.getPetriNet().add(arc); 
	}


	private void buildTokenWeights() {
		tokenweights = new HashMap<String, String>(); 
		tokenweights.put("Default", "1");
	}
	private void run() throws Exception {
		runner.addPropertyChangeListener(new FiringWriter("report.csv"));
		runner.setFiringLimit(100);
		runner.setSeed(123456l); 
		runner.run();
		buildLineList(); 
	}

	private void printResults() {
		for (String line : linelist) {
			System.out.println(line);
		}
	}
	private void checkLine(String comment, int i, String expected) throws IOException {
		assertEquals(comment, expected, linelist.get(i));
	}

	private void buildLineList() throws FileNotFoundException, IOException {
		BufferedReader fileReader = new BufferedReader(new FileReader(filename)); 
		linelist = new ArrayList<String>(); 
		String line = fileReader.readLine(); 
		while (line != null) {
			linelist.add(line); 
			line = fileReader.readLine(); 
		}
		fileReader.close();
	}

    private PetriNet buildBasicNet() {
    	PetriNet net = APetriNet.named("basic-xschema").and(AToken.called("Default").withColor(Color.BLACK)).
    					and(APlace.withId("P0").externallyAccessible()).and(APlace.withId("P1")).and(APlace.withId("P2")).and(APlace.withId("P3")).
    			    	and(AnImmediateTransition.withId("T0")).and(AnImmediateTransition.withId("T1")).and(AnImmediateTransition.withId("T2")).		
    					and(ANormalArc.withSource("P0").andTarget("T0").with("1", "Default").token()).
    					and(ANormalArc.withSource("T0").andTarget("P1").with("1", "Default").token()).
    					and(ANormalArc.withSource("P1").andTarget("T1").with("1", "Default").token()).
    					and(ANormalArc.withSource("T1").andTarget("P2").with("1", "Default").token()).
    					and(ANormalArc.withSource("P2").andTarget("T2").with("1", "Default").token()).
    					andFinally(ANormalArc.withSource("T2").andTarget("P3").with("1", "Default").token());
//    					and(ANormalArc.withSource("P3").andTarget("T3").with("1", "Default").token()).
//    					andFinally(ANormalArc.withSource("T3").andTarget("P4").with("1", "Default").token());
//			and(APlace.withId("P1").externallyAccessible()).and(APlace.withId("P2")).
    	// name components manually, until support added to DSL in PIPECore
    	name(net, Place.class, "P0", "Enabled"); 
    	name(net, Place.class, "P1", "Ready"); 
    	name(net, Place.class, "P2", "Ongoing"); 
    	name(net, Place.class, "P3", "Done"); 
    	name(net, Transition.class, "T0", "Prepare"); 
    	name(net, Transition.class, "T1", "Start"); 
    	name(net, Transition.class, "T2", "Finish"); 
//    	for (Place place : net.getExecutablePetriNet().getPlaces()) {
//			System.out.println(place.getId());
//			System.out.println(place.getName());
//		}
    	return net; 
    }
    private PetriNet buildCloseHand() {
    	// P0 / Enabled is externally accessible for testing, not because required for Grasp xschema
    	PetriNet net = APetriNet.named("Close_hand").and(AToken.called("Default").withColor(Color.BLACK)).
    			and(APlace.withId("P0").externallyAccessible()).and(APlace.withId("P1")).and(APlace.withId("P2")).and(APlace.withId("P3")).
    			and(APlace.withId("P4")).and(APlace.withId("P5")).and(APlace.withId("P6").externallyAccessible()).
    			and(AnImmediateTransition.withId("T0")).and(AnImmediateTransition.withId("T1")).and(AnImmediateTransition.withId("T2")).		
				and(AnExternalTransition.withId("T3").andExternalClass("edu.berkeley.icsi.xschema.TestingCloseExternalTransition")).
    			and(ANormalArc.withSource("P0").andTarget("T0").with("1", "Default").token()).
    			and(ANormalArc.withSource("T0").andTarget("P1").with("1", "Default").token()).
    			and(ANormalArc.withSource("P1").andTarget("T1").with("1", "Default").token()).
    			and(ANormalArc.withSource("T1").andTarget("P2").with("1", "Default").token()).
    			and(ANormalArc.withSource("P2").andTarget("T2").with("1", "Default").token()).
    			and(ANormalArc.withSource("T2").andTarget("P3").with("1", "Default").token()).
    			and(ANormalArc.withSource("T1").andTarget("P4").with("1", "Default").token()).
    			and(ANormalArc.withSource("P4").andTarget("T3").with("1", "Default").token()).
    			and(ANormalArc.withSource("T3").andTarget("P5").with("1", "Default").token()).
    			and(ANormalArc.withSource("P5").andTarget("T2").with("1", "Default").token()).
    			andFinally(ANormalArc.withSource("P6").andTarget("T2").with("1", "Default").token());
//    					and(ANormalArc.withSource("P3").andTarget("T3").with("1", "Default").token()).
//    					andFinally(ANormalArc.withSource("T3").andTarget("P4").with("1", "Default").token());
//			and(APlace.withId("P1").externallyAccessible()).and(APlace.withId("P2")).
    	name(net, Place.class, "P0", "Enabled"); 
    	name(net, Place.class, "P1", "Ready"); 
    	name(net, Place.class, "P2", "Ongoing"); 
    	name(net, Place.class, "P3", "Done"); 
    	name(net, Place.class, "P5", "Closing"); 
    	name(net, Place.class, "P6", "Close_sensed"); 
    	name(net, Transition.class, "T0", "Prepare"); 
    	name(net, Transition.class, "T1", "Start"); 
    	name(net, Transition.class, "T2", "Finish"); 
    	name(net, Transition.class, "T3", "Close"); 
//    	for (Place place : net.getExecutablePetriNet().getPlaces()) {
//			System.out.println(place.getId());
//			System.out.println(place.getName());
//		}
    	return net; 
    }

	@SuppressWarnings("unchecked")
	private void name(PetriNet net, @SuppressWarnings("rawtypes") Class clazz, String component, String name) {
		try {
			net.getComponent(component, clazz).setId(name);
		} catch (PetriNetComponentNotFoundException e) {
			e.printStackTrace();
		}
		
	}

	public void closeExternalTransitionFired() {
//		System.out.println("closeExternalTransitionFired");
		try {
			runner.markPlace(CLOSE_SENSED, "Default", 1);
		} catch (InterfaceException e) {
			e.printStackTrace();
		}
	}
	private void cleanupFile(String filename) {
		file = new File(filename); 
        if (file.exists()) file.delete();
	}


}
//and(APlace.withId("P1").externallyAccessible()).and(APlace.withId("P2")).
//and(AnExternalTransition.withId("T0").andExternalClass("uk.ac.imperial.pipe.models.petrinet.TestingExternalTransition")).
//and(ANormalArc.withSource("P0").andTarget("T0").with("1", "Default").token()).
//and(ANormalArc.withSource("P1").andTarget("T0").with("1", "Default").token()).
//andFinally(ANormalArc.withSource("T0").andTarget("P2").with("1", "Default").token());