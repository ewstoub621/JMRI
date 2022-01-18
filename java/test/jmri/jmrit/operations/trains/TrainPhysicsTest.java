package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.locations.Location;
import jmri.jmrit.operations.rollingstock.engines.Engine;
import jmri.jmrit.operations.rollingstock.engines.EngineManager;
import jmri.jmrit.operations.routes.Route;
import jmri.jmrit.operations.routes.RouteLocation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static jmri.jmrit.operations.trains.TrainPhysics.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainPhysicsTest {
    private static final int ZERO = 0;
    private static final int TEN = 10;
    private static final boolean DEBUG = false;

    @Test
    public void testAcceleration() {
        double forceSI = 100; // Newtons (which are pretty wimpy units)
        double weightSI = 100 * G_SI; // weight of 100 kg = 980.66 Newtons
        double expectedAccelerationSI = 1.0; // in MPS, per second
        double forceUS = forceSI / NEWTONS_PER_TON; // 0.01124045 tons in US
        double weightUS = weightSI / NEWTONS_PER_TON; // 0.11023 tons
        double expectedAccelerationUS = expectedAccelerationSI * MPH_PER_MPS; // 2.2369 MPH, per second
        double calculatedAccelerationUS = getAcceleration(forceUS, weightUS);
        assertEquals(expectedAccelerationUS, calculatedAccelerationUS, 0.001);
    }

    @Test
    public void testGetNewSpeedTrainMotion() {
        List<Integer> carWeights = buildCarWeights(TEN);
        int driverWeight = 115;
        int engineWeight = 115;
        int fullPower = 1350;
        int gradePercent = 1;
        double distance = TEN;
        TrainMotion tm = new TrainMotion();
        double distanceLimit;
        int steps = 19;

        if (DEBUG)
            System.out.println("\ntestGetNewSpeedTrainMotion:\n" + TrainMotion.getMotionsHeader());
        for (int i = ZERO; i < steps; i++) {
            distanceLimit = distance - tm.x;
            double newSpeed = i + 1;
            tm = getNewTrainMotion(tm, newSpeed, driverWeight, engineWeight, carWeights, fullPower, gradePercent, distanceLimit);
            if (DEBUG)
                System.out.println(tm.getRawMotionData());
            assertNotNull(tm);
            assertEquals(newSpeed, tm.v);
        }
        double accelTime = tm.t;
        for (int i = 1; i <= steps; i++) {
            distanceLimit = distance - tm.x;
            double newSpeed = steps - i;
            tm = getNewTrainMotion(tm, newSpeed, driverWeight, engineWeight, carWeights, fullPower, gradePercent, distanceLimit);
            if (DEBUG)
                System.out.println(tm.getRawMotionData());
            assertNotNull(tm);
            assertEquals(newSpeed, tm.v);
        }
        assertEquals(ZERO, tm.v);
        if (DEBUG)
            System.out.println("acceleration elapsed time = " + accelTime);
        if (DEBUG)
            System.out.println("deceleration elapsed time = " + (tm.t - accelTime));
    }

    @Test
    public void testDrawbarPullLimit() {
        double limitLbs = 250000; // lbs

        double expectedLimitTons = limitLbs / LBS_PER_TON; // 125 tons
        double calculatedLimitTons = COUPLER_PULL_LIMIT_TONS;
        assertEquals(expectedLimitTons, calculatedLimitTons);
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
        int cars = TEN;
        int grade = 1;

        Train train = new Train("1", "TM");
        Route route = new Route("1", "Route1");
        train.setRoute(route);

        Location loc1 = new Location("1", "Location1");
        route.addLocation(loc1, 1);

        RouteLocation rl1 = new RouteLocation("1r1", loc1);
        rl1.setDistance(TEN);
        rl1.setGrade(grade);
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

        List<Integer> carWeights = buildCarWeights(cars);
        trainRevenues.getTrainCarWeights().put("1r1", carWeights);

        Engine engine = new Engine("NPK", "123");
        engine.setModel("FT");
        engine.setTypeName("Diesel");
        InstanceManager.getDefault(EngineManager.class).register(engine);
        trainRevenues.getTrainEngines().put("1r1", new ArrayList<>());
        trainRevenues.getTrainEngines().get("1r1").add(engine);

        TrainPhysics trainPhysics = new TrainPhysics(train, true);
        if (DEBUG)
            System.out.println("\ntestMotions:\n" + trainPhysics);
    }

    @Test
    public void testRollingResistance() {
        double dragLbsAtRest = 368.0;
        double speedMPH = 40;
        double dragLbsAtSpeed = 1552.5;

        double weightTons = 115.0;

        double expectedTonsAtRest = dragLbsAtRest / LBS_PER_TON; // 0.184 tons
        assertEquals(expectedTonsAtRest, getRollingResistance(weightTons, ZERO), 0.00001);

        double expectedTonsAtSpeed = dragLbsAtSpeed / LBS_PER_TON; // 0.77625 tons
        assertEquals(expectedTonsAtSpeed, getRollingResistance(weightTons, speedMPH), 0.0005);
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
        double engineWeight = 728;
        double driverWeight = STEAMER_DRIVER_WEIGHT_PER_ENGINE_WEIGHT * engineWeight;
        List<Integer> carWeights = Arrays.asList(75, 25, 75, 25, 75, 25, 75, 25, 75);
        double fullPower = 9000;
        double gradePercent = 2.5;
        boolean journal = true;
        boolean warm = false;

        assertNotNull(getStretchMotion(driverWeight, engineWeight, carWeights, fullPower, gradePercent, journal, warm));
    }

    @Test
    public void testTrainMotionsDeadStartEndStop() {
        int priorSpeed = ZERO;
        int speed2 = priorSpeed + 2;
        int speedLimit = 40;
        boolean endStop = true;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, ZERO, speedLimit, TEN, true, false, endStop, priorSpeed);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsDeadStartEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getRawMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed2, trainMotionList.get(2).v, 0.001);
        assertEquals(ZERO, trainMotionList.get(trainMotionList.size() - 1).v, 0.001);
        assertEquals(TEN, trainMotionList.get(trainMotionList.size() - 1).x, 0.001);
    }

    @Test
    public void testTrainMotionsDeadStartNoEndStop() {
        int priorSpeed = ZERO;
        int speed2 = priorSpeed + 2;
        int speedLimit = 40;
        boolean endStop = false;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, ZERO, speedLimit, TEN, true, false, endStop, priorSpeed);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsDeadStartNoEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getRawMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed2, trainMotionList.get(2).v, 0.001);
        assertEquals(speedLimit, trainMotionList.get(trainMotionList.size() - 1).v, 0.001);
        assertEquals(TEN, trainMotionList.get(trainMotionList.size() - 1).x, 0.001);
    }

    @Test
    public void testTrainMotionsFastStartEndStop() {
        int priorSpeed = 40;
        int speed1 = priorSpeed - 1;
        int speedLimit = 30;
        boolean endStop = true;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, ZERO, speedLimit, TEN, true, false, endStop, priorSpeed);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsFastStartEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getRawMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed1, trainMotionList.get(1).v, 0.001);
        assertEquals(ZERO, trainMotionList.get(trainMotionList.size() - 1).v, 0.001);
        assertEquals(TEN, trainMotionList.get(trainMotionList.size() - 1).x, 0.001);
    }

    @Test
    public void testTrainMotionsFastStartNoEndStop() {
        int priorSpeed = 40;
        int speed1 = priorSpeed - 1;
        int speedLimit = 30;
        boolean endStop = false;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, ZERO, speedLimit, TEN, true, false, endStop, priorSpeed);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsFastStartNoEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getRawMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed1, trainMotionList.get(1).v, 0.001);
        assertEquals(speedLimit, trainMotionList.get(trainMotionList.size() - 1).v, 0.001);
        assertEquals(TEN, trainMotionList.get(trainMotionList.size() - 1).x, 0.001);
    }

    @Test
    public void testTrainMotionsSlowStartEndStop() {
        int priorSpeed = 30;
        int speed1 = priorSpeed + 1;
        int speedLimit = 40;
        boolean endStop = true;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, ZERO, speedLimit, TEN, true, false, endStop, priorSpeed);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsSlowStartEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getRawMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed1, trainMotionList.get(1).v, 0.001);
        assertEquals(ZERO, trainMotionList.get(trainMotionList.size() - 1).v, 0.001);
        assertEquals(TEN, trainMotionList.get(trainMotionList.size() - 1).x, 0.001);
    }

    @Test
    public void testTrainMotionsSlowStartNoEndStop() {
        int priorSpeed = 30;
        int speed1 = priorSpeed + 1;
        int speedLimit = 40;
        boolean endStop = false;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, ZERO, speedLimit, TEN, true, false, endStop, priorSpeed);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsSlowStartNoEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getRawMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed1, trainMotionList.get(1).v, 0.001);
        assertEquals(speedLimit, trainMotionList.get(trainMotionList.size() - 1).v, 0.001);
        assertEquals(TEN, trainMotionList.get(trainMotionList.size() - 1).x, 0.001);
    }

    @Test
    public void testMultipleTrainMotions() {
        Map<Integer, List<Engine>> engines = new HashMap<>();
        engines.put(ZERO, registerNewEngine("NKP", "500", "FT", "Diesel"));
        engines.put(1, registerNewEngine("NKP", "501", "GP40", "Diesel"));
        engines.put(2, registerNewEngine("NKP", "502", "GP40", "Diesel"));
        engines.put(3, registerNewEngine("NKP", "503", "RS1", "Diesel"));

        Map<Integer, List<Integer>> carWeights = new HashMap<>();
        carWeights.put(ZERO, new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25)));
        carWeights.put(1, new ArrayList<>(Arrays.asList(25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25)));
        carWeights.put(2, new ArrayList<>(Arrays.asList(25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25, 75, 25)));
        carWeights.put(3, new ArrayList<>(Arrays.asList(25, 75, 25, 75, 25, 25)));

        double[] gradePercent = new double[]{-1, -2, 3, ZERO};
        int speedLimit = 40; // MPS
        double[] distance = new double[]{TEN, TEN, TEN, TEN};
        boolean journal = true;
        boolean warm = false;

        for (int i = ZERO; i < 4; i++) {
            List<TrainMotion> trainMotionList = getTrainMotions(engines.get(i), carWeights.get(i), gradePercent[i], speedLimit, distance[i], journal, warm, false, ZERO);
            assertFalse(trainMotionList.isEmpty());
        }
    }

    private ArrayList<Integer> buildCarWeights(int cars) {
        boolean loaded = true;
        ArrayList<Integer> carWeights = new ArrayList<>();
        for (int i = ZERO; i < cars; i++) {
            carWeights.add(loaded ? 75 : 25);
            loaded = !loaded;
        }
        return carWeights;
    }

    private List<Engine> registerNewEngine(String road, String number, String model, String type) {
        Engine engine = new Engine(road, number);
        engine.setModel(model);
        engine.setTypeName(type);
        InstanceManager.getDefault(EngineManager.class).register(engine);

        List<Engine> engines = new ArrayList<>();
        engines.add(engine);

        return engines;
    }

}