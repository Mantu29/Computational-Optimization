package Basics.ProjectCode;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HeuristicOptimizationSolver {

    public static void main(String[] args) {

        // Solve the heuristic problem for 10+10 instances
        long startTimeModel = System.currentTimeMillis();

        int largeNumA = 2;
        int largeNumB = largeNumA;
        int instance  = 5;

        Map<String, Object> resultMap = heuristicOptimization(largeNumA, largeNumB, instance);

        // Record the end time
        long endTimeModel = System.currentTimeMillis();

        // Calculate the total optimization time
        long optimizationTimeMillis = endTimeModel - startTimeModel;
        double optimizationTime = optimizationTimeMillis / 1000.0;


        // Accessing the variables from the resultMap
        double[][] O_dummy = (double[][]) resultMap.get("O_dummy");
        double[][] beta_dummy = (double[][]) resultMap.get("beta_dummy");
        double[][][] A_dummy = (double[][][]) resultMap.get("A_dummy");
        OperationDetail[][][] A_dummy_detail = (OperationDetail[][][]) resultMap.get("A_dummy_detail");
        double[] t_dummy = (double[]) resultMap.get("t_dummy");
        double[] e_dummy = (double[]) resultMap.get("e_dummy");
        double Cmax_dummy = (double) resultMap.get("Cmax_dummy");

        //DataExporter.exportData(resultMap, "C:/Users/mantu/OneDrive/Documents/GitHub/Computational-Optimization/Class/src/main/java/Week_01");

        // Output the start and end times, along with the machine assignments for each operation
        System.out.println("\nOperation Details:");
        for (int i = 0; i < t_dummy.length; i++) {
            // Round the start time and end time to 2 decimal places
            double startTimeRounded = Math.round(t_dummy[i] * 100.0) / 100.0;
            double endTimeRounded = Math.round(e_dummy[i] * 100.0) / 100.0;

            System.out.println("Operation " + (i + 1) + ": Start Time = " + startTimeRounded + ", End Time = " + endTimeRounded);

            for (int k = 0; k < O_dummy[i].length; k++) {
                if (O_dummy[i][k] == 1.0) {
                    System.out.println("    Assigned to Machine " + (k + 1));
                }
            }

            for (int o = 0; o < A_dummy[i].length; o++) {
                for (int k = 0; k < A_dummy[i][o].length; k++) {
                    if (A_dummy[i][o][k] == 1.0) {
                        OperationDetail detail = A_dummy_detail[i][o][k];
                        System.out.println("    Job Type: " + detail.getJobType() + ", Process: " + detail.getProcessName() + ", Step: " + detail.getStepNumber() + ", Assigned to Machine " + (k + 1));
                    }
                }
            }
        }

        // Output the final Cmax
        System.out.println("Final Cmax: " + Cmax_dummy);
        System.out.println("Total computation time: " + optimizationTime + " s");

        // Create a string in CSV format
        String data = largeNumA + "," + largeNumB + "," + instance + "," + Cmax_dummy + "," + optimizationTime;

        // Write to a file
        try (FileWriter writer = new FileWriter("optimization_results_heuristic.txt", true)) {
            writer.write(data + "\n");
        } catch (IOException e_0) {
            e_0.printStackTrace();
        }
    }

    public static Map<String, Object> heuristicOptimization(int largeNumA, int largeNumB, int instance) {
        int numMachines = 10;  // Assume 10 machines as per your earlier code
        int numSubProblems = largeNumA / instance; // Assuming largeNumA and largeNumB are multiples of 3
        int remainderA = largeNumA % instance;
        int remainderB = largeNumB % instance;

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
            // Solve the instance+instance subproblem
            Map<String, Object> subResultMap = OptimizationProblem.runOptimization(instance, instance, false, false);

            // Calculate the number of operations in this subproblem
            int subNumOperations = instance * 19 + instance * 16;

            // Aggregate results into larger arrays with cumulative Cmax offset
            aggregateResults(subResultMap, operationCounter, cumulativeCmax, largeO_dummy, largeBeta_dummy, largeA_dummy, largeA_detail, largeT_dummy, largeE_dummy);

            // Update the operation counter with the number of operations in this subproblem
            operationCounter += subNumOperations;

            // Update the cumulative and total makespan
            double subCmax = (double) subResultMap.get("Cmax_dummy");
            cumulativeCmax += subCmax;
            totalCmax = Math.max(totalCmax, cumulativeCmax);
        }

        // Handle any remaining operations
        if (remainderA > 0 || remainderB > 0) {
            Map<String, Object> remainderResultMap = OptimizationProblem.runOptimization(remainderA, remainderB, false, false);
            int remainderNumOperations = remainderA * 19 + remainderB * 16;
            aggregateResults(remainderResultMap, operationCounter, cumulativeCmax, largeO_dummy, largeBeta_dummy, largeA_dummy, largeA_detail, largeT_dummy, largeE_dummy);
            operationCounter += remainderNumOperations;
            double subCmax = (double) remainderResultMap.get("Cmax_dummy");
            cumulativeCmax += subCmax;
            totalCmax = Math.max(totalCmax, cumulativeCmax);
        }

        // Store results in a map
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("O_dummy", largeO_dummy);
        resultMap.put("beta_dummy", largeBeta_dummy);
        resultMap.put("A_dummy", largeA_dummy);
        resultMap.put("A_dummy_detail", largeA_detail);  // Include the operation details
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
}
