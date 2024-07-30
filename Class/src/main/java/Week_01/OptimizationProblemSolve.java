package Basics.ProjectCode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OptimizationProblemSolve {

    static class Process {
        int id;
        Double processingTime; // Using Double to allow null values
        Integer eligibleMachine; // Using Integer to allow null values

        Process(int id) {
            this.id = id;
            this.processingTime = null;
            this.eligibleMachine = null;
        }

        void updateProcess(int eligibleMachine, double processingTime) {
            this.eligibleMachine = eligibleMachine;
            this.processingTime = processingTime;
        }
    }

    static class Job {
        String jobType;
        List<List<Process>> steps;

        Job(String jobType) {
            this.jobType = jobType;
            this.steps = new ArrayList<>();
        }

        void addStep(List<Process> stepProcesses) {
            this.steps.add(stepProcesses);
        }
    }

    public static List<Process> initializeProcesses(int n) {
        List<Process> processes = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            processes.add(new Process(i));
        }
        return processes;
    }

    public static void updateProcessesFromCSV(List<Process> processes, String filePath, String[][] updates) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            List<String[]> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line.replaceAll(",+$", "").split(","));
            }

            for (String[] update : updates) {
                int processId = Integer.parseInt(update[0]);
                String updateCode = update[1];
                int rowIndex = Integer.parseInt(updateCode.substring(0, updateCode.length() - 1)) - 1; // 1-based to 0-based index
                String subCode = updateCode.substring(updateCode.length() - 1);

                if (rowIndex < lines.size()) {
                    String[] values = lines.get(rowIndex);
                    if (subCode.equals("A")) {
                        int eligibleMachine1 = Integer.parseInt(values[2]);
                        double processingTime1 = Double.parseDouble(values[3]);
                        processes.get(processId - 1).updateProcess(eligibleMachine1, processingTime1);
                    } else if (subCode.equals("B") && values.length > 4) {
                        int eligibleMachine2 = Integer.parseInt(values[4]);
                        double processingTime2 = Double.parseDouble(values[5]);
                        processes.get(processId - 1).updateProcess(eligibleMachine2, processingTime2);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        int numberOfProcesses = 45; // Total number of processes

        // Define the number of jobs for each type
        int numberOfJobA = 2;
        int numberOfJobB = 1;

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
        List<Job> jobList = new ArrayList<>();

        // Create jobs of type "A"
        for (int i = 0; i < numberOfJobA; i++) {
            Job jobA = new Job("A");
            int[][] stepsA = {
                    {1}, {3}, {4}, {5}, {6, 7}, {8, 9}, {36, 37}, {10, 11, 12, 13},
                    {14, 15, 16, 17}, {18, 19}, {20, 21, 22, 23}, {24, 25, 26, 27},
                    {28, 29}, {32, 33, 34, 35}, {36, 37}, {38, 39, 40, 41}, {6, 7},
                    {42, 43}, {44}
            };
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
            int[][] stepsB = {
                    {2}, {3}, {4}, {5}, {6, 7}, {8, 9}, {36, 37}, {10, 11, 12, 13},
                    {24, 25, 26, 27}, {28, 29}, {32, 33, 34, 35}, {36, 37}, {38, 39, 40, 41},
                    {6, 7}, {42, 43}, {45}
            };
            for (int[] step : stepsB) {
                List<Process> stepProcesses = new ArrayList<>();
                for (int processId : step) {
                    stepProcesses.add(processesB.get(processId - 1)); // processId - 1 because list is 0-based
                }
                jobB.addStep(stepProcesses);
            }
            jobList.add(jobB);
        }

        // Update processes for each job based on its type
        for (Job job : jobList) {
            if (job.jobType.equals("A")) {
                updateProcessesFromCSV(processesA, "C://Users/mantu/Downloads/seqA.csv", updatesA);
            } else if (job.jobType.equals("B")) {
                updateProcessesFromCSV(processesB, "C://Users/mantu/Downloads/seqB.csv", updatesB);
            }
        }

        // Print the job details to verify
        for (Job job : jobList) {
            System.out.println("Job Type: " + job.jobType);
            int stepNumber = 1;
            for (List<Process> step : job.steps) {
                System.out.print("Step " + stepNumber + ": ");
                for (Process process : step) {
                    System.out.println("Process ID: " + process.id + ", Eligible Machine: " + process.eligibleMachine + ", Processing Time: " + process.processingTime);
                }
                stepNumber++;
            }
        }

        // TODO: Add code to update the processes with data and Gurobi optimization code here
    }
}
