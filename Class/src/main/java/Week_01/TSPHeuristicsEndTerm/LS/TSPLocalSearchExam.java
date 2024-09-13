package TSPHeuristicsEndTerm.LS;

import TSPHeuristicsEndTerm.model.TSPProblem;

import java.util.Random;
import java.util.Arrays;

public class TSPLocalSearchExam {

    public static void LS(TSPProblem tsp, int maxiter, int numInitialSolutions){

        Random rand = new Random();
        double bestOverallTourLength = Double.MAX_VALUE;
        int[] bestOverallTour = new int[tsp.n];

        // Generate multiple initial solutions and perform local search on each
        for (int s = 0; s < numInitialSolutions; s++) {
            int[] current_tour = generateRandomTour(tsp.n, rand);
            double current_tour_length = 0;

            // Calculating the initial tour length
            for(int i = 0; i < tsp.n - 1; i++)
                current_tour_length += tsp.distance_matrix[current_tour[i]][current_tour[i + 1]];
            current_tour_length += tsp.distance_matrix[current_tour[tsp.n - 1]][current_tour[0]];

            double best_tour_length = current_tour_length;
            int[] best_tour = Arrays.copyOf(current_tour, tsp.n);

            System.out.println("Starting Local Search for Initial Solution " + (s + 1) + " with initial length: " + current_tour_length);

            for (int iter = 0; iter < maxiter; iter++) {
                int pos1 = rand.nextInt(tsp.n);
                int pos2 = rand.nextInt(tsp.n);

                if (pos1 != pos2) {
                    reverseSegment(current_tour, pos1, pos2);

                    // Recalculate the tour length after the reverse operation
                    current_tour_length = 0;
                    for(int i = 0; i < tsp.n - 1; i++)
                        current_tour_length += tsp.distance_matrix[current_tour[i]][current_tour[i + 1]];
                    current_tour_length += tsp.distance_matrix[current_tour[tsp.n - 1]][current_tour[0]];

                    if (current_tour_length < best_tour_length) {
                        best_tour_length = current_tour_length;
                        System.arraycopy(current_tour, 0, best_tour, 0, tsp.n);
                    } else {
                        reverseSegment(current_tour, pos1, pos2); // revert if no improvement
                    }
                }
            }

            System.out.println("Final Local Search result for Initial Solution " + (s + 1) + ": " + best_tour_length);

            // Updating the best overall tour if found
            if (best_tour_length < bestOverallTourLength) {
                bestOverallTourLength = best_tour_length;
                System.arraycopy(best_tour, 0, bestOverallTour, 0, tsp.n);
            }
        }

        System.out.println("Best Overall Tour: " + Arrays.toString(bestOverallTour) + " with length: " + bestOverallTourLength);
    }

    private static int[] generateRandomTour(int n, Random rand) {
        int[] tour = new int[n];
        for (int i = 0; i < n; i++) {
            tour[i] = i;
        }
        for (int i = 0; i < n; i++) {
            int j = rand.nextInt(n);
            int temp = tour[i];
            tour[i] = tour[j];
            tour[j] = temp;
        }
        return tour;
    }

    private static void reverseSegment(int[] tour, int pos1, int pos2) {
        int start = Math.min(pos1, pos2);
        int end = Math.max(pos1, pos2);
        while (start < end) {
            int temp = tour[start];
            tour[start] = tour[end];
            tour[end] = temp;
            start++;
            end--;
        }
    }
}
