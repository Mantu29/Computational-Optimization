package Basics.ProjectCode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVUtils {
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
}
