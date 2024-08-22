package Basics.ProjectCode;

import java.util.Map;
import java.io.FileWriter;
import java.io.IOException;

public class ProblemSolver {
    public static void main(String[] args) {
        // Call the runOptimization method
        int NoOfJobA = 4;
        int NoOfJobB = NoOfJobA;
        Map<String, Object> resultMap = OptimizationProblemMain.runOptimization(NoOfJobA, NoOfJobA, false, true);

        // Check if the resultMap is not empty
        if (resultMap != null && !resultMap.isEmpty()) {
            // Accessing the variables from the resultMap
            double[][] O_dummy = (double[][]) resultMap.get("O_dummy");
            double[][] beta_dummy = (double[][]) resultMap.get("beta_dummy");
            double[][][] A_dummy = (double[][][]) resultMap.get("A_dummy");
            double[] t_dummy = (double[]) resultMap.get("t_dummy");
            double[] e_dummy = (double[]) resultMap.get("e_dummy");
            double Cmax_dummy = (double) resultMap.get("Cmax_dummy");

            // Export O_dummy and t_dummy variables with job numbers in filenames
            //exportArray(O_dummy, "O_dummyIndividual_" + NoOfJobA + "_" + NoOfJobB + ".csv");
            //exportArray(t_dummy, "t_dummyIndividual_" + NoOfJobA + "_" + NoOfJobB + ".csv");
            //exportArray(e_dummy, "e_dummyIndividual_" + NoOfJobA + "_" + NoOfJobB + ".csv");
        } else {
            // Print message if the model is not solved
            System.out.println("Model not solved or resultMap is empty.");
        }
    }

    // Method to export 2D double array to CSV
    private static void exportArray(double[][] array, String fileName) {
        try (FileWriter writer = new FileWriter(fileName)) {
            for (double[] row : array) {
                for (int i = 0; i < row.length; i++) {
                    writer.append(String.valueOf(row[i]));
                    if (i < row.length - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to export 1D double array to CSV
    private static void exportArray(double[] array, String fileName) {
        try (FileWriter writer = new FileWriter(fileName)) {
            for (int i = 0; i < array.length; i++) {
                writer.append(String.valueOf(array[i]));
                if (i < array.length - 1) {
                    writer.append(",");
                }
            }
            writer.append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
