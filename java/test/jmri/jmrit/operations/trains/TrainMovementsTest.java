package jmri.jmrit.operations.trains;

import org.junit.jupiter.api.Test;

import java.util.List;

import static jmri.jmrit.operations.trains.TrainMovements.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainMovementsTest {

    @Test
    public void testAcceleration() {
        double forceSI = 100; // Newtons (which are pretty wimpy units)
        double weightSI = 100 * G_SI; // weight of 100 kg = 980.66 Newtons
        double expectedAccelerationSI = 1.0; // in MPS, per second
        double calculatedAccelerationSI = getAcceleration(forceSI, weightSI, true);
        assertEquals(expectedAccelerationSI, calculatedAccelerationSI, 0.00001);

        double forceUS = forceSI / NEWTONS_PER_TON; // 0.01124045 tons in US
        double weightUS = weightSI / NEWTONS_PER_TON; // 0.11023 tons
        double expectedAccelerationUS = expectedAccelerationSI * MPH_PER_MPS; // 2.2369 MPH, per second
        double calculatedAccelerationUS = getAcceleration(forceUS, weightUS, false);
        assertEquals(expectedAccelerationUS, calculatedAccelerationUS, 0.001);
    }

    @Test
    public void testDrawbarPullLimit() {
        double limitLbs = 250000; // lbs

        double expectedLimitTons = limitLbs / LBS_PER_TON; // 125 tons
        double calculatedLimitTons = getDrawbarPullLimit(false);
        assertEquals(expectedLimitTons, calculatedLimitTons);

        double expectedLimitNewtons = expectedLimitTons * NEWTONS_PER_TON; // 1112055 Newtons
        double calculatedLimitNewtons = getDrawbarPullLimit(true);
        assertEquals(expectedLimitNewtons, calculatedLimitNewtons);
    }

    @Test
    public void testGradeResistance() {
        double weight = 100;
        double grade = 1.0;

        double expectedForce = 1;
        double calculatedForce = getGradeResistance(weight, grade);
        assertEquals(expectedForce, calculatedForce);
    }

    @Test
    public void testNetForceAtRest() {
        double totalWeightTons = 50;
        int driverWeightTons = 1;

        double calculatedNetForceAtRestTonsTF = getNetForceAtRest(totalWeightTons, driverWeightTons * 1, true, false, 0, false); // 0
        double calculatedNetForceAtRestTonsTT = getNetForceAtRest(totalWeightTons, driverWeightTons * 2, true, true, 0, false); // 0
        double calculatedNetForceAtRestTonsFF = getNetForceAtRest(totalWeightTons, driverWeightTons * 3, false, false, 0, false); // 0.375
        double calculatedNetForceAtRestTonsFT = getNetForceAtRest(totalWeightTons, driverWeightTons * 4, false, true, 0, false); // 0.875

        assertFalse(calculatedNetForceAtRestTonsTF > 0);
        assertFalse(calculatedNetForceAtRestTonsTT > 0);
        assertTrue(calculatedNetForceAtRestTonsFF > 0);
        assertTrue(calculatedNetForceAtRestTonsFT > 0);

        double totalWeightNewtons = totalWeightTons * NEWTONS_PER_TON;
        double driverWeightNewtons = driverWeightTons * NEWTONS_PER_TON;

        double calculatedNetForceAtRestNewtonsTF = getNetForceAtRest(totalWeightNewtons, driverWeightNewtons * 1, true, false, 0, true); // 0
        double calculatedNetForceAtRestNewtonsTT = getNetForceAtRest(totalWeightNewtons, driverWeightNewtons * 2, true, true, 0, true); // 0
        double calculatedNetForceAtRestNewtonsFF = getNetForceAtRest(totalWeightNewtons, driverWeightNewtons * 3, false, false, 0, true); // 3336
        double calculatedNetForceAtRestNewtonsFT = getNetForceAtRest(totalWeightNewtons, driverWeightNewtons * 4, false, true, 0, true); // 7784

        assertFalse(calculatedNetForceAtRestNewtonsTF > 0);
        assertFalse(calculatedNetForceAtRestNewtonsTT > 0);
        assertTrue(calculatedNetForceAtRestNewtonsFF > 0);
        assertTrue(calculatedNetForceAtRestNewtonsFT > 0);
    }

    @Test
    public void testPositionAfterOneSecond() {
        double accelerationSI = getAcceleration(100, 100 * G_SI, true); // 1.0 MPS/s

        double positionSI_1 = getDistanceAfterOneSecond(0, 0, accelerationSI, true); // 0.5 meters
        assertEquals(0.5, positionSI_1, 0.0000001);
        double speedSI_1 = getSpeedAfterOneSecond(0, accelerationSI); // 1.0 MPS

        double positionSI_2 = getDistanceAfterOneSecond(positionSI_1, speedSI_1, accelerationSI, true); // 2.0 meters
        assertEquals(2.0, positionSI_2, 0.00001);
        double speedSI_2 = getSpeedAfterOneSecond(speedSI_1, accelerationSI); // 2.0 MPS

        double positionSI_3 = getDistanceAfterOneSecond(positionSI_2, speedSI_2, accelerationSI, true); // 4.5 meters
        assertEquals(4.5, positionSI_3, 0.00001);

        double accelerationUS = accelerationSI * MPH_PER_MPS;

        double positionUS_1 = getDistanceAfterOneSecond(0, 0, accelerationUS, false); // 0.000311 miles
        assertEquals(0.5 / METERS_PER_MILE, positionUS_1, 0.00001);
        double speedUS_1 = speedSI_1 * MPH_PER_MPS;

        double positionUS_2 = getDistanceAfterOneSecond(positionUS_1, speedUS_1, accelerationUS, false); // 0.00124 miles
        assertEquals(2.0 / METERS_PER_MILE, positionUS_2, 0.00001);
        double speedUS_2 = speedSI_2 * MPH_PER_MPS;

        double positionUS_3 = getDistanceAfterOneSecond(positionUS_2, speedUS_2, accelerationUS, false); // miles
        assertEquals(4.5 / METERS_PER_MILE, positionUS_3, 0.00001);
    }

    @Test
    public void testSpeedAfterOneSecond() {
        double forceSI = 100; // force of 100 Newton
        double weightSI = 100 * G_SI; // weight of 100 kg = 980.66 Newtons
        double accelerationSI = getAcceleration(forceSI, weightSI, true); // 1.0 MPS/s

        double calculatedNewSpeedSI_0 = getSpeedAfterOneSecond(0, accelerationSI);
        assertEquals(1, calculatedNewSpeedSI_0, 0.00001);

        double calculatedNewSpeedSI_1 = getSpeedAfterOneSecond(calculatedNewSpeedSI_0, accelerationSI); // 2.0 MPS
        assertEquals(2, calculatedNewSpeedSI_1, 0.00001);

        double calculatedNewSpeedSI_2 = getSpeedAfterOneSecond(calculatedNewSpeedSI_1, accelerationSI); // 3.0 MPS
        assertEquals(3, calculatedNewSpeedSI_2, 0.00001);

        double forceUS = forceSI / NEWTONS_PER_TON; // force of 1 Newton in US tons
        double weightUS = weightSI / NEWTONS_PER_TON; // force of 1 Newton in US tons
        double accelerationUS = getAcceleration(forceUS, weightUS, false); // 2.237 MPH/s

        double calculatedNewSpeedUS_0 = getSpeedAfterOneSecond(0, accelerationUS); // 2.237 MPH
        assertEquals(1 * MPH_PER_MPS, calculatedNewSpeedUS_0, 0.0005);

        // MPH
        double calculatedNewSpeedUS_1 = getSpeedAfterOneSecond(calculatedNewSpeedUS_0, accelerationUS); // 4.474 MPH
        assertEquals(2 * MPH_PER_MPS, calculatedNewSpeedUS_1, 0.0005);

        // MPH
        double calculatedNewSpeedUS_2 = getSpeedAfterOneSecond(calculatedNewSpeedUS_1, accelerationUS); // 6.711 MPH
        assertEquals(3 * MPH_PER_MPS, calculatedNewSpeedUS_2, 0.0005);
    }

    @Test
    public void testRollingResistance() {
        double dragLbsAtRest = 368.0;
        double speedMPH = 40;
        double dragLbsAtSpeed = 1552.5;

        double weightTons = 115.0;

        double expectedTonsAtRest = dragLbsAtRest / LBS_PER_TON; // 0.184 tons
        assertEquals(expectedTonsAtRest, getRollingResistance(weightTons, 0, false), 0.00001);

        double expectedTonsAtSpeed = dragLbsAtSpeed / LBS_PER_TON; // 0.776 tons
        assertEquals(expectedTonsAtSpeed, getRollingResistance(weightTons, speedMPH, false), 0.0005);

        double engineWeightNewtons = weightTons * NEWTONS_PER_TON; // 102,3091 Newtons

        double expectedNewtonsAtRest = dragLbsAtRest * NEWTONS_PER_LB; // 1636.9 Newtons
        assertEquals(expectedNewtonsAtRest, getRollingResistance(engineWeightNewtons, 0, true), 0.1);

        double expectedNewtonsAtSpeed = dragLbsAtSpeed * NEWTONS_PER_LB; // 6903.7 Newtons
        double rollingResistance = getRollingResistance(engineWeightNewtons, speedMPH / MPH_PER_MPS, true);
        assertEquals(expectedNewtonsAtSpeed, rollingResistance, 0.1);
    }

    @Test
    public void testStandardTractiveEffort() {
        double trainHp = 1500; // e.g. the F7
        int speedKmPerHour = 15;

        double expectedTractiveEffortNewtons = 190800;
        double calculatedTractiveEffortNewtons = getStandardTractiveForce(trainHp, speedKmPerHour);
        assertEquals(expectedTractiveEffortNewtons, calculatedTractiveEffortNewtons, 0.01); // Newtons
    }

    @Test
    public void testStartingResistance() {
        double weight = 2000;
        double expectedColdJournal = 35;
        double expectedWarmJournal = 25;
        double expectedColdRoller = 15;
        double expectedWarmRoller = 5;

        assertEquals(expectedColdJournal, getStartingResistance(weight, true, false));
        assertEquals(expectedWarmJournal, getStartingResistance(weight, true, true));
        assertEquals(expectedColdRoller, getStartingResistance(weight, false, false));
        assertEquals(expectedWarmRoller, getStartingResistance(weight, false, true));
    }

    @Test
    public void testTractionLimitForce() {
        double driverWeight = 100;
        double expectedTractionLimit = 25;
        assertEquals(expectedTractionLimit, getTractionLimitForce(driverWeight));
    }

    @Test
    public void testTractiveEffort() {
        double trainHp = 1500.0; // e.g. the F7
        double speedKmPerHour = 15.0;

        double trainWatts = trainHp * WATTS_PER_HP; // 1,118,550 watts
        double speedMps = speedKmPerHour * MPS_PER_KMPH; // 4.1667 MPS

        double expectedTractiveEffortNewtons = 190800;
        double calculatedTractiveEffortNewtons = getTractiveEffort(trainWatts, speedMps, true);
        assertEquals(expectedTractiveEffortNewtons, calculatedTractiveEffortNewtons, 0.01); // Newtons

        double speedUS = speedKmPerHour * MILES_PER_KM; // 9.321 MPH

        double expectedTractiveEffortTons = 190800 / NEWTONS_PER_TON; // 21.45 tons
        double calculatedTractiveEffortTons = getTractiveEffort(trainHp, speedUS, false);
        assertEquals(expectedTractiveEffortTons, calculatedTractiveEffortTons, 0.0001); // tons
    }

    @Test
    public void testTimeDistanceList() {
        boolean sI = false;
        double power = 1500.0; // HP, e.g. the F7
        double speedLimit = 40; // MPS
        double driverWeight = 60; // tons
        double totalWeight = 300; // tons
        double gradePercent = 0;
        boolean journalBearing = false;
        boolean aboveFreezing = true;

        List<Double> timeDistanceList = getTimeDistanceList(power, speedLimit, driverWeight, totalWeight, gradePercent, journalBearing, aboveFreezing, sI);
        assertTrue(timeDistanceList.size() > 0);
        assertEquals(0.442632, timeDistanceList.get(timeDistanceList.size() - 1), 0.000001);
    }
}