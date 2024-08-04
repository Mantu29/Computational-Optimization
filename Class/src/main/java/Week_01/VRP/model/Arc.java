package VRP.model;

public class Arc {
    public int n1; //from node id
    public int n2; //to node id
    public int val;

    public Arc(int n1, int n2, int val){
        this.n1=n1;
        this.n2=n2;
        this.val=val;
    }
}
