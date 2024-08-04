package VRP.io;

import VRP.model.Arc;
import VRP.model.Node;
import VRP.model.Request;
import VRP.model.Truck;

import java.util.ArrayList;
import java.util.List;

public class ProblemReader {

    public static void read(List<Request> requests, List<Truck> trucks, List<Node> nodes, List<Arc> arcs){

        int node_id=0;

        //creating trucks
        for(int i=0;i<3;i++){
            //create origin node
            Node origin_node = new Node(node_id, "origin node", 0);
            nodes.add(origin_node);node_id++;
            //create destination node
            Node dest_node = new Node(node_id, "dest node", 0);
            nodes.add(dest_node);node_id++;

            //now we can create the truck object
            Truck t= new Truck(i, origin_node.node_id, dest_node.node_id);
            trucks.add(t);
        }

        //creating requests


        List<Integer> eligible_trucks = new ArrayList<Integer>();//
        int oper_id=0;

        //custom creating requests
        //oper 0
        Node oper_node = new Node(node_id,"operation node",3);//creating node for the corresponding operation
        nodes.add(oper_node);node_id++;
        eligible_trucks=new ArrayList<Integer>();
        //add eligible trucks for request/operation 0
        eligible_trucks.add(0);
        Request request =new Request(oper_id,oper_node.processing_time,oper_node.node_id,eligible_trucks);
        requests.add(request);
        oper_id++;

        //oper 1
        oper_node = new Node(node_id,"operation node",2);//creating node for the corresponding operation
        nodes.add(oper_node);node_id++;
        eligible_trucks=new ArrayList<Integer>();
        //add eligible trucks for request/operation 0
        eligible_trucks.add(1);
        eligible_trucks.add(2);
        request =new Request(oper_id, oper_node.processing_time,oper_node.node_id, eligible_trucks);
        requests.add(request);
        oper_id++;

        //oper 2
        oper_node = new Node(node_id,"operation node",2);//creating node for the corresponding operation
        nodes.add(oper_node);node_id++;
        eligible_trucks=new ArrayList<Integer>();
        //add eligible trucks for request/operation 0
        eligible_trucks.add(0);
        request =new Request(oper_id, oper_node.processing_time,oper_node.node_id, eligible_trucks);
        requests.add(request);
        oper_id++;

        //oper 3
        oper_node = new Node(node_id,"operation node",4);//creating node for the corresponding operation
        nodes.add(oper_node);node_id++;
        eligible_trucks=new ArrayList<Integer>();
        //add eligible trucks for request/operation 0
        eligible_trucks.add(1);
        eligible_trucks.add(2);
        request =new Request(oper_id, oper_node.processing_time,oper_node.node_id, eligible_trucks);
        requests.add(request);
        oper_id++;

        //oper 4
        oper_node = new Node(node_id,"operation node",3);//creating node for the corresponding operation
        nodes.add(oper_node);node_id++;
        eligible_trucks=new ArrayList<Integer>();
        //add eligible trucks for request/operation 0
        eligible_trucks.add(1);
        eligible_trucks.add(2);
        request =new Request(oper_id, oper_node.processing_time,oper_node.node_id, eligible_trucks);
        requests.add(request);
        oper_id++;

        //create all other requests
        //--------


        //creating arcs
        //arcs from origin nodes to all other request nodes
        for(Truck truck: trucks){
            for(Request req: requests){
                if(req.eligible_trucks.contains(truck.truck_id)){
                    Arc arc = new Arc(truck.origin,req.node_id,-1);
                    arcs.add(arc);
                }
            }
        }
        //add arcs from origin to its destination nodes
        for(Truck truck: trucks){
            Arc arc = new Arc(truck.origin, truck.dest,-1);
            arcs.add(arc);
        }

        //arcs between all request nodes, no self loops
        for(Request req1: requests){
            for(Request req2: requests){

                int elg=0;
                for(Integer mach: req1.eligible_trucks){
                    if(req2.eligible_trucks.contains(mach)){
                        elg=1;break;
                    }
                }
                if((req1.node_id != req2.node_id) && elg==1){
                    Arc arc = new Arc(req1.node_id, req2.node_id,-1);
                    arcs.add(arc);
                }
            }
        }

        //arcs from request nodes to destination nodes
        for(Truck truck: trucks){
            for(Request req: requests){
                if(req.eligible_trucks.contains(truck.truck_id)){
                    Arc arc = new Arc(req.node_id,truck.dest,-1);
                    arcs.add(arc);
                }
            }
        }
    }
}
