package CGCuttingStock;

import com.gurobi.gurobi.*;

import java.util.ArrayList;
import java.util.List;

public class RMP {

    public static void RMPSolver(int n, int[] weights, int[] demands, ArrayList<ArrayList<Integer>> patterns, List<Double> bestBounds, List<Double> dualValues) {
        try {
            GRBEnv env = new GRBEnv();
            GRBModel model = new GRBModel(env);
            model.set(GRB.StringAttr.ModelName, "RMP");

            // Create Gurobi variables for each pattern
            List<GRBVar> z = new ArrayList<>();
            for (int i = 0; i < patterns.size(); i++) {
                z.add(model.addVar(0, 10000, 1, GRB.CONTINUOUS, "z_" + i));
            }

            // Add demand constraints
            for (int i = 0; i < n; i++) {
                GRBLinExpr ci = new GRBLinExpr();
                for (int j = 0; j < patterns.size(); j++) {
                    ci.addTerm(patterns.get(j).get(i), z.get(j));
                }
                model.addConstr(ci, GRB.GREATER_EQUAL, demands[i], "c_" + i);
            }

            // Set the objective to minimize the number of patterns used
            GRBLinExpr obj = new GRBLinExpr();
            for (int j = 0; j < patterns.size(); j++) {
                obj.addTerm(1, z.get(j));
            }
            model.setObjective(obj, GRB.MINIMIZE);
            model.optimize();

            if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
                bestBounds.add(model.get(GRB.DoubleAttr.ObjVal));

                // Print the values of z_i
                double optimal_z_sum = 0.0;
                System.out.println("Optimal values of z_i:");
                for (int i = 0; i < z.size(); i++) {
                    System.out.println("z_" + i + " = " + z.get(i).get(GRB.DoubleAttr.X));
                    optimal_z_sum += z.get(i).get(GRB.DoubleAttr.X);
                }
                System.out.println("Optimal values of z_i:" + optimal_z_sum);
                // Update and print the dual values (shadow prices)
                System.out.println("Dual values (shadow prices) for constraints:");
                for (int i = 0; i < n; i++) {
                    double dualValue = model.getConstr(i).get(GRB.DoubleAttr.Pi);
                    dualValues.set(i, dualValue);
                    System.out.println("Dual value for constraint c_" + i + " = " + dualValue);
                }
            }

            // Dispose of model and environment
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }
    }
}
