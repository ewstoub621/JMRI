package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.rollingstock.engines.Engine;
import jmri.jmrit.operations.rollingstock.engines.EngineManager;
import org.junit.jupiter.api.Test;

import java.util.*;

import static jmri.jmrit.operations.trains.TrainPhysics.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainPhysicsTest {
    private static final int ZERO = 0;
    private static final int TEN = 10;
    private static final boolean DEBUG = true;

    // ref Train Forces Calculator by AAK (https://web.archive.org/web/20090408120433/http://www.alkrug.vcn.com/rrfacts/RRForcesCalc.html)
    private static final float X_LOCO_HP = 12000f ;
    private static final float X_LOCO_TONS = 820f;
    private static final float X_LOCO_EFF = 85f; // percentage units
    private static final float X_CAR_COUNT = 82f;
    private static final float X_CAR_FACE_AREA = 110f; // square feet
    private static final float X_AXLES_PER_CAR = 4f;
    private static final float X_TRAIN_TONS = 6973f;
    private static final float X_GRADE_PERCENT = 1.25f;
    private static final float X_CURVE_DEGREES = 2.6f;
    // --> values
    private static final float SPEED = 15.7f;
    private static final float LOCO_HP = X_LOCO_HP * X_LOCO_EFF / 100.0f;
    private static final float TOTAL_TONS = X_TRAIN_TONS + X_LOCO_TONS;
    private static final float CAR_COUNT = X_CAR_COUNT + X_LOCO_TONS / 180f;
    private static final float AXLE_COUNT = CAR_COUNT * X_AXLES_PER_CAR;
    // --> results
    private static float ADHESION_DRAG_LBS() { return 1.3f * TOTAL_TONS; }
    private static float AIR_DRAG_LBS() { return 0.0005f * X_CAR_FACE_AREA * CAR_COUNT * SPEED * SPEED; }
    private static float BEARING_DRAG_LBS() { return 29f * AXLE_COUNT; }
    private static float CURVE_DRAG_LBS() { return X_CURVE_DEGREES * TOTAL_TONS; }
    private static float FLANGE_DRAG_LBS() { return 0.045f * SPEED * TOTAL_TONS; }
    private static float GRADE_DRAG_LBS() { return X_GRADE_PERCENT * 20 * TOTAL_TONS; }
    private static float ROLLING_DRAG_LBS() { return ADHESION_DRAG_LBS() + AIR_DRAG_LBS() + BEARING_DRAG_LBS() + FLANGE_DRAG_LBS(); }
    private static float TOTAL_DRAG_LBS() { return ROLLING_DRAG_LBS() + GRADE_DRAG_LBS() + CURVE_DRAG_LBS(); }

    private static float REQUIRED_HP() { return TOTAL_DRAG_LBS() * SPEED * 0.002667f; }
    private static float CURVE_HP() { return CURVE_DRAG_LBS() * SPEED * 1.4666f / 550; }
    private static float GRADE_HP() { return GRADE_DRAG_LBS() * SPEED * 1.4666f / 550; }
    private static float ROLLING_HP() { return ROLLING_DRAG_LBS() * SPEED * 1.4666f / 550; }
    private static float TOTAL_HP() { return TOTAL_DRAG_LBS() * SPEED * 1.4666f / 550; }

    @Test
    void testStaticResults() {
        assertTrue(REQUIRED_HP() < LOCO_HP);

        assertEquals(26905, ROLLING_DRAG_LBS(), 55);
        assertEquals(194825, GRADE_DRAG_LBS(), 1);
        assertEquals(20261, CURVE_DRAG_LBS(), 1);
        assertEquals(241992, TOTAL_DRAG_LBS(), 65);
        assertEquals(1133, ROLLING_HP(), 11);
        assertEquals(8208, GRADE_HP(), 52);
        assertEquals(853, CURVE_HP(), 5);
        assertEquals(10195, TOTAL_HP(), 67);
    }

    @Test
    public void testGetNewSpeedTrainMotion() {
        List<Integer> carWeights = buildCarWeights(TEN);
        int driverWeight = 115;
        int engineWeight = 115;
        int fullPower = 1350;
        int gradePercent = 1;
        double distance = TEN;
        TrainMotion tm = new TrainMotion("testing");
        double distanceLimit;
        int steps = 19;

        if (DEBUG) {
            System.out.println("\ntestGetNewSpeedTrainMotion:\n" + TrainMotion.getMotionsHeader());
        }
        for (int i = ZERO; i < steps; i++) {
            distanceLimit = distance - tm.x;
            double newSpeed = i + 1;
            tm = getNewTrainMotion(tm, newSpeed, driverWeight, engineWeight, carWeights, fullPower, gradePercent, distanceLimit);
            if (DEBUG && tm != null) {
                System.out.println(tm.getMotionData());
            }
            assertNotNull(tm);
            assertEquals(newSpeed, tm.v);
        }
        double accelTime = tm.t;
        for (int i = 1; i <= steps; i++) {
            distanceLimit = distance - tm.x;
            double newSpeed = steps - i;
            tm = getNewTrainMotion(tm, newSpeed, driverWeight, engineWeight, carWeights, fullPower, gradePercent, distanceLimit);
            if (DEBUG && tm != null) {
                System.out.println(tm.getMotionData());
            }
            assertNotNull(tm);
            assertEquals(newSpeed, tm.v);
        }
        assertEquals(ZERO, tm.v);
        if (DEBUG) {
            System.out.println("acceleration elapsed time = " + accelTime);
            System.out.println("deceleration elapsed time = " + (tm.t - accelTime));
        }
    }

    @Test
    public void testDrawbarPullLimit() {
        double limitLbs = 250000; // lbs

        double expectedLimitTons = limitLbs / LBS_PER_TON; // 125 tons
        assertEquals(expectedLimitTons, COUPLER_PULL_LIMIT_TONS);
    }

    @Test
    public void testAdhesionDrag() {
        // ref Train Forces Calculator by AAK
        assertEquals(ADHESION_DRAG_LBS() / LBS_PER_TON, getAdhesionDrag(TOTAL_TONS), 1);
    }

    @Test
    public void testAirDrag() {
        // ref Train Forces Calculator by AAK
        assertEquals(AIR_DRAG_LBS() / LBS_PER_TON, getAirDrag(X_CAR_COUNT, SPEED, 110), 1);
    }

    @Test
    public void testBearingDrag() {
        // ref Train Forces Calculator by AAK
        assertEquals(BEARING_DRAG_LBS() / LBS_PER_TON, getBearingDrag(AXLE_COUNT), 1);
    }

    @Test
    public void testCruisePower() {
        assertEquals(2133.333, netCruisePower(10, 40), .001);
    }

    @Test
    public void testCurveDrag() {
        // ref Train Forces Calculator by AAK
        assertEquals(CURVE_DRAG_LBS() / LBS_PER_TON, getCurveDrag(X_CURVE_DEGREES, TOTAL_TONS), 1);
    }

    @Test
    public void testFlangeDrag() {
        // ref Train Forces Calculator by AAK
        assertEquals(FLANGE_DRAG_LBS() / LBS_PER_TON, getFlangeDrag(TOTAL_TONS, SPEED), 1);
    }

    @Test
    public void testGradeDrag() {
        // ref Train Forces Calculator by AAK
        assertEquals(GRADE_DRAG_LBS() / LBS_PER_TON, getGradeDrag(X_LOCO_TONS + X_TRAIN_TONS, X_GRADE_PERCENT), 1);
    }

    @Test
    public void testRollingDrag() {

        // ref Train Forces Calculator by AAK
        float lbsPerTon = (float) LBS_PER_TON;
        float adhesionDragAAK = ADHESION_DRAG_LBS() / lbsPerTon; // 5.0655
        float airDragAAK = AIR_DRAG_LBS() / lbsPerTon; // 0.58671
        float bearingDragAAK = BEARING_DRAG_LBS() / lbsPerTon; // 5.0202
        float flangeDragAAK = FLANGE_DRAG_LBS() / lbsPerTon; // 2.75288
        float rollingDragAAK = ROLLING_DRAG_LBS() / lbsPerTon; // 13.42526
        float expectRollingDragAAK = adhesionDragAAK + airDragAAK + bearingDragAAK + flangeDragAAK; // 13.42526

        // using TrainPhysics calculations
        double adhesionDragTP = getAdhesionDrag(TOTAL_TONS); // 5.0655
        double airDragTP = getAirDrag(X_CAR_COUNT, SPEED, 110); // 0.555835
        double bearingDragTP = getBearingDrag(AXLE_COUNT); // 5.0202
        double flangeDragTP = getFlangeDrag(TOTAL_TONS, SPEED); // 2.75288
        double rollingDragTP = getRollingDrag(TOTAL_TONS, SPEED, AXLE_COUNT, CAR_COUNT, 110); // 13.42526
        double expectRollingDragTP = adhesionDragTP + airDragTP + bearingDragTP + flangeDragTP; // 13.39438

        assertEquals(expectRollingDragAAK, rollingDragAAK, .0001);
        assertEquals(expectRollingDragAAK, rollingDragTP, .0001);
        assertEquals(expectRollingDragTP, rollingDragTP, .033);
    }

    @Test
    public void testStartingDrag() {
        double weight = 2000;
        double expectedColdJournal = 35;
        double expectedWarmJournal = 25;
        double expectedColdRoller = 15;
        double expectedWarmRoller = 5;

        assertEquals(expectedColdJournal, getStartingDrag(weight, false, false));
        assertEquals(expectedWarmJournal, getStartingDrag(weight, false, true));
        assertEquals(expectedColdRoller, getStartingDrag(weight, true, false));
        assertEquals(expectedWarmRoller, getStartingDrag(weight, true, true));
    }

    @Test
    public void testStretchMotion() {
        double engineWeight = 728;
        double driverWeight = STEAMER_DRIVER_WEIGHT_PER_ENGINE_WEIGHT * engineWeight;
        List<Integer> carWeights = Arrays.asList(75, 25, 75, 25, 75, 25, 75, 25, 75);
        double fullPower = 9000;
        double gradePercent = 2.5;

        assertNotNull(getStretchMotion(driverWeight, engineWeight, carWeights, fullPower, gradePercent));
    }

    @Test
    public void testGetTractiveForce() {
        assertEquals(TON_FORCE_BY_MPH_PER_HP, getTractiveForce(20, 20), 00001);
    }

    @Test
    public void testTrainMotionsDeadStartEndStop() {
        int priorSpeed = ZERO;
        int speed2 = priorSpeed + 2;
        int speedLimit = 40;
        boolean endStop = true;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, 110, ZERO, 2.5, priorSpeed, speedLimit, TEN, endStop);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsDeadStartEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed2, trainMotionList.get(2).v, 0.001);

        TrainMotion finalTrainMotion = TrainMotion.getFinalTrainMotion(trainMotionList);
        assertNotNull(finalTrainMotion);
        assertEquals(ZERO, finalTrainMotion.v, 0.001);
        assertEquals(TEN, finalTrainMotion.x, 0.001);
    }

    @Test
    public void testTrainMotionsDeadStartNoEndStop() {
        int priorSpeed = ZERO;
        int speed2 = priorSpeed + 2;
        int speedLimit = 40;
        boolean endStop = false;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, 110, ZERO, 2.5, priorSpeed, speedLimit, TEN, endStop);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsDeadStartNoEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed2, trainMotionList.get(2).v, 0.001);

        TrainMotion finalTrainMotion = TrainMotion.getFinalTrainMotion(trainMotionList);
        assertNotNull(finalTrainMotion);
        assertEquals(speedLimit, finalTrainMotion.v, 2);
        assertEquals(TEN, finalTrainMotion.x, 0.001);
    }

    @Test
    public void testTrainMotionsFastStartEndStop() {
        int priorSpeed = 40;
        int speed1 = priorSpeed - 1;
        int speedLimit = 30;
        boolean endStop = true;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, 110, ZERO, 2.5, priorSpeed, speedLimit, TEN, endStop);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsFastStartEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed1, trainMotionList.get(1).v, 0.001);

        TrainMotion finalTrainMotion = TrainMotion.getFinalTrainMotion(trainMotionList);
        assertNotNull(finalTrainMotion);
        assertEquals(ZERO, finalTrainMotion.v, 0.001);
        assertEquals(TEN, finalTrainMotion.x, 0.001);
    }

    @Test
    public void testTrainMotionsFastStartNoEndStop() {
        int priorSpeed = 40;
        int speed1 = priorSpeed - 1;
        int speedLimit = 30;
        boolean endStop = false;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, 110, ZERO, 2.5, priorSpeed, speedLimit, TEN, endStop);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsFastStartNoEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed1, trainMotionList.get(1).v, 0.001);

        TrainMotion finalTrainMotion = TrainMotion.getFinalTrainMotion(trainMotionList);
        assertNotNull(finalTrainMotion);
        assertEquals(speedLimit, finalTrainMotion.v, 0.001);
        assertEquals(TEN, finalTrainMotion.x, 0.001);
    }

    @Test
    public void testTrainMotionsSlowStartEndStop() {
        int priorSpeed = 30;
        int speed1 = priorSpeed + 1;
        int speedLimit = 40;
        boolean endStop = true;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, 110, ZERO, 2.5, priorSpeed, speedLimit, TEN, endStop);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsSlowStartEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed1, trainMotionList.get(1).v, 0.001);

        TrainMotion finalTrainMotion = TrainMotion.getFinalTrainMotion(trainMotionList);
        assertNotNull(finalTrainMotion);
        assertEquals(ZERO, finalTrainMotion.v, 0.001);
        assertEquals(TEN, finalTrainMotion.x, 0.001);
    }

    @Test
    public void testTrainMotionsSlowStartNoEndStop() {
        int priorSpeed = 30;
        int speed1 = priorSpeed + 1;
        int speedLimit = 40;
        boolean endStop = false;

        List<Engine> engines = registerNewEngine("NKP", "500", "FT", "Diesel");
        ArrayList<Integer> carWeights = new ArrayList<>(Arrays.asList(75, 25, 75, 25, 75, 25));

        List<TrainMotion> trainMotionList = getTrainMotions(engines, carWeights, 110, ZERO, 2.5, priorSpeed, speedLimit, TEN, endStop);
        if (DEBUG) {
            System.out.println("\ntestTrainMotionsSlowStartNoEndStop:\n" + TrainMotion.getMotionsHeader());
            for (TrainMotion tm : trainMotionList) {
                System.out.println(tm.getMotionData());
            }
        }

        assertFalse(trainMotionList.isEmpty());
        assertEquals(priorSpeed, trainMotionList.get(ZERO).v, 0.001);
        assertEquals(speed1, trainMotionList.get(1).v, 0.001);

        TrainMotion finalTrainMotion = TrainMotion.getFinalTrainMotion(trainMotionList);
        assertNotNull(finalTrainMotion);
        assertEquals(speedLimit, finalTrainMotion.v, 2);
        assertEquals(TEN, finalTrainMotion.x, 0.001);
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

        for (int i = ZERO; i < 4; i++) {
            List<TrainMotion> trainMotionList = getTrainMotions(engines.get(i), carWeights.get(i), 110, gradePercent[i], 2.5, ZERO, speedLimit, distance[i], false);
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

/*
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
        if (DEBUG) {
            System.out.println("\ntestMotions:\n" + trainPhysics);
        }
    }
*/

}