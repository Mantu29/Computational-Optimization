package VRP.model;

import java.util.List;

public class Request {

    public int oper_id;
    public double process_time;
    public int node_id;
    public List<Integer> eligible_trucks;

    public Request(int oper_id, double process_time, int node_id,  List<Integer> eligible_trucks){
        this.oper_id = oper_id;
        this.process_time = process_time;
        this.node_id = node_id;
        this.eligible_trucks=eligible_trucks;
    }
}
