package Basics.ProjectCode;

import com.gurobi.gurobi.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class OptimizationProblemRunner {

    public static void main(String[] args) {
        // Define the number of jobs for each type
        int numberOfJobA = 1;
        int numberOfJobB = 1;

        // Get the job list
        List<Job> jobList = JobFactory.createAndInitializeJobs(45, numberOfJobA, numberOfJobB); // Initial number of processes is 45, can be adjusted

        // Create a combined list of operations from all jobs
        List<Operation> operations = JobFactory.createOperationsFromJobs(jobList);

        // Dynamically determine the number of processes from the size of the operations list
        int numberOfProcesses = operations.size();

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

        // Determine the maximum number of processes in any operation
        int maxProcesses = operations.stream().mapToInt(op -> op.getProcesses().size()).max().orElse(1);

        // Define binary variables S_i^k, B_ij, A_io^k and continuous variable t_i in Gurobi
        try {
            // Create a new Gurobi environment
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "mip1.log");
            env.start();

            // Create an empty optimization model
            GRBModel model = new GRBModel(env);

            // Define the number of operations and machines
            int numOperations = numberOfProcesses;
            int numMachines = 10; // Example number of machines, adjust as needed

            // Create binary variables S_i^k only for eligible machines
            GRBVar[][] S = new GRBVar[numOperations][numMachines];
            for (int i = 0; i < numOperations; i++) {
                for (int k = 0; k < numMachines; k++) {
                    S[i][k] = null;
                }
                for (int k : operations.get(i).getEligibleMachines()) {
                    S[i][k] = model.addVar(0, 1, 0, GRB.BINARY, "S_" + (i + 1) + "_" + (k + 1));
                }
            }

            // Define binary variables beta_ii'
            GRBVar[][] beta = new GRBVar[numOperations][numOperations];
            for (int i = 0; i < numOperations; i++) {
                for (int j = 0; j < numOperations; j++) {
                    if (i != j) {
                        beta[i][j] = model.addVar(0, 1, 0, GRB.BINARY, "beta_" + (i + 1) + "_" + (j + 1));
                    }
                }
            }

            // Create binary variables A_io^k only for eligible machines
            GRBVar[][][] A = new GRBVar[numOperations][maxProcesses][numMachines];
            for (int i = 0; i < numOperations; i++) {
                for (int o = 0; o < maxProcesses; o++) {
                    for (int k = 0; k < numMachines; k++) {
                        A[i][o][k] = null;
                    }
                    for (int k : operations.get(i).getEligibleMachines()) {
                        A[i][o][k] = model.addVar(0, 1, 0, GRB.BINARY, "A_" + (i + 1) + "_" + (o + 1) + "_" + (k + 1));
                    }
                }
            }

            // Create continuous variables t_i
            GRBVar[] t = new GRBVar[numOperations];
            for (int i = 0; i < numOperations; i++) {
                t[i] = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "t_" + (i + 1));
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

            double H = 10000;

            Map<Integer, Map<Integer, Integer>> jobAConstraints = new HashMap<>();

            // Add constraints to ensure all operations are executed exactly once
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

                // Print the constraint
                System.out.println(constraintString.toString());

                // Add the constraint: sum_o sum_k A_io^k = 1
                model.addConstr(operationExpr, GRB.EQUAL, 1.0, "operation_constraint_" + (i + 1));
            }

            // Add constraints to link S_i^k and A_io^k
            for (int i = 0; i < numOperations; i++) {
                for (int o = 0; o < operations.get(i).getProcesses().size(); o++) {
                    for (int k : operations.get(i).getProcesses().get(o).getEligibleMachines()) {
                        // Create the constraint: S_i^k >= A_io^k
                        GRBLinExpr linkExpr = new GRBLinExpr();
                        linkExpr.addTerm(1.0, A[i][o][k]);

                        // Print the constraint
                        String constraintString = "S_" + (i + 1) + "_" + (k + 1) + " >= A_" + (i + 1) + "_" + (o + 1) + "_" + (k + 1);
                        System.out.println("Link constraint: " + constraintString);

                        // Add the constraint to the model
                        model.addConstr(S[i][k], GRB.GREATER_EQUAL, linkExpr, "link_S_and_A_" + (i + 1) + "_" + (o + 1) + "_" + (k + 1));
                    }
                }
            }

            // Add constraints to ensure each operation is assigned to exactly one machine
            for (int i = 0; i < numOperations; i++) {
                GRBLinExpr operationExpr = new GRBLinExpr();
                StringBuilder constraintString = new StringBuilder("Constraint for Operation " + (i + 1) + ": ");

                for (int k : operations.get(i).getEligibleMachines()) {
                    operationExpr.addTerm(1.0, S[i][k]);
                    constraintString.append("S_").append(i + 1).append("_").append(k + 1).append(" + ");
                }

                // Remove the last " + " and add "= 1"
                constraintString.setLength(constraintString.length() - 3);
                constraintString.append(" = 1");

                // Print the constraint
                System.out.println(constraintString.toString());

                // Add the constraint: sum_k S_i^k = 1
                model.addConstr(operationExpr, GRB.EQUAL, 1.0, "operation_constraint_" + (i + 1));
            }

            // Add constraints to ensure Cmax is greater than or equal to the sum of start time and processing times
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
            }

            // Track the current index in the operations list to maintain the sequence
            int currentIndex = 0;

            // Add constraints to ensure the start time of operation i is greater than or equal to the start time of the preceding operation plus its processing time
            // Job definitions - adding jobs and their corresponding operations
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

                    // Print the constraint
                    System.out.println("Precedence constraint: " + constraintString.toString());

                    // Add the constraint: t_i >= t_pr(i) + sum(p_pr(i)o^k * alpha_pr(i)o^k)
                    model.addConstr(lhs, GRB.GREATER_EQUAL, rhs, "precedence_constraint_" + (currentOpIndex + 1));
                }
            }

            // Add the precedence constraints
            for (int i = 0; i < numOperations; i++) {
                for (int j = 0; j < numOperations; j++) {
                    if (i != j) {
                        for (int k : operations.get(i).getEligibleMachines()) {
                            if (operations.get(j).getEligibleMachines().contains(k)) {
                                // Print the common machine
                                System.out.println("Common machine for operations " + (i + 1) + " and " + (j + 1) + ": " + (k + 1));

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

                                constraintString.append("- 2 * H  + S_" + (i + 1) + "_" + (k + 1) + " + H * S_" + (j + 1) + "_" + (k + 1) + " - H * B_" + (i + 1) + "_" + (j + 1));

                                overlappingB.addTerm(1, t[j]);
                                overlappingB.addTerm(-1, t[i]);
                                overlappingB.addConstant(-2 * H);
                                overlappingB.addTerm(H, S[i][k]);
                                overlappingB.addTerm(H, S[j][k]);
                                overlappingB.addTerm(-H, beta[i][j]);

                                // Print the constraint
                                System.out.println("Precedence constraint: " + constraintString.toString());

                                model.addConstr(overlappingB, GRB.LESS_EQUAL, 0, "overlappingB_" + (i + 1) + "_" + (j + 1) + "_" + (k + 1));
                            }
                        }
                    }
                }
            }

            // Add the precedence constraints for the second constraint
            for (int i = 0; i < numOperations; i++) {
                for (int j = 0; j < numOperations; j++) {
                    if (i != j) {
                        for (int k : operations.get(i).getEligibleMachines()) {
                            if (operations.get(j).getEligibleMachines().contains(k)) {
                                // Print the common machine
                                System.out.println("Common machine for operations " + (i + 1) + " and " + (j + 1) + ": " + (k + 1));

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

                                constraintString.append("- 3 * H + H * S_" + (i + 1) + "_" + (k + 1) + " + H * S_" + (j + 1) + "_" + (k + 1) + " - H * B_" + (i + 1) + "_" + (j + 1));

                                overlappingA.addTerm(-1, t[j]);
                                overlappingA.addTerm(1, t[i]);
                                overlappingA.addConstant(-3 * H);
                                overlappingA.addTerm(H, S[i][k]);
                                overlappingA.addTerm(H, S[j][k]);
                                overlappingA.addTerm(-H, beta[i][j]);

                                // Print the constraint
                                System.out.println("Precedence constraint: " + constraintString.toString());

                                model.addConstr(overlappingA, GRB.LESS_EQUAL, 0, "c4_" + (i + 1) + "_" + (j + 1) + "_" + (k + 1));
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

                        // Print the constraint
                        System.out.println("Constraint: " + constraintString.toString());

                        model.addConstr(c7, GRB.LESS_EQUAL, 0, "beta_constraint_" + i + "_" + j);
                    }
                }
            }

            for (int i = 0; i < operations.size(); i++) {
                Operation operation = operations.get(i);
                System.out.println("Operation " + (i + 1) + ": Job Type - " + operation.getJobType() + ", Step Number - " + operation.getStepNumber());
                for (int processIndex = 0; processIndex < operation.getProcesses().size(); processIndex++) {
                    Process process = operation.getProcesses().get(processIndex);
                    if (!process.getEligibleMachines().isEmpty()) {
                        List<Integer> eligibleMachinesList = new ArrayList<>(process.getEligibleMachines());
                        Integer firstEligibleMachine = eligibleMachinesList.get(0);
                        System.out.println("  Step Number: " + operation.getStepNumber() + ", Process Number: " + (processIndex + 1) + ", First Eligible Machine: " + firstEligibleMachine);

                        // Add the specific constraint for Job Type A, Step Number 5, and Process Index 1
                        if (operation.getJobType().equals("A") && (operation.getStepNumber() == 5) && ((processIndex + 1) == 1)) {
                            if (i + 1 < operations.size()) {
                                Operation nextOperation = operations.get(i + 1);
                                if (nextOperation.getStepNumber() == 6 && !nextOperation.getProcesses().get(1).getEligibleMachines().isEmpty()) {
                                    List<Integer> nextEligibleMachinesList = new ArrayList<>(nextOperation.getProcesses().get(1).getEligibleMachines());
                                    Integer nextFirstEligibleMachine = nextEligibleMachinesList.get(0);
                                    model.addConstr(A[i][1][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][1][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                    System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
                                }
                            }
                        }
                    } else {
                        System.out.println("  Step Number: " + operation.getStepNumber() + ", Process Number: " + (processIndex + 1) + ", No Eligible Machines");
                    }
                }
                System.out.println();
            }

            for (int i = 0; i < operations.size(); i++) {
                Operation operation = operations.get(i);
                List<Process> processes = operation.getProcesses();

                for (int processIndex = 0; processIndex < processes.size(); processIndex++) {
                    Process process = processes.get(processIndex);
                    int firstEligibleMachine = process.getEligibleMachines().iterator().next();

                    // Define the constraints for JobA

                    // S5 1 - S6 1
                    if (operation.getJobType().equals("A") && (operation.getStepNumber() == 5) && ((processIndex + 1) == 1)) {
                        if (i + 1 < operations.size()) {
                            Operation nextOperation = operations.get(i + 1);
                            if (nextOperation.getStepNumber() == 6 && nextOperation.getProcesses().size() >= 1) {
                                Process nextProcess = nextOperation.getProcesses().get(0);
                                int nextFirstEligibleMachine = nextProcess.getEligibleMachines().iterator().next();
                                model.addConstr(A[i][0][firstEligibleMachine], GRB.LESS_EQUAL, A[i + 1][0][nextFirstEligibleMachine], "specific_constraint_" + (i + 1));
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "]");
                                System.out.println("Added constraint: A[" + (i + 1) + "][3][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                System.out.println("Added constraint: A[" + (i + 1) + "][4][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "]");
                                System.out.println("Added constraint: A[" + (i + 1) + "][3][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                System.out.println("Added constraint: A[" + (i + 1) + "][4][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
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
                                // Continue from the previous step
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                            }
                        }
                    }
                }
            }

            // Implementing constraints for Job B
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "]");
                                System.out.println("Added constraint: A[" + (i + 1) + "][3][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
                                System.out.println("Added constraint: A[" + (i + 1) + "][4][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine1 + "] + A[" + (i + 2) + "][2][" + nextFirstEligibleMachine2 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][3][" + nextFirstEligibleMachine3 + "] + A[" + (i + 2) + "][4][" + nextFirstEligibleMachine4 + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][" + (processIndex + 1) + "][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][1][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][1][" + nextFirstEligibleMachine + "]");
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
                                System.out.println("Added constraint: A[" + (i + 1) + "][2][" + firstEligibleMachine + "] <= A[" + (i + 2) + "][2][" + nextFirstEligibleMachine + "]");
                            }
                        }
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

            // Optimize the model
            model.optimize();
            // Print all variables that have been added to the model
            /*GRBVar[] vars = model.getVars();
            for (GRBVar var : vars) {
                System.out.println("Variable: " + var.get(GRB.StringAttr.VarName) + ", Type: " + var.get(GRB.CharAttr.VType));
            }*/

            // Check if the model is infeasible
            if (model.get(GRB.IntAttr.Status) == GRB.Status.INFEASIBLE) {
                System.out.println("Model is infeasible");
                model.computeIIS();
                model.write("projectModel.ilp");
            } else {
                // Print the assignment of processes to machines
                for (int i = 0; i < numOperations; i++) {
                    System.out.println("Start time of Operation " + (i + 1) + ": " + t[i].get(GRB.DoubleAttr.X));
                }
                System.out.println("Cmax is: " + Cmax.get(GRB.DoubleAttr.X));
                for (int i = 0; i < numOperations; i++) {
                    for (int o = 0; o < operations.get(i).getProcesses().size(); o++) {
                        for (int k : operations.get(i).getProcesses().get(o).getEligibleMachines()) {
                            if (A[i][o][k].get(GRB.DoubleAttr.X) == 1.0) {
                                System.out.println("Operation " + (i + 1) + ", Process " + (o + 1) + " is assigned to Machine " + (k));
                            }
                        }
                    }
                }

                // Print the beta variable values
                /*for (int i = 0; i < numOperations; i++) {
                    for (int j = 0; j < numOperations; j++) {
                        if (i != j){
                            if(beta[i][j].get(GRB.DoubleAttr.X) == 1) {
                                //System.out.println("Beta value for operations " + (i + 1) + " and " + (j + 1) + ": " + beta[i][j].get(GRB.DoubleAttr.X));
                            }
                        }
                    }
                }*/
            }

            // Dispose of the model and environment
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            e.printStackTrace();
        }
    }
}

