package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.locations.Location;
import jmri.jmrit.operations.locations.LocationManager;
import jmri.jmrit.operations.locations.Track;
import jmri.jmrit.operations.rollingstock.RollingStock;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarLoad;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.cars.CarRevenue;
import jmri.jmrit.operations.rollingstock.engines.Engine;
import jmri.jmrit.operations.rollingstock.engines.EngineManager;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.setup.Setup;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * TrainRevenues - a POJO for a train's CarRevenue POJOs and other supporting data
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainRevenues implements Serializable {
    public static final int ORIG = 0;
    public static final int TERM = 1;
    private static final long serialVersionUID = 4L;
    // energy costs
    private static final double BTUS_PER_DOLLAR_COAL = 820000.;
    private static final double BTUS_PER_DOLLAR_ELECTRIC = 22700.;
    private static final double BTUS_PER_DOLLAR_OIL = 51000.;
    private static final double BTU_PER_SEC_PER_HP = 42.424294330155 / 60.;
    // power efficiency
    private static final double EFF_DIESEL = 0.225;
    private static final double EFF_ELECTRIC = 0.23;
    private static final double EFF_OTHER = 0.19;
    private static final double EFF_STEAM = 0.065;
    // locomotive operational costs
    private static final double ANNUAL_RESERVES_PER_HP_DIESEL = 12.; // Dollars
    private static final double ANNUAL_RESERVES_PER_HP_ELECTRIC = 15.; // Dollars
    private static final double ANNUAL_RESERVES_PER_HP_STEAM = 1.; // Dollars
    private static final double ANNUAL_RESERVES_PER_HP_OTHER = 8.; // Dollars
    private static final double DAILY_LABOR_RATE_ENGINEER = 40.; // Dollars
    private static final double DAILY_LABOR_RATE_CONDUCTOR = 37.50; // Dollars
    private static final double DAILY_LABOR_RATE_FIREMAN = 31.75; // Dollars
    private static final double DAILY_LABOR_RATE_BRAKEMAN = 32.25; // Dollars
    private static final double MOE_PER_TON_MILE = 0.0434; // Dollars
    private static final double MOW_PER_TON_MILE = 0.0372; // Dollars
    private static final double TGNA_PER_TON_MILE = 0.0372; // Dollars

    private final Map<String, Map<String, CarRevenue>> carRevenueMapByCarId = new TreeMap<>();
    private final Map<String, Map<String, Integer>> spurCapacityMapByCustomer = new HashMap<>();
    private final Set<String> carIdsInDemur = new TreeSet<>();
    private final Set<String> carIdsInMulctSet = new TreeSet<>();
    private final Map<String, Integer> trainPickUpOrDropOff = new HashMap<>();
    private transient Map<String, List<Engine>> trainEngines;
    private final Map<String, List<String>> trainEngineIds = new HashMap<>();
    private final Map<String, String[]> origTrackIdsByCarId = new HashMap<>();
    private final Map<String, List<Integer>> trainCarWeights = new HashMap<>();
    private final Map<String, List<TrainMotion>> trainMotions = new HashMap<>();
    private BigDecimal maxRouteTransportFee = BigDecimal.ZERO;
    private transient Train train;
    private Double routeMoeCost;
    private Double routeFuelCost;
    private Double routeLaborCost;
    private Double routeMowCost;
    private Double routeOverheadCost;

    public TrainRevenues(Train train) {
        this.train = train;
    }

    public static String getCustomer(Car car) {
        if (car.getTrack() == null) {
            if (car.getRouteLocation() == car.getRouteDestination() && car.getDestinationTrack() != null && car.getDestinationTrack().isSpur()) {
                return TrainCommon.splitString(car.getDestinationTrack().getName());
            }
        } else {
            if (car.getTrack().isSpur()) {
                return TrainCommon.splitString(car.getTrack().getName());
            }
        }

        return null;
    }

    public static TrainRevenues getTrainRevenues(Train train) {
        TrainRevenues trainRevenues = null;
        File file = getTrainRevenuesSerFile(train);
        if (file.exists()) {
            InputStream isFile, buffer;
            ObjectInput input;
            try {
                isFile = new FileInputStream(file);
                buffer = new BufferedInputStream(isFile);
                input = new ObjectInputStream(buffer);

                Object in = input.readObject();
                trainRevenues = (TrainRevenues) in;
                trainRevenues.setTrain(train);

                input.close();
                buffer.close();
                isFile.close();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            trainRevenues = new TrainRevenues(train);
        }

        return trainRevenues;
    }

    public static File getTrainRevenuesCsvFile(Train train) throws IOException {
        updateOperationsCosts(train.getTrainRevenues());
        new TrainCsvRevenue(train);

        File file = InstanceManager.getDefault(TrainManagerXml.class).getTrainCsvRevenueFile(train);
        if (!file.exists()) {
            return null;
        }
        return file;
    }

    private static void updateOperationsCosts(TrainRevenues trainRevenues) {
        trainRevenues.setRouteQuadCosts();
        trainRevenues.setRouteLaborCost();
    }

    public static File getTrainRevenuesSerFile(Train train) {
        return InstanceManager.getDefault(TrainManagerXml.class).getTrainRevenuesSerFile(train);
    }

    public static void deleteTrainRevenuesSerFile(Train train) {
        File trainRevenuesSerFile = getTrainRevenuesSerFile(train);
        if (trainRevenuesSerFile.exists()) {
            trainRevenuesSerFile.delete();
        }
    }

    public Collection<CarRevenue> getCarRevenues() {
        List<CarRevenue> carRevenues = new ArrayList<>();
        carRevenueMapByCarId.forEach((key, value) -> value.forEach((key1, value1) -> carRevenues.add(value1)));

        return carRevenues;
    }

    public Map<String, Set<CarRevenue>> getCarRevenueSetByCustomer() {
        Map<String, Set<CarRevenue>> map = new TreeMap<>();

        for (CarRevenue carRevenue : getCarRevenues()) {
            String customer = carRevenue.getCustomerName();
            if (customer != null && !customer.trim().isEmpty()) {
                map.putIfAbsent(customer, new TreeSet<>());
                map.get(customer).add(carRevenue);
            }
        }

        return map;
    }

    public BigDecimal getMaxRouteTransportFee() {
        return maxRouteTransportFee;
    }

    public BigDecimal getRouteMoeCost() {
        if (routeMoeCost == null) {
            return BigDecimal.ZERO;
        } else {
            return BigDecimal.valueOf(routeMoeCost);
        }
    }

    public BigDecimal getRouteFuelCost() {
        if (routeFuelCost == null) {
            return BigDecimal.ZERO;
        } else {
            return BigDecimal.valueOf(routeFuelCost);
        }
    }

    /**
     * Fuel costs depend on route location transit times, engines and types, and the total power produced by the train's
     * engines.
     */
    public void setRouteQuadCosts() {
        routeFuelCost = 0.;
        routeMowCost = 0.;
        routeMoeCost = 0.;
        routeOverheadCost = 0.;
        int routeCrews = getRouteCrews();
        for (Map.Entry<String, List<TrainMotion>> e : trainMotions.entrySet()) {
            String rlId = e.getKey();
            setRouteLocationQuadCosts(e.getValue(), rlId, routeCrews);
        }
    }

    /**
     * Route location fuel cost is calculated from applied power from all engines, by engine type, according to the
     * general power efficiency of the engine type and the cost of the corresponding fuel. It is assumed that each
     * engine contributes equally to the total horsepower applied to hauling the train.
     *
     * @param trainMotions List of train motions for the route with ID matching the rlId parameter
     * @param rlId         the ID for the route location under consideration
     * @param routeCrews   the number of crews needed for the train on this route
     */
    private void setRouteLocationQuadCosts(List<TrainMotion> trainMotions, String rlId, int routeCrews) {
        double hpSeconds = 0;
        for (TrainMotion tm : trainMotions) {
            hpSeconds += tm.p * tm.dt;
        }
        double engineCount = 0;

        double dieselEngines = 0;
        double electricEngines = 0;
        double steamerEngines = 0;
        double otherEngines = 0;

        double dieselHp = 0;
        double electricHp = 0;
        double steamerHp = 0;
        double otherHp = 0;

        List<Engine> engineList = getTrainEngines().get(rlId);
        if (engineList != null) {
            for (Engine engine : engineList) {
                engineCount++;
                String typeName = engine.getTypeName().split("-")[0];
                switch (typeName) {
                    case "Diesel": // us, ca, da, de, fr, it, nl
                    case "Motorová": // cs
                    case "ディーゼル": // ja_JP
                        dieselEngines++;
                        dieselHp += engine.getHpInteger();
                        break;

                    case "Steam": // us, ca, da
                    case "Dampf": // de
                    case "Parní": // cs
                    case "Stoom": // nl
                    case "Vapeur": // fr
                    case "Vapeurt": // fr
                    case "Vapore": // it
                    case "蒸気": // ja_JP
                        steamerEngines++;
                        steamerHp += engine.getHpInteger();
                        break;

                    case "Electric": // us, ca, da
                    case "Elektrická": // cs
                    case "Electrique": // fr
                    case "Electrisch": // de, nl
                    case "Elettrica": // it
                    case "電気": // ja_JP
                        electricEngines++;
                        electricHp += engine.getHpInteger();
                        break;

                    default:
                        otherEngines++;
                        otherHp += engine.getHpInteger();
                }
            }
        }
        if (engineCount != 0) {
            double btusPerEngine = BTU_PER_SEC_PER_HP * hpSeconds / engineCount;

            double routeFuelCostDiesel = btusPerEngine * dieselEngines / EFF_DIESEL / BTUS_PER_DOLLAR_OIL;
            double routeFuelCostElectric = btusPerEngine * electricEngines / EFF_ELECTRIC / BTUS_PER_DOLLAR_ELECTRIC;
            double routeFuelCostSteam = btusPerEngine * steamerEngines / EFF_STEAM / BTUS_PER_DOLLAR_COAL;
            double routeFuelCostOther = btusPerEngine * otherEngines / EFF_OTHER / BTUS_PER_DOLLAR_OIL;

            this.routeFuelCost += routeFuelCostDiesel;
            this.routeFuelCost += routeFuelCostElectric;
            this.routeFuelCost += routeFuelCostSteam;
            this.routeFuelCost += routeFuelCostOther;

            TrainMotion finalTrainMotion = TrainMotion.getFinalTrainMotion(trainMotions);
            if (finalTrainMotion != null) {
                double miles = finalTrainMotion.x;
                double tons = finalTrainMotion.w;
                double tonMiles = tons * miles;
                routeMoeCost += MOE_PER_TON_MILE * tonMiles;
                routeMowCost += MOW_PER_TON_MILE * tonMiles;
                routeOverheadCost +=  TGNA_PER_TON_MILE * tonMiles;

            }
            double annualUsePerEngine = routeCrews / 250.;

            routeOverheadCost += ANNUAL_RESERVES_PER_HP_DIESEL * dieselHp * dieselEngines * annualUsePerEngine;
            routeOverheadCost += ANNUAL_RESERVES_PER_HP_ELECTRIC * electricHp * electricEngines * annualUsePerEngine;
            routeOverheadCost += ANNUAL_RESERVES_PER_HP_STEAM * steamerHp * steamerEngines * annualUsePerEngine;
            routeOverheadCost += ANNUAL_RESERVES_PER_HP_OTHER * otherHp * otherEngines * annualUsePerEngine;
        }
    }

    public BigDecimal getRouteTonMiles() {
        double tonMiles = 0;
        for (List<TrainMotion> list : trainMotions.values()) {
            TrainMotion finalTrainMotion = TrainMotion.getFinalTrainMotion(list);
            if (finalTrainMotion != null) {
                tonMiles += finalTrainMotion.w * finalTrainMotion.x;
            }
        }

        return BigDecimal.valueOf(tonMiles);
    }

    public BigDecimal getRouteLaborCost() {
        if (routeLaborCost == null) {
            return BigDecimal.ZERO;
        } else {
            return BigDecimal.valueOf(routeLaborCost);
        }
    }

    /**
     * Labor cost is calculated by the number of shifts required to run the train's assigned route, according to travel
     * times for each segment, plus switching times, if any, at each segment end point, and accounting for crew
     * composition and associated direct crew labor costs.
     */
    public void setRouteLaborCost() {
        if (routeLaborCost == null) {
            routeLaborCost = 0.;
            int routeCrews = getRouteCrews();
            calculateRouteLaborCost(routeCrews);
        }
    }

    private void calculateRouteLaborCost(int routeCrews) {
        int crewPerShift = Integer.parseInt(Setup.getTrainCrewCount());
        int crewEngineers = 0;
        if (crewPerShift > 0) {
            crewEngineers++;
            crewPerShift--;
        }
        int crewConductors = 0;
        if (crewPerShift > 0) {
            crewConductors++;
            crewPerShift--;
        }
        int crewFiremen = 0;
        if (crewPerShift > 0) {
            crewFiremen++;
            crewPerShift--;
        }
        int crewBrakemen = crewPerShift;

        routeLaborCost += crewEngineers * routeCrews * DAILY_LABOR_RATE_ENGINEER;
        routeLaborCost += crewConductors * routeCrews * DAILY_LABOR_RATE_CONDUCTOR;
        routeLaborCost += crewBrakemen * routeCrews * DAILY_LABOR_RATE_BRAKEMAN;
        routeLaborCost += crewFiremen * routeCrews * DAILY_LABOR_RATE_FIREMAN;
    }

    private int getRouteCrews() {
        double routeShifts = 0; // assume 8-hour shifts, no O/T - crew change required at last stop
        int routeCrews = 1; // initial number of crews is 1
        for (Map.Entry<String, List<TrainMotion>> e : trainMotions.entrySet()) {
            TrainMotion finalTrainMotion = TrainMotion.getFinalTrainMotion(e.getValue());
            if (finalTrainMotion != null) {
                routeShifts += finalTrainMotion.t / 3600. / ANNUAL_RESERVES_PER_HP_OTHER;
            }
            Integer switchingActions = trainPickUpOrDropOff.get(e.getKey());
            if (switchingActions != null) {
                routeShifts += switchingActions * Setup.getSwitchTime() / 60. / ANNUAL_RESERVES_PER_HP_OTHER;
            }
            if (routeShifts > ANNUAL_RESERVES_PER_HP_STEAM) {
                routeShifts--;
                routeCrews++; // add another crew for each additional shift as required
            }
        }
        return routeCrews;
    }

    public BigDecimal getRouteMowCost() {
        if (routeMowCost == null) {
            return BigDecimal.ZERO;
        } else {
            return BigDecimal.valueOf(routeMowCost);
        }
    }

    public BigDecimal getRouteOverheadCost() {
        if (routeOverheadCost == null) {
            return BigDecimal.ZERO;
        } else {
            return BigDecimal.valueOf(routeOverheadCost);
        }
    }

    public BigDecimal getRouteTotalCost() {
        return getRouteFuelCost().add(getRouteLaborCost()).add(getRouteMoeCost()).add(getRouteMowCost()).add(getRouteOverheadCost());
    }

    public Map<String, Map<String, Integer>> getSpurCapacityMapByCustomer() {
        return spurCapacityMapByCustomer;
    }

    public Train getTrain() {
        return train;
    }

    public void setTrain(Train train) {
        this.train = train;
    }

    public Map<String, List<Integer>> getTrainCarWeights() {
        return trainCarWeights;
    }

    public Map<String, List<Engine>> getTrainEngines() {
        if (trainEngines == null) {
            trainEngines = new HashMap<>();
        }

        if (trainEngines.isEmpty() && !trainEngineIds.isEmpty()) {
            EngineManager engineManager = InstanceManager.getDefault(EngineManager.class);
            for (Map.Entry<String, List<String>> e : trainEngineIds.entrySet()) {
                String rlId = e.getKey();
                List<String> engineIds = e.getValue();
                for (String engineId : engineIds) {
                    Engine engine = engineManager.getById(engineId);
                    if (engine != null) {
                        trainEngines.computeIfAbsent(rlId, k -> new ArrayList<>());
                        trainEngines.get(rlId).add(engine);
                    }
                }
            }
        }

        return trainEngines;
    }

    public Map<String, List<String>> getTrainEngineIds() {
        return trainEngineIds;
    }

    public Map<String, List<TrainMotion>> getTrainMotions() {
        return trainMotions;
    }

    public Map<String, Integer> getTrainPickUpsOrDropOffs() {
        return trainPickUpOrDropOff;
    }

    public void loadOrigTrackIdsByCarId() {
        for (Car car : InstanceManager.getDefault(CarManager.class).getList(train)) {
            if (!car.isCaboose() && !car.isPassenger()) {
                String[] ids = new String[2];
                ids[ORIG] = car.getTrackId();
                ids[TERM] = car.getDestinationTrackId();
                origTrackIdsByCarId.put(car.getId(), ids);
            }
        }
        saveTrainRevenuesSerFile();
    }

    public void updateRouteLocationRevenues(RouteLocation rl, RouteLocation rlNext) {
        if (rl == null) {
            return;
        }
        updateSpurCapacity(rl);
        updateDemurCharges(rl);
        updateMulctCharges();
        updateTrainCharges(rl);
        updateTrainData(rl, rlNext);
        updateTrainMotions(rl);

        if (rl != train.getTrainTerminatesRouteLocation()) {
            maxRouteTransportFee = maxRouteTransportFee.add(BigDecimal.valueOf(rl.getTransportFee()));
        }
        saveTrainRevenuesSerFile();
    }

    private TrainMotion getPriorRouteLocationFinalTrainMotion(RouteLocation rl) {
        List<RouteLocation> list = train.getRoute().getLocationsBySequenceList();
        for (int i = 1; i < list.size() - 1; i++) {
            if (rl == list.get(i)) {
                RouteLocation lastRl = list.get(i - 1);
                List<TrainMotion> lastRlTrainMotions = trainMotions.get(lastRl.getId());
                return TrainMotion.getFinalTrainMotion(lastRlTrainMotions);
            }
        }
        return new TrainMotion();
    }

    private boolean isSpurPickUp(Car car, RouteLocation rl) {
        if (car.getTrack() == null || !car.getTrack().isSpur() || !rl.getLocation().getTracksList().contains(car.getTrack())) {
            return false;
        }
        if (rl.getLocation().getId().equals(car.getLastLocationId())) {
            return true;
        }

        return car.getRouteLocation() == rl;
    }

    private boolean isSpurSetOut(Car car, RouteLocation rl) {
        if (car.getDestinationTrack() == null || !car.getDestinationTrack().isSpur()) {
            return false;
        }

        return car.getRouteDestination() == rl;
    }

    private CarRevenue carRevenue(Car car) {
        String carId = car.getId();
        Map<String, CarRevenue> map = carRevenueMapByCarId.computeIfAbsent(carId, k -> new TreeMap<>());
        String customer = getCustomer(car);
        if (customer == null) {
            return map.values().stream().filter(cr -> carId.equals(cr.getCarId())).findFirst().orElse(null);
        } else {
            return map.computeIfAbsent(customer, c -> new CarRevenue(carId, c, car.getLoadName()));
        }
    }

    private List<Car> carsOnTracks(Location location) {
        List<Car> cars = new ArrayList<>();

        List<Track> tracksList = location.getTracksList();
        for (Car car : InstanceManager.getDefault(CarManager.class).getList()) {
            if (tracksList.contains(car.getTrack())) {
                cars.add(car);
            }
        }

        return cars;
    }

    private void hazardFeeCharges(Car car, CarRevenue carRevenue) {
        if (!CarLoad.LOAD_TYPE_EMPTY.equals(car.getLoadType()) && car.isHazardous()) {
            carRevenue.setHazardFeeCharges(BigDecimal.valueOf(Integer.parseInt(Setup.getHazardFee())));
        }
    }

    private void saveTrainRevenuesSerFile() {
        deleteTrainRevenuesSerFile(train);
        OutputStream osFile, buffer;
        ObjectOutput output;
        try {
            File file = InstanceManager.getDefault(TrainManagerXml.class).createTrainRevenuesSerFile(train);
            osFile = new FileOutputStream(file);
            buffer = new BufferedOutputStream(osFile);
            output = new ObjectOutputStream(buffer);

            output.writeObject(this);

            output.close();
            buffer.close();
            osFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private BigDecimal switchingChargeByLoadAndType(Car car) {
        String loadName = car.getLoadName();
        String defaultEmptyName = CarRevenue.getDefaultEmptyName();
        if (defaultEmptyName.equals(loadName)) {
            return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchEmpty()));
        }

        switch (car.getTypeName()) {
            case "Boxcar":
            case "XM": {
                String lcLoadName = loadName.toLowerCase();
                if (lcLoadName.contains("aggr") || lcLoadName.contains("cmnt") || lcLoadName.contains("coal") || lcLoadName.contains("sand")) {
                    return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchAggrs()));
                } else if (lcLoadName.contains("feed") || lcLoadName.contains("grain") || lcLoadName.contains("seed")) {
                    return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchGrain()));
                } else if (lcLoadName.contains("coil") || lcLoadName.contains("iron") || lcLoadName.contains("metal") || lcLoadName.contains("scrap") || lcLoadName.contains("steel")) {
                    return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchMetal()));
                } else if (lcLoadName.contains("paper") || lcLoadName.contains("lumber") || lcLoadName.contains("pulp") || lcLoadName.contains("timber") || lcLoadName.contains("wood")) {
                    return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchWoody()));
                } else {
                    return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchLoads()));
                }
            }
            case "Coal":
            case "HopCmnt":
            case "HopCoal":
            case "HopSand":
            case "HopCoal-Ety":
            case "HT":
            case "HMR-cmnt":
            case "HF-coal":
            case "HMR-sand":
            case "HF-coalEty":
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchAggrs()));
            case "HopGrain":
            case "HMR-grain":
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchGrain()));
            case "Coilcar":
            case "Gon-scrap":
            case "FD-coil":
            case "GB-scrap":
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchMetal()));
            case "FlatBHPaper":
            case "FlatBHWood":
            case "FlatTimber":
            case "FlatWood":
            case "FB-paper":
            case "FB-wood":
            case "FL":
            case "FM-wood":
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchWoody()));
            case "Tank Oil":
            case "TM-oil":
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchTanks()));
            default:
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchLoads()));
        }
    }

    private void switchingCharges(Car car, CarRevenue carRevenue) {
        Track currentTrack = car.getTrack();
        Track destinyTrack = car.getDestinationTrack();
        boolean currentTrackIsSpur = currentTrack != null && currentTrack.isSpur();
        boolean destinyTrackIsSpur = destinyTrack != null && destinyTrack.isSpur();
        if (currentTrackIsSpur || destinyTrackIsSpur) {
            carRevenue.setSwitchingCharges(switchingChargeByLoadAndType(car));
        }
    }

    private List<Integer> trainCarWeights(RouteLocation rl) {
        List<Integer> carWeights = new ArrayList<>();
        for (Car rs : InstanceManager.getDefault(CarManager.class).getList(train)) {
            int carWeight = 0;
            if (rs.getRouteLocation() == rl) {
                carWeight += rs.getAdjustedWeightTons();
            }
            if (rs.getRouteDestination() == rl) {
                carWeight -= rs.getAdjustedWeightTons();
            }
            carWeights.add(carWeight);
        }
        return carWeights;
    }

    private void transportCharges(Car car, CarRevenue carRevenue) {
        RouteLocation carRouteLocation = car.getRouteLocation();
        RouteLocation carRouteDestination = car.getRouteDestination();

        int totalRouteFee = 0;
        RouteLocation origRouteLocation = null;
        String carId = car.getId();
        String[] origTrackIds = origTrackIdsByCarId.computeIfAbsent(carId, k -> new String[]{car.getTrackId(), car.getDestinationTrackId()});
        if (carRouteLocation == carRouteDestination) {
            String origTrackId = origTrackIds[ORIG];
            Location origLocation = null;
            for (Location location : InstanceManager.getDefault(LocationManager.class).getList()) {
                origLocation = location.getTracksList().stream().filter(track -> track.getId().equals(origTrackId)).findFirst().map(Track::getLocation).orElse(null);
                if (origLocation != null) {
                    break;
                }
            }
            for (RouteLocation routeLocation : train.getRoute().getLocationsBySequenceList()) {
                if (routeLocation.getLocation() == origLocation) {
                    origRouteLocation = routeLocation;
                }
                if (routeLocation == carRouteDestination) {
                    break;
                }
                if (origRouteLocation != null) {
                    totalRouteFee += routeLocation.getTransportFee();
                }
            }
        }
        if (totalRouteFee > 0) {
            carRevenue.setTransportCharges(BigDecimal.valueOf(totalRouteFee));
        }
    }

    private void updateDemurCharges(RouteLocation rl) {
        if (rl == null || rl.getLocation() == null) {
            return;
        }
        for (Car car : carsOnTracks(rl.getLocation())) {
            String carId = car.getId();
            if (!car.isCaboose() && !car.isPassenger() && !carIdsInDemur.contains(carId)) {
                Track carTrack = car.getTrack();
                boolean carIsOnSpur = carTrack != null && carTrack.isSpur();
                int credits = Integer.parseInt(Setup.getDemurCredits());
                boolean carInDemurrage = car.getWait() >= credits;
                if (carIsOnSpur && carInDemurrage) {
                    carIdsInDemur.add(carId);
                    String customer = getCustomer(car);
                    if (customer != null) {
                        Map<String, CarRevenue> carRevenueByCustomer = carRevenueMapByCarId.computeIfAbsent(carId, k -> new TreeMap<>());
                        CarRevenue carRevenue = carRevenueByCustomer.computeIfAbsent(customer, c -> new CarRevenue(carId, c, car.getLoadName()));
                        String demurrage = car.getRoadName().toUpperCase().endsWith("X") ? Setup.getDemurrageXX() : Setup.getDemurrageRR();
                        carRevenue.setDemurrageCharges(BigDecimal.valueOf(Integer.parseInt(demurrage)));
                    }
                }
            }
        }
    }

    private void updateMulctCharges() {
        String[] ids;
        for (Car car : InstanceManager.getDefault(CarManager.class).getList(train)) {
            String carId = car.getId();
            if (!car.isCaboose() && !car.isPassenger() && !carIdsInMulctSet.contains(carId)) {
                String customer = getCustomer(car);
                if (customer != null) {
                    Map<String, CarRevenue> carRevenueByCustomer = carRevenueMapByCarId.computeIfAbsent(carId, k -> new TreeMap<>());
                    CarRevenue carRevenue = carRevenueByCustomer.get(customer);
                    if ((ids = origTrackIdsByCarId.get(carId)) != null && RollingStock.NONE.equals(ids[TERM])) {
                        carIdsInMulctSet.add(carId);
                        if (carRevenue == null) {
                            carRevenue = new CarRevenue(carId, customer, car.getLoadName());
                            carRevenueByCustomer.put(customer, carRevenue);
                        }
                        carRevenue.setDiversionMulct(BigDecimal.valueOf(Integer.parseInt(Setup.getDivertMulct())));
                    } else {
                        if (car.getRouteLocation() == null && car.getRouteDestination() == null) {
                            car.setTrain(null);
                            car.setDestination(null, null);
                            carIdsInMulctSet.add(carId);
                            if (carRevenue == null) {
                                carRevenue = new CarRevenue(carId, customer, car.getLoadName());
                                carRevenueByCustomer.put(customer, carRevenue);
                            }
                            carRevenue.setSwitchingCharges(BigDecimal.ZERO);
                            carRevenue.setTransportCharges(BigDecimal.ZERO);
                            carRevenue.setHazardFeeCharges(BigDecimal.ZERO);
                            carRevenue.setCancellationMulct(BigDecimal.valueOf(Integer.parseInt(Setup.getCancelMulct())));
                        } else if ((ids = origTrackIdsByCarId.get(carId)) != null && !ids[TERM].equals(car.getDestinationTrackId())) {
                            carIdsInMulctSet.add(carId);
                            if (carRevenue == null) {
                                carRevenue = new CarRevenue(carId, customer, car.getLoadName());
                                carRevenueByCustomer.put(customer, carRevenue);
                            }
                            carRevenue.setDiversionMulct(BigDecimal.valueOf(Integer.parseInt(Setup.getDivertMulct())));
                        }
                    }
                }
            }
        }
    }

    private void updateSpurCapacity(RouteLocation rl) {
        if (rl == null || rl.getLocation() == null) {
            return;
        }
        for (Track track : rl.getLocation().getTracksList()) {
            if (track.isSpur()) {
                String customer = TrainCommon.splitString(track.getName());
                spurCapacityMapByCustomer.computeIfAbsent(customer, k -> new HashMap<>());
                Map<String, Integer> trackIdLengthMap = spurCapacityMapByCustomer.get(customer);
                trackIdLengthMap.computeIfAbsent(track.getId(), k -> track.getLength());
            }
        }
    }

    private void updateTrainCharges(RouteLocation rl) {
        for (Car car : InstanceManager.getDefault(CarManager.class).getList(train)) {
            if (!car.isCaboose() && !car.isPassenger()) {
                CarRevenue carRevenue = carRevenue(car);
                if (carRevenue != null) {
                    hazardFeeCharges(car, carRevenue);
                    switchingCharges(car, carRevenue);
                    transportCharges(car, carRevenue);
                    if (isSpurPickUp(car, rl) && !Boolean.TRUE.equals(carRevenue.isPickup())) {
                        carRevenue.setPickup(true);
                        carRevenue.setLoadName(car.getLoadName()); // car load name is mutable
                    }
                    if (isSpurSetOut(car, rl) && !Boolean.FALSE.equals(carRevenue.isPickup())) {
                        carRevenue.setPickup(false);
                        carRevenue.setLoadName(car.getLoadName()); // car load name is mutable
                    }
                }
            }
        }
    }

    private void updateTrainData(RouteLocation rl, RouteLocation rlNext) {
        for (Engine engine : InstanceManager.getDefault(EngineManager.class).getList(train)) {
            if (engine.getRouteLocation() == rl && engine.getRouteDestination() != rl) {
                if (rlNext != null) {
                    Location location = rlNext.getLocation();
                    trainPickUpOrDropOff.put(rl.getId(), location.getPickupRS() + location.getDropRS());
                }
                getTrainEngines().computeIfAbsent(rl.getId(), k -> new ArrayList<>());
                getTrainEngines().get(rl.getId()).add(engine);

                getTrainEngineIds().computeIfAbsent(rl.getId(), k -> new ArrayList<>());
                getTrainEngineIds().get(rl.getId()).add(engine.getId());

                List<Integer> trainCarWeights = trainCarWeights(rl);
                this.trainCarWeights.put(rl.getId(), trainCarWeights);
            }
        }
    }

    private void updateTrainMotions(RouteLocation rl) {
        double priorSpeed = getPriorRouteLocationFinalTrainMotion(rl).v;
        List<TrainMotion> tmList = TrainPhysics.getTrainMotions(train, rl, priorSpeed);
        trainMotions.put(rl.getId(), tmList);
    }

}
