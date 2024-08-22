package Basics.ProjectCode;

import java.util.HashMap;
import java.util.Map;

public class HeuristicOptimizationMainTrial {

    public static void main(String[] args) {
        // Solve the heuristic problem for 10+10 instances
        Map<String, Object> resultMap = heuristicOptimization(10, 10);

        // Accessing the variables from the resultMap
        double[][] O_dummy = (double[][]) resultMap.get("O_dummy");
        double[][] beta_dummy = (double[][]) resultMap.get("beta_dummy");
        double[][][] A_dummy = (double[][][]) resultMap.get("A_dummy");
        OperationDetail[][][] A_dummy_detail = (OperationDetail[][][]) resultMap.get("A_dummy_detail");
        double[] t_dummy = (double[]) resultMap.get("t_dummy");
        double[] e_dummy = (double[]) resultMap.get("e_dummy");
        double Cmax_dummy = (double) resultMap.get("Cmax_dummy");

        // Output the final Cmax before optimization
        System.out.println("Initial Cmax: " + Cmax_dummy);

        // Apply Heuristic 2: Minimize Idle Time
        minimizeIdleTime(O_dummy, t_dummy, e_dummy, A_dummy_detail);

        // Recalculate the new Cmax after optimization
        Cmax_dummy = calculateCmax(e_dummy);
        System.out.println("Final Cmax after optimization: " + Cmax_dummy);

        // Output the start and end times, along with the machine assignments for each operation
        System.out.println("\nOperation Details:");
        for (int i = 0; i < t_dummy.length; i++) {
            System.out.println("Operation " + (i + 1) + ": Start Time = " + t_dummy[i] + ", End Time = " + e_dummy[i]);

            for (int k = 0; k < O_dummy[i].length; k++) {
                if (O_dummy[i][k] == 1.0) {
                    System.out.println("    Assigned to Machine " + (k + 1));
                }
            }

            for (int o = 0; o < A_dummy[i].length; o++) {
                for (int k = 0; k < A_dummy[i][o].length; k++) {
                    if (A_dummy[i][o][k] == 1.0) {
                        OperationDetail detail = A_dummy_detail[i][o][k];
                        if (detail != null) {
                            System.out.println("    Job Type: " + detail.getJobType() + ", Process: " + detail.getProcessName() + ", Step: " + detail.getStepNumber() + ", Assigned to Machine " + (k + 1));
                        }
                    }
                }
            }
        }
    }

    public static Map<String, Object> heuristicOptimization(int largeNumA, int largeNumB) {
        int numMachines = 10;  // Assume 10 machines as per your earlier code
        int numSubProblems = largeNumA / 3; // Assuming largeNumA and largeNumB are multiples of 3
        int remainderA = largeNumA % 3;
        int remainderB = largeNumB % 3;

        double totalCmax = 0;
        double cumulativeCmax = 0;

        // Estimate total number of operations based on your formula
        int numOperations = largeNumA * 19 + largeNumB * 16;

        double[][] largeO_dummy = new double[numOperations][numMachines];
        double[][] largeBeta_dummy = new double[numOperations][numOperations];
        double[][][] largeA_dummy = new double[numOperations][19][numMachines];
        OperationDetail[][][] largeA_detail = new OperationDetail[numOperations][19][numMachines];
        double[] largeT_dummy = new double[numOperations];
        double[] largeE_dummy = new double[numOperations];

        int operationCounter = 0;  // To keep track of the operation indices

        for (int i = 0; i < numSubProblems; i++) {
            // Solve the 3+3 subproblem
            Map<String, Object> subResultMap = OptimizationProblemMain.runOptimization(3, 3, false, false);

            // Aggregate results into larger arrays with cumulative Cmax offset
            aggregateResults(subResultMap, operationCounter, cumulativeCmax, largeO_dummy, largeBeta_dummy, largeA_dummy, largeA_detail, largeT_dummy, largeE_dummy);

            // Update the operation counter
            operationCounter += 19 * 3 + 16 * 3;

            // Update the cumulative and total makespan
            double subCmax = (double) subResultMap.get("Cmax_dummy");
            cumulativeCmax += subCmax;
            totalCmax = Math.max(totalCmax, cumulativeCmax);
        }

        // Handle any remaining operations
        if (remainderA > 0 || remainderB > 0) {
            Map<String, Object> remainderResultMap = OptimizationProblemMain.runOptimization(remainderA, remainderB, false, false);
            aggregateResults(remainderResultMap, operationCounter, cumulativeCmax, largeO_dummy, largeBeta_dummy, largeA_dummy, largeA_detail, largeT_dummy, largeE_dummy);
            double subCmax = (double) remainderResultMap.get("Cmax_dummy");
            cumulativeCmax += subCmax;
            totalCmax = Math.max(totalCmax, cumulativeCmax);
        }

        // Store results in a map
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("O_dummy", largeO_dummy);
        resultMap.put("beta_dummy", largeBeta_dummy);
        resultMap.put("A_dummy", largeA_dummy);
        resultMap.put("A_dummy_detail", largeA_detail);
        resultMap.put("t_dummy", largeT_dummy);
        resultMap.put("e_dummy", largeE_dummy);
        resultMap.put("Cmax_dummy", totalCmax);

        return resultMap;
    }

