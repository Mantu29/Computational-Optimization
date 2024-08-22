package CGCuttingStock;

import com.gurobi.gurobi.*;

import java.util.ArrayList;
import java.util.List;

public class PricingProblem {
    public static Boolean PricingSolver(int n, int[] weights, double stocklength, List<Double> dualValues, ArrayList<ArrayList<Integer>> patterns) {
        Boolean isOptimal = true;

        try {

            GRBEnv env = new GRBEnv();
            GRBModel model = new GRBModel(env);
            model.set(GRB.StringAttr.ModelName, "Pricing");

            // Create variables for the pricing problem
            List<GRBVar> a = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                a.add(model.addVar(0, GRB.INFINITY, 1, GRB.INTEGER, "a_" + i));
            }

            // Add constraint: sum(weights[i] * a[i]) <= stockLength
            GRBLinExpr lengthExpr = new GRBLinExpr();
            StringBuilder constraintExpr = new StringBuilder("Constraint: ");
            for (int i = 0; i < n; i++) {
                lengthExpr.addTerm(weights[i], a.get(i));
                constraintExpr.append(weights[i]).append(" * a_").append(i);
                if (i < n - 1) {
                    constraintExpr.append(" + ");
                }
            }
            constraintExpr.append(" <= ").append(stocklength);
            model.addConstr(lengthExpr, GRB.LESS_EQUAL, stocklength, "length_constraint");

            // Print the constraint expression
            System.out.println(constraintExpr.toString());

            // Set the objective function to maximize the sum of dual variables * a[i]
            GRBLinExpr obj = new GRBLinExpr();
            StringBuilder objExpr = new StringBuilder("Objective function: maximize ");
            for (int i = 0; i < n; i++) {
                obj.addTerm(dualValues.get(i), a.get(i));
                objExpr.append(String.format("%.2f * a_%d", dualValues.get(i), i));
                if (i < n - 1) {
                    objExpr.append(" + ");
                }
            }
            System.out.println(objExpr.toString());
            model.setObjective(obj, GRB.MAXIMIZE);

            // Optimize the model
            model.optimize();

            // Check if the new pattern has a product sum greater than 1
            if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
                double reducedCost = model.get(GRB.DoubleAttr.ObjVal);

                // Calculate the product of the solution obtained (dualValues[i] * a[i])
                double productSum = 0;
                ArrayList<Integer> newPattern = new ArrayList<>();

                System.out.println("Values of a[i]:");
                for (int i = 0; i < n; i++) {
                    double aValue = a.get(i).get(GRB.DoubleAttr.X);
                    System.out.println("a_" + i + " = " + aValue);

                    productSum += dualValues.get(i) * aValue;
                    newPattern.add((int) aValue);
                }

                if (productSum > 1) {
                    // Product sum is greater than 1, return the pattern and set isOptimal to false
                    patterns.add(newPattern);
                    isOptimal = false;
                }
            }

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }

        return isOptimal;
    }
}
