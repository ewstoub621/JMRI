package jmri.jmrit.operations.trains;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainMovementsTest {
    private static final double MPH_TO_MPS = 0.44704;
    private static final double G = 9.8;

    @Test
    public void testAcceleration() {
        double mass = 1;
        double force = 1;

        double expectedAcceleration = 1;

        assertEquals(expectedAcceleration, TrainMovements.getAcceleration(mass, force));
        assertEquals(expectedAcceleration / 10, TrainMovements.getAcceleration(10 * mass, force));
        assertEquals(10 * expectedAcceleration, TrainMovements.getAcceleration(mass, 10 * force));
        assertEquals(expectedAcceleration, TrainMovements.getAcceleration(10 * mass, 10 * force));
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
    public void testNetForce() {
        double power = 1500 * TrainMovements.HP_TO_WATTS; // Newtons
        double speed = 0;
        double driverWeight = 115 * TrainMovements.NEWTONS_PER_TON; // Newtons
        double weight = 300 * TrainMovements.NEWTONS_PER_TON; // Newtons
        double gradePercent = 0;
        boolean journalBearing = true;
        boolean aboveFreezing = false;
        double mass = weight / G; // kg
        boolean mks = true;

        {
            double expectedNetForce = 209065; // Newtons
            double expectedAcceleration = 0.76767; // m/s/s
            speed = getSpeed(power, speed, driverWeight, weight, gradePercent, journalBearing, aboveFreezing, mass, mks, expectedNetForce, expectedAcceleration);
        }
        {
            double expectedNetForce = 251768; // Newtons
            double expectedAcceleration = 0.92446; // m/s/s
            speed = getSpeed(power, speed, driverWeight, weight, gradePercent, journalBearing, aboveFreezing, mass, mks, expectedNetForce, expectedAcceleration);
        }

        double expectedSpeed = 1.692;
        assertEquals(expectedSpeed, speed, 0.0005);
    }

    private double getSpeed(double power, double speed, double driverWeight, double weight, double gradePercent, boolean journalBearing, boolean aboveFreezing, double mass, boolean mks, double expectedNetForce, double expectedAcceleration) {
        double calculatedNetForce = TrainMovements.getNetForce(power, speed, driverWeight, weight, gradePercent, journalBearing, aboveFreezing, mks);
        assertEquals(expectedNetForce, calculatedNetForce, 0.5);

        double calculatedAcceleration = TrainMovements.getAcceleration(mass, calculatedNetForce);
        assertEquals(expectedAcceleration, calculatedAcceleration, 0.00001);

        speed += calculatedAcceleration; // after constant net force applied for 1 second
        return speed;
    }

    @Test
    public void testRollingResistance() {
        double engineTonWeight = 115.0;
        double expectedLbs = 345.0;
        assertEquals(expectedLbs / TrainMovements.LBS_PER_TON, TrainMovements.getRollingResistance(engineTonWeight), 0.00001);

        double engineWeightNewtons = engineTonWeight * TrainMovements.NEWTONS_PER_TON;
        double expectedKg = expectedLbs * 4.44822;
        assertEquals(expectedKg, TrainMovements.getRollingResistance(engineWeightNewtons), 0.1);
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
    public void testTractionLimitForce() {
        double driverWeight = 100;
        double expectedTractionLimit = 25;
        assertEquals(expectedTractionLimit, TrainMovements.getTractionLimitForce(driverWeight));
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
    public void testTractiveEffort() {
        double trainHp = 1500; // e.g. the F7
        int speedKmPerHour = 15;
        {
            double speedMps = speedKmPerHour * TrainMovements.KM_PER_HR_TO_MPS; // 15 km/hr to 4.166667 mps
            double trainWatts = trainHp * TrainMovements.HP_TO_WATTS;

            double expectedTractiveEffortNewtons = 190800;
            double calculatedTractiveEffortNewtons = TrainMovements.getTractiveEffort(trainWatts, speedMps, true);
            assertEquals(expectedTractiveEffortNewtons, calculatedTractiveEffortNewtons, 0.01); // Newtons
        }
        {
            double speedMph = speedKmPerHour * TrainMovements.KM_PER_HR_TO_MPS / MPH_TO_MPS;

            double expectedTractiveEffortTons = 190800 / TrainMovements.NEWTONS_PER_TON;
            double calculatedTractiveEffortTons = TrainMovements.getTractiveEffort(trainHp, speedMph, false);
            assertEquals(expectedTractiveEffortTons, calculatedTractiveEffortTons, 0.0001); // tons
        }
    }

}