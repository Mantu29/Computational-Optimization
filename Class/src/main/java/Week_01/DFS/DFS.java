package DFS;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DFS {

    public static void dfs_tree(Vertex currentVertex){
        currentVertex.visited=1;
        System.out.println("Visiting vertex "+currentVertex.vertex_no);

        for(Vertex nbr: currentVertex.neighbours){
            if(nbr.visited==0){
                dfs_tree(nbr);
            }
        }
    }
    public static void main(String[] args){

        //creating the custom graph
        //creating vertices
        List<Vertex> vertices = new ArrayList<Vertex>();

        for(int i=0;i<7;i++){
            LinkedList<Vertex> empty_nbrs = new LinkedList<Vertex>();
            Vertex v = new Vertex(i,0, empty_nbrs);
            vertices.add(v);
        }

        //adding neighbours -- directed edges
        vertices.get(0).neighbours.add(vertices.get(1));
        vertices.get(0).neighbours.add(vertices.get(2));

        vertices.get(1).neighbours.add(vertices.get(3));
        vertices.get(1).neighbours.add(vertices.get(4));

        vertices.get(2).neighbours.add(vertices.get(5));
        vertices.get(2).neighbours.add(vertices.get(6));

        //now we are ready to define the graph object
        Graph graph = new Graph(7,vertices);

        //write a statement to print the vertex number of the first neighbour of vertex 3 of the graph
        System.out.println(graph.vertices.get(2).neighbours.get(0).vertex_no);

        //write a suitable function call to start the dfs tree exploration process
        dfs_tree(graph.vertices.get(0));
    }
}
