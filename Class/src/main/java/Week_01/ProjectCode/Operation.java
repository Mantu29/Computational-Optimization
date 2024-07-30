package Basics.ProjectCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Operation {
    String jobType;
    int stepNumber;
    List<Process> processes;
    List<Integer> eligibleMachines;

    public Operation(String jobType, int stepNumber, List<Process> processes) {
        this.jobType = jobType;
        this.stepNumber = stepNumber;
        this.processes = processes;
        this.eligibleMachines = getUniqueEligibleMachines(processes);
    }

    public String getJobType() {
        return jobType;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public List<Process> getProcesses() {
        return processes;
    }

    public List<Integer> getEligibleMachines() {
        return eligibleMachines;
    }

    private List<Integer> getUniqueEligibleMachines(List<Process> processes) {
        Set<Integer> machineSet = new HashSet<>();
        for (Process process : processes) {
            machineSet.addAll(process.getEligibleMachines());
        }
        return new ArrayList<>(machineSet);
    }
}
