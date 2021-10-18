package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.OperationsTestCase;
import jmri.jmrit.operations.locations.Location;
import jmri.jmrit.operations.locations.LocationManager;
import jmri.jmrit.operations.locations.Track;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.cars.CarTypes;
import jmri.jmrit.operations.routes.Route;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.routes.RouteManager;
import jmri.jmrit.operations.setup.Setup;
import jmri.jmrit.operations.setup.TrainRevenues;
import jmri.util.JUnitOperationsUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

import static java.util.Locale.*;
import static jmri.jmrit.operations.trains.Train.NONE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TrainCsvRevenueTest tests a train's revenue report for multiple locales
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainCsvRevenueTest extends OperationsTestCase {
    private CarManager carManager;
    private CarTypes carTypes;
    private LocationManager locationManager;
    private RouteManager routeManager;
    private TrainManager trainManager;
    private TrainManagerXml trainManagerXml;

    private Locale defaultLocale;

    private static final String CANCEL_CAR_ID = "GBW 906";
    private static final String DIVERT_CAR_ID = "CDLX 287";
    private static final String ROUTE_ID = "3";
    private static final String ROUTE_DIVERT_LOCATION_ID = "1r1";

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

        defaultLocale = getDefault(); // save the default locale.
    }

    @Override
    @AfterEach
    public void tearDown() {
        JUnitOperationsUtil.checkOperationsShutDownTask();
        setDefault(defaultLocale);

        super.tearDown();
    }

    @Test
    public void testCTor() throws IOException {
        Train train = trainManager.getTrainById("1");
        assertTrue(train.build());

        File trainRevenuesSerFile = TrainRevenues.getTrainRevenuesSerFile(train);
        assertNotNull(trainRevenuesSerFile, "exists");

        train.terminate();
        assertFalse(train.isBuilt());

        File trainRevenuesFile = TrainRevenues.getTrainRevenuesCsvFile(train);
        assertNotNull(trainRevenuesFile, "exists");
    }

    @Test
    public void testCreateCsvRevenueTrainDK() throws IOException {
        try {
            setDefault(new Locale("da", "DK"));
            createBuildVerifyTrain3(true);
        } finally {
            setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainFR() throws IOException {
        try {
            setDefault(FRANCE);
            createBuildVerifyTrain3(false);
        } finally {
            setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainDE() throws IOException {
        try {
            setDefault(GERMANY);
            createBuildVerifyTrain3(false);
        } finally {
            setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainGB() throws IOException {
        try {
            setDefault(UK);
            createBuildVerifyTrain3(false);
        } finally {
            setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainIT() throws IOException {
        try {
            setDefault(ITALY);
            createBuildVerifyTrain3(false);
        } finally {
            setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainJP() throws IOException {
        try {
            setDefault(JAPAN);
            createBuildVerifyTrain3(false);
        } finally {
            setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainNL() throws IOException {
        try {
            setDefault(new Locale("nl", "NL"));
            createBuildVerifyTrain3(false);
        } finally {
            setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainUS() throws IOException {
        try {
            setDefault(US);
            createBuildVerifyTrain3(true);
        } finally {
            setDefault(defaultLocale);
        }
    }

    private void addCancelMulct() {
        Car car = carManager.getById(CANCEL_CAR_ID);
        car.setRouteLocation(null);
        car.setRouteDestination(null);
    }

    private void addDivertMulct() {
        Car car = carManager.getById(DIVERT_CAR_ID);
        Track track = tracks.get(1);

        car.setDestination(track.getLocation(), track);

        Route route = routeManager.getRouteById(ROUTE_ID);
        RouteLocation routeLocation = route.getLocationById(ROUTE_DIVERT_LOCATION_ID);

        car.setRouteDestination(routeLocation);
    }

    /**
     *
     * @param restartMove optional switch for simulating JMRI shutdown and restart events between moves
     */
    private void createBuildVerifyTrain3(boolean restartMove) throws IOException {
        Train train = createTrain3();
        assertTrue(train.build());

        addCancelMulct();
        addDivertMulct();

        if (restartMove) {
            verifyRestartMoveRestoreCycle(train);
            verifyRestartMoveRestoreCycle(train);
        }

        train.terminate();
        assertFalse(train.isBuilt());

        verifyTrainCsvRevenue(getCsvRevenueAsTreeMap(train));
    }

    private Location createLocation(String locId, String locName) {
        Location loc = new Location(locId, locName);
        loc.setSwitchListEnabled(true);

        locationManager.register(loc);

        return loc;
    }

    /**
     *
     * set up a route of 4 locations:
     *   West Terminal - 1 staging track
     *   West Industry - 1 spur
     *   East Industry - 1 spur
     *   East Terminal - 1 staging track
     *
     * @return 4-location route object
     */
    private Route createRoute(String id) {
        int i;
        i = -1;
        List<Location> locations = new ArrayList<>();
        locations.add(createLocation(String.valueOf(++i), "West Terminal"));
        locations.add(createLocation(String.valueOf(++i), "West Industry"));
        locations.add(createLocation(String.valueOf(++i), "East Industry"));
        locations.add(createLocation(String.valueOf(++i), "East Terminal"));

        i = -1;
        tracks = new ArrayList<>();
        tracks.add(createNewTrack(++i + "s1", locations.get(i), "West Terminal", Track.STAGING, 400));
        tracks.add(createNewTrack(++i + "s1", locations.get(i), "West Industry", Track.SPUR, 360));
        tracks.add(createNewTrack(++i + "s1", locations.get(i), "East Industry", Track.SPUR, 600));
        tracks.add(createNewTrack(++i + "s1", locations.get(i), "East Terminal", Track.STAGING, 350));

        Route route = new Route(id, "Route" + id);

        i = -1;
        registerNewRouteLocation(++i + "r1", i, locations.get(i), route);
        registerNewRouteLocation(++i + "r1", i, locations.get(i), route);
        registerNewRouteLocation(++i + "r1", i, locations.get(i), route);
        registerNewRouteLocation(++i + "r1", i, locations.get(i), route);

        routeManager.register(route);

        return route;
    }

    private Track createNewTrack(String trackId, Location location, String trackName, String trackType, int trackLength) {
        Track track = new Track(trackId, trackName, trackType, location);
        track.setLength(trackLength);

        location.register(track);

        return track;
    }

    /**
     * creates a new Route with id 3
     * creates a new Train with id 3
     *
     * @return new Train object with new Route
     */
    private Train createTrain3() {
        String id= "3";
        Setup.setStagingTrainCheckEnabled(false);
        Setup.setBuildAggressive(false);
        Setup.setMaxTrainLength(400);

        Route route = createRoute(id);

        Train train = new Train(id, "CP " + id);
        train.setRequirements(Train.CABOOSE);
        train.setCabooseRoad("CP");
        train.setRoute(route);
        train.setDepartureTime("6", "5");
        train.setComment("Test comment for train CP " + id);
        train.setDescription("Train CP " + id);

        trainManager.register(train);

        registerNewCars(train);

        return train;
    }

    /**
     * reads the entire csv revenue file into a treemap of rows of CSV values
     *
     * @return TreeMap representing the CSV file contents
     */
    private TreeMap<Integer, List<String>> getCsvRevenueAsTreeMap(Train train) throws IOException {
        TreeMap<Integer, List<String>> map = new TreeMap<>();

        File trainRevenuesCsvFile = TrainRevenues.getTrainRevenuesCsvFile(train);
        assertNotNull(trainRevenuesCsvFile, "exists");

        int rows = 0;
        String line;
        BufferedReader in = JUnitOperationsUtil.getBufferedReader(trainRevenuesCsvFile);
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

    private void registerNewCars(Train train) {
        ResourceBundle rb = ResourceBundle.getBundle("jmri.jmrit.operations.JmritOperationsBundle");

        updateCar(train, "CP", "C10099", rb.getString("Caboose"), "E", tracks.get(0));

        updateCar(train, "CDLX ", "287", "RB", "E", tracks.get(0));
        updateCar(train, "CONX ", "5092", "Tank Oil", "L", tracks.get(0)).setHazardous(true);
        updateCar(train, "C&O ", "1002", "FlatTimber", "L", tracks.get(0));
        updateCar(train, "C&O ", "51137", "FD-coil", "L", tracks.get(0));
        updateCar(train, "EORX ", "18554", "Tank Oil", "E", tracks.get(0)).setHazardous(true);

        updateCar(train, "FCKX ", "6302", "RB", "L", tracks.get(1)).setWait(4);
        updateCar(train, "GBW ", "906", "Boxcar", "L", tracks.get(1));
        updateCar(train, "NKP ", "99713", "HopGrain", "L", tracks.get(1));
        updateCar(train, "N&W ", "29002", "Stock", "L", tracks.get(1));
        updateCar(train, "N&W ", "76566", "Hopper", "L", tracks.get(1));

        updateCar(train, "PRR ", "24166", "Boxcar", "L", tracks.get(2));
        updateCar(train, "PRR ", "220874", "Hopper", "L", tracks.get(2));
        updateCar(train, "UP ", "46774D", "Stock", "L", tracks.get(2));
        updateCar(train, "VGN ", "15508", "Hopper", "L", tracks.get(2));
        updateCar(train, "VGN ", "15779", "Hopper", "L", tracks.get(2));
    }

    private void registerNewRouteLocation(String rlId, int sequenceNumber, Location location, Route route) {
        RouteLocation rl = new RouteLocation(rlId, location);
        rl.setSequenceNumber(sequenceNumber);
        rl.setTrainDirection(RouteLocation.EAST);
        rl.setMaxCarMoves(25);
        rl.setMaxTrainLength(1000);

        route.register(rl);
    }

    /**
     * creates a new car as needed and ensures all interested parties accept the car load and type
     *
     * @return updated Car object
     */
    private Car updateCar(Train train, String road, String number, String type, String load, Track thisTrack) {
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

        car.setLocation(thisTrack.getLocation(), thisTrack);

        return car;
    }

    private void updateLoadName(String loadName, List<Track> tracks, Train train, Car car) {
        for (Track track : tracks) {
            track.addLoadName(loadName);
        }
        train.addLoadName(loadName);
        car.setLoadName(loadName);
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

    private void verifyTrainCsvRevenue(TreeMap<Integer, List<String>> cellMap) {
        Locale locale = getDefault();
        String country = locale.getCountry();
        if (US.getCountry().equals(country)) {
            int rowIndex = 30;
            List<List<String>> expected = Arrays.asList(
                    Arrays.asList("\"\"",NONE,NONE,"Switching","Transport","Hazard","Demurrage","Cancelled","Diverted","Customer","Total"),
                    Arrays.asList("\"\"",NONE,NONE,"Tariff","Tariff","Tariff","Charge","Mulct","Mulct","Discount","Revenue"),
                    Arrays.asList("REV","By Car","For Customer"),
                    Arrays.asList("RDR","Pick up : (L) VGN   15508   - Hopper","East Industry","$480.00","$20.00",NONE,NONE,NONE,NONE,"$125.00","$375.00"),
                    Arrays.asList("RDR","Set out : (L) N&W   29002   - Stock","East Industry","$480.00","$20.00",NONE,NONE,NONE,NONE,"$125.00","$375.00"),
                    Arrays.asList("RDR","------- : (E) EORX  18554   - Tank Oil","East Terminal",NONE,"$60.00",NONE,NONE,NONE,NONE,NONE,"$60.00"),
                    Arrays.asList("RDR","------- : (L) C&O   1002    - FlatTimber","East Terminal",NONE,"$60.00",NONE,NONE,NONE,NONE,NONE,"$60.00"),
                    Arrays.asList("RDR","------- : (L) CONX  5092    - Tank Oil","East Terminal",NONE,"$60.00","$150.00",NONE,NONE,NONE,NONE,"$210.00"),
                    Arrays.asList("RDR","------- : (L) FCKX  6302    - RB","West Industry",NONE,NONE,NONE,"$100.00",NONE,NONE,NONE,"$100.00"),
                    Arrays.asList("RDR","------- : (L) GBW   906     - Boxcar","West Industry",NONE,NONE,NONE,NONE,"$150.00",NONE,NONE,"$150.00"),
                    Arrays.asList("RDR","Pick up : (L) N&W   76566   - Hopper","West Industry","$480.00","$40.00",NONE,NONE,NONE,NONE,"$78.00","$442.00"),
                    Arrays.asList("RDR","Pick up : (L) NKP   99713   - HopGrain","West Industry","$250.00","$40.00",NONE,NONE,NONE,NONE,"$43.50","$246.50"),
                    Arrays.asList("RDR","Set out : (E) CDLX  287     - RB","West Industry","$150.00","$20.00",NONE,NONE,NONE,"$250.00","$25.50","$394.50"),
                    Arrays.asList("RDR","Set out : (L) C&O   51137   - FD-coil","West Industry","$300.00","$20.00",NONE,NONE,NONE,NONE,"$48.00","$272.00"),
                    Collections.singletonList(NONE),
                    Arrays.asList("REV","By Customer", "Discount Rate"),
                    Arrays.asList("RDR","East Industry","25.00%","$960.00","$40.00",NONE,NONE,NONE,NONE,"$250.00","$750.00"),
                    Arrays.asList("RDR","East Terminal",NONE,NONE,"$180.00","$150.00",NONE,NONE,NONE,NONE,"$330.00"),
                    Arrays.asList("RDR","West Industry","15.00%","\"$1,180.00\"","$120.00",NONE,"$100.00","$150.00","$250.00","$195.00","\"$1,605.00\""),
                    Collections.singletonList(NONE),
                    Arrays.asList("REV","By Train","Route Rate"),
                    Arrays.asList("RDR","Train CP 3","$60.00","\"$2,140.00\"","$340.00","$150.00","$100.00","$150.00","$250.00","$445.00","\"$2,685.00\"")
            );
            for (List<String> expectedList : expected) {
                List<String> actualList = cellMap.get(rowIndex++);
                assertEquals(expectedList.size(), actualList.size(),
                        "row size mismatch in row " + rowIndex + "\n\t" + expectedList + "\n\t" + actualList);
                for (int i = 0; i < expectedList.size(); i++) {
                    assertEquals(expectedList.get(i), actualList.get(i),
                            "mismatch in row " + rowIndex + " at column " + (i + 1) + "\n\t" + expectedList + "\n\t" + actualList);
                }
            }
        }

        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
        String expectedTotal = "\"" + numberFormat.format(2685) + "\"";
        assertEquals(expectedTotal, cellMap.get(51).get(10));
    }
}
