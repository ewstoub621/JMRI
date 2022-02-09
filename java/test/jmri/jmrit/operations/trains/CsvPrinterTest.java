package jmri.jmrit.operations.trains;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;

public class CsvPrinterTest {

    @Test
    public void testCsvPrinter() throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.withIgnoreSurroundingSpaces(false);
/*
        format = format.withCommentMarker(' ');
        format = format.withHeaderComments("Generated by Apache Commons CSV 1.1.", new Date());
        format = format.withHeader("Header 1","Header 2","Header 3","Header 4");
*/
        File csvFile = new File("testCsvPrinter.csv");
        FileOutputStream fos = new FileOutputStream(csvFile);
        OutputStreamWriter out = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        BufferedWriter bufferedWriter = new BufferedWriter(out);
        try (CSVPrinter printer = new CSVPrinter(bufferedWriter, format)) {
            printer.printRecord("id", "userName", "firstName", "lastName", "birthday");
            printer.printRecord(1, "john73", "John Spaces             ", "Doe", LocalDate.of(1973, 9, 15));
            printer.println();
/*
            printer.printComment("this is a long, long, long, long, long, long, long, long, comment!"); // writes into 9 columns
*/
            printer.printRecord(2, "mary", "Mary", "             Spaces Meyer", LocalDate.of(1985, 3, 29));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
