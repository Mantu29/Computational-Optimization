package Basics.ProjectCode;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class DataExporter {

    public static void exportData(Map<String, Object> resultMap, String directoryPath) {
        double[][] O_dummy = (double[][]) resultMap.get("O_dummy");
        double[] t_dummy = (double[]) resultMap.get("t_dummy");
        double[] e_dummy = (double[]) resultMap.get("e_dummy");

        try {
            // Export O_dummy
            FileWriter oWriter = new FileWriter(directoryPath + "/O_dummy.csv");
            for (double[] row : O_dummy) {
                for (int i = 0; i < row.length; i++) {
                    oWriter.append(String.valueOf(row[i]));
                    if (i < row.length - 1) {
                        oWriter.append(",");
                    }
                }
                oWriter.append("\n");
            }
            oWriter.flush();
            oWriter.close();

            // Export t_dummy
            FileWriter tWriter = new FileWriter(directoryPath + "/t_dummy.csv");
            for (double t : t_dummy) {
                tWriter.append(String.valueOf(t));
                tWriter.append("\n");
            }
            tWriter.flush();
            tWriter.close();

            // Export e_dummy
            FileWriter eWriter = new FileWriter(directoryPath + "/e_dummy.csv");
            for (double e : e_dummy) {
                eWriter.append(String.valueOf(e));
                eWriter.append("\n");
            }
            eWriter.flush();
            eWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
