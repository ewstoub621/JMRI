package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.OperationsTestCase;
import jmri.jmrit.operations.locations.LocationManager;
import jmri.jmrit.operations.locations.Track;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.cars.CarTypes;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.routes.RouteManager;
import jmri.jmrit.operations.setup.Setup;
import jmri.util.JUnitOperationsUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static jmri.jmrit.operations.trains.Train.NONE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TrainCsvRevenueTest tests a train's revenue report
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainCsvRevenueTest extends OperationsTestCase {
    private static final String LOCATION_ID_HEAD = "1";
    private static final String LOCATION_ID_SPUR = "20";
    private static final String LOCATION_ID_TERM = "3";

    private static final String TRACK_ID_HEAD = "1s1";
    private static final String TRACK_ID_SPUR = "20s1";
    private static final String TRACK_ID_TERM1 = "3s1";
    private static final String TRACK_ID_TERM2 = "3s2";

    private static final List<String> CAR_SET_A = Arrays.asList("CP777", "CPX10001", "CPX10002", "CP888");
    private static final List<String> CAR_SET_B = Arrays.asList("CP99", "CP888", "CPX10001", "CP777");

    private CarManager carManager;
    private CarTypes carTypes;
    private LocationManager locationManager;
    private RouteManager routeManager;
    private TrainManager trainManager;
    private TrainManagerXml trainManagerXml;

    private Locale defaultLocale;
    private List<Track> tracks;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();

        carManager = InstanceManager.getDefault(CarManager.class);
        carTypes = InstanceManager.getDefault(CarTypes.class);
        locationManager = InstanceManager.getDefault(LocationManager.class);
        routeManager = InstanceManager.getDefault(RouteManager.class);
        trainManager = InstanceManager.getDefault(TrainManager.class);
        trainManagerXml = InstanceManager.getDefault(TrainManagerXml.class);

        File csvRevenueDirectory = new File(trainManagerXml.getDefaultTrainCsvRevenueDirectory());
        if (csvRevenueDirectory.exists()) {
            assertTrue(csvRevenueDirectory.delete());
        }

        JUnitOperationsUtil.initOperationsData();
        Setup.setSaveTrainRevenuesEnabled(true);

        defaultLocale = Locale.getDefault(); // save the default locale.
    }

    @Override
    @AfterEach
    public void tearDown() {
        JUnitOperationsUtil.checkOperationsShutDownTask();
        Locale.setDefault(defaultLocale);

        super.tearDown();
    }

    @Test
    public void testCTor() throws IOException {
        Train train = trainManager.getTrainById("1");
        updateRailroadName(train, ": Test CTor with Train 1");
        assertTrue(train.build());

        train.terminate();
        assertFalse(train.isBuilt());

        TrainCsvRevenue t = new TrainCsvRevenue(train);
        assertNotNull(t, "exists");
    }

    @Test
    public void testCreateCsvRevenueTrain1WithTransportCharges() throws IOException {
        int trainNumber = 1;

        Train train = setUpTrain(String.valueOf(trainNumber));
        updateRailroadName(train, ": test train #" + trainNumber + ", with transport charges");

        assertTrue(train.build());

        train.terminate();
        assertFalse(train.isBuilt());

        TreeMap<Integer, List<String>> cellMap = getCsvRevenueAsTreeMap(train);

        verifyTrain1CsvRevenueUS(cellMap);
    }

    @Test
    public void testCreateCsvRevenueTrain2WithAllChargesCarSetA() throws IOException {
        int trainNumber = 2;

        Train train = setUpTrain(String.valueOf(trainNumber));
        updateRailroadName(train, ": test train #" + trainNumber + ", with all charges");

        addDemurrage(CAR_SET_A.get(0));

        assertTrue(train.build());

        addSwitching();
        addHazardous(CAR_SET_A.get(1));
        addCancelMulct(CAR_SET_A.get(2));
        addDivertMulct(CAR_SET_A.get(3));

        train.terminate();
        assertFalse(train.isBuilt());

        TreeMap<Integer, List<String>> cellMap = getCsvRevenueAsTreeMap(train);

        verifyTrain2CsvRevenueUSCarSetA(cellMap);
    }

    @Test
    public void testCreateCsvRevenueTrain2WithAllRestartsCarSetB() throws IOException {
        int trainNumber = 2;

        Train train = setUpTrain(String.valueOf(trainNumber));
        updateRailroadName(train, ": test train #" + trainNumber + ", with all restarts");

        addDemurrage(CAR_SET_B.get(0));

        assertTrue(train.build());

        addSwitching();
        addHazardous(CAR_SET_B.get(1));
        addCancelMulct(CAR_SET_B.get(2));
        addDivertMulct(CAR_SET_B.get(3));

        verifyRestartMoveRestoreCycle(train);
        verifyRestartMoveRestoreCycle(train);

        train.terminate();
        assertFalse(train.isBuilt());

        TreeMap<Integer, List<String>> cellMap = getCsvRevenueAsTreeMap(train);

        verifyTrain2CsvRevenueUSCarSetB(cellMap);
    }

    @Test
    public void testCreateCsvRevenueTrain2WithLocaleFranceCarSetB() throws IOException {
        Locale.setDefault(Locale.FRANCE);

        try {
            int trainNumber = 2;

            Train train = setUpTrain(String.valueOf(trainNumber));
            updateRailroadName(train, ": test train #" + trainNumber + ", with Locale.FRANCE");

            addDemurrage(CAR_SET_B.get(0));

            assertTrue(train.build());

            addSwitching();
            addHazardous(CAR_SET_B.get(1));
            addCancelMulct(CAR_SET_B.get(2));
            addDivertMulct(CAR_SET_B.get(3));

            train.terminate();
            assertFalse(train.isBuilt());

            TreeMap<Integer, List<String>> cellMap = getCsvRevenueAsTreeMap(train);

            verifyTrain2CsvRevenueFRCarSetB(cellMap);
        } finally {
            Locale.setDefault(Locale.US);
        }
    }

    private void addCancelMulct(String carId) {
        Car car = carManager.getById(carId);
        car.setRouteLocation(null);
        car.setRouteDestination(null);
    }

    private void addDemurrage(String carId) {
        carManager.getById(carId).setWait(4);
    }

    private void addDivertMulct(String carId) {
        carManager.getById(carId).setRouteDestination(routeManager.getRouteById("1").getLocationById("1r2"));
    }

    private void addHazardous(String carId) {
        carManager.getById(carId).setHazardous(true);
    }

    private void addSwitching() {
        Track track = locationManager.getLocationById(LOCATION_ID_SPUR).getTrackById(TRACK_ID_SPUR);
        track.setTrackType(Track.SPUR);
        track.setName(track.getName().replace(Track.YARD, Track.SPUR));
    }

    private TreeMap<Integer, List<String>> getCsvRevenueAsTreeMap(Train train) throws IOException {
        TreeMap<Integer, List<String>> map = new TreeMap<>();

        File csvRevenueFile = train.getTrainRevenues().getCsvRevenueFile(train);
        assertNotNull(csvRevenueFile, "exists");

        int rows = 0;
        String line;
        BufferedReader in = JUnitOperationsUtil.getBufferedReader(csvRevenueFile);
        while ((line = in.readLine()) != null) {
            String[] cells = line.split(",");
            List<String> rowCells = new ArrayList<>();
            for (String cell : cells) {
                boolean added = false;
                if (cell != null && !cell.isEmpty() && Character.isDigit(cell.charAt(0))) {
                    int lastCellIndex = rowCells.size() - 1;
                    String lastCell = rowCells.get(lastCellIndex);
                    if (lastCell != null && !lastCell.isEmpty() && Character.isDigit(lastCell.charAt(lastCell.length() - 1))) {
                        String newCell = lastCell + ',' + cell;
                        rowCells.set(lastCellIndex, newCell);
                        added = true;
                    }
                }
                if (!added) {
                    rowCells.add(cell);
                }
            }
            map.put(rows++, rowCells);
        }
        in.close();

        return map;
    }

    private Train setUpTrain(String id) {
        Train train = trainManager.getTrainById(id);

        for (Car car : carManager.getList()) {
            if (car.getNumber().startsWith("X")) {
                car.setRoadName(car.getRoadName() + "X");
                car.setNumber(car.getNumber().substring(1));
                assertTrue(car.getRoadName().endsWith("X"));
                assertFalse(car.getNumber().startsWith("X"));
            }
        }

        updateTrackList();

        updateCar(train, "CPX", "10001", "Tank Oil", "L", 0, 1);
        updateCar(train, "CPX", "10002", "Tank Oil", "E", 0, 1);
        updateCar(train, "CPX", "10003", "Tank Oil", "L", 0, 1);
        updateCar(train, "CPX", "20001", "Coilcar", "L", 0, 1);

        updateCar(train, "CP", "777", "FlatWood", "L", 1, 2);
        updateCar(train, "CP", "888", "HopGrain", "L", 1, 2);
        updateCar(train, "CP", "99", "HopCoal", "L", 1, 2);
        updateCar(train, "CPX", "20002", "Tank Oil", "E", 1, 2);

        for (RouteLocation rl : routeManager.getRouteById("1").getLocationsBySequenceList()) {
            rl.setMaxCarMoves(20);
        }

        return train;
    }

    private void updateCar(Train train, String road, String number, String type, String load, int thisTrack, int lastTrack) {
        Car car = carManager.getById(road + number);
        if (car == null) {
            car = carManager.newRS(road, number);
            car.setBuilt("1984");
            car.setOwner("DAB");
            car.setLength("40");
            car.setMoves(0);
            car.setColor("Black");
        }

        updateLoadName(load, tracks, train, car);
        updateTypeName(type, carTypes, tracks, train, car);

        Track currentTrack = tracks.get(thisTrack);
        Track destinyTrack = tracks.get(lastTrack);

        car.setLocation(currentTrack.getLocation(), currentTrack);
        car.setDestination(destinyTrack.getLocation(), destinyTrack);
    }

    private void updateLoadName(String loadName, List<Track> tracks, Train train, Car car) {
        for (Track track : tracks) {
            track.addLoadName(loadName);
        }
        train.addLoadName(loadName);
        car.setLoadName(loadName);
    }

    private void updateRailroadName(Train train, String testDescription) {
        String railRoadName = train.getRailroadName().isEmpty() ? Setup.getRailroadName() : train.getRailroadName();
        train.setRailroadName(railRoadName + testDescription);
    }

    private void updateTrackList() {
        tracks = new ArrayList<>();

        tracks.add(locationManager.getLocationById(LOCATION_ID_HEAD).getTrackById(TRACK_ID_HEAD));
        tracks.add(locationManager.getLocationById(LOCATION_ID_SPUR).getTrackById(TRACK_ID_SPUR));
        tracks.add(locationManager.getLocationById(LOCATION_ID_TERM).getTrackById(TRACK_ID_TERM1));
        tracks.add(locationManager.getLocationById(LOCATION_ID_TERM).getTrackById(TRACK_ID_TERM2));
    }

    private void updateTypeName(String typeName, CarTypes carTypes, List<Track> tracks, Train train, Car car) {
        for (Track track : tracks) {
            track.getLocation().addTypeName(typeName);
            track.addTypeName(typeName);
        }
        train.addTypeName(typeName);
        carTypes.addName(typeName);
        if (car.getTrack() != null) {
            car.getTrack().addTypeName(typeName);
        }
        car.setTypeName(typeName);
    }

    private void verifyRestartMoveRestoreCycle(Train train) {
        assertTrue(trainManagerXml.getTrainRevenuesSerFile(train).exists());

        train.setTrainRevenues(null);
        assertTrue(trainManagerXml.getTrainRevenuesSerFile(train).exists());

        train.move();
        assertTrue(trainManagerXml.getTrainRevenuesSerFile(train).exists());
    }

    private void verifyTrain1CsvRevenueUS(TreeMap<Integer, List<String>> cellMap) {

        List<List<String>> expected = Arrays.asList(Arrays.asList("REV", "By Car", "For", "Switching", "Transport", "Hazard", "Customer", "Demurrage", "Cancelled", "Diverted", "Total"), Arrays.asList("\"\"", NONE, "Customer", "Tariff", "Tariff", "Fee", "Discount", "Fee", "Mulct", "Mulct", "Revenue"), Arrays.asList("RDR", "------- : (E) CPX   10002   - Tank Oil", "NI Yard", NONE, "$20.00", NONE, NONE, NONE, NONE, NONE, "$20.00"), Arrays.asList("RDR", "------- : (L) CPX   10001   - Tank Oil", "NI Yard", NONE, "$20.00", NONE, NONE, NONE, NONE, NONE, "$20.00"), Arrays.asList("RDR", "------- : (L) CPX   10003   - Tank Oil", "NI Yard", NONE, "$20.00", NONE, NONE, NONE, NONE, NONE, "$20.00"), Arrays.asList("RDR", "------- : (L) CPX   20001   - Coilcar", "NI Yard", NONE, "$20.00", NONE, NONE, NONE, NONE, NONE, "$20.00"), Arrays.asList("RDR", "------- : (E) CPX   20002   - Tank Oil", "South End 2", NONE, "$20.00", NONE, NONE, NONE, NONE, NONE, "$20.00"), Arrays.asList("RDR", "------- : (L) CP    777     - FlatWood", "South End 2", NONE, "$20.00", NONE, NONE, NONE, NONE, NONE, "$20.00"), Arrays.asList("RDR", "------- : (L) CP    888     - HopGrain", "South End 2", NONE, "$20.00", NONE, NONE, NONE, NONE, NONE, "$20.00"), Arrays.asList("RDR", "------- : (L) CP    99      - HopCoal", "South End 2", NONE, "$20.00", NONE, NONE, NONE, NONE, NONE, "$20.00"), Collections.singletonList(NONE), Arrays.asList("REV", "By Customer", "Discount", "Switching", "Transport", "Hazard", "Customer", "Demurrage", "Cancelled", "Diverted", "Total"), Arrays.asList("\"\"", NONE, "Rate", "Tariff", "Tariff", "Fee", "Discount", "Fee", "Mulct", "Mulct", "Revenue"), Arrays.asList("RDR", "NI Yard", NONE, NONE, "$80.00", NONE, NONE, NONE, NONE, NONE, "$80.00"), Arrays.asList("RDR", "South End 2", NONE, NONE, "$80.00", NONE, NONE, NONE, NONE, NONE, "$80.00"), Collections.singletonList(NONE), Arrays.asList("REV", "By Train", "Route", "Switching", "Transport", "Hazard", "Customer", "Demurrage", "Cancelled", "Diverted", "Total"), Arrays.asList("\"\"", NONE, "Rate", "Tariff", "Tariff", "Fee", "Discount", "Fee", "Mulct", "Mulct", "Revenue"), Arrays.asList("RDR", "Train STF", "$40.00", NONE, "$160.00", NONE, NONE, NONE, NONE, NONE, "$160.00"));

        int rowIndex = 30;
        for (List<String> expectedList : expected) {
            List<String> actualList = cellMap.get(rowIndex++);
            assertEquals(expectedList.size(), actualList.size(), "row size mismatch in row " + rowIndex + "\n\t" + expectedList + "\n\t" + actualList);
            for (int i = 0; i < expectedList.size(); i++) {
                String expectedValue = expectedList.get(i);
                String actualValue = actualList.get(i);
                assertEquals(expectedValue, actualValue, "mismatch in row " + rowIndex + " at column " + (i + 1) + "\n\t" + expectedList + "\n\t" + actualList);
            }
        }
    }

    private void verifyTrain2CsvRevenueUSCarSetA(TreeMap<Integer, List<String>> cellMap) {

        List<List<String>> expected = Arrays.asList(Arrays.asList("REV", "By Car", "For", "Switching", "Transport", "Hazard", "Customer", "Demurrage", "Cancelled", "Diverted", "Total"), Arrays.asList("\"\"", NONE, "Customer", "Tariff", "Tariff", "Fee", "Discount", "Fee", "Mulct", "Mulct", "Revenue"), Arrays.asList("RDR", "------- : (E) CPX   10002   - Tank Oil", "NI Spur", NONE, NONE, NONE, NONE, NONE, "$150.00", NONE, "$150.00"), Arrays.asList("RDR", "------- : (L) CP    777     - FlatWood", "NI Spur", NONE, NONE, NONE, NONE, "$100.00", NONE, NONE, "$100.00"), Arrays.asList("RDR", "Pick up : (E) CPX   20002   - Tank Oil", "NI Spur", "$150.00", "$20.00", NONE, "$42.50", NONE, NONE, NONE, "$127.50"), Arrays.asList("RDR", "Pick up : (L) CP    888     - HopGrain", "NI Spur", "$250.00", NONE, NONE, "$62.50", NONE, NONE, "$250.00", "$437.50"), Arrays.asList("RDR", "Pick up : (L) CP    99      - HopCoal", "NI Spur", "$250.00", "$20.00", NONE, "$67.50", NONE, NONE, NONE, "$202.50"), Arrays.asList("RDR", "Set out : (L) CPX   10001   - Tank Oil", "NI Spur", "$250.00", "$20.00", "$150.00", "$105.00", NONE, NONE, NONE, "$315.00"), Arrays.asList("RDR", "Set out : (L) CPX   10003   - Tank Oil", "NI Spur", "$250.00", "$20.00", NONE, "$67.50", NONE, NONE, NONE, "$202.50"), Arrays.asList("RDR", "Set out : (L) CPX   20001   - Coilcar", "NI Spur", "$300.00", "$20.00", NONE, "$80.00", NONE, NONE, NONE, "$240.00"), Collections.singletonList(NONE), Arrays.asList("REV", "By Customer", "Discount", "Switching", "Transport", "Hazard", "Customer", "Demurrage", "Cancelled", "Diverted", "Total"), Arrays.asList("\"\"", NONE, "Rate", "Tariff", "Tariff", "Fee", "Discount", "Fee", "Mulct", "Mulct", "Revenue"), Arrays.asList("RDR", "NI Spur", "25.00%", "\"$1,450.00\"", "$100.00", "$150.00", "$425.00", "$100.00", "$150.00", "$250.00", "\"$1,775.00\""), Collections.singletonList(NONE), Arrays.asList("REV", "By Train", "Route", "Switching", "Transport", "Hazard", "Customer", "Demurrage", "Cancelled", "Diverted", "Total"), Arrays.asList("\"\"", NONE, "Rate", "Tariff", "Tariff", "Fee", "Discount", "Fee", "Mulct", "Mulct", "Revenue"), Arrays.asList("RDR", "Train SFF", "$40.00", "\"$1,450.00\"", "$100.00", "$150.00", "$425.00", "$100.00", "$150.00", "$250.00", "\"$1,775.00\""));

        int rowIndex = 29;
        for (List<String> expectedList : expected) {
            List<String> actualList = cellMap.get(rowIndex++);
            assertEquals(expectedList.size(), actualList.size(), "row size mismatch in row " + rowIndex + "\n\t" + expectedList + "\n\t" + actualList);
            for (int i = 0; i < expectedList.size(); i++) {
                String expectedValue = expectedList.get(i);
                String actualValue = actualList.get(i);
                assertEquals(expectedValue, actualValue, "mismatch in row " + rowIndex + " at column " + (i + 1) + "\n\t" + expectedList + "\n\t" + actualList);
            }
        }
    }

    private void verifyTrain2CsvRevenueUSCarSetB(TreeMap<Integer, List<String>> cellMap) {

        List<List<String>> expected = Arrays.asList(Arrays.asList("REV", "By Car", "For", "Switching", "Transport", "Hazard", "Customer", "Demurrage", "Cancelled", "Diverted", "Total"), Arrays.asList("\"\"", NONE, "Customer", "Tariff", "Tariff", "Fee", "Discount", "Fee", "Mulct", "Mulct", "Revenue"), Arrays.asList("RDR", "------- : (L) CP    99      - HopCoal", "NI Spur", NONE, NONE, NONE, NONE, "$100.00", NONE, NONE, "$100.00"), Arrays.asList("RDR", "------- : (L) CPX   10001   - Tank Oil", "NI Spur", NONE, NONE, NONE, NONE, NONE, "$150.00", NONE, "$150.00"), Arrays.asList("RDR", "Pick up : (E) CPX   20002   - Tank Oil", "NI Spur", "$150.00", "$20.00", NONE, "$42.50", NONE, NONE, NONE, "$127.50"), Arrays.asList("RDR", "Pick up : (L) CP    777     - FlatWood", "NI Spur", "$350.00", NONE, NONE, "$87.50", NONE, NONE, "$250.00", "$512.50"), Arrays.asList("RDR", "Pick up : (L) CP    888     - HopGrain", "NI Spur", "$250.00", "$20.00", "$150.00", "$105.00", NONE, NONE, NONE, "$315.00"), Arrays.asList("RDR", "Set out : (E) CPX   10002   - Tank Oil", "NI Spur", "$150.00", "$20.00", NONE, "$42.50", NONE, NONE, NONE, "$127.50"), Arrays.asList("RDR", "Set out : (L) CPX   10003   - Tank Oil", "NI Spur", "$250.00", "$20.00", NONE, "$67.50", NONE, NONE, NONE, "$202.50"), Arrays.asList("RDR", "Set out : (L) CPX   20001   - Coilcar", "NI Spur", "$300.00", "$20.00", NONE, "$80.00", NONE, NONE, NONE, "$240.00"), Collections.singletonList(NONE), Arrays.asList("REV", "By Customer", "Discount", "Switching", "Transport", "Hazard", "Customer", "Demurrage", "Cancelled", "Diverted", "Total"), Arrays.asList("\"\"", NONE, "Rate", "Tariff", "Tariff", "Fee", "Discount", "Fee", "Mulct", "Mulct", "Revenue"), Arrays.asList("RDR", "NI Spur", "25.00%", "\"$1,450.00\"", "$100.00", "$150.00", "$425.00", "$100.00", "$150.00", "$250.00", "\"$1,775.00\""), Collections.singletonList(NONE), Arrays.asList("REV", "By Train", "Route", "Switching", "Transport", "Hazard", "Customer", "Demurrage", "Cancelled", "Diverted", "Total"), Arrays.asList("\"\"", NONE, "Rate", "Tariff", "Tariff", "Fee", "Discount", "Fee", "Mulct", "Mulct", "Revenue"), Arrays.asList("RDR", "Train SFF", "$40.00", "\"$1,450.00\"", "$100.00", "$150.00", "$425.00", "$100.00", "$150.00", "$250.00", "\"$1,775.00\""));

        int rowIndex = 29;
        for (List<String> expectedList : expected) {
            List<String> actualList = cellMap.get(rowIndex++);
            assertEquals(expectedList.size(), actualList.size(), "row size mismatch in row " + rowIndex + "\n\t" + expectedList + "\n\t" + actualList);
            for (int i = 0; i < expectedList.size(); i++) {
                String expectedValue = expectedList.get(i);
                String actualValue = actualList.get(i);
                assertEquals(expectedValue, actualValue, "mismatch in row " + rowIndex + " at column " + (i + 1) + "\n\t" + expectedList + "\n\t" + actualList);
            }
        }
    }

    private void verifyTrain2CsvRevenueFRCarSetB(TreeMap<Integer, List<String>> cellMap) {

        List<List<String>> expected = Arrays.asList(Arrays.asList("REV", "En voiture de chemin de fer", "Pour", "Commutation", "Transport", "Risquer", "Client", "Surestarie", "Annulé", "Dévié", "Le total"), Arrays.asList("\"\"", NONE, "Client", "Tarif", "Tarif", "Honoraires", "Remise", "Honoraires", "Amende", "Amende", "Revenu"), Arrays.asList("RDR", "-------- : (L) CP    99      - HopCoal", "NI Spur", NONE, NONE, NONE, NONE, "\"100,00 €\"", NONE, NONE, "\"100,00 €\""), Arrays.asList("RDR", "-------- : (L) CPX   10001   - Tank Oil", "NI Spur", NONE, NONE, NONE, NONE, NONE, "\"150,00 €\"", NONE, "\"150,00 €\""), Arrays.asList("RDR", "Collecte : (E) CPX   20002   - Tank Oil", "NI Spur", "\"150,00 €\"", "\"20,00 €\"", NONE, "\"42,50 €\"", NONE, NONE, NONE, "\"127,50 €\""), Arrays.asList("RDR", "Collecte : (L) CP    777     - FlatWood", "NI Spur", "\"350,00 €\"", NONE, NONE, "\"87,50 €\"", NONE, NONE, "\"250,00 €\"", "\"512,50 €\""), Arrays.asList("RDR", "Collecte : (L) CP    888     - HopGrain", "NI Spur", "\"250,00 €\"", "\"20,00 €\"", "\"150,00 €\"", "\"105,00 €\"", NONE, NONE, NONE, "\"315,00 €\""), Arrays.asList("RDR", "Dépose   : (E) CPX   10002   - Tank Oil", "NI Spur", "\"150,00 €\"", "\"20,00 €\"", NONE, "\"42,50 €\"", NONE, NONE, NONE, "\"127,50 €\""), Arrays.asList("RDR", "Dépose   : (L) CPX   10003   - Tank Oil", "NI Spur", "\"250,00 €\"", "\"20,00 €\"", NONE, "\"67,50 €\"", NONE, NONE, NONE, "\"202,50 €\""), Arrays.asList("RDR", "Dépose   : (L) CPX   20001   - Coilcar", "NI Spur", "\"300,00 €\"", "\"20,00 €\"", NONE, "\"80,00 €\"", NONE, NONE, NONE, "\"240,00 €\""), Collections.singletonList(NONE), Arrays.asList("REV", "Par le client", "Remise", "Commutation", "Transport", "Risquer", "Client", "Surestarie", "Annulé", "Dévié", "Le total"), Arrays.asList("\"\"", NONE, "Tarif", "Tarif", "Tarif", "Honoraires", "Remise", "Honoraires", "Amende", "Amende", "Revenu"), Arrays.asList("RDR", "NI Spur", "\"25,00 %\"", "\"1 450,00 €\"", "\"100,00 €\"", "\"150,00 €\"", "\"425,00 €\"", "\"100,00 €\"", "\"150,00 €\"", "\"250,00 €\"", "\"1 775,00 €\""), Collections.singletonList(NONE), Arrays.asList("REV", "Par le train", "Route", "Commutation", "Transport", "Risquer", "Client", "Surestarie", "Annulé", "Dévié", "Le total"), Arrays.asList("\"\"", NONE, "Tarif", "Tarif", "Tarif", "Honoraires", "Remise", "Honoraires", "Amende", "Amende", "Revenu"), Arrays.asList("RDR", "Train SFF", "\"40,00 €\"", "\"1 450,00 €\"", "\"100,00 €\"", "\"150,00 €\"", "\"425,00 €\"", "\"100,00 €\"", "\"150,00 €\"", "\"250,00 €\"", "\"1 775,00 €\""));

        int rowIndex = 29;
        for (List<String> expectedList : expected) {
            List<String> actualList = cellMap.get(rowIndex++);
            assertEquals(expectedList.size(), actualList.size(), "row size mismatch in row " + rowIndex + "\n\t" + expectedList + "\n\t" + actualList);
            for (int i = 0; i < expectedList.size(); i++) {
                String expectedValue = expectedList.get(i);
                String actualValue = actualList.get(i);
                assertEquals(expectedValue, actualValue, "mismatch in row " + rowIndex + " at column " + (i + 1) + "\n\t" + expectedList + "\n\t" + actualList);
            }
        }
    }

}
