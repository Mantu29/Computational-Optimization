package TSPHeuristicsEndTerm.LS;

import TSPHeuristicsEndTerm.model.TSPProblem;

import java.util.Random;
import java.util.Arrays;

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
        current_tour_length=current_tour_length+tsp.distance_matrix[current_tour[tsp.n-1]][current_tour[0]];
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
            current_tour_length=current_tour_length+tsp.distance_matrix[current_tour[tsp.n-1]][current_tour[0]];

            if(current_tour_length<best_tour_length){
                best_tour_length = current_tour_length;
                for(int i = 0; i < tsp.n; i++){
                    best_tour[i] = current_tour[i];
                }
            }else{
                temp=current_tour[pos1];
                current_tour[pos1]=current_tour[pos2];
                current_tour[pos2]=temp;
            }

            System.out.println("Current Tour Length: " + current_tour_length + " -- "+" Best Tour Lenth: " + best_tour_length);
        }

        System.out.println("Best Tour is: " + Arrays.toString(best_tour));
    }
}
