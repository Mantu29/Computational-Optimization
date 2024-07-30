package DFS;

//import org.apache.groovy.groovysh.completion.BackslashEscapeCompleter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

public class TSPBnBDFS {
    public static void tsp_dfs_tree(Graph graph, Vertex currentVertex, Stack<Vertex> vertexStack, List<Integer> BestTour){
        double incumbent=10000;
        double BestBound=0;

        for(int i=0;i<BestTour.size()-1;i++){
            BestBound+= graph.GetDistances(graph.vertices.get(BestTour.get(i)), graph.vertices.get(BestTour.get(i+1)));
            //System.out.println(i+"-"+bestBound);
        }
        BestBound+=graph.GetDistances(graph.vertices.get(BestTour.get(BestTour.size()-1)), graph.vertices.get(BestTour.get(0)));

        currentVertex.visited=1;
        vertexStack.push(currentVertex);
        System.out.println("Visiting vertex "+currentVertex.vertex_no+"--"+vertexStack.size());

        //finding length of the current tour
        incumbent=0;
        if(vertexStack.size()>1){
            for(int i=0;i<vertexStack.size()-1;i++){
                incumbent+= graph.GetDistances(vertexStack.get(i), vertexStack.get(i+1));
            }
            if(vertexStack.size()== graph.n){ // to check if it is a leaf node or not
                incumbent+=graph.GetDistances(vertexStack.get(vertexStack.size()-1), vertexStack.get(0));
            }
        }
        if(vertexStack.size()== graph.n){
            if(incumbent<BestBound){
                BestBound=incumbent;
                for(int i=0;i<vertexStack.size();i++){
                    BestTour.set(i, vertexStack.get(i).vertex_no);
                    System.out.print(vertexStack.get(i).vertex_no);
                }
                System.out.println(incumbent+"--bestTour: "+BestTour);
            }
        }else if(incumbent<BestBound){//greater or equal means pruning by bounds
            for(Vertex nbr: currentVertex.neighbours){
                if(nbr.visited==0){
                    tsp_dfs_tree(graph,nbr,vertexStack,BestTour);
                }
            }
        }

        //updates to be made
        currentVertex.visited=0;
        vertexStack.pop();

    }
    public static void main(String args[]) {

        //creating vertices/cities/nodes
        List<Vertex> vertices = new ArrayList<Vertex>();

        for(int i=0;i<6;i++){
            LinkedList<Vertex> empty_nbrs = new LinkedList<Vertex>();
            Vertex v = new Vertex(i,0, empty_nbrs);
            vertices.add(v);
        }

        //adding neighbours for each vertex
        for(int i=0;i<6;i++){
            for(int j=0;j<6;j++){
                if(i!=j){
                    vertices.get(i).neighbours.add(vertices.get(j));
                }
            }
        }
        //creating graph object
        Graph graph = new Graph(6,vertices);

        //System.out.println(graph.GetDistances(vertices.get(0),vertices.get(5)));

        Stack<Vertex> vertexStack = new Stack<Vertex>(); //current path
        List<Integer> bestTour = new ArrayList<Integer>();
        for(int i=0;i< graph.n;i++){
            bestTour.add(i);
        }
         System.out.println(bestTour.size());

        double bestBound=0;

        tsp_dfs_tree(graph,graph.vertices.get(0),vertexStack, bestTour);

        for(int i=0;i<bestTour.size()-1;i++){
            bestBound+= graph.GetDistances(graph.vertices.get(bestTour.get(i)), graph.vertices.get(bestTour.get(i+1)));
            //System.out.println(i+"-"+bestBound);
        }
        bestBound+=graph.GetDistances(graph.vertices.get(bestTour.get(bestTour.size()-1)), graph.vertices.get(bestTour.get(0)));
        System.out.println(bestBound+"--bestTour: "+bestTour);

    }
}
