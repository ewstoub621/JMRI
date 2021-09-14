package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.OperationsTestCase;
import jmri.jmrit.operations.locations.Track;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.setup.Setup;
import jmri.jmrit.operations.setup.TrainRevenues;
import jmri.util.JUnitOperationsUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * TrainCsvRevenueTest tests a train's revenue report
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainCsvRevenueTest extends OperationsTestCase {
    private static final String ROWS_IN_REVENUE = "confirm number of rows in Revenue\n";
    private static final String COLS_IN_REVENUE = "confirm number of cols in Revenue\n";
    private static final int EXPECTED_ROWS = 41;
    private static final int EXPECTED_COLS = 6;
    private static final int ROWS = 0, COLS = 1;

    private Locale defaultLocale;
    private int[] dimensions;
    private int expected_cols, expected_rows;

    @Test
    public void testCTor() {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test CTor");
        Assertions.assertTrue(train1.build());
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        TrainCsvRevenue t = new TrainCsvRevenue(train1);
        Assertions.assertNotNull(t, "exists");

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        defaultLocale = Locale.getDefault(); // save the default locale.
    }

    @Override
    @AfterEach
    public void tearDown() {
        Locale.setDefault(defaultLocale);
        super.tearDown();
    }

    @Test
    public void testCreateCsvRevenueWithDefault_TransportCharges() throws IOException {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test With Default - Transport Charges");
        Assertions.assertTrue(train1.build());
        expected_cols = EXPECTED_COLS - 1; // no spurs, no customer capacity, so no discounts
        expected_rows = EXPECTED_ROWS;
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    @Test
    public void testCreateCsvRevenueWithAllCharges() throws IOException {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test With All Charges");
        Assertions.assertTrue(train1.build());
        int[] addDemurrage = addDemurrage(train1);
        int addSwitching = addSwitching(train1);
        expected_cols = EXPECTED_COLS
                + addSwitching
                + addDemurrage[0]
                + addHazards(train1)
                + addCancelMulct(train1)
                + addDivertMulct(train1);
        expected_rows = EXPECTED_ROWS
                + addSwitching
                + addDemurrage[1];
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        TreeMap<Integer, List<String>> cellMap = checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    @Test
    public void testCreateCsvRevenueWithAllRestarts() throws IOException {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test With All Restarts");

        train1.build();
        Assertions.assertTrue(train1.isBuilt());
        Assertions.assertTrue(
                InstanceManager.getDefault(TrainManagerXml.class).getTrainRevenuesSerFile(train1).exists());

        TrainRevenues trainRevenues = train1.getTrainRevenues();
        Assertions.assertNotNull(trainRevenues);

        Map<String, String[]> origRouteIdsByCarKey = trainRevenues.getOrigRouteIdsByCarKey();
        Assertions.assertNotNull(origRouteIdsByCarKey);

        train1.setTrainRevenues(null);
        Assertions.assertTrue(
                InstanceManager.getDefault(TrainManagerXml.class).getTrainRevenuesSerFile(train1).exists());

        train1.move();
        Assertions.assertTrue(
                InstanceManager.getDefault(TrainManagerXml.class).getTrainRevenuesSerFile(train1).exists());

        Map<String, String[]> origRouteIdsByCarKey1 = trainRevenues.getOrigRouteIdsByCarKey();
        Assertions.assertNotNull(origRouteIdsByCarKey1);
        Assertions.assertEquals(origRouteIdsByCarKey, origRouteIdsByCarKey1);

        train1.setTrainRevenues(null);
        Assertions.assertTrue(
                InstanceManager.getDefault(TrainManagerXml.class).getTrainRevenuesSerFile(train1).exists());

        train1.move();
        Assertions.assertTrue(
                InstanceManager.getDefault(TrainManagerXml.class).getTrainRevenuesSerFile(train1).exists());

        Map<String, String[]> origRouteIdsByCarKey2 = trainRevenues.getOrigRouteIdsByCarKey();
        Assertions.assertNotNull(origRouteIdsByCarKey2);
        Assertions.assertEquals(origRouteIdsByCarKey, origRouteIdsByCarKey2);

        train1.setTrainRevenues(null);
        Assertions.assertTrue(
                InstanceManager.getDefault(TrainManagerXml.class).getTrainRevenuesSerFile(train1).exists());

        expected_cols = EXPECTED_COLS - 1; // no spurs, no customer capacity, so no discounts
        expected_rows = EXPECTED_ROWS;

        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    @Test
    public void testCreateCsvRevenueWithCancelMulct() throws IOException {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test With Cancel Mulct");
        Assertions.assertTrue(train1.build());
        int addSwitching = addSwitching(train1);
        expected_cols = EXPECTED_COLS + addSwitching + addCancelMulct(train1);
        expected_rows = EXPECTED_ROWS + addSwitching;
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    @Test
    public void testCreateCsvRevenueWithDemurrage() throws IOException {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test With Demurrage");
        Assertions.assertTrue(train1.build());
        int[] addDemurrage = addDemurrage(train1);
        int addSwitching = addSwitching(train1);
        expected_cols = EXPECTED_COLS + addSwitching + addDemurrage[0];
        expected_rows = EXPECTED_ROWS + addSwitching + addDemurrage[1];
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    @Test
    public void testCreateCsvRevenueWithDivertMulct() throws IOException {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test With Divert Mulct");
        Assertions.assertTrue(train1.build());
        int addSwitching = addSwitching(train1);
        expected_cols = EXPECTED_COLS + addSwitching + addDivertMulct(train1);
        expected_rows = EXPECTED_ROWS + addSwitching;
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    @Test
    public void testCreateCsvRevenueWithFrenchLocale() throws IOException {
        Locale.setDefault(Locale.FRANCE);
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test With French Locale");
        Assertions.assertTrue(train1.build());
        int addSwitching = addSwitching(train1);
        expected_cols = EXPECTED_COLS
                + addSwitching
                + addHazards(train1)
                + addCancelMulct(train1);
        expected_rows = EXPECTED_ROWS + addSwitching;
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
        Locale.setDefault(Locale.US);
    }

    @Test
    public void testCreateCsvRevenueWithHazard() throws IOException {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test With Hazard");
        Assertions.assertTrue(train1.build());
        int addSwitching = addSwitching(train1);
        expected_cols = EXPECTED_COLS + addSwitching + addHazards(train1);
        expected_rows = EXPECTED_ROWS + addSwitching;
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    @Test
    public void testCreateCsvRevenueWithSwitching() throws IOException {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("1");
        updateRailroadName(train1, ": Test With Switching");
        Assertions.assertTrue(train1.build());
        int addSwitching = addSwitching(train1);
        expected_cols = EXPECTED_COLS + addSwitching;
        expected_rows = EXPECTED_ROWS + addSwitching;
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    @Test
    public void testCreateCsvRevenueWithSwitchingTrain2() throws IOException {
        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        Train train1 = InstanceManager.getDefault(TrainManager.class).getTrainById("2");
        updateRailroadName(train1, ": Test With Switching");
        Assertions.assertTrue(train1.build());
        int addSwitching = addSwitching(train1);
        expected_cols = EXPECTED_COLS + addSwitching;
        expected_rows = EXPECTED_ROWS + addSwitching;
        train1.terminate();
        Assertions.assertFalse(train1.isBuilt());

        checkCsvRevenueFileContents(train1);

        JUnitOperationsUtil.checkOperationsShutDownTask();
    }

    private int addCancelMulct(Train train1) {
        int limit = 1;
        boolean cancel = true;
        for (Car car : new HashSet<>(InstanceManager.getDefault(CarManager.class).getList(train1))) {
            if (isTrainFreightCar(train1, car) && cancel && limit > 0) {
                car.setRouteLocation(null);
                car.setRouteDestination(null);
                limit--;
            }
            cancel = !cancel;
        }
        return 1;
    }

    private int[] addDemurrage(Train train1) {
        RouteLocation rl = train1.getCurrentRouteLocation();
        RouteLocation rlNext = train1.getNextRouteLocation(rl);
        if (rlNext != null && rlNext.getLocation() != null) {
            for (Car car : rlNext.getLocation().getCarsOnTracks()) {
                car.setWait(4);
            }
        }
        return new int[]{1, 2};
    }

    private int addDivertMulct(Train train1) {
        int limit = 1;
        boolean divert = false;
        for (Car car : new HashSet<>(InstanceManager.getDefault(CarManager.class)
                                             .getList(train1))) {
            if (isTrainFreightCar(train1, car) && divert && limit > 0) {
                RouteLocation currentRl = car.getRouteDestination();
                if (currentRl != null) {
                    for (RouteLocation rl : train1.getRoute().getLocationsBySequenceList()) {
                        if (currentRl != rl) {
                            car.setRouteDestination(rl);
                            limit--;
                        }
                    }
                }
            }
            divert = !divert;
        }
        return 1;
    }

    private int addHazards(Train train1) {
        HashSet<Car> cars = new HashSet<>(InstanceManager.getDefault(CarManager.class)
                                                  .getList(train1));
        boolean hazard = false;
        for (Car car : cars) {
            if (isTrainFreightCar(train1, car)) {
                car.setHazardous((hazard = !hazard));
            }
        }
        return 1;
    }

    private int addSwitching(Train train1) {
        for (Track track : train1.getNextRouteLocation(train1.getCurrentRouteLocation())
                .getLocation().getTracksList()) {
            track.setTrackType(Track.SPUR);
        }
        return 1;
    }

    private TreeMap<Integer, List<String>> checkCsvRevenueFileContents(Train train1) throws IOException {
        File file = train1.getTrainRevenues().getCsvRevenueFile(train1);
        Assertions.assertNotNull(file, "exists");

        dimensions = new int[]{0, 0};
        TreeMap<Integer, List<String>> integerListTreeMap = processSheetAsString(file);
        String sheet = integerListTreeMap.toString();
        Assertions.assertEquals(expected_rows, dimensions[ROWS], ROWS_IN_REVENUE + sheet);
        Assertions.assertEquals(expected_cols, dimensions[COLS], COLS_IN_REVENUE + sheet);

        return integerListTreeMap;
    }

    private boolean isTrainFreightCar(Train train1, Car car) {
        return car.getTrain() == train1
                && !car.isCaboose()
                && !car.isPassenger();
    }

    private TreeMap<Integer, List<String>> processSheetAsString(File file) throws IOException {
        BufferedReader in = JUnitOperationsUtil.getBufferedReader(file);

        TreeMap<Integer, List<String>> map = new TreeMap<>();
        String line;
        while ((line = in.readLine()) != null) {
            dimensions[ROWS]++;
            String[] cells = line.split(",");
            List<String> rowCells = new ArrayList<>();
            for (String cell : cells) {
                rowCells.add(cell);
            }
            map.put(dimensions[ROWS], rowCells);
            if (dimensions[ROWS] == expected_rows - 1) {
                dimensions[COLS] = Math.max(rowCells.size(), dimensions[COLS]);
            }
        }
        in.close();

        return map;
    }

    private void updateRailroadName(Train train1, String testDescription) {
        String railRoadName = train1.getRailroadName().isEmpty() ? Setup.getRailroadName() : train1.getRailroadName();
        train1.setRailroadName(railRoadName + testDescription);
    }

}