    private static void aggregateResults(Map<String, Object> subResultMap, int offset, double cumulativeCmax, double[][] largeO, double[][] largeBeta, double[][][] largeA, OperationDetail[][][] largeA_detail, double[] largeT, double[] largeE) {
        // Extract the small instance results
        double[][] O_dummy = (double[][]) subResultMap.get("O_dummy");
        double[][] beta_dummy = (double[][]) subResultMap.get("beta_dummy");
        double[][][] A_dummy = (double[][][]) subResultMap.get("A_dummy");
        OperationDetail[][][] A_dummy_detail = (OperationDetail[][][]) subResultMap.get("A_dummy_detail");
        double[] t_dummy = (double[]) subResultMap.get("t_dummy");
        double[] e_dummy = (double[]) subResultMap.get("e_dummy");

        int numOperations = O_dummy.length;  // number of operations in subproblem

        for (int i = 0; i < numOperations; i++) {
            System.arraycopy(O_dummy[i], 0, largeO[offset + i], 0, O_dummy[i].length);
            System.arraycopy(beta_dummy[i], 0, largeBeta[offset + i], 0, beta_dummy[i].length);
            largeT[offset + i] = t_dummy[i] + cumulativeCmax;
            largeE[offset + i] = e_dummy[i] + cumulativeCmax;
            for (int o = 0; o < A_dummy[i].length; o++) {
                for (int k = 0; k < A_dummy[i][o].length; k++) {
                    largeA[offset + i][o][k] = A_dummy[i][o][k];
                    largeA_detail[offset + i][o][k] = A_dummy_detail[i][o][k];
                }
            }
        }
    }

    private static void minimizeIdleTime(double[][] O_dummy, double[] t_dummy, double[] e_dummy, OperationDetail[][][] operationDetails) {
        int numMachines = O_dummy[0].length;

        for (int machine = 0; machine < numMachines; machine++) {
            double lastEndTime = 0.0;
            for (int i = 0; i < t_dummy.length; i++) {
                if (O_dummy[i][machine] == 1.0) {
                    if (t_dummy[i] > lastEndTime) {
                        double idleTime = t_dummy[i] - lastEndTime;
                        System.out.println("Idle time on Machine " + (machine + 1) + ": " + idleTime);

                        // Suggest moving operation earlier if possible
                        if (canMoveOperation(i, lastEndTime, t_dummy, e_dummy, operationDetails)) {
                            t_dummy[i] = lastEndTime;
                            e_dummy[i] = lastEndTime + (e_dummy[i] - t_dummy[i]); // Adjust end time accordingly
                            lastEndTime = e_dummy[i]; // Update the lastEndTime after moving the operation
                        }
                    } else {
                        lastEndTime = e_dummy[i]; // Update the lastEndTime normally if no move is made
                    }
                }
            }
        }
    }

    private static boolean canMoveOperation(int operationIndex, double newStartTime, double[] t_dummy, double[] e_dummy, OperationDetail[][][] operationDetails) {
        OperationDetail operation = operationDetails[operationIndex][0][0];
        if (operation == null) {
            return false; // Can't move a null operation
        }

        // Check if the new start time interferes with any preceding steps in the same job
        for (int i = 0; i < t_dummy.length; i++) {
            OperationDetail checkOperation = operationDetails[i][0][0];
            if (i != operationIndex &&
                    checkOperation != null &&
                    checkOperation.getJobType().equals(operation.getJobType()) &&
                    checkOperation.getStepNumber() < operation.getStepNumber()) {

                // Ensure that the preceding step ends before the new start time
                if (e_dummy[i] > newStartTime) {
                    return false; // Can't move the operation because the preceding step isn't finished yet
                }
            }
        }
        return true;
    }

    private static double calculateCmax(double[] e_dummy) {
        double Cmax = 0.0;
        for (double endTime : e_dummy) {
            Cmax = Math.max(Cmax, endTime);
        }
        return Cmax;
    }
}
