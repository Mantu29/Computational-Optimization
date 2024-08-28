package Basics.ProjectCode;

import com.gurobi.gurobi.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.FileWriter;
import java.io.IOException;

public class OptimizationProblem {

    public static Map<String, Object> runOptimization(int numberOfJobA, int numberOfJobB, boolean DebugPrint, boolean PrintFinalResult) {

        // Map machine IDs to machine names
        Map<Integer, String> machineNames = new HashMap<>();
        machineNames.put(0, "F1");
        machineNames.put(1, "Aligner");
        machineNames.put(2, "LLA");
        machineNames.put(3, "LLB");
        machineNames.put(4, "Blade 1");
        machineNames.put(5, "Blade 2");
        machineNames.put(6, "CHA");
        machineNames.put(7, "CHB");
        machineNames.put(8, "CHC");
        machineNames.put(9, "CHD");

        // Get the job list
        List<Job> jobList = JobFactory.createAndInitializeJobs(45, numberOfJobA, numberOfJobB); // Initial number of processes is 45, can be adjusted

        // Create a combined list of operations from all jobs
        List<Operation> operations = JobFactory.createOperationsFromJobs(jobList);

        // Dynamically determine the number of processes from the size of the operations list
        int numberOfProcesses = operations.size();

        if (DebugPrint) {
            // Print the job details to verify
            for (Job job : jobList) {
                System.out.println("Job Type: " + job.getJobType());
                int stepNumber = 1;
                for (List<Process> step : job.getSteps()) {
                    System.out.print("Step " + stepNumber + ": ");
                    for (Process process : step) {
                        System.out.println("Process ID: " + process.getId() + ", Eligible Machine: " + process.getEligibleMachines() + ", Processing Time: " + process.getProcessingTime());
                    }
                    stepNumber++;
                }
            }

            // Print the list of eligible machines for all operations
            System.out.println("List of eligible machines for all operations:");
            for (int i = 0; i < operations.size(); i++) {
                System.out.println("Operation " + (i + 1) + ": " + operations.get(i).getEligibleMachines());
            }
        }

        // Determine the maximum number of processes in any operation
        int maxProcesses = operations.stream().mapToInt(op -> op.getProcesses().size()).max().orElse(1);

        Map<String, Object> resultMap = null;

        try {
            // Create a new Gurobi environment
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "mip1.log");
            env.start();

            // Create an empty optimization model
            GRBModel model = new GRBModel(env);

            // Define the number of operations and machines
            int numOperations = numberOfProcesses;
            int numMachines = 10;


            // Create binary variables O_i^k only for eligible machines
            GRBVar[][] O = new GRBVar[numOperations][numMachines];
            double[][] O_dummy = new double[numOperations][numMachines];

            for (int i = 0; i < numOperations; i++) {
                for (int k = 0; k < numMachines; k++) {
                    O[i][k] = null;
                    O_dummy[i][k] = 0;
                }
                for (int k : operations.get(i).getEligibleMachines()) {
                    O[i][k] = model.addVar(0, 1, 0, GRB.BINARY, "O_" + (i + 1) + "_" + (k + 1));
                }
            }

            // Define binary variables beta_ii'
            GRBVar[][] beta = new GRBVar[numOperations][numOperations];
            double[][] beta_dummy = new double[numOperations][numOperations];
            for (int i = 0; i < numOperations; i++) {
                for (int j = 0; j < numOperations; j++) {
                    if (i != j) {
                        beta[i][j] = model.addVar(0, 1, 0, GRB.BINARY, "beta_" + (i + 1) + "_" + (j + 1));
                    }
                }
            }

            // Create binary variables A_io^k only for eligible machines
            GRBVar[][][] A = new GRBVar[numOperations][maxProcesses][numMachines];
            double[][][] A_dummy = new double[numOperations][maxProcesses][numMachines];
            for (int i = 0; i < numOperations; i++) {
                for (int o = 0; o < maxProcesses; o++) {
                    for (int k = 0; k < numMachines; k++) {
                        A[i][o][k] = null;
                        A_dummy[i][o][k] = 0;
                    }
                    for (int k : operations.get(i).getEligibleMachines()) {
                        A[i][o][k] = model.addVar(0, 1, 0, GRB.BINARY, "A_" + (i + 1) + "_" + (o + 1) + "_" + (k + 1));
                    }
                }
            }

            // Create continuous variables t_i
            GRBVar[] t = new GRBVar[numOperations];
            double[] t_dummy = new double[numOperations];
            for (int i = 0; i < numOperations; i++) {
                t[i] = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "t_" + (i + 1));
                t_dummy[i] = 0.0;  // Initialize the dummy array
            }

            model.addConstr(t[0], GRB.EQUAL, 0.0, "start_time_zero");

