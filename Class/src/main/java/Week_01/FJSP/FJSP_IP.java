package Basics.FJSP;
import com.gurobi.gurobi.*;
import java.util.ArrayList;
import java.util.List;

public class FJSP_IP {
    public static void demo(){
        System.out.println("Mantu (2321006)");
    }

    public static void main(String[] args) {
        int n = 3; // no. of jobs
        int m = 3; // no. of machines
        int o = 5; // no. of operations

        //Job definitions - adding jobs and their corresponding operations
        List<List<Integer>> jobs = new ArrayList<List<Integer>>(); // job definitions
        List<Integer> OpSeq = new ArrayList<Integer>();
        //adding first job definition
        OpSeq.add(0);
        OpSeq.add(1);
        jobs.add(OpSeq);
        //adding second job definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(2);
        OpSeq.add(3);
        jobs.add(OpSeq);
        //adding third job definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(4);
        jobs.add(OpSeq);

        System.out.println(jobs);

        //Operation definitions - adding operations and their eligible machines
        List<List<Integer>> operations = new ArrayList<List<Integer>>(); // operation definitions
        //adding first operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(0);
        operations.add(OpSeq);
        //adding second operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(1);
        OpSeq.add(2);
        operations.add(OpSeq);
        //adding third operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(0);
        operations.add(OpSeq);
        //adding fourth operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(1);
        OpSeq.add(2);
        operations.add(OpSeq);
        //adding fifth operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(1);
        OpSeq.add(2);
        operations.add(OpSeq);

        System.out.println(operations);

        //processing times definition
        //int [][] processing_times = new int[5][3]; //5 operations, 3 machines
        int[][] processing_times = {{3, 1000, 1000}, {1000, 2, 2}, {2, 1000, 1000}, {1000, 4, 4}, {1000, 3, 3}};
        System.out.println(processing_times); // displays the memory location
        for (int i = 0; i < o; i++) {
            for (int j = 0; j < n; j++) {
                System.out.print(processing_times[i][j] + "\t");
            }
            System.out.print("\n");
        }

        //IP Solver //no more explicit dealing with data
        //saving solutions (from GUROBI into separate dataset)
        int[][] A = new int[o][m];
        int[][] B = new int[o][o];
        double[] T = new double[o];
        double CMAX;

        //GUROBI  solver
        try {
            // Create empty environment, set options, and start
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "FJSP.log");
            env.start();

            // Create empty model
            GRBModel model = new GRBModel(env);

            //for big M
            double H = 6000;

            //defining GUROBI variables
            GRBVar[][] a = new GRBVar[o][m]; //alpha_i_k
            for (int i = 0; i < o; i++) {
                for (int j = 0; j < m; j++) {
                    a[i][j] = model.addVar(0, 1, 1, GRB.BINARY, "a" + Integer.toString(i) + "_" + Integer.toString(j));
                }
            }

            GRBVar[][] b = new GRBVar[o][o]; //beta_i_i'
            for (int i = 0; i < o; i++) {
                for (int j = 0; j < o; j++) {
                    b[i][j] = model.addVar(0, 1, 1, GRB.BINARY, "b" + Integer.toString(i) + "_" + Integer.toString(j));
                }
            }

            GRBVar[] t = new GRBVar[o]; //start time variables t_i
            for (int i = 0; i < o; i++) {
                t[i] = model.addVar(0, H, 0, GRB.CONTINUOUS, "t" + Integer.toString(i));
            }

            //cmax
            GRBVar cmax = model.addVar(0, H, 0, GRB.CONTINUOUS, "cmax");

            //defining constraints

            //contsraint 1: every operation is assigned to exactly one eligible machine
            for (int i = 0; i < o; i++) { //for each operation
                GRBLinExpr c1 = new GRBLinExpr();
                for (Integer elg_mach : operations.get(i)) { //sum over all the eligible machines
                    c1.addTerm(1, a[i][elg_mach]);
                }
                //for(int k=0;k<operations.get(i).size();k++){
                //    c1.addTerm(1, a[i][operations.get(i).get(k)-1]);

                // }
                model.addConstr(c1, GRB.EQUAL, 1, "c1" + Integer.toString(i));
            }

            //constarint 6:
            for (int i = 0; i < o; i++) {
                GRBLinExpr c6 = new GRBLinExpr();
                c6.addTerm(1, t[i]);
                for (Integer elg_mach : operations.get(i)) {
                    c6.addTerm(processing_times[i][elg_mach], a[i][elg_mach]);
                }
                c6.addTerm(-1, cmax);
                model.addConstr(c6, GRB.LESS_EQUAL, 0, "c6" + Integer.toString(i));
            }

            /* constraint 3: start time of the operation is greater than the start time of its predecessor */
            for (int j = 0; j < n; j++) { //for each job
                for (int i = 1; i < jobs.get(j).size(); i++) {
                    GRBLinExpr c3 = new GRBLinExpr(); //for all operation except for the first one in the sequence
                    c3.addTerm(1, t[jobs.get(j).get(i - 1)]); //add start time of operation in (i-1)th position in job's sequence
                    c3.addTerm(-1, t[jobs.get(j).get(i)]); //add start time of operation in (i)th position in job's sequence

                    for (Integer mach : operations.get(jobs.get(j).get(i - 1))) {
                        c3.addTerm(processing_times[jobs.get(j).get(i - 1)][mach], a[jobs.get(j).get(i - 1)][mach]);
                        model.addConstr(c3, GRB.LESS_EQUAL, 0, "c3" + Integer.toString(mach));
                    }
                }
            }

            for (int i = 0; i < o; i++) {
                for (int j = 0; j < o; j++) {
                    if (i != j) {
                        System.out.print(operations.get(i) + "," + operations.get(j));
                        List<Integer> intersection0 = new ArrayList<>(operations.get(i));
                        intersection0.retainAll(operations.get(j));
                        for (Integer k : intersection0) {
                            GRBLinExpr c4 = new GRBLinExpr();
                            c4.addConstant(processing_times[j][k]);
                            c4.addTerm(1, t[j]);
                            c4.addTerm(-1, t[i]);
                            c4.addConstant(-2 * H);
                            c4.addTerm(H, a[i][k]);
                            c4.addTerm(H, a[j][k]);
                            c4.addTerm(-H, b[i][j]);
                            model.addConstr(c4, GRB.LESS_EQUAL, 0, "c4_" + i + "_" + j + "_" + k);
                        }

                    }

                }
            }

            /*for(int i=0;i<o;i++) {
                for (int j = 0; j < o; j++) {
                    if (i != j) {
                        for (Integer mi : operations.get(i)) {
                            for (Integer mj : operations.get(j)) {
                                if (mi == mj) {
                                    GRBLinExpr c4a = new GRBLinExpr();
                                    c4a.addTerm(1, t[j]);
                                    c4a.addTerm(-1, t[i]);
                                    c4a.addTerm(H, a[i][mi]);
                                    c4a.addTerm(H, a[j][mi]);
                                    c4a.addTerm(-H, b[i][j]);
                                    model.addConstr(c4a, GRB.LESS_EQUAL, 2 * H - processing_times[j][mi], "c4_" + i + "_" + j);
                                }
                            }
                        }
                    }
                }
            }*/

            for (int i = 0; i < o; i++) {
                for (int j = 0; j < o; j++) {
                    if (i != j) {
                        for (Integer mi : operations.get(i)) {
                            for (Integer mj : operations.get(j)) {
                                if (mi == mj) {
                                    GRBLinExpr c5a = new GRBLinExpr();
                                    c5a.addTerm(1, t[i]);
                                    c5a.addTerm(-1, t[j]);
                                    c5a.addTerm(H, a[i][mi]);
                                    c5a.addTerm(H, a[j][mi]);
                                    c5a.addTerm(-H, b[i][j]);
                                    model.addConstr(c5a, GRB.LESS_EQUAL, 3 * H - processing_times[i][mi], "c4_" + i + "_" + j);
                                }
                            }
                        }
                    }
                }
            }


            /*for(int i=0;i<o;i++){
                for (int j=0;j<o;j++){
                    if(i != j){
                        List<Integer> intersection1 = new ArrayList<>(operations.get(i));
                        intersection1.retainAll(operations.get(j));

                        for(Integer k : intersection1){
                            GRBLinExpr c5 = new GRBLinExpr();
                            c5.addConstant(processing_times[i][k]);
                            c5.addTerm(1,  t[i]);
                            c5.addTerm(-1, t[j]);
                            c5.addConstant(-3*H);
                            c5.addTerm(H, a[i][k]);
                            c5.addTerm(H, a[j][k]);
                            c5.addTerm(-H, b[i][j]);
                            model.addConstr(c5, GRB.LESS_EQUAL, 0, "c4_");
                        }

                    }

                }
            }*/

            for (int i = 0; i < o; i++) {
                for (int j = 0; j < o; j++) {
                    GRBLinExpr c7 = new GRBLinExpr();
                    c7.addTerm(1, b[i][j]);
                    c7.addTerm(1, b[j][i]);
                    c7.addConstant(-1);
                    model.addConstr(c7, GRB.LESS_EQUAL, 0, "c7");
                }
            }

            //objective function
            GRBLinExpr obj = new GRBLinExpr();
            obj.addTerm(1, cmax);

            //model sense
            model.setObjective(obj, GRB.MINIMIZE);
            model.optimize();

            System.out.print("A\n");
            if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
                for (int i = 0; i < o; i++) {
                    for (int k = 0; k < m; k++) {
                        A[i][k] = (int) a[i][k].get(GRB.DoubleAttr.X);
                        System.out.print(A[i][k] + ",");
                    }
                    System.out.print("\n");
                }
            }

            System.out.print("B\n");
            if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
                for (int i = 0; i < o; i++) {
                    for (int k = 0; k < o; k++) {
                        B[i][k] = (int) b[i][k].get(GRB.DoubleAttr.X);
                        System.out.print(B[i][k] + ",");
                    }
                    System.out.print("\n");
                }
            }

            System.out.print("Time\n");
            if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
                for (int i = 0; i < o; i++) {
                    T[i] = t[i].get(GRB.DoubleAttr.X);
                    System.out.print(T[i] + ",");
                }
                System.out.print("\n");
            }

            System.out.print("CMAX\n");

            if (model.get(GRB.IntAttr.Status) == GRB.Status.OPTIMAL) {
                CMAX = cmax.get(GRB.DoubleAttr.X);
                System.out.print(CMAX + ",");
                System.out.print("\n");
                model.write("FJSP_IP.lp");
            } else {
                model.computeIIS();
                model.write("FJSP_IP_contradictive.lp");
            }

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }

    }
}