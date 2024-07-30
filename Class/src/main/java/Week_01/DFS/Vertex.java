package DFS;

import java.util.LinkedList;

public class Vertex {
    public int vertex_no;
    public int visited;
    public LinkedList<Vertex> neighbours =new LinkedList<Vertex>();

    public Vertex(int i,int visited,LinkedList<Vertex> nbrs ){
        this.vertex_no=i;
        this.visited=visited;
        this.neighbours=nbrs;
    }
}
