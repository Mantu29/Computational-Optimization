package CGCuttingStock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ColumnGeneration {

    public static void main(String[] args){

        int [] weights =  new int[] {81,70,68};
        int [] demands =  new int[] {44,3,48};
        double stockLength = 200;
        int n=3;

        ArrayList<ArrayList<Integer>> patterns = new ArrayList<ArrayList<Integer>>();
        //adding initial patters
        patterns.add(new ArrayList<Integer>(Arrays.asList(1,0,0)));
        patterns.add(new ArrayList<Integer>(Arrays.asList(0,1,0)));
        patterns.add(new ArrayList<Integer>(Arrays.asList(0,0,1)));

        System.out.println(patterns);

        int iter=0;
        Boolean is_optimal=false;
        List<Double> dualValues = new ArrayList<Double>();
        dualValues= new ArrayList<Double>(Arrays.asList(-1.0, -1.0, -1.0));
        List<Double> bestBounds = new ArrayList<>();

        while(iter<100 && !is_optimal){

            //create and solve the restricted master problem using current set of patterns and capture the dual values
            //also keep track of the best bound found
            CGCuttingStock.RMP.RMPSolver(n, weights, demands, patterns, bestBounds, dualValues);
            // Create and solve the restricted master problem using current set of patterns
            System.out.println("Best Bounds: " + bestBounds);
            System.out.println("Dual Values: " + dualValues);

            // Create and solve the pricing problem and check if the solution is optimal
            is_optimal = CGCuttingStock.PricingProblem.PricingSolver(n, weights, stockLength, dualValues, patterns);
            System.out.println("Iteration " + iter + " : patterns " + patterns);

            // If not optimal, a new pattern was added, so continue to the next iteration
            iter++;

        }

        System.out.println("Final Patterns: " + patterns);
        System.out.println("Optimal Solution Found: " + is_optimal);
    }

}