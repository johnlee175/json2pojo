/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package com.johnsoft;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.android.tools.perflib.captures.DataBuffer;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Queries;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.Type;

/**
 * @author John Kenrinus Lee
 * @version 2018-08-08
 */
public class PerflibDemoMain {
    public static void main(String args[]) {
        try {
            if (args.length != 1) {
                System.err.println("The hprof file path required");
                return;
            }
            if (!args[0].endsWith(".hprof")) {
                args[0] += ".hprof";
            }
            File file = new File(args[0]);
            if (!file.exists() || !file.isFile() || !file.canRead()) {
                System.err.println("The hprof file is invalid: not exist or not file or can't readable");
                return;
            }

            long start = System.nanoTime();
            DataBuffer buffer = new MemoryMappedFileBuffer(file);
            Snapshot snapshot = Snapshot.createSnapshot(buffer);
            snapshot.computeDominators();

            for (Heap heap : snapshot.getHeaps()) {
                System.out.println("Found heap named: " + heap.getName());
            }
            System.out.println("Reachable instance counts: " + snapshot.getReachableInstances().size());
            System.out.println("GC Roots counts: " + snapshot.getGCRoots().size());

            testClassesQuery(snapshot);
            testInstancesQuery(snapshot);
            analyzeActivityLeak(snapshot);

            System.out.println("--------------------------------------------");
            System.out.println("Memory stats: free=" + Runtime.getRuntime().freeMemory()
                    + " / total=" + Runtime.getRuntime().totalMemory());
            System.out.println("Time: " + (System.nanoTime() - start) / 1000000 + "ms");

            snapshot.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void testClassesQuery(Snapshot snapshot) {
        ClassObj activityClass = Queries.findClass(snapshot, "android.app.Activity");
        dumpClassTree(activityClass);
    }

    public static void dumpClassTree(ClassObj classObj) {
        dumpClassTree(classObj, "+\t", "+\t");
    }

    private static void dumpClassTree(ClassObj classObj, String leading, String leadingWord) {
        System.out.println(leading + classObj + " -> [ " + classObj.getClassLoader() + " ] ");
        leading += leadingWord;
        for (ClassObj theClass : classObj.getSubclasses()) {
            dumpClassTree(theClass, leading, leadingWord);
        }
    }

    private static void testInstancesQuery(Snapshot snapshot) {
        Instance[] instances = Queries.allInstancesOf(snapshot, "android.app.Activity");
        for (Instance instance : instances) {
            System.out.println("Found " + instance.getClassObj() + " instance, CompositeSize = "
                    + instance.getCompositeSize());

            /* builtin-shortestPathToGcRoot */
            Instance currentRef = instance;
            while (true) {
                Instance parentRef = currentRef.getNextInstanceToGcRoot();
                if (parentRef != null) {
                    System.out.println(">>>>>> " + parentRef);
                    currentRef = parentRef;
                } else {
                    break;
                }
            }

            List<Instance> references = shortestPathToGcRoot(instance);
            System.out.println("Shortest path to GC root: ");
            for (int i = 0; i < references.size() - 1; ++i) {
                Instance referencing = references.get(i);
                Instance referencedBy = references.get(i + 1);
                List<String> fields = findFieldsReferencedBy(null, referencing, referencedBy);
                System.out.println(referencing.toString() + " refer by " + fields.toString() + " in " + referencedBy);
            }
        }
    }

    public static List<Instance> shortestPathToGcRoot(Instance instance) {
        ArrayList<Instance> instances = new ArrayList<>();
        shortestPathToGcRoot(instances, instance, Integer.MAX_VALUE, true);
        return instances;
    }

    public static void shortestPathToGcRoot(List<Instance> instances, Instance shortestReference,
                                            int shortestDistance, boolean sizePriority) {
        /* The path to gc root is a graph, not tree, so the result is not unique. */
        if (shortestReference != null) {
            instances.add(shortestReference);
            ArrayList<Instance> references = shortestReference.getHardReverseReferences();
            if (references != null) {
                for (Instance reference : references) {
                    if (reference == null) {
                        continue;
                    }
                    int distance = reference.getDistanceToGcRoot();
                    if (distance < shortestDistance) {
                        shortestDistance = distance;
                        shortestReference = reference;
                    } else if (sizePriority && distance == shortestDistance) {
                        if (reference.getTotalRetainedSize() > shortestReference.getTotalRetainedSize()) {
                            shortestDistance = distance;
                            shortestReference = reference;
                        }
                    }
                }
                if (shortestDistance == 0) {
                    instances.add(shortestReference);
                } else {
                    shortestPathToGcRoot(instances, shortestReference, shortestDistance, sizePriority);
                }
            }
        }
    }

    public static List<String> findFieldsReferencedBy(List<String> fields, Instance instance, Instance referencedBy) {
        if (fields == null) {
            fields = new ArrayList<>();
        }
        if (referencedBy instanceof ClassInstance) {
            ClassInstance classInstance = (ClassInstance) referencedBy;
            for (ClassInstance.FieldValue entry : classInstance.getValues()) {
                if (entry.getField().getType() == Type.OBJECT && entry.getValue() == instance) {
                    fields.add(entry.getField().getName());
                }
            }
        } else if (referencedBy instanceof ArrayInstance) {
            ArrayInstance arrayInstance = (ArrayInstance) referencedBy;
            assert arrayInstance.getArrayType() == Type.OBJECT;
            Object[] values = arrayInstance.getValues();
            for (int i = 0; i < values.length; ++i) {
                if (values[i] == instance) {
                    fields.add("[" + String.valueOf(i) + "]");
                }
            }
        } else if (referencedBy instanceof ClassObj) {
            ClassObj classObj = (ClassObj) referencedBy;
            Map<Field, Object> staticValues = classObj.getStaticFieldValues();
            for (Map.Entry<Field, Object> entry : staticValues.entrySet()) {
                if (entry.getKey().getType() == Type.OBJECT && entry.getValue() == instance) {
                    fields.add(entry.getKey().getName());
                }
            }
        }
        return fields;
    }

    public static void analyzeActivityLeak(Snapshot snapshot) {
        List<Instance> leakingInstances = new ArrayList<>();

        List<ClassObj> activityClasses = snapshot.findAllDescendantClasses("android.app.Activity");
        for (ClassObj activityClass : activityClasses) {
            List<Instance> instances = new ArrayList<>();
            for (Heap heap : snapshot.getHeaps()) {
                instances.addAll(activityClass.getHeapInstances(heap.getId()));
            }

            for (Instance instance : instances) {
                Instance immediateDominator = instance.getImmediateDominator();
                if (!(instance instanceof ClassInstance) || immediateDominator == null) {
                    continue;
                }

                for (ClassInstance.FieldValue value : ((ClassInstance) instance).getValues()) {
                    if ("mFinished".equals(value.getField().getName()) || "mDestroyed"
                            .equals(value.getField().getName())) {
                        if (instance.getDistanceToGcRoot() != Integer.MAX_VALUE && value
                                .getValue() instanceof Boolean &&
                                (Boolean) value.getValue()) {
                            leakingInstances.add(instance);
                            break;
                        }
                    }
                }
            }
        }

        System.out.println("leaking activities: " + leakingInstances);
    }
}
