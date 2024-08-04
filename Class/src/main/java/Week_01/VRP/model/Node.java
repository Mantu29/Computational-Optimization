package VRP.model;

public class Node {
    public int node_id; //index of the node in the list that stores all nodes
    public String info; //what type of a node is it?
    public double processing_time;

    public Node(int node_id, String info, double processing_time){
        this.node_id=node_id;
        this.info=info;
        this.processing_time=processing_time;
    }
}
