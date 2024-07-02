package Basics.FJSP;
import com.gurobi.*;
import com.gurobi.gurobi.*;

import java.util.ArrayList;
import java.util.List;

public class FJSP_IP {
    public static void main(String []args){

        //READING DATA
        int n=3;//no: of jobs
        int m=3;//no: of machines
        int o=5;//no: of operations

        List<List<Integer>> jobs = new ArrayList<List<Integer>>();//job definitions
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

        List<List<Integer>> operations = new ArrayList<List<Integer>>();//operation definitions
        OpSeq = new ArrayList<Integer>();
        //adding first oper definition
        OpSeq.add(1);
        operations.add(OpSeq);
        //adding second oper definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(2); OpSeq.add(3);
        operations.add(OpSeq);
        //adding third oper definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(1);
        operations.add(OpSeq);
        //adding fourth oper definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(2); OpSeq.add(3);
        operations.add(OpSeq);
        //adding fifth oper definition
        OpSeq = new ArrayList<Integer>();
        OpSeq.add(2); OpSeq.add(3);
        operations.add(OpSeq);
        System.out.println(operations);

        int[][] processing_times={{3,1000,1000},{1000,2,2},{2,1000,1000},{1000,4,4},{1000,3,3}};
        for(int i=0;i<5;i++){
            for(int j=0;j<3;j++){
                System.out.print(processing_times[i][j]+"\t");
            }
            System.out.println("\n");
        }

        //IP solver//no more explicit dealing with data
        
        //saving solutions
        int [][] A=new int[o][m];
        int [][]B=new int[o][o];
        double [] T=new double[o];
        double CMAX;
        
        //Gurobi solver
        try {
            GRBEnv env = new GRBEnv(true);
            env.set("logFile", "FJSP.log");
            env.start();

            // Create empty model
            GRBModel model = new GRBModel(env);
            
            //defining gurobi variables
            GRBVar [][] a=new GRBVar[o][m];
            for(int i=0;i<o;i++){
                for(int j=0;j<m;j++){
                    a[i][j]=model.addVar(0,1,1,GRB.BINARY,"a"+Integer.toString(i)+"_"+Integer.toString(j));
                }
            }
            
            
        }catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
        
        
        



    }
}
