package jmri.jmrit.operations.trains;

import org.junit.jupiter.api.Test;

import static jmri.jmrit.operations.trains.TrainMotion.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainMotionTest {

    @Test
    void testMinHp1000() {
        double speedLimit = 12;
        double totalWeight = 1000;
        double gradePercent = 1;

        double expectedMinHp = 1000;
        assertEquals(expectedMinHp, getMinHp(speedLimit, totalWeight, gradePercent));
    }

    @Test
    void testMinHp2500() {
        double speedLimit = 30;
        double totalWeight = 2000;
        double gradePercent = 0.5;

        double expectedMinHp = 2500;
        assertEquals(expectedMinHp, getMinHp(speedLimit, totalWeight, gradePercent));
    }

    @Test
    void testMotionsFormatting() {
        TrainMotion tm = new TrainMotion("accelerating");

        String[] splitHeader = getMotionsHeader().split("\n");
        String[] captionLineA = splitHeader[0].split(",");
        String[] captionLineB = splitHeader[1].split(",");
        String[] splitData = tm.getMotionData().split(",");
        for (int i = 0; i < splitData.length; i++) {
            String captionA = captionLineA[i];
            String captionB = captionLineB[i];
            String datum = splitData[i];
            assertEquals(captionA.length(), datum.length(), captionA + " vs " + datum);
            assertEquals(captionB.length(), datum.length(), captionB + " vs " + datum);
        }
    }

    @Test
    void testRawMotionsFormatting() {
        TrainMotion tm = new TrainMotion("accelerating");

        String[] splitHeader = getRawMotionsHeader().split(",");
        String[] splitData = tm.getRawMotionData().split(",");
        for (int i = 0; i < splitData.length; i++) {
            String caption = splitHeader[i];
            String datum = splitData[i];
            assertEquals(caption.length(), datum.length(), caption + " vs " + datum);
        }
    }

    @Test
    void testTimeString_12_Char() {
        assertEquals("00:00:00.000", timeString_12_Char(0));
        assertEquals("00:00:00.123", timeString_12_Char(0.123));
        assertEquals("00:00:59.000", timeString_12_Char(59));
        assertEquals("00:00:59.789", timeString_12_Char(59.789));
        assertEquals("00:01:00.000", timeString_12_Char(60));
        assertEquals("00:59:59.000", timeString_12_Char(3599));
        assertEquals("00:59:59.999", timeString_12_Char(3599.999));
        assertEquals("01:00:00.000", timeString_12_Char(3600));
    }

    @Test
    void testTimeString_8_Char() {
        assertEquals("00:00:00", TrainMotion.timeString_8_Char(0));
        assertEquals("00:00:59", TrainMotion.timeString_8_Char(59));
        assertEquals("00:01:00", TrainMotion.timeString_8_Char(60));
        assertEquals("00:59:59", TrainMotion.timeString_8_Char(3599));
        assertEquals("01:00:00", TrainMotion.timeString_8_Char(3600));
    }

}