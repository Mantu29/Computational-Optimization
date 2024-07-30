package Basics.ProjectCode;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Process {
    private int id;
    private Double processingTime;
    private Set<Integer> eligibleMachines;

    // Constructor
    public Process(int id) {
        this.id = id;
        this.processingTime = null;
        this.eligibleMachines = new HashSet<>();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public Double getProcessingTime() {
        return processingTime;
    }

    public Set<Integer> getEligibleMachines() {
        return eligibleMachines;
    }

    public void updateProcess(int eligibleMachine, double processingTime) {
        this.eligibleMachines.add(eligibleMachine);
        this.processingTime = processingTime;
    }
}
