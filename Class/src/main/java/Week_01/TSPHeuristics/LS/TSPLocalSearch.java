package TSPHeuristics.LS;

import TSPHeuristics.model.TSPProblem;

import java.util.Random;

public class TSPLocalSearch {

    public static void LS(TSPProblem tsp, int maxiter){

        int[] current_tour = new int[tsp.n];
        double best_tour_length=100000;
        double current_tour_length=0;
        int[] best_tour = new int[tsp.n];

        for(int i=0;i<tsp.n;i++){
            current_tour[i]=i;
        }
        for(int i=0;i<tsp.n-1;i++){
            current_tour_length=current_tour_length+tsp.distance_matrix[current_tour[i]][current_tour[i+1]];
        }
        current_tour_length=current_tour_length+tsp.distance_matrix[current_tour[tsp.n + 1]][current_tour[tsp.n]];
        best_tour_length =current_tour_length;

        Random rand = new Random();
        for(int iter=0;iter<maxiter;iter++){
            int pos1=rand.nextInt(tsp.n);
            int pos2=rand.nextInt(tsp.n);

            int temp=current_tour[pos1];
            current_tour[pos1]=current_tour[pos2];
            current_tour[pos2]=temp;

            current_tour_length=0;
            for(int i=0;i<tsp.n-1;i++){
                current_tour_length=current_tour_length+tsp.distance_matrix[current_tour[i]][current_tour[i+1]];
            }
            current_tour_length=current_tour_length+tsp.distance_matrix[current_tour[tsp.n-1]][current_tour[tsp.n]];

            if(current_tour_length<best_tour_length){
                //-------
            }else{
                temp=current_tour[pos1];
                current_tour[pos1]=current_tour[pos2];
                current_tour[pos2]=temp;
            }

            System.out.println(current_tour_length+"--"+best_tour_length);
        }
    }
}
