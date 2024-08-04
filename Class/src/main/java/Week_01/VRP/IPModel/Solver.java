package Basics.VRP.IPModel;

import VRP.model.Arc;
import VRP.model.Node;
import VRP.model.Request;
import VRP.model.Truck;
import com.gurobi.gurobi.*;

import java.util.ArrayList;
import java.util.List;

public class Solver {

    public static double GRBSolver(List<Request> requests, List<Truck> trucks, List<Node> nodes, List<Arc> arcs){

        double Cmax=100;
        double M=100;

         try{

             GRBEnv env = new GRBEnv();
             GRBModel model = new GRBModel(env);
             model.set(GRB.StringAttr.ModelName,"VRP_IP");

             //binary variable for every arc created per truck
             ArrayList<ArrayList<GRBVar>> xijk =new ArrayList<ArrayList<GRBVar>>();
             for(int i=0;i<trucks.size();i++){
                 ArrayList<GRBVar> xij = new ArrayList<GRBVar>();
                 for(int j=0;j<arcs.size();j++){
                     xij.add(model.addVar(0,1,1,GRB.BINARY,"x_"+Integer.toString(arcs.get(j).n1)+"_"+Integer.toString(arcs.get(j).n1)+"_"+Integer.toString(i)));
                 }
                 xijk.add(xij);
             }

             //continuous vars for visit time of each node by each truck
             ArrayList<ArrayList<GRBVar>> tik = new ArrayList<ArrayList<GRBVar>>();
             for(int i=0;i<trucks.size();i++){
                 ArrayList<GRBVar> ti = new ArrayList<GRBVar>();
                 for(int j=0;j<nodes.size();j++){
                     ti.add(model.addVar(0,M,1,GRB.CONTINUOUS,"tik_"+Integer.toString(j)+"_"+Integer.toString(i)));
                 }
                 tik.add(ti);
             }

             //C_max
             GRBVar cmax = model.addVar(0,M,1,GRB.CONTINUOUS,"cmax");


             //c1: each truck starts from its origin location
             for(Truck truck: trucks){
                 GRBLinExpr c1 = new GRBLinExpr();
                 for(int i=0;i<arcs.size();i++){
                     if(arcs.get(i).n1==truck.origin){
                         c1.addTerm(1,xijk.get(truck.truck_id).get(i));
                     }
                 }
                 model.addConstr(c1,GRB.EQUAL,1,"c1");
             }

             /*//c2: each truck must reach at its destination
             //----
             //----

             //c3: flow preservation
             for(Truck truck: trucks){//for each truck
                 for(Request req: requests){//for each request node
                     GRBLinExpr c3 = new GRBLinExpr();
                     for(int i=0;i<arcs.size();i++){
                        if(arcs.get(i).n2== req.node_id){
                            c3.addTerm(1,xijk.get(truck.truck_id).get(i));
                        }
                     }

                     for(int i=0;i<arcs.size();i++){
                         if(arcs.get(i).n1== req.node_id){
                             c3.addTerm(-1,xijk.get(truck.truck_id).get(i));
                         }
                     }

                     model.addConstr(c3,GRB.EQUAL,0,"c3");
                 }
             }

             //c4: each request is served by an eligible truck
             //-----------


             //c6: if an arc (i,j) is taken by a truck, the time at jth node is ...
             for(int i=0; i<arcs.size();i++){
                 for(Truck truck: trucks){
                     GRBLinExpr c6 = new GRBLinExpr();
                     c6.addTerm(1,tik.get(truck.truck_id).get(arcs.get(i).n1));
                     c6.addTerm(-1,tik.get(truck.truck_id).get(arcs.get(i).n2));
                     c6.addTerm(M,xijk.get(truck.truck_id).get(i));
                     //------------------------
                 }
             }

             //c7: operation precedence constraints
             //custom constraints based on operation precedence
             for(Truck truck: trucks){
                 GRBLinExpr c7 = new GRBLinExpr();
                 c7.addTerm(-1,tik.get(truck.truck_id).get(requests.get(0).node_id));
                 c7.addTerm(1,tik.get(truck.truck_id).get(requests.get(1).node_id));
                 model.addConstr(c7,GRB.GREATER_EQUAL,requests.get(0).process_time,"c7");
             }

             //write precedence constraints for other jobs

             //c8: cmax def*/


             //Objective function
             GRBLinExpr obj = new GRBLinExpr();
             obj.addTerm(1, cmax);
             //model sense
             model.setObjective(obj,GRB.MINIMIZE);
             model.optimize();

             if(model.get(GRB.IntAttr.Status) ==GRB.Status.OPTIMAL){
                 for(Truck truck: trucks){
                     for(int i=0; i<nodes.size();i++){
                         System.out.print(i+":"+tik.get(truck.truck_id).get(i).get(GRB.DoubleAttr.X)+",\t");
                     }
                     System.out.println();
                 }
                 System.out.println();
                 System.out.println();

                 for(Truck truck: trucks){
                     for(int i=0; i<arcs.size();i++){
                         System.out.print(arcs.get(i).n1+"-"+arcs.get(i).n2+":"+xijk.get(truck.truck_id).get(i).get(GRB.DoubleAttr.X)+",\t");
                     }
                     System.out.println();//System.out.println();
                 }
             }

             Cmax=cmax.get(GRB.DoubleAttr.X);

         }catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }
         return(Cmax);
    }
}
