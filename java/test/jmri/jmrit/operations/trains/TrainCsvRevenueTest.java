package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.OperationsTestCase;
import jmri.jmrit.operations.locations.Location;
import jmri.jmrit.operations.locations.LocationManager;
import jmri.jmrit.operations.locations.Track;
import jmri.jmrit.operations.rollingstock.cars.*;
import jmri.jmrit.operations.rollingstock.engines.Engine;
import jmri.jmrit.operations.rollingstock.engines.EngineManager;
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
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TrainCsvRevenueTest tests a train's revenue report for multiple locales
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainCsvRevenueTest extends OperationsTestCase {
    private static final String ID = "1";
    private static final int LENGTH = 1000;
    private static final int LOC_1 = 1;
    private static final int LOC_2 = 2;
    private static final int LOC_3 = 3;
    private static final int LOC_4 = 4;
    private static final int LOC_5 = 5;
    private static final double TRAIN_TOTAL_REVENUE = 6566.25;

    private CarManager carManager;
    private CarTypes carTypes;
    private EngineManager engineManager;
    private LocationManager locationManager;
    private RouteManager routeManager;
    private TrainManager trainManager;
    private TrainManagerXml trainManagerXml;

    private Locale defaultLocale;
    private Route route;
    private Train train;
    private Map<String, Track> tracksByTrackId;
    private Map<String, RouteLocation> routeLocationsById;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();

        carManager = InstanceManager.getDefault(CarManager.class);
        carTypes = InstanceManager.getDefault(CarTypes.class);
        engineManager = InstanceManager.getDefault(EngineManager.class);
        locationManager = InstanceManager.getDefault(LocationManager.class);
        routeManager = InstanceManager.getDefault(RouteManager.class);
        trainManager = InstanceManager.getDefault(TrainManager.class);
        trainManagerXml = InstanceManager.getDefault(TrainManagerXml.class);

        File csvRevenueDirectory = new File(trainManagerXml.getDefaultTrainCsvRevenueDirectory());
        if (csvRevenueDirectory.exists()) {
            assertTrue(csvRevenueDirectory.delete());
        }

        Setup.setSaveTrainRevenuesEnabled(true);
        Setup.setStagingTrainCheckEnabled(false);
        Setup.setBuildAggressive(false);
        Setup.setMaxTrainLength(LENGTH);
        Setup.setPickupManifestMessageFormat(new String[]{"Road", "Number", "Type", "Load", "Hazardous", "Location", "Dest&Track"});
        Setup.setDropManifestMessageFormat(new String[]{"Road", "Number", "Type", "Load", "Hazardous", "Track"});

       defaultLocale = Locale.getDefault(); // save the default locale.
    }

    @Override
    @AfterEach
    public void tearDown() {
        JUnitOperationsUtil.checkOperationsShutDownTask();
        if (defaultLocale != null) {
            Locale.setDefault(defaultLocale);
        }

        super.tearDown();
    }

    @Test
    public void testCTor() throws IOException {
        buildRailroad();
        assertTrue(train.build());

        File trainRevenuesSerFile = TrainRevenues.getTrainRevenuesSerFile(train);
        assertNotNull(trainRevenuesSerFile, "exists");

        train.terminate();
        assertFalse(train.isBuilt());

        File trainRevenuesFile = TrainRevenues.getTrainRevenuesCsvFile(train);
        assertNotNull(trainRevenuesFile, "exists");
    }

    @Test
    public void testCreateCsvRevenueTrainDE() throws IOException {
        try {
            Locale.setDefault(Locale.GERMANY);
            verifyCsvRevenue(false);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainDK() throws IOException {
        try {
            Locale.setDefault(new Locale("da", "DK"));
            verifyCsvRevenue(true);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainFR() throws IOException {
        try {
            Locale.setDefault(Locale.FRANCE);
            verifyCsvRevenue(false);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainGB() throws IOException {
        try {
            Locale.setDefault(Locale.UK);
            verifyCsvRevenue(false);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainIT() throws IOException {
        try {
            Locale.setDefault(Locale.ITALY);
            verifyCsvRevenue(false);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainJP() throws IOException {
        try {
            Locale.setDefault(Locale.JAPAN);
            verifyCsvRevenue(false);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainNL() throws IOException {
        try {
            Locale.setDefault(new Locale("nl", "NL"));
            verifyCsvRevenue(false);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void testCreateCsvRevenueTrainUS() throws IOException {
        try {
            Locale.setDefault(Locale.US);
            verifyCsvRevenue(false);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private void buildRailroad() {
        Setup.setBuildReportLevel(Setup.BUILD_REPORT_VERY_DETAILED);

        String dEn = CarRevenue.getDefaultEmptyName();
        String dLn = CarRevenue.getDefaultLoadedName();

        CarLoads carLoads = new CarLoads();
        carLoads.setDefaultEmptyName(dEn);
        carLoads.setDefaultLoadName(dLn);

        route = new Route(ID, "Route" + ID);
        routeManager.register(route);

        train = registerNewTrain();
        assertNotNull(train);

        routeLocationsById = new TreeMap<>();
        tracksByTrackId = new TreeMap<>();

        Track track;
        String road = "CP";
        Engine engine = null;
        RouteLocation rl3;
        RouteLocation rl4;
        int customerNumber = 1;
        Location location;
        for (int loc = LOC_1; loc <= LOC_5; loc++) {
            location = registerNewLocation(loc);

            switch (loc) {
                case LOC_1:
                    registerNewRouteLocation(loc, 2 * loc, 0.0, location);

                    track = createNewTrack(0, location, "Yard " + loc, Track.YARD);
                    engine = registerNewEngine(track, road, "500" + loc, "FT");
                    updateCar(road, "C10099", "Caboose", dEn, track).setCaboose(true);

                    updateCar("C&O", "1002", "FlatTimber", dLn, track);
                    updateCar("CONX", "5092", "Tank Oil", dLn, track).setHazardous(true);
                    updateCar("N&W", "29002", "Stock", dLn, track);
                    updateCar("NKP", "99713", "HopGrain", dLn, track);
                    break;
                case LOC_2:
                    registerNewRouteLocation(loc, 2 * loc, 1.0, location);

                    track = createNewTrack(customerNumber, location, "Customer " + customerNumber + "-" + loc, Track.SPUR);
                    updateCar("C&O", "51137", "FD-coil", dLn, track);
                    updateCar("GBW", "906", "Boxcar", "Paper", track);
                    updateCar("PRR", "24166", "Boxcar", "Grain", track);

                    customerNumber++;
                    track = createNewTrack(customerNumber, location, "Customer " + customerNumber + "-" + loc, Track.SPUR);
                    updateCar("EORX", "18554", "Tank Oil", dEn, track).setHazardous(true);
                    updateCar("N&W", "76566", "Hopper", "Feed", track);
                    break;
                case LOC_3:
                    rl3 = registerNewRouteLocation(loc, 2 * loc, 2.0, location);

                    track = createNewTrack(0, location, "Yard " + loc, Track.YARD);
                    track.setBlockCarsEnabled(true);
                    engine.reset();
                    registerNewEngine(track, road, "500" + loc, "TRAINMASTER");
                    train.setSecondLegOptions(Train.CHANGE_ENGINES);
                    train.setSecondLegStartRouteLocation(rl3);
                    train.setSecondLegNumberEngines("1");
                    train.setSecondLegEngineModel("TRAINMASTER");
                    train.setSecondLegEngineRoad(road);
                    train.setSecondLegCabooseRoad(road);

                    track = createNewTrack(customerNumber, location, "Customer " + customerNumber + "-" + loc, Track.SPUR);
                    updateCar("VGN", "15779", "Hopper", "Coal", track);

                    customerNumber++;
                    track = createNewTrack(customerNumber, location, "Customer " + customerNumber + "-" + loc, Track.SPUR);
                    updateCar("PRR", "220874", "Hopper", dLn, track);
                    updateCar("UP", "46774D", "Stock", dLn, track);
                    break;
                case LOC_4:
                    rl4 = registerNewRouteLocation(loc, 2 * loc, -3.0, location);
                    train.setSecondLegEndRouteLocation(rl4);
                    engine.reset();
                    track = createNewTrack(0, location, "Yard " + loc, Track.YARD);
                    track.setBlockCarsEnabled(true);
                    registerNewEngine(track, road, "500" + loc, "RS1");
                    train.setThirdLegOptions(Train.CHANGE_ENGINES);
                    train.setThirdLegStartRouteLocation(rl4);
                    train.setThirdLegNumberEngines("1");
                    train.setThirdLegEngineModel("RS1");
                    train.setThirdLegEngineRoad(road);
                    train.setThirdLegCabooseRoad(road);

                    track = createNewTrack(customerNumber, location, "Customer " + customerNumber + "-" + loc, Track.SPUR);
                    updateCar("FCKX", "6302", "RB", dLn, track).setWait(4);

                    customerNumber++;
                    track = createNewTrack(customerNumber, location, "Customer " + customerNumber + "-" + loc, Track.SPUR);
                    updateCar("VGN", "15508", "Hopper", dLn, track);
                    break;
                case LOC_5:
                    registerNewRouteLocation(loc, 2 * loc, 0.0, location);
                    createNewTrack(6, location, "Yard " + loc, Track.YARD);
                    break;
            }
        }
        train.build();

        for (Engine e : InstanceManager.getDefault(EngineManager.class).getList(train)) {
            System.out.println(String.format("Engine %-5s HP %-6s at %-3s tons: %s", e.getHp(), e.getTypeName(), e.getWeightTons(), e.getModel()));
        }

        assertTrue(train.isBuilt());
    }

    private void conductorAdd(String road, String number, String type, String load, int origCustomer, int origLoc, int termCustomer, int termLoc) {
        Car car = carManager.getByRoadAndNumber(road, number);
        if (car == null) {
            Track origTrack = tracksByTrackId.get(String.format("%ss%d%d", ID, origCustomer, origLoc));
            car = updateCar(road, number, type, load, origTrack);
        }
        car.setTrain(train);

        Track termTrack = tracksByTrackId.get(String.format("%ss%d%d", ID, termCustomer, termLoc));
        car.setDestination(termTrack.getLocation(), termTrack);

        car.setRouteLocation(routeLocationsById.get(String.format("%sr%d", ID, origLoc)));
        car.setRouteDestination(routeLocationsById.get(String.format("%sr%d", ID, termLoc)));
    }

    private void conductorCancel(String road, String number) {
        Car car = carManager.getByRoadAndNumber(road, number);
        if (car != null) {
            car.setRouteLocation(null);
            car.setRouteDestination(null);
            //            car.setTrain(null);
        }
    }

    private void conductorDivert(String road, String number, int customer, int termLoc) {
        Car car = carManager.getByRoadAndNumber(road, number);
        if (car != null) {
            Track termTrack = tracksByTrackId.get(String.format("%ss%d%d", ID, customer, termLoc));
            car.setDestination(termTrack.getLocation(), termTrack);

            car.setRouteDestination(routeLocationsById.get(String.format("%sr%d", ID, termLoc)));
        }
    }

    private Track createNewTrack(int customerNumber, Location location, String trackName, String trackType) {
        int locNumber = Integer.parseInt(location.getId());
        String trackId = String.format("%s%s%d%d", ID, "s", customerNumber, locNumber);
        Track track = new Track(trackId, trackName, trackType, location);
        tracksByTrackId.put(trackId, track);
        track.setLength(LENGTH);
        location.register(track);

        return track;
    }

    private TreeMap<Integer, List<String>> getCsvRevenueAsTreeMap(Train train) throws IOException {
        TreeMap<Integer, List<String>> map = new TreeMap<>();

        File trainRevenuesCsvFile = TrainRevenues.getTrainRevenuesCsvFile(train);
        assertNotNull(trainRevenuesCsvFile, "exists");

        int rows = 0;
        String line;
        BufferedReader in = JUnitOperationsUtil.getBufferedReader(trainRevenuesCsvFile);
        while ((line = in.readLine()) != null) {
            List<String> rowCells = new ArrayList<>();
            for (String cell : line.split(",")) {
                boolean added = false;
                if (cell != null && !cell.isEmpty() && Character.isDigit(cell.charAt(0))) {
                    int lastCellIndex = rowCells.size() - 1;
                    String lastCell = rowCells.get(lastCellIndex);
                    if (lastCell != null && !lastCell.isEmpty()
                            && Character.isDigit(lastCell.charAt(lastCell.length() - 1))) {
                        String newCell = lastCell + ',' + cell;
                        rowCells.set(lastCellIndex, newCell.replace("\"", ""));
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

    private Engine registerNewEngine(Track track, String road, String number, String model) {
        Engine engine = new Engine(road, number);
        engine.setModel(model);
        engine.setLocation(track.getLocation(), track);
        engineManager.register(engine);

        return engine;
    }

    private Location registerNewLocation(int loc) {
        Location location = new Location(String.valueOf(loc), "Location " + loc);
        location.setSwitchListEnabled(true);
        locationManager.register(location);

        return location;
    }

    private RouteLocation registerNewRouteLocation(int sequenceNumber, int wait, double grade, Location location) {
        String rlId = ID + "r" + sequenceNumber;
        RouteLocation routeLocation = new RouteLocation(rlId, location);
        routeLocation.setGrade(grade);
        routeLocation.setSequenceNumber(sequenceNumber);
        routeLocation.setWait(wait);
        routeLocation.setTrainDirection(RouteLocation.EAST);
        routeLocation.setMaxCarMoves(25);
        routeLocation.setMaxTrainLength(1000);
        routeLocationsById.put(rlId, routeLocation);
        route.register(routeLocation);

        return routeLocation;
    }

    private Train registerNewTrain() {
        String name = "CP " + ID;
        Train train = new Train(ID, name);
        train.setRequirements(Train.CABOOSE);
        train.setCabooseRoad("CP");
        train.setRoute(route);
        train.setDepartureTime("6", "5");
        train.setComment(String.format("Test CSV train %s", name));
        train.setDescription("Train " + name);
        train.setNumberEngines("1");
        train.setBuildTrainNormalEnabled(true);
        trainManager.register(train);

        return train;
    }

    private Car updateCar(String road, String number, String type, String load, Track track) {
        Car car = carManager.getByRoadAndNumber(road, number);
        if (car == null) {
            car = carManager.newRS(road, number);
            car.setBuilt("1984");
            car.setOwner("DAB");
            car.setLength("40");
            car.setWeightTons("75");
            car.setMoves(0);
            car.setColor("Black");
            car.setLoadName(load);
        }

        updateLoadName(load, car);
        updateTypeName(type, car);

        car.setLocation(track.getLocation(), track);

        return car;
    }

    private void updateLoadName(String loadName, Car car) {
        for (Track track : tracksByTrackId.values()) {
            track.addLoadName(loadName);
        }
        train.addLoadName(loadName);
        car.setLoadName(loadName);
    }

    private void updateTypeName(String typeName, Car car) {
        for (Track track : tracksByTrackId.values()) {
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

    private void verifyCsvRevenue(boolean restartMove) throws IOException {
        buildRailroad();

        for (int loc = LOC_1; loc < LOC_5; loc++) {
            switch (loc) {
                case LOC_1:
                    conductorCancel("C&O", "1002");
                    conductorDivert("CONX", "5092", 2, 2);
                    break;
                case LOC_2:
                    conductorAdd("UP", "99999", "Gondola", "Scrap", 2, 2, 3, 3);
                    conductorDivert("GBW", "906", 3, 4);
                    conductorDivert("N&W", "76566", 4, 4);
                    break;
                case LOC_3:
                    conductorAdd("NKP", "76555", "XM", "Seed", 3, 3, 4, 4);
                    conductorCancel("VGN", "15779");
                    conductorDivert("UP", "46774D", 3, 4);
                    break;
                case LOC_4:
                    conductorCancel("VGN", "15508");
                    break;
            }
            if (restartMove) {
                verifyRestartMoveRestoreCycle(train);
            } else {
                train.move();
            }
        }

        train.terminate();
        assertFalse(train.isBuilt());

//        loadRevenueMaps();

        TreeMap<Integer, List<String>> csvRevenueAsTreeMap = getCsvRevenueAsTreeMap(train);
        List<String> lastRowValues = csvRevenueAsTreeMap.get(csvRevenueAsTreeMap.size() - 1);
        assertEquals(
                NumberFormat.getCurrencyInstance(Locale.getDefault()).format(BigDecimal.valueOf(TRAIN_TOTAL_REVENUE)),
                csvRevenueAsTreeMap.get(csvRevenueAsTreeMap.size() - 1).get(lastRowValues.size() - 1)
        );

        TrainRevenues trainRevenues = train.getTrainRevenues();
        TrainMovements trainMovements = new TrainMovements(trainRevenues);
        assertNotNull(trainMovements);
    }

    private Map<String, Double> gradePercent;
    private Map<String, Integer> trainHp;
    private Map<String, String> engineModel;
    private Map<String, Integer> trainCarsTonWeight;
    private Map<String, Double> trainStartingForce;
    private Map<String, Integer> trainTonWeight;
    private Map<String, Integer> travelMinutes;
    private Map<String, Integer> trainAxles;

    private void loadRevenueMaps() {
        TrainRevenues trainRevenues = train.getTrainRevenues();

        gradePercent = trainRevenues.getGradePercent();
        trainHp = trainRevenues.getTrainHp();
        engineModel = trainRevenues.getEngineModel();
        trainCarsTonWeight = trainRevenues.getTrainCarsTons();
        trainTonWeight = trainRevenues.getTrainTonWeight();
        travelMinutes = trainRevenues.getTravelMinutes();
        trainStartingForce = trainRevenues.getTrainStartingForce();
        trainAxles = trainRevenues.getTrainAxles();

        System.out.println("\ntrain grade (%): " + gradePercent);
        System.out.println("train model: " + engineModel);
        System.out.println("train power (HP): " + trainHp);
        System.out.println("train cars weight (Tons): " + trainCarsTonWeight);
        System.out.println("train total weight (Tons): " + trainTonWeight);
        System.out.println("train travel times (Minutes): " + travelMinutes);
        System.out.println("train starting force (Tons): " + trainStartingForce);
        System.out.println("train axles: " + trainAxles);
    }
}
