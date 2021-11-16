package jmri.jmrit.operations.trains;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainMovementsTest {
    private static final double MPH_TO_MPS = 0.44704;

    @Test
    public void testAcceleration() {
        double force = 1; // force of 1 Newton in SI or 1 ton in US

        double weightSI = TrainMovements.G_SI; // weight of 1 kg
        double expectedAccelerationSI = 1; // 1 meter/sec/sec
        assertEquals(expectedAccelerationSI, TrainMovements.getAcceleration(weightSI, force, true));
        assertEquals(expectedAccelerationSI / 10, TrainMovements.getAcceleration(10 * weightSI, force, true));
        assertEquals(10 * expectedAccelerationSI, TrainMovements.getAcceleration(weightSI, 10 * force, true));
        assertEquals(expectedAccelerationSI, TrainMovements.getAcceleration(10 * weightSI, 10 * force, true));

        double weightUS = 1; // tons
        double expectedAccelerationUS = TrainMovements.MPH_PER_FPS * TrainMovements.G_US;
        assertEquals(expectedAccelerationUS, TrainMovements.getAcceleration(weightUS, force, false), 0.0005);
    }

    @Test
    public void testDrawbarPullLimit() {
        assertEquals(125, TrainMovements.getDrawbarPullLimit(false));
        assertEquals(1112050, TrainMovements.getDrawbarPullLimit(true));
    }

    @Test
    public void testGradeResistance() {
        double weight = 2000;
        double grade = 1.0;

        double expectedForce = 20;
        assertEquals(expectedForce, TrainMovements.getGradeResistance(weight, grade));
    }

    @Test
    public void testNetForceSI() {
        double power = 1500 * TrainMovements.WATTS_PER_HP; // Newtons
        double speed = 0;
        double driverWeight = 115 * TrainMovements.NEWTONS_PER_TON; // Newtons
        double weight = 300 * TrainMovements.NEWTONS_PER_TON; // Newtons
        double gradePercent = 0;
        boolean journalBearing = true;
        boolean aboveFreezing = false;

        double tractiveForceLimit = TrainMovements.getTractiveForceLimit(driverWeight,true);
        if (speed == 0) {
            double expectedNetForce = 209065; // Newtons
            double expectedAcceleration = 0.7682; // m/s/s

            double calculatedNetForce = TrainMovements.getNetForceAtRest(weight, journalBearing, aboveFreezing, tractiveForceLimit);
            assertEquals(expectedNetForce, calculatedNetForce, 0.5);

            double calculatedAcceleration = TrainMovements.getAcceleration(weight, calculatedNetForce, true);
            assertEquals(expectedAcceleration, calculatedAcceleration, 0.0001);

            speed += calculatedAcceleration; // after constant net force applied for 1 second
        }
        if (speed > 0) {
            double expectedNetForce = 251500; // Newtons
            double expectedAcceleration = 0.9241; // m/s/s

            double calculatedNetForce = TrainMovements.getNetForceInMotion(power, speed, weight, gradePercent, true, tractiveForceLimit);
            assertEquals(expectedNetForce, calculatedNetForce, 2);

            double calculatedAcceleration = TrainMovements.getAcceleration(weight, calculatedNetForce, true);
            assertEquals(expectedAcceleration, calculatedAcceleration, 0.0001);

            speed += calculatedAcceleration; // after constant net force applied for 1 second
        }

        double expectedSpeed = 1.692;
        assertEquals(expectedSpeed, speed, 0.0005);
    }

    @Test
    public void testNetForceUS() {
        double power = 1500; // tons
        double speed = 0;
        double driverWeight = 115; // tons
        double weight = 300; // tons
        double gradePercent = 0;
        boolean journalBearing = true;
        boolean aboveFreezing = false;

        double tractiveForceLimit = TrainMovements.getTractiveForceLimit(driverWeight,false);
        if (speed == 0) {
            double expectedNetForce = 209065 / TrainMovements.NEWTONS_PER_TON; // Newtons -> tons
            double expectedAcceleration = 0.7682 * TrainMovements.MPH_PER_MPS; // m/s/s -> MPH/s

            double calculatedNetForce = TrainMovements.getNetForceAtRest(weight, journalBearing, aboveFreezing, tractiveForceLimit);
            assertEquals(expectedNetForce, calculatedNetForce, 0.5);

            double calculatedAcceleration = TrainMovements.getAcceleration(weight, calculatedNetForce, false);
            assertEquals(expectedAcceleration, calculatedAcceleration, 0.0001);

            speed += calculatedAcceleration; // after constant net force applied for 1 second
        }
        if (speed > 0) {
            double expectedNetForce = 251768 / TrainMovements.NEWTONS_PER_TON; // Newtons -> tons
            double expectedAcceleration = 0.9241 * TrainMovements.MPH_PER_MPS; // m/s/s -> MPH/s

            double calculatedNetForce = TrainMovements.getNetForceInMotion(power, speed, weight, gradePercent, false, tractiveForceLimit);
            assertEquals(expectedNetForce, calculatedNetForce, 0.5);

            double calculatedAcceleration = TrainMovements.getAcceleration(weight, calculatedNetForce, false);
            assertEquals(expectedAcceleration, calculatedAcceleration, 0.0001);

            speed += calculatedAcceleration; // after constant net force applied for 1 second
        }

        double expectedSpeed = 1.692 * TrainMovements.MPH_PER_MPS;
        assertEquals(expectedSpeed, speed, 0.001);
    }

    @Test
    public void testRollingResistance() {
        double expectedLbsAtRest = 368.0;
        double expectedLbsAtSpeed = 1552.0;

        double engineTonWeight = 115.0;
        double engineWeightNewtons = engineTonWeight * TrainMovements.NEWTONS_PER_TON;

        double expectedTonsAtRest = expectedLbsAtRest / TrainMovements.LBS_PER_TON;
        double calculatedTonsAtRest = TrainMovements.getRollingResistance(engineTonWeight, 0, false);
        assertEquals(expectedTonsAtRest, calculatedTonsAtRest, 0.00001);

        double expectedNewtonsAtRest = expectedLbsAtRest * TrainMovements.NEWTONS_PER_LB;
        double calculatedNewtons = TrainMovements.getRollingResistance(engineWeightNewtons, 0, true);
        assertEquals(expectedNewtonsAtRest, calculatedNewtons, 0.1);

        double expectedTonsAtSpeed = expectedLbsAtSpeed / TrainMovements.LBS_PER_TON;
        double calculatedTonsAtSpeed = TrainMovements.getRollingResistance(engineTonWeight, 40, false);
        assertEquals(expectedTonsAtSpeed, calculatedTonsAtSpeed, 0.0005);
    }

    @Test
    public void testStandardTractiveEffort() {
        double trainHp = 1500; // e.g. the F7
        int speedKmPerHour = 15;

        double expectedTractiveEffortNewtons = 190800;
        double calculatedTractiveEffortNewtons = TrainMovements.getStandardTractiveForce(trainHp, speedKmPerHour);
        assertEquals(expectedTractiveEffortNewtons, calculatedTractiveEffortNewtons, 0.01); // Newtons
    }

    @Test
    public void testStartingResistance() {
        double weight = 2000;
        double expectedColdJournal = 35;
        double expectedWarmJournal = 25;
        double expectedColdRoller = 15;
        double expectedWarmRoller = 5;

        assertEquals(expectedColdJournal, TrainMovements.getStartingResistance(weight, true, false));
        assertEquals(expectedWarmJournal, TrainMovements.getStartingResistance(weight, true, true));
        assertEquals(expectedColdRoller, TrainMovements.getStartingResistance(weight, false, false));
        assertEquals(expectedWarmRoller, TrainMovements.getStartingResistance(weight, false, true));
    }

    @Test
    public void testTractionLimitForce() {
        double driverWeight = 100;
        double expectedTractionLimit = 25;
        assertEquals(expectedTractionLimit, TrainMovements.getTractionLimitForce(driverWeight));
    }

    @Test
    public void testTractiveEffort() {
        double trainHp = 1500; // e.g. the F7
        int speedKmPerHour = 15;
        {
            double speedMps = speedKmPerHour * TrainMovements.MPS_PER_KMPH; // 15 km/hr to 4.166667 mps
            double trainWatts = trainHp * TrainMovements.WATTS_PER_HP;

            double expectedTractiveEffortNewtons = 190800;
            double calculatedTractiveEffortNewtons = TrainMovements.getTractiveEffort(trainWatts, speedMps, true);
            assertEquals(expectedTractiveEffortNewtons, calculatedTractiveEffortNewtons, 0.01); // Newtons
        }
        {
            double speedMph = speedKmPerHour * TrainMovements.MPS_PER_KMPH / MPH_TO_MPS;

            double expectedTractiveEffortTons = 190800 / TrainMovements.NEWTONS_PER_TON;
            double calculatedTractiveEffortTons = TrainMovements.getTractiveEffort(trainHp, speedMph, false);
            assertEquals(expectedTractiveEffortTons, calculatedTractiveEffortTons, 0.0001); // tons
        }
    }

}