package EndTermCode;
import com.gurobi.gurobi.*;

public class CuttingStockProblemWastage {
    public static void main(String[] args) {
        try {
            GRBEnv env = new GRBEnv("cutting_stock.log");
            GRBModel model = new GRBModel(env);

            int demandType1 = 44;
            int demandType2 = 3;
            int demandType3 = 48;
            int stockLength = 218;

            int lengthType1 = 81;
            int lengthType2 = 70;
            int lengthType3 = 68;

            int[][] wastageMatrixA = {
                    {1, 4, 2},
                    {1, 2, 3},
                    {5, 1 ,0}
            };

            GRBVar[] z = new GRBVar[9];
            for (int i = 0; i < 9; i++) {
                z[i] = model.addVar(0, GRB.INFINITY, 0, GRB.INTEGER, "z" + (i + 1));
            }

            GRBLinExpr objective = new GRBLinExpr();
            objective.addTerm(1, z[0]);
            objective.addTerm(1, z[1]);
            objective.addTerm(1, z[2]);
            objective.addTerm(1, z[3]);
            objective.addTerm(1, z[4]);
            objective.addTerm(1, z[5]);
            objective.addTerm(1, z[6]);
            objective.addTerm(1, z[7]);
            objective.addTerm(1, z[8]);

            objective.addTerm(wastageMatrixA[0][0], z[0]);
            objective.addTerm(wastageMatrixA[0][1], z[1]);
            objective.addTerm(wastageMatrixA[0][2], z[2]);
            objective.addTerm(wastageMatrixA[1][0], z[3]);
            objective.addTerm(wastageMatrixA[1][1], z[4]);
            objective.addTerm(wastageMatrixA[1][2], z[5]);
            objective.addTerm(wastageMatrixA[2][0], z[6]);
            objective.addTerm(wastageMatrixA[2][1], z[7]);
            objective.addTerm(wastageMatrixA[2][2], z[8]);

            model.setObjective(objective, GRB.MINIMIZE);

            GRBLinExpr type1Constraint = new GRBLinExpr();
            type1Constraint.addTerm(2, z[0]);
            type1Constraint.addTerm(1, z[1]);
            type1Constraint.addTerm(1, z[2]);
            type1Constraint.addTerm(1, z[3]);
            type1Constraint.addTerm(1, z[6]);
            model.addConstr(type1Constraint, GRB.GREATER_EQUAL, demandType1, "type1");

            GRBLinExpr type2Constraint = new GRBLinExpr();
            type2Constraint.addTerm(1, z[1]);
            type2Constraint.addTerm(1, z[3]);
            type2Constraint.addTerm(2, z[4]);
            type2Constraint.addTerm(1, z[5]);
            type2Constraint.addTerm(1, z[7]);
            model.addConstr(type2Constraint, GRB.GREATER_EQUAL, demandType2, "type2");

            GRBLinExpr type3Constraint = new GRBLinExpr();
            type3Constraint.addTerm(1, z[2]);
            type3Constraint.addTerm(1, z[5]);
            type3Constraint.addTerm(1, z[6]);
            type3Constraint.addTerm(1, z[7]);
            type3Constraint.addTerm(2, z[8]);
            model.addConstr(type3Constraint, GRB.GREATER_EQUAL, demandType3, "type3");

            model.optimize();

            System.out.println("Optimal Cutting Plan:");
            System.out.println("============================================");
            System.out.printf("%-30s %-15s\n", "Cutting Pattern", "Stock Used");

            double totalWastage = 0;
            double totalStockUsed = 0;
            double[] wastages = new double[] {1, 4, 2, 1, 2, 3, 5, 1, 0};
            String[] patterns = new String[] {
                    "Two pieces of Type 1",
                    "One Type 1, One Type 2",
                    "One Type 1, One Type 3",
                    "One Type 2, One Type 1",
                    "Two pieces of Type 2",
                    "One Type 2, One Type 3",
                    "One Type 3, One Type 1",
                    "One Type 3, One Type 2",
                    "Two pieces of Type 3"
            };

            for (int i = 0; i < 9; i++) {
                double stockUsed = z[i].get(GRB.DoubleAttr.X);
                double wastage = wastages[i];

                totalWastage += stockUsed * wastage;
                totalStockUsed += stockUsed;

                System.out.printf("%-30s %-15.2f\n", patterns[i], stockUsed);
            }
            System.out.println("============================================");

            System.out.println("Total Stock Used: " + totalStockUsed);
            System.out.printf("Total Wastage: %.2f cm\n", totalWastage);
            System.out.printf("Objective Value (Total Stock Pieces + Wastage): %.2f\n", model.get(GRB.DoubleAttr.ObjVal));

            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            e.printStackTrace();
        }
    }
}
