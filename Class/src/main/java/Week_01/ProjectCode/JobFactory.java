package Basics.ProjectCode;

import java.util.ArrayList;
import java.util.List;

public class JobFactory {

    public static List<Process> initializeProcesses(int n) {
        List<Process> processes = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            processes.add(new Process(i));
        }
        return processes;
    }

    public static List<Job> createAndInitializeJobs(int numberOfProcesses, int numberOfJobA, int numberOfJobB) {
        // Initialize processes for Job A
        List<Process> processesA = initializeProcesses(numberOfProcesses);
        // Initialize processes for Job B
        List<Process> processesB = initializeProcesses(numberOfProcesses);

        // Define updates for Job A
        String[][] updatesA = {
                {"1", "1A"}, {"3", "2A"}, {"4", "3A"}, {"5", "4A"}, {"6", "5A"}, {"7", "5B"},
                {"8", "6A"}, {"9", "7A"}, {"10", "9A"}, {"11", "9B"}, {"12", "10A"}, {"13", "10B"},
                {"14", "11A"}, {"15", "11B"}, {"16", "12A"}, {"17", "12B"}, {"18", "13A"}, {"19", "13B"},
                {"20", "14A"}, {"21", "14B"}, {"22", "15A"}, {"23", "15B"}, {"24", "16A"}, {"25", "16B"},
                {"26", "17A"}, {"27", "17B"}, {"28", "18A"}, {"29", "18B"}, {"32", "19A"}, {"33", "19B"},
                {"34", "20A"}, {"35", "20B"}, {"36", "21A"}, {"37", "21B"}, {"38", "22A"}, {"39", "22B"},
                {"40", "23A"}, {"41", "23B"}, {"42", "25A"}, {"43", "26A"}, {"44", "27A"}
        };

        // Define updates for Job B
        String[][] updatesB = {
                {"2", "1A"}, {"3", "2A"}, {"4", "3A"}, {"5", "4A"}, {"6", "5A"}, {"7", "5B"},
                {"8", "6A"}, {"9", "7A"}, {"10", "9A"}, {"11", "9B"}, {"12", "10A"}, {"13", "10B"},
                {"24", "11A"}, {"25", "11B"}, {"26", "12A"}, {"27", "12B"}, {"28", "13A"}, {"29", "13B"},
                {"32", "14A"}, {"33", "14B"}, {"34", "15A"}, {"35", "15B"}, {"36", "16A"}, {"37", "16B"},
                {"38", "17A"}, {"39", "17B"}, {"40", "18A"}, {"41", "18B"}, {"42", "20A"}, {"43", "21A"},
                {"45", "22A"}
        };

        // Create a list of jobs
        List<Job> jobList = createJobs(numberOfJobA, numberOfJobB, processesA, processesB);

        // Update processes for each job based on its type
        for (Job job : jobList) {
            if (job.getJobType().equals("A")) {
                CSVUtils.updateProcessesFromCSV(processesA, "C:\\Users\\mantu\\OneDrive\\Documents\\GitHub\\Computational-Optimization\\Class\\src\\main\\java\\Week_01\\ProjectCode\\seqA.csv", updatesA);
            } else if (job.getJobType().equals("B")) {
                CSVUtils.updateProcessesFromCSV(processesB, "C:\\Users\\mantu\\OneDrive\\Documents\\GitHub\\Computational-Optimization\\Class\\src\\main\\java\\Week_01\\ProjectCode\\seqB.csv", updatesB);
            }
        }

        return jobList;
    }

    public static List<Job> createJobs(int numberOfJobA, int numberOfJobB, List<Process> processesA, List<Process> processesB) {
        List<Job> jobList = new ArrayList<>();

        // Define steps for job type "A"
        int[][] stepsA = {
                {1}, {3}, {4}, {5}, {6, 7}, {8, 9}, {36, 37}, {10, 11, 12, 13},
                {14, 15, 16, 17}, {18, 19}, {20, 21, 22, 23}, {24, 25, 26, 27},
                {28, 29}, {32, 33, 34, 35}, {36, 37}, {38, 39, 40, 41}, {6, 7},
                {42, 43}, {44}
        };

        // Define steps for job type "B"
        int[][] stepsB = {
                {2}, {3}, {4}, {5}, {6, 7}, {8, 9}, {36, 37}, {10, 11, 12, 13},
                {24, 25, 26, 27}, {28, 29}, {32, 33, 34, 35}, {36, 37}, {38, 39, 40, 41},
                {6, 7}, {42, 43}, {45}
        };

        // Create jobs of type "A"
        for (int i = 0; i < numberOfJobA; i++) {
            Job jobA = new Job("A");
            for (int[] step : stepsA) {
                List<Process> stepProcesses = new ArrayList<>();
                for (int processId : step) {
                    stepProcesses.add(processesA.get(processId - 1)); // processId - 1 because list is 0-based
                }
                jobA.addStep(stepProcesses);
            }
            jobList.add(jobA);
        }

        // Create jobs of type "B"
        for (int i = 0; i < numberOfJobB; i++) {
            Job jobB = new Job("B");
            for (int[] step : stepsB) {
                List<Process> stepProcesses = new ArrayList<>();
                for (int processId : step) {
                    stepProcesses.add(processesB.get(processId - 1)); // processId - 1 because list is 0-based
                }
                jobB.addStep(stepProcesses);
            }
            jobList.add(jobB);
        }

        return jobList;
    }

    public static List<Operation> createOperationsFromJobs(List<Job> jobList) {
        List<Operation> operations = new ArrayList<>();

        for (Job job : jobList) {
            int stepNumber = 1;
            for (List<Process> step : job.getSteps()) {
                operations.add(new Operation(job.getJobType(), stepNumber, step));
                stepNumber++;
            }
        }

        return operations;
    }

}
