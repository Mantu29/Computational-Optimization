package TSPHeuristics.model;

public class TSPProblem {
    public int n;
    public double [][] distance_matrix;
    public int[] optimal_tour;
    public double optimal_tour_length;

    public TSPProblem(int n, double[][] distance_matrix, int[] optimal_tour, double optimal_tour_length){

        this.n=n;
        this.distance_matrix=distance_matrix;
        this.optimal_tour=optimal_tour;
        this.optimal_tour_length=optimal_tour_length;
    }
}
