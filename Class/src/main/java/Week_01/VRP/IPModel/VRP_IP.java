package VRP.IPModel;

import Basics.VRP.IPModel.Solver;
import VRP.io.ProblemReader;
import VRP.model.Arc;
import VRP.model.Node;
import VRP.model.Request;
import VRP.model.Truck;

import java.util.ArrayList;
import java.util.List;

public class VRP_IP {

    public static void main(String args []){

        //declaring the data structures
        List<Request> requests = new ArrayList<Request>();
        List<Truck> trucks = new ArrayList<Truck>();
        List<Node> nodes = new ArrayList<Node>();
        List<Arc> arcs = new ArrayList<Arc>();

        int A = 3;
        //function to read the input, pass data structures to initialize data structures
        ProblemReader.read(requests, trucks, nodes, arcs);
        System.out.println(arcs.get(A).n1);
        System.out.println(arcs.get(A).n2);
        System.out.println(arcs.get(A).val);
        //print the destination node of the second arc and verify whether it is correct

        //call the solver function
        double cmax = Solver.GRBSolver(requests, trucks, nodes, arcs);
        System.out.println("Cmax is:" + cmax);

        //print the optimized objective value

    }
}
