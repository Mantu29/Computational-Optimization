package Basics.ProjectCode;

public class OperationDetail {
    private String jobType;
    private String processName;
    private int stepNumber;
    private double value; // Value of A_dummy

    public OperationDetail(String jobType, String processName, int stepNumber, double value) {
        this.jobType = jobType;
        this.processName = processName;
        this.stepNumber = stepNumber;
        this.value = value;
    }

    public String getJobType() {
        return jobType;
    }

    public String getProcessName() {
        return processName;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public double getValue() {
        return value;
    }
}
