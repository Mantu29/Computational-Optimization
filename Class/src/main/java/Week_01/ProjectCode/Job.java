package Basics.ProjectCode;

import java.util.ArrayList;
import java.util.List;

public class Job {
    String jobType;
    List<List<Process>> steps;

    public Job(String jobType) {
        this.jobType = jobType;
        this.steps = new ArrayList<>();
    }

    public void addStep(List<Process> stepProcesses) {
        this.steps.add(stepProcesses);
    }

    public String getJobType() {
        return jobType;
    }

    public List<List<Process>> getSteps() {
        return steps;
    }
}
