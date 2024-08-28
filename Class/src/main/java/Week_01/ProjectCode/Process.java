package Basics.ProjectCode;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Process {
    private int id;
    private String name; // New field for process name
    private Double processingTime;
    private Set<Integer> eligibleMachines;

    // Constructor
    public Process(int id, String name) {
        this.id = id;
        this.name = name;
        this.processingTime = null;
        this.eligibleMachines = new HashSet<>();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public String getName() { // Getter for process name
        return name;
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
