package ebs.generator;

import java.io.*;
import java.util.List;

/**
 * Writes generated messages to text files.
 */
public class FileWriter {

    /**
     * Writes a list of objects (one per line) to the specified file path.
     */
    public static void write(String path, List<?> items) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new java.io.FileWriter(path))) {
            for (Object item : items) {
                bw.write(item.toString());
                bw.newLine();
            }
        }
        System.out.println("[FileWriter] Wrote " + items.size() + " entries -> " + path);
    }
}
