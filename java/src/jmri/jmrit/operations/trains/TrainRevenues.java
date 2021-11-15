package jmri.jmrit.operations.setup;

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
import jmri.jmrit.operations.trains.Train;
import jmri.jmrit.operations.trains.TrainCommon;
import jmri.jmrit.operations.trains.TrainCsvRevenue;
import jmri.jmrit.operations.trains.TrainManagerXml;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/**
 * TrainRevenues - a POJO for a train's CarRevenue POJOs and other supporting data
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainRevenues implements Serializable {
    public static final int ORIG = 0;
    public static final int TERM = 1;
    private static final long serialVersionUID = 4L;

    private final Map<String, Map<String, CarRevenue>> carRevenueMapByCarId = new TreeMap<>();
    private final Map<String, Map<String, Integer>> spurCapacityMapByCustomer = new HashMap<>();
    private final Set<String> carIdsInDemur = new TreeSet<>();
    private final Set<String> carIdsInMulctSet = new TreeSet<>();

    private transient Train train;
    private Map<String, String[]> origTrackIdsByCarId;
    private BigDecimal maxRouteTransportFee = BigDecimal.ZERO;

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
        new TrainCsvRevenue(train);

        File file = InstanceManager.getDefault(TrainManagerXml.class).getTrainCsvRevenueFile(train);
        if (!file.exists()) {
            return null;
        }
        return file;
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

    public static List<Car> sortCars(List<Car> cars) {
        cars.sort(Comparator.comparing((Function<Car, String>) RollingStock::getRoadName).thenComparing(RollingStock::getNumber));

        return cars;
    }

    public Train getTrain() {
        return train;
    }

    public void setTrain(Train train) {
        this.train = train;
    }

    public BigDecimal getMaxRouteTransportFee() {
        return maxRouteTransportFee;
    }

    public Map<String, Map<String, Integer>> getSpurCapacityMapByCustomer() {
        return spurCapacityMapByCustomer;
    }

    public Map<String, String[]> getOrigTrackIdsByCarId() {
        return origTrackIdsByCarId;
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

    public void loadOrigTrackIdsByCarId() {
        origTrackIdsByCarId = new HashMap<>();
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

    public void updateCarRevenues(RouteLocation rl) {
        if (rl == null) {
            return;
        }
        updateSpurCapacity(rl);
        updateDemurCharges(rl);
        updateMulctCharges();
        updateTrainCharges(rl);
        updateTrainExpenseData(rl);

        if (rl != train.getTrainTerminatesRouteLocation()) {
            maxRouteTransportFee = maxRouteTransportFee.add(BigDecimal.valueOf(rl.getTransportFee()));
        }
        saveTrainRevenuesSerFile();
    }

    private CarRevenue getCarRevenue(Car car) {
        String carId = car.getId();
        Map<String, CarRevenue> map = carRevenueMapByCarId.computeIfAbsent(carId, k -> new TreeMap<>());
        String customer = getCustomer(car);
        if (customer == null) {
            return map.values().stream().filter(cr -> carId.equals(cr.getCarId())).findFirst().orElse(null);
        } else {
            return map.computeIfAbsent(customer, c -> new CarRevenue(carId, c, car.getLoadName()));
        }
    }

    private BigDecimal getSwitchingChargeByLoadAndType(Car car) {
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

    private void setHazardFeeCharges(Car car, CarRevenue carRevenue) {
        if (!CarLoad.LOAD_TYPE_EMPTY.equals(car.getLoadType()) && car.isHazardous()) {
            carRevenue.setHazardFeeCharges(BigDecimal.valueOf(Integer.parseInt(Setup.getHazardFee())));
        }
    }

    private void setTransportCharges(Car car, CarRevenue carRevenue) {
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

    private void setSwitchingCharges(Car car, CarRevenue carRevenue) {
        Track currentTrack = car.getTrack();
        Track destinyTrack = car.getDestinationTrack();
        boolean currentTrackIsSpur = currentTrack != null && currentTrack.isSpur();
        boolean destinyTrackIsSpur = destinyTrack != null && destinyTrack.isSpur();
        if (currentTrackIsSpur || destinyTrackIsSpur) {
            carRevenue.setSwitchingCharges(getSwitchingChargeByLoadAndType(car));
        }
    }

    private List<Car> getCarsOnTracks(Location location) {
        List<Car> cars = new ArrayList<>();

        List<Track> tracksList = location.getTracksList();
        for (Car car : InstanceManager.getDefault(CarManager.class).getList()) {
            if (tracksList.contains(car.getTrack())) {
                cars.add(car);
            }
        }

        return cars;
    }

    private void updateDemurCharges(RouteLocation rl) {
        if (rl == null || rl.getLocation() == null) {
            return;
        }
        for (Car car : getCarsOnTracks(rl.getLocation())) {
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
                CarRevenue carRevenue = getCarRevenue(car);
                if (carRevenue != null) {
                    setHazardFeeCharges(car, carRevenue);
                    setSwitchingCharges(car, carRevenue);
                    setTransportCharges(car, carRevenue);
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

    private final Map<String, Double> gradePercent = new HashMap<>();
    private final Map<String, Integer> trainHp = new HashMap<>();
    private final Map<String, String> engineModel = new HashMap<>();
    private final Map<String, Integer> trainCarsTons = new HashMap<>();
    private final Map<String, Integer> trainTonWeight = new HashMap<>();
    private final Map<String, Integer> travelMinutes = new HashMap<>();
    private final Map<String, Double> trainStartingForce = new HashMap<>();
    private final Map<String, Integer> trainAxles = new HashMap<>();

    public Map<String, Double> getGradePercent() { return gradePercent; }
    public Map<String, Integer> getTrainHp() { return trainHp; }
    public Map<String, String> getEngineModel() { return engineModel; }
    public Map<String, Integer> getTrainCarsTons() { return trainCarsTons; }
    public Map<String, Double> getTrainStartingForce() { return trainStartingForce; }
    public Map<String, Integer> getTrainTonWeight() { return trainTonWeight; }
    public Map<String, Integer> getTravelMinutes() { return travelMinutes; }
    public Map<String, Integer> getTrainAxles() { return trainAxles; }

    private void updateTrainExpenseData(RouteLocation rl) {
        gradePercent.put(rl.getId(), rl.getGrade());
        trainHp.put(rl.getId(), this.getEngineHp(rl));
        engineModel.put(rl.getId(), this.getEngineModel(rl));
        trainCarsTons.put(rl.getId(), this.getTrainCarsWeight(rl));
        trainTonWeight.put(rl.getId(), train.getTrainWeight());
        travelMinutes.put(rl.getId(), Setup.getTravelTime() + rl.getWait());
        trainStartingForce.put(rl.getId(), getEnginesStartingForce(rl));
        trainAxles.put(rl.getId(), this.getTrainAxles(rl));
    }

    private int getTrainAxles(RouteLocation rl) {
        int axles = 0;

        for (Engine engine : InstanceManager.getDefault(EngineManager.class).getList(train)) {
            if (engine.getRouteLocation() == rl) { axles += "Diesel".equals(engine.getTypeName()) ? 6 : 8; }
            if (engine.getRouteDestination() == rl) { axles -= "Diesel".equals(engine.getTypeName()) ? 6 : 8; }
        }
        for (Car rs : InstanceManager.getDefault(CarManager.class).getList(train)) {
            if (rs.getRouteLocation() == rl) { axles += 4; }
            if (rs.getRouteDestination() == rl) { axles -= 4; }
        }

        return axles;
    }

    private int getEngineHp(RouteLocation rl) {
        int hp = 0;
        for (Engine engine : InstanceManager.getDefault(EngineManager.class).getList(train)) {
            if (engine.getRouteLocation() == rl) { hp += engine.getHpInteger(); }
            if (engine.getRouteDestination() == rl) { hp -= engine.getHpInteger(); }
        }
        return hp;
    }

    private String getEngineModel(RouteLocation rl) {
        String model = "";
        for (Engine engine : InstanceManager.getDefault(EngineManager.class).getList(train)) {
            if (engine.getRouteLocation() == rl && engine.getRouteDestination() != rl) {
                if (model.isEmpty()) {
                    model = engine.getModel();
                } else {
                    model += "; " + engine.getModel();
                }
            }
        }
        return model;
    }

    private double getEnginesStartingForce(RouteLocation rl) {
        double tf = 0;

        for (Engine engine : InstanceManager.getDefault(EngineManager.class).getList(train)) {
            double adhesion = "Diesel".equals(engine.getTypeName()) ? 0.25 : 0.15;
            int weightTons = engine.getAdjustedWeightTons();
            double traction = adhesion * weightTons;

            if (engine.getRouteLocation() == rl) {
                tf += traction;
            }
            if (engine.getRouteDestination() == rl) {
                tf -= traction;
            }
        }
        return tf;
    }

    private int getTrainCarsWeight(RouteLocation rl) {
        int weight = 0;
        for (Car rs : InstanceManager.getDefault(CarManager.class).getList(train)) {
            if (rs.getRouteLocation() == rl) { weight += rs.getAdjustedWeightTons(); }
            if (rs.getRouteDestination() == rl) { weight -= rs.getAdjustedWeightTons(); }
        }
        return weight;
    }


}
