package edu.cmu.ui;
import edu.cmu.codeformer.CodeFormer;
import edu.cmu.compilation.Test;
import edu.cmu.equivprogram.DependencyMap;
import edu.cmu.parser.*;
import edu.cmu.petrinet.*;
import edu.cmu.reachability.*;
import edu.cmu.utils.TimerUtils;
import uniol.apt.adt.pn.PetriNet;
import org.sat4j.specs.TimeoutException;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SyMonster {
	
	public static void enforceSolution(List<String> solution, Encoding encoding) {
		for (String api : solution) {
			encoding.forceTransition(api);
		}
	}
	
	public static void main(String[] args) throws IOException {
	    //Command line arguments
        List<String> arglist = Arrays.asList(args);
        boolean clone = false;
        boolean equiv = true;
        boolean copyPoly = false;
        boolean incremental = false;
        String jsonPath;
        BufferedWriter out = null;
        // 0. Read input from the user
        SyMonsterInput jsonInput;

        if (args.length == 0) {
            System.out.println("Please use the program args next time.");
            String outputPath = "symonster-output.txt";
            File outfile = new File(outputPath);
            if (!outfile.exists()) outfile.createNewFile();
            out = new BufferedWriter(new FileWriter(outfile));
            //jsonInput = JsonParser.parseJsonInput("untested/large/benchmark10.json");
            jsonInput = JsonParser.parseJsonInput("benchmarks/geometry/13/benchmark13.json");
        }
        else{
            jsonInput = JsonParser.parseJsonInput(args[0]);
            String outputPath = args[1];
            File outfile = new File(outputPath);
            if (!outfile.exists()) outfile.createNewFile();
            out = new BufferedWriter(new FileWriter(outfile));
            clone = arglist.contains("-c");
            equiv = arglist.contains("-e");
            copyPoly = arglist.contains("-cp");
            incremental = arglist.contains("-i");
        }
        // 1. Read config
        SymonsterConfig jsonConfig = JsonParser.parseJsonConfig("config/config.json");
        Set<String> acceptableSuperClasses = new HashSet<>();
        acceptableSuperClasses.addAll(jsonConfig.acceptableSuperClasses);

        String methodName = jsonInput.methodName;
        List<String> libs = jsonInput.libs;
        List<String> inputs = jsonInput.srcTypes;

        List<String> varNames = jsonInput.paramNames;
        String retType = jsonInput.tgtType;
        File file = new File(jsonInput.testPath);
        BufferedReader br = new BufferedReader(new FileReader(file));
        StringBuilder fileContents = new StringBuilder();
        String line = br.readLine();
        while (line != null) {
            fileContents.append(line);
            line = br.readLine();
        }
        String testCode = fileContents.toString();
        TimerUtils.startTimer("total");
        TimerUtils.startTimer("soot");
        List<MethodSignature> sigs = JarParser.parseJar(libs,jsonInput.packages,jsonConfig.blacklist);
        Map<String,Set<String>> superclassMap = JarParser.getSuperClasses(acceptableSuperClasses);
        Map<String,Set<String>> subclassMap = new HashMap<>();
        for (String key : superclassMap.keySet()){
            for (String value :superclassMap.get(key)){
                if (!subclassMap.containsKey(value)){
                    subclassMap.put(value,new HashSet<String>());
                }
                subclassMap.get(value).add(key);
            }
        }
        TimerUtils.stopTimer("soot");
        // 3. build a petrinet and signatureMap of library
        // Currently built without clone edges
        TimerUtils.startTimer("equiv");
        Set<List<MethodSignature>> repeatSolutions = new HashSet<>();
        DependencyMap dependencyMap = null;
        Map<String, MethodSignature> signatureMap;
        if (equiv){
            dependencyMap = JarParser.createDependencyMap();
        }
        TimerUtils.stopTimer("equiv");
        PetriNet net;
        TimerUtils.startTimer("buildnet");
        if (clone){
            BuildNetNoVoid b = new BuildNetNoVoid();
            net = b.build(sigs, superclassMap, subclassMap, inputs, copyPoly);
            signatureMap = b.dict;
        }
        else{
            BuildNetNoVoidClone b = new BuildNetNoVoidClone();
            net = b.build(sigs, superclassMap, subclassMap, inputs, copyPoly);
            signatureMap = b.dict;
        }
        TimerUtils.stopTimer("buildnet");
//        System.out.println("#places = " + net.getPlaces().size());
//        System.out.println("#transitions = " + net.getTransitions().size());

        int loc = 1;
		int paths = 0;
		int programs = 0;
		boolean solution = false;
		Encoding encoding = null;
		
		// put here the methods you want to force to be true
		List<String> force_methods = new ArrayList<>();
//		force_methods.add("getX");
//		force_methods.add("getY");
//		force_methods.add("getRotateInstance");
//		force_methods.add("createTransformedArea");
		
		if (incremental) {
			TimerUtils.startTimer("encoding");
			encoding = new IncrementalEncoding(net);
			encoding.setState(EncodingUtil.setInitialState(net, inputs), 0);
			TimerUtils.stopTimer("encoding");
		}

        while (!solution) {
            TimerUtils.startTimer("path");
            // create a formula that has the same semantics as the petri-net
            if (incremental) {
            	TimerUtils.startTimer("encoding");
				List<Integer> fstate  = encoding.getFState(EncodingUtil.setGoalState(net, retType), loc);
				// for each loc find all possible programs
				Encoding.solver.setFState(fstate);
				TimerUtils.stopTimer("encoding");
			} else {
				TimerUtils.startTimer("encoding");
				encoding = new SequentialEncoding(net, loc);                     // Set encoding
				// set initial state and final state
				encoding.setState(EncodingUtil.setInitialState(net, inputs),  0);
				encoding.setState(EncodingUtil.setGoalState(net, retType),  loc);
				// force methods here if needed
				enforceSolution(force_methods, encoding);
				TimerUtils.stopTimer("encoding");
			}
            // 4. Perform reachability analysis

            // for each loc find all possible programs
            List<Variable> result = Encoding.solver.findPath(loc);
            TimerUtils.stopTimer("path");
            while(!result.isEmpty() && !solution){
                TimerUtils.startTimer("path");
                paths++;
                String path = "Path #" + paths + " =\n";
                List<String> apis  = new ArrayList<String>();
                //A list of method signatures
                List<MethodSignature> signatures = new ArrayList<>();
                for (Variable s : result) {
                    apis.add(s.getName());
                    path += s.toString() + "\n";
                    MethodSignature sig = signatureMap.get(s.getName());

                    if(sig != null) { //check if s is a line of a code
                        signatures.add(sig);
                    } else {
                        //System.out.println(s.getName());
                    }
                }
                TimerUtils.stopTimer("path");
                if (!equiv || !repeatSolutions.contains(signatures)){
                    if (equiv){
                        List<List<MethodSignature>> repeated = dependencyMap.findAllTopSorts(signatures);
                        repeatSolutions.addAll(repeated);
                    }
                    // 5. Convert a path to a program
                    // NOTE: one path may correspond to multiple programs and we may need a loop here!
                    boolean sat = true;
                    CodeFormer former = new CodeFormer(signatures,inputs,retType, varNames, methodName,subclassMap, superclassMap);
                    while (sat){
                        TimerUtils.startTimer("code");
                        String code;
                        try {
                            code = former.solve();
                        } catch (TimeoutException e) {
                            sat = false;
                            break;
                        }
                        sat = !former.isUnsat();
                        programs++;
                        TimerUtils.stopTimer("code");
                        // 6. Run the test cases
                        // TODO: write this code; if all test cases pass then we can terminate
                        TimerUtils.startTimer("compile");
                        boolean compre = Test.runTest(code,testCode);
                        TimerUtils.stopTimer("compile");
                        if (compre) {
                            solution = true;
                            System.out.println("Code: " + code);
//                            System.out.println("Encoding time: "+TimerUtils.getCumulativeTime("encoding")+"\n");
//                            System.out.println("Find path time: "+TimerUtils.getCumulativeTime("path")+"\n");
                            writeLog(out,"Options:\n");
                            writeLog(out,"Incremental: "+incremental + "\n");
                            writeLog(out,"Clone: "+clone + "\n");
                            writeLog(out,"Copy polymorphism: "+copyPoly + "\n");
                            writeLog(out,"Equivalent program: "+equiv + "\n");
                            writeLog(out,"Programs explored = " + programs+"\n");
                            writeLog(out,"Paths explored = " + paths+"\n");
                            writeLog(out,"code:\n");
                            writeLog(out,code+"\n");
                            writeLog(out,"Soot time: "+TimerUtils.getCumulativeTime("soot")+"\n");
                            writeLog(out,"Equivalent program preprocess time: "+TimerUtils.getCumulativeTime("equiv")+"\n");
                            writeLog(out,"Build graph time: "+TimerUtils.getCumulativeTime("buildnet")+"\n");
                            writeLog(out,"Find path time: "+TimerUtils.getCumulativeTime("path")+"\n");
                            writeLog(out,"Form code time: "+TimerUtils.getCumulativeTime("code")+"\n");
                            writeLog(out,"Compilation time: "+TimerUtils.getCumulativeTime("compile")+"\n");
                            writeLog(out,"Encoding time: "+TimerUtils.getCumulativeTime("encoding")+"\n");
                            writeLog(out,"#Places: "+net.getPlaces().size());
                            writeLog(out,"#Transitions: "+net.getTransitions().size());

                            File compfile = new File("build/Target.class");
                            compfile.delete();
                            break;
                        }
                    }
                }

				// the current path did not result in a program that passes all test cases
				// find the next path
				result = Encoding.solver.findPath(loc);
			}
			
			// we did not find a program of length = loc
			loc++;
			if (incremental) {
				TimerUtils.startTimer("encoding");
				encoding.updateSAT(loc);
				TimerUtils.stopTimer("encoding");
			}
		}
		out.close();
	}

	private static void writeLog(BufferedWriter out,String string){
        try {
            out.write(string);
        } catch (IOException e) {
            System.exit(1);
        }
    }
}