            // Create continuous variables e_i
            GRBVar[] e = new GRBVar[numOperations];
            double[] e_dummy = new double[numOperations];
            for (int i = 0; i < numOperations; i++) {
                e[i] = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "e_" + (i + 1));
                e_dummy[i] = 0.0; // Initialize the dummy array
            }

            // Create parameters p_io^k
            double[][][] p = new double[numOperations][maxProcesses][numMachines];

            for (int i = 0; i < numOperations; i++) {
                Operation operation = operations.get(i);
                for (int o = 0; o < operation.getProcesses().size(); o++) {
                    Process process = operation.getProcesses().get(o);
                    for (int k : process.getEligibleMachines()) {
                        p[i][o][k] = process.getProcessingTime();
                    }
                }
            }

            // Create parameter Cmax
            GRBVar Cmax = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "Cmax");
            double Cmax_dummy = 0.0;

            double H = 10000;

            //{Constraint (1):} Ensures each operation is executed exactly once.
            for (int i = 0; i < numOperations; i++) {
                GRBLinExpr operationExpr = new GRBLinExpr();
                StringBuilder constraintString = new StringBuilder("Constraint for Operation " + (i + 1) + ": ");

                for (int k : operations.get(i).getEligibleMachines()) {
                    operationExpr.addTerm(1.0, O[i][k]);
                    constraintString.append("O_").append(i + 1).append("_").append(k + 1).append(" + ");
                }

                // Remove the last " + " and add "= 1"
                constraintString.setLength(constraintString.length() - 3);
                constraintString.append(" = 1");

                // Print the constraint if DebugPrint is true
                if (DebugPrint) {
                    System.out.println(constraintString.toString());
                }

                // Add the constraint: sum_k O_i^k = 1
                model.addConstr(operationExpr, GRB.EQUAL, 1.0, "operation_constraint_" + (i + 1));
            }

            //{Constraint (2):} Ensures the start time of an operation is greater than or equal to the start time of its preceding operation plus its processing time.
            // Track the current index in the operations list to maintain the sequence
            int currentIndex = 0;
            List<List<Integer>> jobs = new ArrayList<>();
            List<Integer> OpSeq;

            // Adding jobs based on operations list
            int currentIndex_dummy = 0;
            for (Job job : jobList) {
                OpSeq = new ArrayList<>();
                for (int stepNumber = 1; stepNumber <= job.getSteps().size(); stepNumber++) {
                    OpSeq.add(currentIndex_dummy++);
                }
                jobs.add(OpSeq);
            }

            // Add precedence constraints for each job
            for (List<Integer> job : jobs) {
                for (int i = 1; i < job.size(); i++) {
                    int currentOpIndex = job.get(i);
                    int precedingOpIndex = job.get(i - 1);

                    GRBLinExpr lhs = new GRBLinExpr();
                    lhs.addTerm(1.0, t[currentOpIndex]); // t_i

                    GRBLinExpr rhs = new GRBLinExpr();
                    rhs.addTerm(1.0, t[precedingOpIndex]); // t_pr(i)

                    // Build the constraint string for printing
                    StringBuilder constraintString = new StringBuilder("t_" + (currentOpIndex + 1) + " >= t_" + (precedingOpIndex + 1) + " + ");
                    boolean firstTerm = true;

                    // sum_{k in R_pr(i)} sum_{o in O_pr(i)} p_pr(i)o^k * alpha_pr(i)o^k
                    for (int o = 0; o < operations.get(precedingOpIndex).getProcesses().size(); o++) {
                        Process process = operations.get(precedingOpIndex).getProcesses().get(o);

                        for (int k : process.getEligibleMachines()) {

                            rhs.addTerm(p[precedingOpIndex][o][k], A[precedingOpIndex][o][k]);

                            // Append to the constraint string
                            if (firstTerm) {
                                firstTerm = false;
                            } else {
                                constraintString.append(" + ");
                            }
                            constraintString.append(p[precedingOpIndex][o][k]).append(" * A_").append(precedingOpIndex + 1).append("_").append(o + 1).append("_").append(k + 1);
                        }
                    }

                    // Print the constraint if DebugPrint is true
                    if (DebugPrint) {
                        System.out.println("Precedence constraint: " + constraintString.toString());
                    }

                    // Add the constraint: t_i >= t_pr(i) + sum(p_pr(i)o^k * alpha_pr(i)o^k)
                    model.addConstr(lhs, GRB.GREATER_EQUAL, rhs, "precedence_constraint_" + (currentOpIndex + 1));
                }
            }

            //{Constraint (3):} Sequencing constraint to ensure that the start time of operation i is greater than or equal to the end time of operation j if jth operation is sequenced before ith operation.
            for (int i = 0; i < numOperations; i++) {
                for (int j = 0; j < numOperations; j++) {
                    if (i != j) {
                        for (int k : operations.get(i).getEligibleMachines()) {
                            if (operations.get(j).getEligibleMachines().contains(k)) {
                                // Build the constraint string for printing
                                StringBuilder constraintString = new StringBuilder();
                                constraintString.append("t_" + (i + 1) + " >= t_" + (j + 1) + " + ");

                                // Create the total processing time expression for operation j on machine k
                                GRBLinExpr overlappingB = new GRBLinExpr();
                                for (int stepIndex = 0; stepIndex < operations.get(j).getProcesses().size(); stepIndex++) {
                                    Process process = operations.get(j).getProcesses().get(stepIndex);
                                    if (process.getEligibleMachines().contains(k)) {
                                        overlappingB.addTerm(p[j][stepIndex][k], A[j][stepIndex][k]);
                                        constraintString.append(p[j][stepIndex][k] + " * A_" + (j + 1) + "_" + (stepIndex + 1) + "_" + (k + 1) + " + ");
                                    }
                                }

                                constraintString.append("- 2 * H  + O_" + (i + 1) + "_" + (k + 1) + " + H * O_" + (j + 1) + "_" + (k + 1) + " - H * beta_" + (i + 1) + "_" + (j + 1));

                                overlappingB.addTerm(1, t[j]);
                                overlappingB.addTerm(-1, t[i]);
                                overlappingB.addConstant(-2 * H);
                                overlappingB.addTerm(H, O[i][k]);
                                overlappingB.addTerm(H, O[j][k]);
                                overlappingB.addTerm(-H, beta[i][j]);

                                // Print the constraint if DebugPrint is true
                                if (DebugPrint) {
                                    System.out.println("Precedence constraint: " + constraintString.toString());
                                }

                                model.addConstr(overlappingB, GRB.LESS_EQUAL, 0, "overlappingB_" + (i + 1) + "_" + (j + 1) + "_" + (k + 1));
                            }
                        }
                    }
                }
            }

            //{Constraint (4):} Sequencing constraint to ensure that the start time of operation j is greater than or equal to the end time of operation i if ith operation is sequenced before jth operation.
            for (int i = 0; i < numOperations; i++) {
                for (int j = 0; j < numOperations; j++) {
                    if (i != j) {
                        for (int k : operations.get(i).getEligibleMachines()) {
                            if (operations.get(j).getEligibleMachines().contains(k)) {
                                // Build the constraint string for printing
                                StringBuilder constraintString = new StringBuilder();
                                constraintString.append("t_" + (j + 1) + " >= t_" + (i + 1) + " + ");

                                // Create the total processing time expression for operation i on machine k
                                GRBLinExpr overlappingA = new GRBLinExpr();
                                for (int stepIndex = 0; stepIndex < operations.get(i).getProcesses().size(); stepIndex++) {
                                    Process process = operations.get(i).getProcesses().get(stepIndex);
                                    if (process.getEligibleMachines().contains(k)) {
                                        overlappingA.addTerm(p[i][stepIndex][k], A[i][stepIndex][k]);
                                        constraintString.append(p[i][stepIndex][k] + " * A_" + (i + 1) + "_" + (stepIndex + 1) + "_" + (k + 1) + " + ");
                                    }
                                }

                                constraintString.append("- 3 * H + H * O_" + (i + 1) + "_" + (k + 1) + " + H * O_" + (j + 1) + "_" + (k + 1) + " - H * beta_" + (i + 1) + "_" + (j + 1));

                                overlappingA.addTerm(-1, t[j]);
                                overlappingA.addTerm(1, t[i]);
                                overlappingA.addConstant(-3 * H);
                                overlappingA.addTerm(H, O[i][k]);
                                overlappingA.addTerm(H, O[j][k]);
                                overlappingA.addTerm(-H, beta[i][j]);

                                // Print the constraint if DebugPrint is true
                                if (DebugPrint) {
                                    System.out.println("Precedence constraint: " + constraintString.toString());
                                }

                                model.addConstr(overlappingA, GRB.LESS_EQUAL, 0, "c4_" + (i + 1) + "_" + (j + 1) + "_" + (k + 1));
                            }
                        }
                    }
                }
            }

            //{Constraint (5):} Ensures the makespan Cmax is greater than or equal to the end time of each operation.
            for (int i = 0; i < numOperations; i++) {
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1.0, t[i]); // Add t_i

                for (int o = 0; o < operations.get(i).getProcesses().size(); o++) {
                    Process process = operations.get(i).getProcesses().get(o);
                    for (int k : process.getEligibleMachines()) {
                        expr.addTerm(p[i][o][k], A[i][o][k]); // Add p_io^k * A_io^k
                    }
                }

                // Add the constraint: Cmax >= t_i + sum(p_io^k * A_io^k)
                GRBLinExpr lhs = new GRBLinExpr();
                lhs.addTerm(1.0, Cmax);
                model.addConstr(lhs, GRB.GREATER_EQUAL, expr, "cmax_constraint_" + (i + 1));

                // Print the constraint if DebugPrint is true
                if (DebugPrint) {
                    System.out.println("Cmax constraint for operation " + (i + 1) + ": Cmax >= " + expr.toString());
                }
            }

            //{Constraint (6):} If operation i is assigned to machine k, then one of its processes must also be assigned to machine k.
            for (int i = 0; i < numOperations; i++) {
                for (int o = 0; o < operations.get(i).getProcesses().size(); o++) {
                    for (int k : operations.get(i).getProcesses().get(o).getEligibleMachines()) {
                        // Create the constraint: O_i^k >= A_io^k
                        GRBLinExpr linkExpr = new GRBLinExpr();
                        linkExpr.addTerm(1.0, A[i][o][k]);

                        // Print the constraint if DebugPrint is true
                        if (DebugPrint) {
                            String constraintString = "O_" + (i + 1) + "_" + (k + 1) + " >= A_" + (i + 1) + "_" + (o + 1) + "_" + (k + 1);
                            System.out.println("Link constraint: " + constraintString);
                        }

                        // Add the constraint to the model
                        model.addConstr(O[i][k], GRB.GREATER_EQUAL, linkExpr, "link_O_and_A_" + (i + 1) + "_" + (o + 1) + "_" + (k + 1));
                    }
                }
            }

            //{Constraint (7):} Ensures that only one process of each operation must be assigned.
            for (int i = 0; i < numOperations; i++) {
                GRBLinExpr operationExpr = new GRBLinExpr();
                StringBuilder constraintString = new StringBuilder("Operation constraint for Operation " + (i + 1) + ": ");

                for (int o = 0; o < operations.get(i).getProcesses().size(); o++) {
                    for (int k : operations.get(i).getProcesses().get(o).getEligibleMachines()) {
                        operationExpr.addTerm(1.0, A[i][o][k]);
                        constraintString.append("A_").append(i + 1).append("_").append(o + 1).append("_").append(k + 1).append(" + ");
                    }
                }

                // Remove the last " + " and add "= 1"
                constraintString.setLength(constraintString.length() - 3);
                constraintString.append(" = 1");

                // Print the constraint if DebugPrint is true
                if (DebugPrint) {
                    System.out.println(constraintString.toString());
                }

                // Add the constraint: sum_o sum_k A_io^k = 1
                model.addConstr(operationExpr, GRB.EQUAL, 1.0, "operation_constraint_" + (i + 1));
            }

            //{Constraint (8):} The start time of operation j must be greater than or equal to the end time of operation j for specific pairs in set O
            for (int i = 0; i < numOperations; i++) {
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1.0, t[i]); // Start time t_i

                for (int o = 0; o < operations.get(i).getProcesses().size(); o++) {
                    Process process = operations.get(i).getProcesses().get(o);
                    for (int k : process.getEligibleMachines()) {
                        expr.addTerm(p[i][o][k], A[i][o][k]); // Processing time term
                    }
                }

                // Add constraint: e_i = t_i + sum(p_io^k * A_io^k)
                model.addConstr(e[i], GRB.EQUAL, expr, "end_time_constraint_" + (i + 1));

            }

            // For Job Type A
            for (int i = 0; i < numOperations; i++) {
                Operation operation = operations.get(i);

                if (operation.getJobType().equals("A")) {
                    // Step 18 of job A can only happen after step 6 of job A
                    if (operation.getStepNumber() == 18) {
                        for (int j = 0; j < numOperations; j++) {
                            Operation otherOperation = operations.get(j);
                            if (otherOperation.getJobType().equals("A") && otherOperation.getStepNumber() == 6) {
                                // Add constraint: t[i] >= e[j]
                                model.addConstr(t[i], GRB.GREATER_EQUAL, e[j], "precedence_A_18_6");
                                if (DebugPrint) {
                                    System.out.println("Added precedence constraint: t[" + i + "] >= e[" + j + "] for Job Type A (Step 18 after Step 6)");
                                }
                            }
                        }
                    }

                    // Step 14 of job A can only happen after step 6 of job A
                    if (operation.getStepNumber() == 14) {
                        for (int j = 0; j < numOperations; j++) {
                            Operation otherOperation = operations.get(j);
                            if (otherOperation.getJobType().equals("A") && otherOperation.getStepNumber() == 6) {
                                // Add constraint: t[i] >= e[j]
                                model.addConstr(t[i], GRB.GREATER_EQUAL, e[j], "precedence_A_14_6");
                                if (DebugPrint) {
                                    System.out.println("Added precedence constraint: t[" + i + "] >= e[" + j + "] for Job Type A (Step 14 after Step 6)");
                                }
                            }
                        }
                    }

                    // Step 1 of job A can only happen after step 6 of job A
                    if (operation.getStepNumber() == 1) {
                        for (int j = 0; j < numOperations; j++) {
                            Operation otherOperation = operations.get(j);
                            if (otherOperation.getJobType().equals("A") && otherOperation.getStepNumber() == 6 && (i - j) % 14 == 0 && i != j) {
                                model.addConstr(t[i], GRB.GREATER_EQUAL, e[j], "precedence_A_1_6");
                                if (DebugPrint) {
                                    System.out.println("Added precedence constraint: t[" + i + "] >= e[" + j + "] for Job Type A (Step 1 after Step 6)");
                                }
                            }
                        }
                    }
                }

                // For Job Type B
                if (operation.getJobType().equals("B")) {
                    // Step 13 of job B can only happen after step 6 of job B
                    if (operation.getStepNumber() == 13) {
                        for (int j = 0; j < numOperations; j++) {
                            Operation otherOperation = operations.get(j);
                            if (otherOperation.getJobType().equals("B") && otherOperation.getStepNumber() == 6) {
                                // Add constraint: t[i] >= e[j]
                                model.addConstr(t[i], GRB.GREATER_EQUAL, e[j], "precedence_B_13_6");
                                if (DebugPrint) {
                                    System.out.println("Added precedence constraint: t[" + i + "] >= e[" + j + "] for Job Type B (Step 13 after Step 6)");
                                }
                            }
                        }
                    }

                    // Step 11 of job B can only happen after step 10 of job B
                    if (operation.getStepNumber() == 11) {
                        for (int j = 0; j < numOperations; j++) {
                            Operation otherOperation = operations.get(j);
                            if (otherOperation.getJobType().equals("B") && otherOperation.getStepNumber() == 10) {
                                // Add constraint: t[i] >= e[j]
                                model.addConstr(t[i], GRB.GREATER_EQUAL, e[j], "precedence_B_11_10");
                                if (DebugPrint) {
                                    System.out.println("Added precedence constraint: t[" + i + "] >= e[" + j + "] for Job Type B (Step 11 after Step 10)");
                                }
                            }
                        }
                    }

                    // Additional constraint: Start time of step 1 of job B should be greater than end time of step 6 of job A
                    if (operation.getStepNumber() == 1) {
                        for (int j = 0; j < numOperations; j++) {
                            Operation otherOperation = operations.get(j);
                            if (otherOperation.getJobType().equals("A") && otherOperation.getStepNumber() == 6) {
                                // Add constraint: t[i] >= e[j]
                                model.addConstr(t[i], GRB.GREATER_EQUAL, e[j], "precedence_B1_A6");
                                if (DebugPrint) {
                                    System.out.println("Added precedence constraint: t[" + i + "] >= e[" + j + "] for Job Type B (Step 1 after Step 6 of Job A)");
                                }
                            }
                        }
                    }

                    // Step 1 of job B can only happen after step 6 of job B
                    if (operation.getStepNumber() == 1) {
                        for (int j = 0; j < numOperations; j++) {
                            Operation otherOperation = operations.get(j);
                            if (otherOperation.getJobType().equals("B") && otherOperation.getStepNumber() == 6 && (i - j) % 11 == 0 && i != j) {
                                model.addConstr(t[i], GRB.GREATER_EQUAL, e[j], "precedence_B_1_1");
                                if (DebugPrint) {
                                    System.out.println("Added precedence constraint: t[" + i + "] >= e[" + j + "] for Job Type B (Step 1 after Step 6)");
                                }
                            }
                        }
                    }
                }
            }

            // Continue with similar logic for any other specific constraints or further implementation steps.


            //{Constraint (9):} Ensures that certain processes in adjacent operations are executed by the same machine or in a specified manner.
            for (int i = 0; i < operations.size(); i++) {
                Operation operation = operations.get(i);
                if (DebugPrint) {
                    System.out.println("Operation " + (i + 1) + ": Job Type - " + operation.getJobType() + ", Step Number - " + operation.getStepNumber());
                }
                for (int processIndex = 0; processIndex < operation.getProcesses().size(); processIndex++) {
                    Process process = operation.getProcesses().get(processIndex);
                    if (!process.getEligibleMachines().isEmpty()) {
                        List<Integer> eligibleMachinesList = new ArrayList<>(process.getEligibleMachines());
                        Integer firstEligibleMachine = eligibleMachinesList.get(0);
                        if (DebugPrint) {
                            System.out.println("  Step Number: " + operation.getStepNumber() + ", Process Number: " + (processIndex + 1) + ", First Eligible Machine: " + firstEligibleMachine);
                        }

                        // Add the specific constraint for Job Type A, Step Number 5, and Process Index 1
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 5) && ((processIndex + 1) == 1)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 6 && !nextOperation.getProcesses().get(1).getEligibleMachines().isEmpty()) {
                                    List<Integer> nextEligibleMachinesList = new ArrayList<>(nextOperation.getProcesses().get(1).getEligibleMachines());
                                    Integer nextFirstEligibleMachine = nextEligibleMachinesList.get(0);
                                    model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S5 2 - S6 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 5) && ((processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 6 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess = nextOperation.getProcesses().get(1);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();
                                    model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S6 1 - S7 1
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 6) && ((processIndex + 1) == 1)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 7 && nextOperation.getProcesses().size() >= 1) {
                                    Process nextProcess = nextOperation.getProcesses().get(0);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();
                                    model.addConstr(A[i][0][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S6 2 - S7 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 6) && ((processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 7 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess = nextOperation.getProcesses().get(1);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();
                                    model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S7 1 - S8 1 or 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 7) && ((processIndex + 1) == 1)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 8 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess1 = nextOperation.getProcesses().get(0);
                                    Process nextProcess2 = nextOperation.getProcesses().get(1);

                                    int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();

                                    GRBLinExpr lhs = new GRBLinExpr();
                                    lhs.addTerm(1, A[i][0][firstEligibleMachine]);

                                    GRBLinExpr rhs = new GRBLinExpr();
                                    rhs.addTerm(1, A[i + 1][0][nextFirstEligibleMachine1]);
                                    rhs.addTerm(1, A[i + 1][1][nextFirstEligibleMachine2]);

                                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                    }
                                }
                            }
                        }

                        // S7 2 - S8 3 or 4
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 7) && ((processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 8 && nextOperation.getProcesses().size() >= 4) {
                                    Process nextProcess3 = nextOperation.getProcesses().get(2);
                                    Process nextProcess4 = nextOperation.getProcesses().get(3);

                                    int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                    GRBLinExpr lhs = new GRBLinExpr();
                                    lhs.addTerm(1, A[i][1][firstEligibleMachine]);

                                    GRBLinExpr rhs = new GRBLinExpr();
                                    rhs.addTerm(1, A[i + 1][2][nextFirstEligibleMachine3]);
                                    rhs.addTerm(1, A[i + 1][3][nextFirstEligibleMachine4]);

                                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                    }
                                }
                            }
                        }

                        // S8 1 or 3 - S9 1 or 3
                        if (operation.getJobType().equals("A") &&
                                operation.getStepNumber() == 8 &&
                                ((processIndex + 1) == 1 || (processIndex + 1) == 3)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 9 &&
                                        (nextOperation.getProcesses().size() >= 1 && nextOperation.getProcesses().size() >= 3)) {
                                    Process nextProcess1 = nextOperation.getProcesses().get(0);
                                    Process nextProcess3 = nextOperation.getProcesses().get(2);
                                    int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][0][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine1], "specific_constraint_A_" + (i + 1));
                                    model.addConstr(A[i][2][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][2][nextFirstEligibleMachine3], "specific_constraint_A_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "]");
                                        System.out.println("Added constraint: A[" + (i + 1) + "][3][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "]");
                                    }
                                }
                            }
                        }

                        // S8 2 or 4 - S9 2 or 4
                        if (operation.getJobType().equals("A") &&
                                operation.getStepNumber() == 8 &&
                                ((processIndex + 1) == 2 || (processIndex + 1) == 4)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 9 &&
                                        (nextOperation.getProcesses().size() >= 2 && nextOperation.getProcesses().size() >= 4)) {
                                    Process nextProcess2 = nextOperation.getProcesses().get(1);
                                    Process nextProcess4 = nextOperation.getProcesses().get(3);
                                    int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine2], "specific_constraint_A_" + (i + 1));
                                    model.addConstr(A[i][3][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][3][nextFirstEligibleMachine4], "specific_constraint_A_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                        System.out.println("Added constraint: A[" + (i + 1) + "][4][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                    }
                                }
                            }
                        }

                        // S9 1 or 2 - S10 - 1
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 9) && ((processIndex + 1) == 1 || (processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 10 && nextOperation.getProcesses().size() >= 1) {
                                    Process nextProcess = nextOperation.getProcesses().get(0);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][processIndex][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S9 3 or 4 - S10 - 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 9) && ((processIndex + 1) == 3 || (processIndex + 1) == 4)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 10 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess = nextOperation.getProcesses().get(1);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][processIndex][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S10 1 - S11 1 or 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 10) && ((processIndex + 1) == 1)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 11 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess1 = nextOperation.getProcesses().get(0);
                                    Process nextProcess2 = nextOperation.getProcesses().get(1);

                                    int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();

                                    GRBLinExpr lhs = new GRBLinExpr();
                                    lhs.addTerm(1, A[i][0][firstEligibleMachine]);

                                    GRBLinExpr rhs = new GRBLinExpr();
                                    rhs.addTerm(1, A[i + 1][0][nextFirstEligibleMachine1]);
                                    rhs.addTerm(1, A[i + 1][1][nextFirstEligibleMachine2]);

                                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                    }
                                }
                            }
                        }

                        // S10 2 - S11 3 or 4
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 10) && ((processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 11 && nextOperation.getProcesses().size() >= 4) {
                                    Process nextProcess3 = nextOperation.getProcesses().get(2);
                                    Process nextProcess4 = nextOperation.getProcesses().get(3);

                                    int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                    GRBLinExpr lhs = new GRBLinExpr();
                                    lhs.addTerm(1, A[i][1][firstEligibleMachine]);

                                    GRBLinExpr rhs = new GRBLinExpr();
                                    rhs.addTerm(1, A[i + 1][2][nextFirstEligibleMachine3]);
                                    rhs.addTerm(1, A[i + 1][3][nextFirstEligibleMachine4]);

                                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                    }
                                }
                            }
                        }

                        // S11 1 or 3 - S12 1 or 3
                        if (operation.getJobType().equals("A") &&
                                operation.getStepNumber() == 11 &&
                                ((processIndex + 1) == 1 || (processIndex + 1) == 3)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 12 &&
                                        (nextOperation.getProcesses().size() >= 1 && nextOperation.getProcesses().size() >= 3)) {
                                    Process nextProcess1 = nextOperation.getProcesses().get(0);
                                    Process nextProcess3 = nextOperation.getProcesses().get(2);
                                    int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][0][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine1], "specific_constraint_A_" + (i + 1));
                                    model.addConstr(A[i][2][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][2][nextFirstEligibleMachine3], "specific_constraint_A_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "]");
                                        System.out.println("Added constraint: A[" + (i + 1) + "][3][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "]");
                                    }
                                }
                            }
                        }

                        // S11 2 or 4 - S12 2 or 4
                        if (operation.getJobType().equals("A") &&
                                operation.getStepNumber() == 11 &&
                                ((processIndex + 1) == 2 || (processIndex + 1) == 4)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 12 &&
                                        (nextOperation.getProcesses().size() >= 2 && nextOperation.getProcesses().size() >= 4)) {
                                    Process nextProcess2 = nextOperation.getProcesses().get(1);
                                    Process nextProcess4 = nextOperation.getProcesses().get(3);
                                    int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine2], "specific_constraint_A_" + (i + 1));
                                    model.addConstr(A[i][3][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][3][nextFirstEligibleMachine4], "specific_constraint_A_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                        System.out.println("Added constraint: A[" + (i + 1) + "][4][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                    }
                                }
                            }
                        }

                        // S12 1 or 2 - S13 - 1
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 12) && ((processIndex + 1) == 1 || (processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 13 && nextOperation.getProcesses().size() >= 1) {
                                    Process nextProcess = nextOperation.getProcesses().get(0);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][processIndex][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S12 3 or 4 - S13 - 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 12) && ((processIndex + 1) == 3 || (processIndex + 1) == 4)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 13 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess = nextOperation.getProcesses().get(1);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][processIndex][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S13 1 - S14 1 or 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 13) && ((processIndex + 1) == 1)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 14 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess1 = nextOperation.getProcesses().get(0);
                                    Process nextProcess2 = nextOperation.getProcesses().get(1);

                                    int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();

                                    GRBLinExpr lhs = new GRBLinExpr();
                                    lhs.addTerm(1, A[i][0][firstEligibleMachine]);

                                    GRBLinExpr rhs = new GRBLinExpr();
                                    rhs.addTerm(1, A[i + 1][0][nextFirstEligibleMachine1]);
                                    rhs.addTerm(1, A[i + 1][1][nextFirstEligibleMachine2]);

                                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                    }
                                }
                            }
                        }

                        // S13 2 - S14 3 or 4
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 13) && ((processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 14 && nextOperation.getProcesses().size() >= 4) {
                                    Process nextProcess3 = nextOperation.getProcesses().get(2);
                                    Process nextProcess4 = nextOperation.getProcesses().get(3);

                                    int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                    GRBLinExpr lhs = new GRBLinExpr();
                                    lhs.addTerm(1, A[i][1][firstEligibleMachine]);

                                    GRBLinExpr rhs = new GRBLinExpr();
                                    rhs.addTerm(1, A[i + 1][2][nextFirstEligibleMachine3]);
                                    rhs.addTerm(1, A[i + 1][3][nextFirstEligibleMachine4]);

                                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                    }
                                }
                            }
                        }

                        // S15 1 - S16 1 or 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 15) && ((processIndex + 1) == 1)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 16 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess1 = nextOperation.getProcesses().get(0);
                                    Process nextProcess2 = nextOperation.getProcesses().get(1);

                                    int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();

                                    GRBLinExpr lhs = new GRBLinExpr();
                                    lhs.addTerm(1, A[i][0][firstEligibleMachine]);

                                    GRBLinExpr rhs = new GRBLinExpr();
                                    rhs.addTerm(1, A[i + 1][0][nextFirstEligibleMachine1]);
                                    rhs.addTerm(1, A[i + 1][1][nextFirstEligibleMachine2]);

                                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                    }
                                }
                            }
                        }

                        // S15 2 - S16 3 or 4
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 15) && ((processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 16 && nextOperation.getProcesses().size() >= 4) {
                                    Process nextProcess3 = nextOperation.getProcesses().get(2);
                                    Process nextProcess4 = nextOperation.getProcesses().get(3);

                                    int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();
                                    int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                    GRBLinExpr lhs = new GRBLinExpr();
                                    lhs.addTerm(1, A[i][1][firstEligibleMachine]);

                                    GRBLinExpr rhs = new GRBLinExpr();
                                    rhs.addTerm(1, A[i + 1][2][nextFirstEligibleMachine3]);
                                    rhs.addTerm(1, A[i + 1][3][nextFirstEligibleMachine4]);

                                    model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                    }
                                }
                            }
                        }

                        // S16 1 or 2 - S17 - 1
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 16) && ((processIndex + 1) == 1 || (processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 17 && nextOperation.getProcesses().size() >= 1) {
                                    Process nextProcess = nextOperation.getProcesses().get(0);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][processIndex][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S16 3 or 4 - S17 - 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 16) && ((processIndex + 1) == 3 || (processIndex + 1) == 4)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 17 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess = nextOperation.getProcesses().get(1);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][processIndex][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S17 1 - S18 1
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 17) && ((processIndex + 1) == 1)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 18 && nextOperation.getProcesses().size() >= 1) {
                                    Process nextProcess = nextOperation.getProcesses().get(0);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                    model.addConstr(A[i][0][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }

                        // S17 2 - S18 2
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 17) && ((processIndex + 1) == 2)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 18 && nextOperation.getProcesses().size() >= 2) {
                                    Process nextProcess = nextOperation.getProcesses().get(1);
                                    int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();
                                    model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    if (DebugPrint) {
                                        System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                    }
                                }
                            }
                        }
                    } else {
                        if (DebugPrint) {
                            System.out.println("  Step Number: " + operation.getStepNumber() + ", Process Number: " + (processIndex + 1) + ", No Eligible Machines");
                        }
                    }
                }
                if (DebugPrint) {
                    System.out.println();
                }
            }

            // Implementing similar constraints for Job B
            for (int i = 0; i < operations.size(); i++) {
                Operation operation = operations.get(i);
                for (int processIndex = 0; processIndex < operation.getProcesses().size(); processIndex++) {
                    Process process = operation.getProcesses().get(processIndex);
                    int firstEligibleMachine = process.getEligibleMachines().iterator().next();

                    // S5 1 - S6 1
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 5) && ((processIndex + 1) == 1)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 6 && nextOperation.getProcesses().size() >= 1) {
                                Process nextProcess = nextOperation.getProcesses().get(0);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                model.addConstr(A[i][0][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine], "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }

                    // S5 2 - S6 2
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 5) && ((processIndex + 1) == 2)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 6 && nextOperation.getProcesses().size() >= 2) {
                                Process nextProcess = nextOperation.getProcesses().get(1);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }

                    // S6 1 - S7 1
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 6) && ((processIndex + 1) == 1)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 7 && nextOperation.getProcesses().size() >= 1) {
                                Process nextProcess = nextOperation.getProcesses().get(0);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                model.addConstr(A[i][0][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine], "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }

                    // S6 2 - S7 2
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 6) && ((processIndex + 1) == 2)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 7 && nextOperation.getProcesses().size() >= 2) {
                                Process nextProcess = nextOperation.getProcesses().get(1);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }

                    // S7 1 - S8 1 or 2
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 7) && ((processIndex + 1) == 1)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 8 && nextOperation.getProcesses().size() >= 2) {
                                Process nextProcess1 = nextOperation.getProcesses().get(0);
                                Process nextProcess2 = nextOperation.getProcesses().get(1);

                                int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][0][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][0][nextFirstEligibleMachine1]);
                                rhs.addTerm(1, A[i + 1][1][nextFirstEligibleMachine2]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                }
                            }
                        }
                    }

                    // S7 2 - S8 3 or 4
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 7) && ((processIndex + 1) == 2)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 8 && nextOperation.getProcesses().size() >= 4) {
                                Process nextProcess3 = nextOperation.getProcesses().get(2);
                                Process nextProcess4 = nextOperation.getProcesses().get(3);

                                int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();
                                int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][1][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][2][nextFirstEligibleMachine3]);
                                rhs.addTerm(1, A[i + 1][3][nextFirstEligibleMachine4]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                }
                            }
                        }
                    }

                    // S8 1 or 3 - S9 1 or 3
                    if (operation.getJobType().equals("B") &&
                            operation.getStepNumber() == 8 &&
                            ((processIndex + 1) == 1 || (processIndex + 1) == 3)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 9 &&
                                    (nextOperation.getProcesses().size() >= 1 && nextOperation.getProcesses().size() >= 3)) {
                                Process nextProcess1 = nextOperation.getProcesses().get(0);
                                Process nextProcess3 = nextOperation.getProcesses().get(2);
                                int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();

                                model.addConstr(A[i][0][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine1], "specific_constraint_A_" + (i + 1));
                                model.addConstr(A[i][2][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][2][nextFirstEligibleMachine3], "specific_constraint_A_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "]");
                                    System.out.println("Added constraint: A[" + (i + 1) + "][3][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "]");
                                }
                            }
                        }
                    }

                    // S8 2 or 4 - S9 2 or 4
                    if (operation.getJobType().equals("B") &&
                            operation.getStepNumber() == 8 &&
                            ((processIndex + 1) == 2 || (processIndex + 1) == 4)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 9 &&
                                    (nextOperation.getProcesses().size() >= 2 && nextOperation.getProcesses().size() >= 4)) {
                                Process nextProcess2 = nextOperation.getProcesses().get(1);
                                Process nextProcess4 = nextOperation.getProcesses().get(3);
                                int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();
                                int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine2], "specific_constraint_A_" + (i + 1));
                                model.addConstr(A[i][3][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][3][nextFirstEligibleMachine4], "specific_constraint_A_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                    System.out.println("Added constraint: A[" + (i + 1) + "][4][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                }
                            }
                        }
                    }

                    // S9 1 or 2 - S10 1
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 9) && ((processIndex + 1) == 1 || (processIndex + 1) == 2)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 10 && nextOperation.getProcesses().size() >= 1) {
                                Process nextProcess = nextOperation.getProcesses().get(0);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][processIndex][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][0][nextFirstEligibleMachine]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }

                    // S9 3 or 4 - S10 2
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 9) && ((processIndex + 1) == 3 || (processIndex + 1) == 4)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 10 && nextOperation.getProcesses().size() >= 2) {
                                Process nextProcess = nextOperation.getProcesses().get(1);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][processIndex][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][1][nextFirstEligibleMachine]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }

                    // S10 1 - S11 1 or 2
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 10) && ((processIndex + 1) == 1)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 11 && nextOperation.getProcesses().size() >= 2) {
                                Process nextProcess1 = nextOperation.getProcesses().get(0);
                                Process nextProcess2 = nextOperation.getProcesses().get(1);

                                int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][0][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][0][nextFirstEligibleMachine1]);
                                rhs.addTerm(1, A[i + 1][1][nextFirstEligibleMachine2]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                }
                            }
                        }
                    }

                    // S10 2 - S11 3 or 4
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 10) && ((processIndex + 1) == 2)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 11 && nextOperation.getProcesses().size() >= 4) {
                                Process nextProcess3 = nextOperation.getProcesses().get(2);
                                Process nextProcess4 = nextOperation.getProcesses().get(3);

                                int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();
                                int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][1][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][2][nextFirstEligibleMachine3]);
                                rhs.addTerm(1, A[i + 1][3][nextFirstEligibleMachine4]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                }
                            }
                        }
                    }

                    // S12 1 - S13 1 or 2
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 12) && ((processIndex + 1) == 1)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 13 && nextOperation.getProcesses().size() >= 2) {
                                Process nextProcess1 = nextOperation.getProcesses().get(0);
                                Process nextProcess2 = nextOperation.getProcesses().get(1);

                                int nextFirstEligibleMachine1 = nextProcess1.getEligibleMachines().iterator().next();
                                int nextFirstEligibleMachine2 = nextProcess2.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][0][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][0][nextFirstEligibleMachine1]);
                                rhs.addTerm(1, A[i + 1][1][nextFirstEligibleMachine2]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                }
                            }
                        }
                    }

                    // S12 2 - S13 3 or 4
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 12) && ((processIndex + 1) == 2)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 13 && nextOperation.getProcesses().size() >= 4) {
                                Process nextProcess3 = nextOperation.getProcesses().get(2);
                                Process nextProcess4 = nextOperation.getProcesses().get(3);

                                int nextFirstEligibleMachine3 = nextProcess3.getEligibleMachines().iterator().next();
                                int nextFirstEligibleMachine4 = nextProcess4.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][1][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][2][nextFirstEligibleMachine3]);
                                rhs.addTerm(1, A[i + 1][3][nextFirstEligibleMachine4]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
                                }
                            }
                        }
                    }

                    // S13 1 or 2 - S14 1
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 13) && ((processIndex + 1) == 1 || (processIndex + 1) == 2)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 14 && nextOperation.getProcesses().size() >= 1) {
                                Process nextProcess = nextOperation.getProcesses().get(0);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][processIndex][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][0][nextFirstEligibleMachine]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }

                    // S13 3 or 4 - S14 2
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 13) && ((processIndex + 1) == 3 || (processIndex + 1) == 4)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 14 && nextOperation.getProcesses().size() >= 2) {
                                Process nextProcess = nextOperation.getProcesses().get(1);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                GRBLinExpr lhs = new GRBLinExpr();
                                lhs.addTerm(1, A[i][processIndex][firstEligibleMachine]);

                                GRBLinExpr rhs = new GRBLinExpr();
                                rhs.addTerm(1, A[i + 1][1][nextFirstEligibleMachine]);

                                model.addConstr(lhs, GRB.LESS_EQUAL, rhs, "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }

                    // S14 1 - S15 1
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 14) && ((processIndex + 1) == 1)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 15 && nextOperation.getProcesses().size() >= 1) {
                                Process nextProcess = nextOperation.getProcesses().get(0);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                model.addConstr(A[i][0][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine], "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }

                    // S14 2 - S15 2
                    if (operation.getJobType().equals("B") && (operation.getStepNumber() == 14) && ((processIndex + 1) == 2)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 15 && nextOperation.getProcesses().size() >= 2) {
                                Process nextProcess = nextOperation.getProcesses().get(1);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();

                                model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_B_" + (i + 1));
                                if (DebugPrint) {
                                    System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    }
                }
            }


            for (int i = 0; i < numOperations; i++) {
                for (int j = 0; j < numOperations; j++) {
                    if (i != j) {
                        GRBLinExpr c7 = new GRBLinExpr();
                        c7.addTerm(1, beta[i][j]);
                        c7.addTerm(1, beta[j][i]);
                        c7.addConstant(-1);

                        // Build the constraint string for printing
                        StringBuilder constraintString = new StringBuilder();
                        constraintString.append("beta_").append(i + 1).append("_").append(j + 1).append(" + ");
                        constraintString.append("beta_").append(j + 1).append("_").append(i + 1).append(" - 1 <= 1");

                        // Print the constraint if DebugPrint is true
                        if (DebugPrint) {
                            System.out.println("Constraint: " + constraintString.toString());
                        }

                        model.addConstr(c7, GRB.LESS_EQUAL, 0, "beta_constraint_" + i + "_" + j);
                    }
                }
            }

            // Integrate new variables and constraints into the model
            model.update();

            // Define the objective function to minimize Cmax
            GRBLinExpr obj = new GRBLinExpr();
            obj.addTerm(1.0, Cmax);

            // Set the objective to minimize Cmax
            model.setObjective(obj, GRB.MINIMIZE);

            long startTimeModel = System.currentTimeMillis();

            // Optimize the model
            model.optimize();

            // Record the end time
            long endTimeModel = System.currentTimeMillis();

            // Calculate the total optimization time
            long optimizationTimeMillis = endTimeModel - startTimeModel;
            double optimizationTime = optimizationTimeMillis / 1000.0;

            // Check if the model is infeasible
            if (model.get(GRB.IntAttr.Status) == GRB.Status.INFEASIBLE) {
                System.out.println("Model is infeasible");
                model.computeIIS();
                model.write("projectModel.ilp");
            } else {

                for (int i = 0; i < numOperations; i++) {
                    for (int k = 0; k < numMachines; k++) {
                        if (O[i][k] != null) {
                            O_dummy[i][k] = O[i][k].get(GRB.DoubleAttr.X);
                        }
                    }
                }

                for (int i = 0; i < numOperations; i++) {
                    for (int j = 0; j < numOperations; j++) {
                        if (beta[i][j] != null) {
                            beta_dummy[i][j] = beta[i][j].get(GRB.DoubleAttr.X);
                        }
                    }
                }

                for (int i = 0; i < numOperations; i++) {
                    t_dummy[i] = t[i].get(GRB.DoubleAttr.X);
                }

                for (int i = 0; i < numOperations; i++) {
                    e_dummy[i] = e[i].get(GRB.DoubleAttr.X);
                }

                Cmax_dummy = Cmax.get(GRB.DoubleAttr.X);

                // Initialize an array to store the details for A_dummy
                OperationDetail[][][] A_dummy_detail = new OperationDetail[numOperations][maxProcesses][numMachines];

                for (int i = 0; i < numOperations; i++) {
                    Operation operation = operations.get(i);
                    String jobType = operation.getJobType(); // Assuming Operation class has a getJobType() method
                    for (int o = 0; o < operation.getProcesses().size(); o++) {
                        Process process = operation.getProcesses().get(o);
                        String processName = process.getName(); // Assuming Process class has a getName() method
                        int stepNumber = operation.getStepNumber();
                        for (int k : process.getEligibleMachines()) {
                            double value = A[i][o][k].get(GRB.DoubleAttr.X);
                            A_dummy[i][o][k] = value;
                            A_dummy_detail[i][o][k] = new OperationDetail(jobType, processName, stepNumber, value);
                        }
                    }
                }

                // Create a map to store all the dummy arrays and variables
                resultMap = new HashMap<>();
                resultMap.put("O_dummy", O_dummy);
                resultMap.put("beta_dummy", beta_dummy);
                resultMap.put("A_dummy", A_dummy);  // Raw values of A_dummy
                resultMap.put("A_dummy_detail", A_dummy_detail); // Detailed values of A_dummy
                resultMap.put("t_dummy", t_dummy);
                resultMap.put("e_dummy", e_dummy);
                resultMap.put("Cmax_dummy", Cmax_dummy);

                try {
                    // Print machine-wise assigned operations
                    for (int machineId = 0; machineId < machineNames.size(); machineId++) {

                        if(PrintFinalResult) {
                            System.out.println("\n" + machineNames.get(machineId) + ":");
                        }

                        for (int i = 0; i < numOperations; i++) {
                            Operation operation = operations.get(i);
                            double startTime = t[i].get(GRB.DoubleAttr.X); // Get the start time from Gurobi variable
                            startTime = Math.round(startTime * 100.0) / 100.0;

                            double endTime = e[i].get(GRB.DoubleAttr.X);
                            endTime = Math.round(endTime * 100.0) / 100.0;

                            String jobType = operation.getJobType(); // Get the job type (A/B)
                            int stepNumber = operation.getStepNumber();

                            for (int o = 0; o < operation.getProcesses().size(); o++) {
                                Process process = operation.getProcesses().get(o);
                                String processName = process.getName(); // Assuming Process class has a getName() method

                                if (process.getEligibleMachines().contains(machineId)) {
                                    if (A[i][o][machineId].get(GRB.DoubleAttr.X) == 1.0) {
                                        if(PrintFinalResult) {
                                            System.out.println("Operation " + (i + 1) + ", Start Time: " + startTime + ", End Time: " + endTime +
                                                    ", Job Type: " + jobType + "  Step " + stepNumber + ", Process: " + processName
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (GRBException e_2) {
                    e_2.printStackTrace();
                }

                // Print the CMax
                System.out.println("Cmax is: " + Cmax.get(GRB.DoubleAttr.X) + " s");

                // Print the total optimization time
                System.out.println("Total computation time: " + optimizationTime + " s");

                // Create a string in CSV format
                String data = numberOfJobA + "," + numberOfJobB + "," + Cmax.get(GRB.DoubleAttr.X) + "," + optimizationTime;

                // Write to a file
                try (FileWriter writer = new FileWriter("optimization_results.txt", true)) {
                    writer.write(data + "\n");
                } catch (IOException e_0) {
                    e_0.printStackTrace();
                }

                model.dispose();
                env.dispose();

            }
        } catch (GRBException e_3) {
            e_3.printStackTrace();
        }

        return resultMap;
    }
}
