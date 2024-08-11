package TSPHeuristics.io;

import TSPHeuristics.model.TSPProblem;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class TSPProblemReader {
    public static TSPProblem read(String filename){

        int n; n=0;
        try(BufferedReader inst = new BufferedReader(new FileReader(filename))) {
            String str;

            //counting the number of lines
            while ((str = inst.readLine()) != null) {
                n++;
            }
        }    catch (IOException e) {
            System.out.println("filewrite_error: " + e);
        }

        double[][] distance_matrix = new double[n][n];
        try(BufferedReader inst = new BufferedReader(new FileReader(filename))) {
            String str;

            //declare the distance matrix

            for(int i=0;i<n;i++){
                for(int j=0;j<n;j++){
                    distance_matrix[i][j]=-1;
                }
            }

            //reading the file
            int line=0;
            while ((str = inst.readLine()) != null) {
                String[] ss =str.split("\\s+");

                for(int i=0;i<ss.length;i++){
                    distance_matrix[line][i]=Double.parseDouble(ss[i]);
                }
                line++;
            }

        }catch (IOException e) {
            System.out.println("filewrite_error: " + e);
        }

        int [] optimal_tour = new int[n];
        for(int i=0;i<n;i++){
            optimal_tour[i]=-1;
        }

        double optimal_tour_length=-1;

        TSPProblem tspProblem = new TSPProblem(n,distance_matrix,optimal_tour,optimal_tour_length);
        return(tspProblem);
    }
}
