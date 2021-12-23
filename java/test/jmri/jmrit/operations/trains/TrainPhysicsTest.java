package jmri.jmrit.operations.trains;

import jmri.jmrit.operations.locations.Location;
import jmri.jmrit.operations.routes.Route;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.setup.Setup;
import org.junit.jupiter.api.Test;

import java.util.*;

import static jmri.jmrit.operations.trains.TrainMotion.getMotionsHeader;
import static jmri.jmrit.operations.trains.TrainMotionParams.getMotionParamsHeader;
import static jmri.jmrit.operations.trains.TrainPhysics.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainPhysicsTest {

    @Test
    public void testAcceleration() {
        double forceSI = 100; // Newtons (which are pretty wimpy units)
        double weightSI = 100 * G_SI; // weight of 100 kg = 980.66 Newtons
        double expectedAccelerationSI = 1.0; // in MPS, per second
        try {
            Setup.setLengthUnit(Setup.METER);
            double calculatedAccelerationSI = getAcceleration(forceSI, weightSI);
            assertEquals(expectedAccelerationSI, calculatedAccelerationSI, 0.00001);
        } finally {
            Setup.setLengthUnit(Setup.FEET);
        }

        double forceUS = forceSI / NEWTONS_PER_TON; // 0.01124045 tons in US
        double weightUS = weightSI / NEWTONS_PER_TON; // 0.11023 tons
        double expectedAccelerationUS = expectedAccelerationSI * MPH_PER_MPS; // 2.2369 MPH, per second
        double calculatedAccelerationUS = getAcceleration(forceUS, weightUS);
        assertEquals(expectedAccelerationUS, calculatedAccelerationUS, 0.001);
    }

    @Test
    public void testDrawbarPullLimit() {
        double limitLbs = 250000; // lbs

        double expectedLimitTons = limitLbs / LBS_PER_TON; // 125 tons
        double calculatedLimitTons = getDrawbarPullLimit();
        assertEquals(expectedLimitTons, calculatedLimitTons);

        try {
            Setup.setLengthUnit(Setup.METER);
            double expectedLimitNewtons = expectedLimitTons * NEWTONS_PER_TON; // 1112055 Newtons
            double calculatedLimitNewtons = getDrawbarPullLimit();
            assertEquals(expectedLimitNewtons, calculatedLimitNewtons);
        } finally {
            Setup.setLengthUnit(Setup.FEET);
        }
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
    public void testMotions() throws Exception {
        Train train = new Train("1", "TM");
        Route route = new Route("1", "Route1");
        train.setRoute(route);

        Location loc1 = new Location("1", "Location1");
        route.addLocation(loc1, 1);

        RouteLocation rl1 = new RouteLocation("1r1", loc1);
        rl1.setDistance(10);
        rl1.setGrade(3);
        rl1.setSpeedLimit(40);
        rl1.setSequenceNumber(1);
        route.register(rl1);

        Location loc2 = new Location("2", "Location2");
        route.addLocation(loc2, 2);

        RouteLocation rl2 = new RouteLocation("1r2", loc2);
        rl2.setSequenceNumber(2);
        route.register(rl2);

        TrainRevenues trainRevenues = new TrainRevenues(train);
        train.setTrainRevenues(trainRevenues);

        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25));
        trainRevenues.getTrainCarWeights().put("1r1", carWeights);
        trainRevenues.getTrainCarCount().put("1r1", carWeights.size());
        trainRevenues.getTrainEngineDriverWeight().put("1r1", STEAMER_DRIVER_TO_ENGINE_WEIGHT_RATIO * 364.0);
        trainRevenues.getTrainEngineHP().put("1r1", 4500);
        trainRevenues.getTrainEngineModel().put("1r1", "S-2 2-8-4");
        trainRevenues.getTrainEngineType().put("1r1", "Steam");
        trainRevenues.getTrainEngineWeight().put("1r1", 364);
        trainRevenues.getTrainTotalWeight().put("1r1", 989);

        TrainPhysics movements = new TrainPhysics(train);
        System.out.println("testMotions:\n" + movements);
    }

    @Test
    public void testNewDistance() {
        double accelerationSi; // 1.0 MPS/s
        double speedSI_1; // 1.0 MPS
        double speedSI_2; // 2.0 MPS
        try {
            Setup.setLengthUnit(Setup.METER);

            accelerationSi = getAcceleration(100, 100 * G_SI);

            double positionSI_1 = getNewDistance(0, 0, accelerationSi); // 0.5 meters
            assertEquals(0.5, positionSI_1, 0.0000001);
            speedSI_1 = getNewSpeed(0, accelerationSi);

            double positionSI_2 = getNewDistance(positionSI_1, speedSI_1, accelerationSi); // 2.0 meters
            assertEquals(2.0, positionSI_2, 0.00001);
            speedSI_2 = getNewSpeed(speedSI_1, accelerationSi);

            double positionSI_3 = getNewDistance(positionSI_2, speedSI_2, accelerationSi); // 4.5 meters
            assertEquals(4.5, positionSI_3, 0.00001);
        } finally {
            Setup.setLengthUnit(Setup.FEET);
        }

        double accelerationUS = accelerationSi * MPH_PER_MPS;

        double positionUS_1 = getNewDistance(0, 0, accelerationUS); // 0.000311 miles
        assertEquals(0.5 / METERS_PER_MILE, positionUS_1, 0.00001);
        double speedUS_1 = speedSI_1 * MPH_PER_MPS;

        double positionUS_2 = getNewDistance(positionUS_1, speedUS_1, accelerationUS); // 0.00124 miles
        assertEquals(2.0 / METERS_PER_MILE, positionUS_2, 0.00001);
        double speedUS_2 = speedSI_2 * MPH_PER_MPS;

        double positionUS_3 = getNewDistance(positionUS_2, speedUS_2, accelerationUS); // miles
        assertEquals(4.5 / METERS_PER_MILE, positionUS_3, 0.00001);

        double speedUs = 40; // MPH
        double speedSi = speedUs / MPH_PER_MPS;
        double decelerationSi = -accelerationSi; // MPS/sec
        double decelerationUs = -getAcceleration(100 / NEWTONS_PER_TON, 100 * G_SI / NEWTONS_PER_TON); // -2.236936 MPH/sec
        assertEquals(decelerationSi * MPH_PER_MPS, decelerationUs, 0.0002);

        double positionSi; // 0.5 meters
        try {
            Setup.setLengthUnit(Setup.METER);
            positionSi = getNewDistance(0, speedSi, decelerationSi);
        } finally {
            Setup.setLengthUnit(Setup.FEET);
        }
        double positionUs = getNewDistance(0, speedUs, decelerationUs); // 0.5 meters
        double expectedUs = positionSi * MILES_PER_KM / 1000;
        assertEquals(expectedUs, positionUs, 0.000001);
    }

    @Test
    public void testNewSpeed() {
        double forceSI = 100; // force of 100 Newton
        double weightSI = 100 * G_SI; // weight of 100 kg = 980.66 Newtons
        double accelerationSI; // 1.0 MPS/s
        try {
            Setup.setLengthUnit(Setup.METER);
            accelerationSI = getAcceleration(forceSI, weightSI);
        } finally {
            Setup.setLengthUnit(Setup.FEET);
        }

        double speedSI_0 = getNewSpeed(0, accelerationSI);
        assertEquals(1, speedSI_0, 0.00001);

        double speedSI_1 = getNewSpeed(speedSI_0, accelerationSI); // 2.0 MPS
        assertEquals(2, speedSI_1, 0.00001);

        double speedSI_2 = getNewSpeed(speedSI_1, accelerationSI); // 3.0 MPS
        assertEquals(3, speedSI_2, 0.00001);

        double forceUS = forceSI / NEWTONS_PER_TON; // force of 1 Newton in US tons
        double weightUS = weightSI / NEWTONS_PER_TON; // force of 1 Newton in US tons
        double accelerationUS = getAcceleration(forceUS, weightUS); // 2.237 MPH/s

        double speedUS_0 = getNewSpeed(0, accelerationUS); // 2.237 MPH
        assertEquals(1 * MPH_PER_MPS, speedUS_0, 0.0005);

        double speedUS_1 = getNewSpeed(speedUS_0, accelerationUS); // 4.474 MPH
        assertEquals(2 * MPH_PER_MPS, speedUS_1, 0.0005);

        double speedUS_2 = getNewSpeed(speedUS_1, accelerationUS); // 6.711 MPH
        assertEquals(3 * MPH_PER_MPS, speedUS_2, 0.0005);
    }

    @Test
    public void testRollingResistance() {
        double dragLbsAtRest = 368.0;
        double speedMPH = 40;
        double dragLbsAtSpeed = 1552.5;

        double weightTons = 115.0;

        double expectedTonsAtRest = dragLbsAtRest / LBS_PER_TON; // 0.184 tons
        assertEquals(expectedTonsAtRest, getRollingResistance(weightTons, 0), 0.00001);

        double expectedTonsAtSpeed = dragLbsAtSpeed / LBS_PER_TON; // 0.77625 tons
        assertEquals(expectedTonsAtSpeed, getRollingResistance(weightTons, speedMPH), 0.0005);

        double engineWeightNewtons = weightTons * NEWTONS_PER_TON; // 102,3091 Newtons

        try {
            Setup.setLengthUnit(Setup.METER);
            double expectedNewtonsAtRest = dragLbsAtRest * NEWTONS_PER_LB; // 1636.9455 Newtons
            assertEquals(expectedNewtonsAtRest, getRollingResistance(engineWeightNewtons, 0), 0.1);

            double expectedNewtonsAtSpeed = dragLbsAtSpeed * NEWTONS_PER_LB; // 6905.864 Newtons
            double rollingResistance = getRollingResistance(engineWeightNewtons, speedMPH / MPH_PER_MPS);
            assertEquals(expectedNewtonsAtSpeed, rollingResistance, 0.2);
        } finally {
            Setup.setLengthUnit(Setup.FEET);
        }
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
    public void testStretchMotion() {
        List<Integer> carWeights = Arrays.asList(75, 25, 75, 25, 75, 25, 75, 25, 75);
        TrainMotionParams tmp = new TrainMotionParams(carWeights, 364, 0.35 * (double) 364, 4500, 3, 0, 0, true, false);

        TrainMotion tm = getStretchMotion(tmp);
        assertNotNull(tm);
        System.out.println(String.format("testStretchMotion {\n\tTrainMotionParams {\n\t%s\n\t%s\n\t}\n\tStretch TrainMotion {\n\t\t%s\n\t\t%s\n\t}\n}", getMotionParamsHeader(), tmp.getMotionParamsData(), getMotionsHeader(), tm.getMotionData()));
    }

    @Test
    public void testTractiveForce() {
        double trainHp = 1500.0; // e.g. the F7
        double speedKmPerHour = 15.0;

        double trainWatts = trainHp * WATTS_PER_HP; // 1,118,550 watts
        double speedMps = speedKmPerHour * MPS_PER_KMPH; // 4.1667 MPS

        double speedUS = speedKmPerHour * MILES_PER_KM; // 9.321 MPH

        double expectedTractiveEffortTons = 190800 / NEWTONS_PER_TON; // 21.45 tons
        double calculatedTractiveEffortTons = getTractiveForce(trainHp, speedUS);
        assertEquals(expectedTractiveEffortTons, calculatedTractiveEffortTons, 0.0001); // tons

        // SI units
        try {
            Setup.setLengthUnit(Setup.METER);
            double expectedTractiveEffortNewtons = 190800;
            double calculatedTractiveEffortNewtons = getTractiveForce(trainWatts, speedMps);
            assertEquals(expectedTractiveEffortNewtons, calculatedTractiveEffortNewtons, 0.01); // Newtons
        } finally {
            Setup.setLengthUnit(Setup.FEET);
        }

    }

    @Test
    public void testTrainMotions() {
        double speedLimit = 40; // MPS
        Map<Integer, List<Integer>> carWeights = new HashMap<>();
        carWeights.put(0, new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25)));
        carWeights.put(1, new ArrayList<>(Arrays.asList(25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25)));
        carWeights.put(2, new ArrayList<>(Arrays.asList(25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25)));
        carWeights.put(3, new ArrayList<>(Arrays.asList(25, 75, 25, 75, 25, 25)));
        //        double[] totalWeight = new double[]{415, 740, 989, 374}; // tons
        double[] driverWeight = new double[]{115, 115, STEAMER_DRIVER_TO_ENGINE_WEIGHT_RATIO * 364, 124}; // tons
        double[] engineWeight = new double[]{115, 115, 364, 124}; // tons
        double[] gradePercent = new double[]{-1, -2, 3, 0};
        double[] power = new double[]{1350, 1350, 4500, 1000};
        int[] distance = new int[]{10, 10, 10, 10};

        for (int i = 0; i < 4; i++) {
            TrainMotionParams tmp = new TrainMotionParams(carWeights.get(i), engineWeight[i], driverWeight[i], power[i], gradePercent[i], speedLimit, distance[i], true, false);
            List<TrainMotion> motionsList = getTrainMotions(tmp).get(0);
            assertFalse(motionsList.isEmpty());
        }
    }

}