package Basics.FJSP;
import com.gurobi.gurobi.*;
import java.util.ArrayList;
import java.util.List;

public class FJSP_IP {
    public static void main(String []args){
        int n=3; // no. of jobs
        int m=3; // no. of machines
        int o=5; // no. of operations

        //Job definitions - adding jobs and their corresponding operations
        List<List<Integer>> jobs = new ArrayList<List<Integer>>(); // job definitions
        List<Integer> OpSeq = new ArrayList<Integer>();
        //adding first job definition
        OpSeq.add(1); OpSeq.add(2);
        jobs.add(OpSeq);
        //adding second job definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(3); OpSeq.add(4);
        jobs.add(OpSeq);
        //adding third job definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(5);
        jobs.add(OpSeq);

        System.out.println(jobs);

        //Operation definitions - adding operations and their eligible machines
        List<List<Integer>> operations = new ArrayList<List<Integer>>(); // operation definitions
        //adding first operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(1);
        operations.add(OpSeq);
        //adding second operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(2); OpSeq.add(3);
        operations.add(OpSeq);
        //adding third operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(1);
        operations.add(OpSeq);
        //adding fourth operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(2);OpSeq.add(3);
        operations.add(OpSeq);
        //adding fifth operation definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(2);OpSeq.add(3);
        operations.add(OpSeq);

        System.out.println(operations);

        //processing times definition
        //int [][] processing_times = new int[5][3]; //5 operations, 3 machines
        int [][] processing_times= {{3, 1000, 1000}, {1000, 2, 2}, {2, 1000, 1000}, {1000, 4, 4}, {1000, 3, 3}};
        System.out.println(processing_times); // displays the memory location
        for(int i=0;i<5;i++){
            for(int j=0;j<3;j++){
                System.out.print(processing_times[i][j]+"\t");
            }
            System.out.print("\n");
        }

        //IP Solver //no more explicit dealing with data
        // saving solutions (from GUROBI into separate dataset)
        int [][]A = new int[o][m];
        int [][]B = new int[o][o];
        double []T = new double[o];
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
            double H = 1000;

            //defining GUROBI variables
            GRBVar[][] a = new GRBVar[o][m]; //alpha_i_k
            for(int i=0;i<o;i++){
                for(int j=0;j<m;j++){
                    a[i][j]=model.addVar(0,1, 1, GRB.BINARY, "a"+Integer.toString(i)+"_"+Integer.toString(j));
                }
            }

            GRBVar [][] b = new GRBVar[o][o]; //beta_i_i'
            for(int i=0;i<o;i++){
                for(int j=0;j<o;j++){
                    b[i][j]=model.addVar(0,1, 1, GRB.BINARY, "b"+Integer.toString(i)+"_"+Integer.toString(j));
                }
            }

            GRBVar [] t = new GRBVar[o]; //start time variables t_i
            for(int i=0;i<o;i++){
                t[i]=model.addVar(0,H, 0, GRB.CONTINUOUS, "t"+Integer.toString(i));
            }

            //Cmax
            GRBVar cmax = model.addVar(0,H,0,GRB.CONTINUOUS,"cmax");

            //Defining constraints

            //Contsraint 1
            for(int i=0;i<o;i++){//for each operation
                GRBLinExpr c1 = new GRBLinExpr();
                for(int k=0;k<operations.get(i).size();k++){
                    c1.addTerm(1,a[i][operations.get(i).get(k)-1]);

                }
                model.addConstr(c1,GRB.EQUAL,1,"c1"+Integer.toString(i));
            }

            model.optimize();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
    }
}
