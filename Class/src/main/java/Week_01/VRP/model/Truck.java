package VRP.model;

public class Truck {
    public int truck_id;
    public int origin;//node id of the origin node of this truck
    public int dest;//node id of the destination node of this truck

    public Truck(int machine_id, int origin, int dest){
        this.truck_id = machine_id;
        this.origin=origin;
        this.dest=dest;
    }
}
