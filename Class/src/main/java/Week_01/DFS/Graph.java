package DFS;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Graph {
    public int n;
    public List<Vertex> vertices = new ArrayList<Vertex>();

    public Graph(int n,  List<Vertex> vertices){
        this.n=n;
        this.vertices=vertices;

    }

    public static double GetDistances(Vertex v1, Vertex v2){
        double [][] dist ={{0,5,2,1,4,2},{5,0,1,3,6,3},{2,1,0,2,5,1},{1,3,2,0,4,6},{4,6,5,4,0,5},{2,3,2,6,5,0}};
        return(dist[v1.vertex_no][v2.vertex_no]);
    }

}
