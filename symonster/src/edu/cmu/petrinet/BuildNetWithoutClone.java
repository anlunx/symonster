package edu.cmu.petrinet;

import edu.cmu.parser.JarParser;
import edu.cmu.parser.MethodSignature;
import soot.Type;

import uniol.apt.adt.exception.NoSuchEdgeException;
import uniol.apt.adt.exception.NoSuchNodeException;
import uniol.apt.adt.pn.Flow;
import uniol.apt.adt.pn.PetriNet;
import uniol.apt.adt.pn.Place;
import uniol.apt.adt.pn.Transition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

/*
   An reimplementation of BuildNet, but eliminates all clone edges by creating
   a copy for each method that returns its own arguments.
 */
public class BuildNetWithoutClone {
    static public PetriNet petrinet = new PetriNet("net");
    //map from transition name to a method signature
    static public Map<String, MethodSignature> dict = new HashMap<String, MethodSignature>();

    public static void main(String[] args) {
        List<String> libs = new ArrayList<>();
        libs.add("lib/point.jar");
        List<MethodSignature> sigs = JarParser.parseJar(libs);
        build(sigs);

        Set<Place> pl = petrinet.getPlaces();
        Set<Transition> tl = petrinet.getTransitions();
        for (Place p : pl) {
            System.out.println(p.toString());
            System.out.println(p.getMaxToken());
            //output all methods that take p as an argument
            for (Transition t : tl) {
                try {
                    System.out.println(petrinet.getFlow(p.getId(), t.getId()));
                } catch (NoSuchEdgeException e) {

                }
            }
        }
        for (Transition t : tl) {
            System.out.println(t.toString());
            for (Place p : pl) {
                try {
                    System.out.println(petrinet.getFlow(t.getId(), p.getId()));
                } catch (NoSuchEdgeException e) {
                }
            }
        }
    }

    public static PetriNet build(List<MethodSignature> result) {
        //create void type
        petrinet.createPlace("void");

        //iterate through each method
        for (MethodSignature k : result) {
            String methodname = k.getName();
            boolean isStatic = k.getIsStatic();
            boolean isConstructor = k.getIsConstructor();
            String className = k.getHostClass().getName();
            //We create two transitions for each method
            //transitionCopy will have its input as its additional output
            String transitionName;
            String transitionCopy;
            List<Type> args = k.getArgTypes();

            //adding transition
            if (isConstructor) {
                transitionName = methodname + "(";
                for(Type t : args) {
                    transitionName += t.toString() + " ";
                }
                transitionName += ")";
                transitionCopy = transitionName + "Copy";
                petrinet.createTransition(transitionName);
                petrinet.createTransition(transitionCopy);
            } else if (isStatic) {
                transitionName = className + "." + methodname + "(";
                for(Type t : args) {
                    transitionName += t.toString() + " ";
                }
                transitionName += ")";
                transitionCopy = transitionName + "Copy";
                petrinet.createTransition(transitionName);
                petrinet.createTransition(transitionCopy);
            } else { //The method is not static, so it has an extra argument
                transitionName = className + "." + methodname + "(";
                for(Type t : args) {
                    transitionName += t.toString() + " ";
                }
                transitionName += ")";
                transitionCopy = transitionName + "Copy";
                petrinet.createTransition(transitionName);
                petrinet.createTransition(transitionCopy);

                //creating the place for the class instance
                try {
                    petrinet.getPlace(k.getHostClass().toString());
                } catch (NoSuchNodeException e) {
                    petrinet.createPlace(k.getHostClass().toString());
                }

                //creating flow from class instance to transition
                try {
                    Flow f = petrinet.getFlow(k.getHostClass().toString(), transitionName);
                    f.setWeight(f.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(k.getHostClass().toString(), transitionName);
                }

                //create flow from class instance to transition copy
                try {
                    Flow f = petrinet.getFlow(k.getHostClass().toString(), transitionCopy);
                    f.setWeight(f.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(k.getHostClass().toString(), transitionCopy);
                }

                //Creating the out edge from transitionCopy to class instance for transitionCopy
                try {
                    Flow f = petrinet.getFlow(transitionCopy, k.getHostClass().toString());
                    f.setWeight(f.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(transitionCopy, k.getHostClass().toString());
                }
            }
            //add method signatures into map
            dict.put(transitionName, k);
            dict.put(transitionCopy, k);

            //If method has no argument and is static , create flow with void
            if(args.size() == 0 && (isStatic || isConstructor)) {
                petrinet.createFlow("void", transitionName);
                petrinet.createFlow("void", transitionCopy);
                petrinet.createFlow(transitionCopy, "void");
            }

            for (Type t : args) {
                //add place for each argument
                try {
                    petrinet.getPlace(t.toString());
                } catch (NoSuchNodeException e) {
                    petrinet.createPlace(t.toString());
                }

                //add flow for each argument
                try {
                    Flow originalFlow = petrinet.getFlow(t.toString(), transitionName);
                    originalFlow.setWeight(originalFlow.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(t.toString(), transitionName, 1);
                }
                try {
                    Flow originalFlow = petrinet.getFlow(t.toString(), transitionCopy);
                    originalFlow.setWeight(originalFlow.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(t.toString(), transitionCopy, 1);
                }

                //add output for transitionCopy
                try {
                    Flow originalFlow = petrinet.getFlow(transitionCopy, t.toString());
                    originalFlow.setWeight(originalFlow.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(transitionCopy, t.toString(), 1);
                }
            }

            //add place for the return type
            Type retType = k.getRetType();
            try {
                petrinet.getPlace(retType.toString());
            } catch (NoSuchNodeException e) {
                petrinet.createPlace(retType.toString());
            }

            //add flows for the return type
            petrinet.createFlow(transitionName, retType.toString(), 1);
            petrinet.createFlow(transitionCopy, retType.toString(), 1);
        }

        //Set max tokens for each place
        for (Place p : petrinet.getPlaces()) {
            int count = 0;
            for (Transition t : petrinet.getTransitions()) {
                try {
                    Flow f = petrinet.getFlow(p.getId(), t.getId());
                    count = Math.max(count, f.getWeight());
                } catch (NoSuchEdgeException e) {
                    continue;
                }
            }
            p.setMaxToken(count);
        }

        return petrinet;
    }
}
