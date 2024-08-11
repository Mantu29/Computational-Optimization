package TSPHeuristics;

import TSPHeuristics.LS.TSPLocalSearch;
import TSPHeuristics.io.TSPProblemReader;
import TSPHeuristics.model.TSPProblem;

public class TSPSolver {

    public static void main(String[] args){
        String tsp_file ="TSP_instance51.txt";
        TSPProblem tsp= TSPProblemReader.read(tsp_file);

        //print tsp matrix
        for(int i=0;i<tsp.n;i++){
            for(int j=0;j<tsp.n;j++){
                System.out.println(tsp.distance_matrix[i][j]+"\t");
            }
            System.out.println("i:"+(i+1));
        }

        //TSPLocalSearch.LS(tsp,100000);

    }
}
