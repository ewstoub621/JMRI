package jmri.jmrit.operations.setup;

import jmri.InstanceManager;
import jmri.jmrit.operations.locations.Track;
import jmri.jmrit.operations.rollingstock.RollingStock;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarLoads;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.cars.CarRevenue;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.trains.Train;
import jmri.jmrit.operations.trains.TrainCommon;
import jmri.jmrit.operations.trains.TrainCsvRevenue;
import jmri.jmrit.operations.trains.TrainManagerXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * TrainRevenues - a POJO for a train's CarRevenue POJOs and other supporting data
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainRevenues implements Serializable {
    private static final long serialVersionUID = 4L;

    public static final int ORIG = 0;
    public static final int DEST = 1;
    private final static Logger log = LoggerFactory.getLogger(TrainRevenues.class);
    private final Map<String, CarRevenue> carRevenuesByCarKey = new TreeMap<>();
    private final Map<String, Map<String, Integer>> spurCapacityByCustomer = new HashMap<>();
    private final Set<String> carKeysInDemur = new TreeSet<>();
    private final Set<String> carKeysInMulctSet = new TreeSet<>();
    private transient Train train;
    private Map<String, String[]> origRouteIdsByCarKey;
    private BigDecimal maxRouteTransportFee = BigDecimal.ZERO;

    public TrainRevenues(Train train) {
        this.train = train;
    }

    public static String getCustomer(Car car) {
        Track track;

        track = car.getDestinationTrack();
        if (track != null && track.isSpur()) {
            return TrainCommon.splitString(track.getName());
        }

        track = car.getTrack();
        if (track != null && track.isSpur()) {
            return TrainCommon.splitString(track.getName());
        }

        Train train = car.getTrain();
        return train.getLeadEngineRoadName() + " " + train.getCurrentLocationName();
    }

    public static TrainRevenues getTrainRevenues(Train train) {
        TrainRevenues trainRevenues = null;
        File file = InstanceManager.getDefault(TrainManagerXml.class)
                .getTrainRevenuesSerFile(train);
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

    public void deleteTrainRevenuesSerFile(Train train) {
        File trainRevenuesSerFile = InstanceManager.getDefault(TrainManagerXml.class)
                .getTrainRevenuesSerFile(train);
        if (trainRevenuesSerFile.exists()) {
            trainRevenuesSerFile.delete();
        }
    }

    public BigDecimal getMaxRouteTransportFee() {
        return maxRouteTransportFee;
    }

    public Map<String, Map<String, Integer>> getSpurCapacityByCustomer() {
        return spurCapacityByCustomer;
    }

    public Map<String, String[]> getOrigRouteIdsByCarKey() {
        return origRouteIdsByCarKey;
    }

    public Collection<CarRevenue> getCarRevenues() {
        return carRevenuesByCarKey.values();
    }

    public Map<String, Set<CarRevenue>> getCarRevenuesByCustomer() {
        Map<String, Set<CarRevenue>> map = new TreeMap<>();

        for (CarRevenue carRevenue : carRevenuesByCarKey.values()) {
            String customer = carRevenue.getCustomerName();
            if (!customer.trim().isEmpty()) {
                map.putIfAbsent(customer, new TreeSet<>());
                map.get(customer).add(carRevenue);
            }
        }

        return map;
    }

    public File getCsvRevenueFile(Train train) {
        new TrainCsvRevenue(train);
        // validate file creation
        File file = InstanceManager.getDefault(TrainManagerXml.class)
                .getTrainCsvRevenueFile(train);
        if (!file.exists()) {
            log.warn("CSV revenue file {} was not created for train ({})", file.getName(), train.getName());
            return null;
        }
        return file;
    }

    public void loadOrigRouteIds() {
        origRouteIdsByCarKey = new HashMap<>();
        for (Car car : InstanceManager.getDefault(CarManager.class)
                .getList(train)) {
            if (!car.isCaboose() && !car.isPassenger()) {
                String[] ids = new String[2];
                ids[ORIG] = car.getRouteLocationId();
                ids[DEST] = car.getRouteDestinationId();
                origRouteIdsByCarKey.put(car.toString(), ids);
            }
        }
        saveTrainRevenuesSerFile(this);
    }

    public void setTrain(Train train) {
        this.train = train;
    }

    public void updateCarRevenues(RouteLocation rl) {
        if (rl == null) {
            return;
        }
        log.debug("updateCarRevenues(this RouteLocation '{}', next RouteLocation '{}')", rl, rl);
        boolean spursChanges = updateSpurCapacity(rl);
        boolean demurChanges = updateDemurCharges(rl);
        boolean mulctChanges = updateMulctCharges();
        boolean trainChanges = updateTrainCharges(rl);

        if (rl != train.getTrainTerminatesRouteLocation()) {
            maxRouteTransportFee = maxRouteTransportFee.add(BigDecimal.valueOf(rl.getTransportFee()));
        }

        if (spursChanges || demurChanges || mulctChanges || trainChanges) {
            saveTrainRevenuesSerFile(this);
        }
    }

    private CarRevenue getCarRevenue(Car trainCar) {
        String carKey = trainCar.toString();
        String customer = getCustomer(trainCar);
        CarRevenue carRevenue = carRevenuesByCarKey.get(carKey);
        if (carRevenue == null) {
            carRevenue = new CarRevenue(carKey, customer);
            carRevenuesByCarKey.put(carKey, carRevenue);
        }

        return carRevenue;
    }

    private BigDecimal getSwitchingChargeByLoadAndType(Car car) {
        String loadName = car.getLoadName();
        String defaultEmptyName = InstanceManager.getDefault(CarLoads.class)
                .getDefaultEmptyName();
        if (defaultEmptyName.equals(loadName)) {
            return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchEmpty()));
        }

        switch (car.getTypeName()) {
        case "Boxcar":
        case "XM": {
            String lcLoadName = loadName.toLowerCase();
            if (lcLoadName.contains("aggr") ||
                    lcLoadName.contains("cmnt") ||
                    lcLoadName.contains("coal") ||
                    lcLoadName.contains("sand")) {
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchAggrs()));
            } else if (lcLoadName.contains("feed") ||
                    lcLoadName.contains("grain") ||
                    lcLoadName.contains("seed")) {
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchGrain()));
            } else if (lcLoadName.contains("coil") ||
                    lcLoadName.contains("iron") ||
                    lcLoadName.contains("metal") ||
                    lcLoadName.contains("scrap") ||
                    lcLoadName.contains("steel")) {
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchMetal()));
            } else if (lcLoadName.contains("paper") ||
                    lcLoadName.contains("lumber") ||
                    lcLoadName.contains("pulp") ||
                    lcLoadName.contains("timber") ||
                    lcLoadName.contains("wood")) {
                return BigDecimal.valueOf(Integer.parseInt(Setup.getSwitchWoody()));
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

    private boolean isCarPickUp(Car car, RouteLocation rl) {
        String thisLocationId = rl.getLocation().getId();
        String lastLocationId = car.getLastLocationId();
        boolean locIdsMatch = thisLocationId.equals(lastLocationId);

        List<Track> tracksList = rl.getLocation().getTracksList();
        boolean rlsMatch = car.getRouteLocation() == rl;

        boolean isCarTrackInTrackList = tracksList.contains(car.getTrack());
        return (locIdsMatch || rlsMatch) && isCarTrackInTrackList;
    }

    private boolean isCarSetOut(Car car, RouteLocation rl) {
        return car.getRouteDestination() == rl;
    }

    private void saveTrainRevenuesSerFile(TrainRevenues trainRevenues) {
        Train train = trainRevenues.train;
        deleteTrainRevenuesSerFile(train);
        OutputStream osFile, buffer;
        ObjectOutput output;
        try {
            File file = InstanceManager.getDefault(TrainManagerXml.class)
                    .createTrainRevenuesSerFile(train);
            osFile = new FileOutputStream(file);
            buffer = new BufferedOutputStream(osFile);
            output = new ObjectOutputStream(buffer);

            output.writeObject(trainRevenues);

            output.close();
            buffer.close();
            osFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean setHazardFeeCharges(Car trainCar, CarRevenue carRevenue) {
        boolean changed = false;

        if (trainCar.isHazardous()) {
            carRevenue.setHazardFeeCharges(BigDecimal.valueOf(Integer.parseInt(Setup.getHazardFee())));
            changed = true;
        }

        return changed;
    }

    private boolean setTransportCharges(Car trainCar, CarRevenue carRevenue) {
        boolean changed = false;
        int transportFee = 0;
        String carKey = trainCar.toString();
        String[] ids = origRouteIdsByCarKey.get(carKey);
        for (RouteLocation routeLocation : train.getRoute().getLocationsBySequenceList()) {
            String id = routeLocation.getId();
            if (id.equals(ids[ORIG])) {
                transportFee += routeLocation.getTransportFee();
            }
        }
        if (transportFee > 0) {
            carRevenue.setTransportCharges(BigDecimal.valueOf(transportFee));
            changed = true;
        }
        return changed;
    }

    private boolean setSwitchingCharges(Car trainCar, CarRevenue carRevenue) {
        boolean changed = false;
        Track currentTrack = trainCar.getTrack();
        Track destinyTrack = trainCar.getDestinationTrack();
        boolean currentTrackIsSpur = currentTrack != null && currentTrack.isSpur();
        boolean destinyTrackIsSpur = destinyTrack != null && destinyTrack.isSpur();
        if (currentTrackIsSpur || destinyTrackIsSpur) {
            carRevenue.setSwitchingCharges(getSwitchingChargeByLoadAndType(trainCar));
            changed = true;
        }
        return changed;
    }

    private boolean updateDemurCharges(RouteLocation rl) {
        if (rl == null || rl.getLocation() == null) {
            return false;
        }
        boolean changed = false;
        for (Car car : rl.getLocation().getCarsOnTracks()) {
            String carKey = car.toString();
            if (!car.isCaboose() && !car.isPassenger() && !carKeysInDemur.contains(carKey)) {
                Track carTrack = car.getTrack();
                boolean carIsOnSpur = carTrack != null && carTrack.isSpur();
                int credits = Integer.parseInt(Setup.getDemurCredits());
                boolean carInDemurrage = car.getWait() >= credits;
                if (carIsOnSpur && carInDemurrage) {
                    changed = true;
                    carKeysInDemur.add(carKey);
                    log.debug("demurrage fee for in-spur freight car {} at {} - will wait {} vs credits {} days",
                              car, carTrack.getName(), car.getWait(), credits
                    );
                    CarRevenue carRevenue = carRevenuesByCarKey.get(carKey);
                    if (carRevenue == null) {
                        carRevenue = new CarRevenue(carKey, getCustomer(car));
                        carRevenuesByCarKey.put(carKey, carRevenue);
                    }
                    String demurrage = car.getRoadName().toUpperCase().endsWith(
                            "X") ? Setup.getDemurrageXX() : Setup.getDemurrageRR();
                    carRevenue.setDemurrageCharges(BigDecimal.valueOf(Integer.parseInt(demurrage)));
                }
            }
        }
        return changed;
    }

    private boolean updateMulctCharges() {
        boolean changed = false;
        List<Car> trainCarList = InstanceManager.getDefault(CarManager.class)
                .getList(train);
        HashSet<Car> trainCarSet = new HashSet<>(trainCarList);
        for (Car trainCar : trainCarSet) {
            String carKey = trainCar.toString();
            boolean carKeyInMulctSet = carKeysInMulctSet.contains(carKey);
            if (!trainCar.isCaboose() && !trainCar.isPassenger() && !carKeyInMulctSet) {
                String carCustomer = getCustomer(trainCar);
                //
                String[] ids = origRouteIdsByCarKey.get(carKey);
                if (ids != null) {
                    String origRouteDestinationId = ids[DEST];
                    if (RollingStock.NONE.equals(origRouteDestinationId)) {
                        changed = true;
                        log.debug("diversion mulct for freight car '{}' added to train", trainCar);

                        carKeysInMulctSet.add(carKey);
                        CarRevenue carRevenue = carRevenuesByCarKey.get(carKey);
                        if (carRevenue == null) {
                            carRevenue = new CarRevenue(carKey, carCustomer);
                            carRevenuesByCarKey.put(carKey, carRevenue);
                        }

                        carRevenue.setDiversionMulct(BigDecimal.valueOf(Integer.parseInt(Setup.getDivertMulct())));
                    } else {
                        if (trainCar.getRouteLocation() == null && trainCar.getRouteDestination() == null) {
                            changed = true;
                            log.debug("cancellation mulct for car '{}' in train", trainCar);

                            carKeysInMulctSet.add(carKey);
                            trainCar.setTrain(null);
                            trainCar.setDestination(null, null);
                            trainCar.setRouteDestination(null);

                            CarRevenue carRevenue = carRevenuesByCarKey.get(carKey);
                            if (carRevenue == null) {
                                carRevenue = new CarRevenue(carKey, carCustomer);
                                carRevenuesByCarKey.put(carKey, carRevenue);
                            }
                            carRevenue.setSwitchingCharges(BigDecimal.ZERO);
                            carRevenue.setTransportCharges(BigDecimal.ZERO);
                            carRevenue.setHazardFeeCharges(BigDecimal.ZERO);

                            carRevenue.setCancellationMulct(
                                    BigDecimal.valueOf(Integer.parseInt(Setup.getCancelMulct())));
                        } else if (!origRouteDestinationId.equals(trainCar.getRouteDestinationId())) {
                            changed = true;
                            log.debug("diversion mulct for car '{}' in train", trainCar);

                            carKeysInMulctSet.add(carKey);
                            CarRevenue carRevenue = carRevenuesByCarKey.get(carKey);
                            if (carRevenue == null) {
                                carRevenue = new CarRevenue(carKey, carCustomer);
                                carRevenuesByCarKey.put(carKey, carRevenue);
                            }
                            carRevenue.setCustomerName(getCustomer(trainCar));

                            carRevenue.setSwitchingCharges(BigDecimal.ZERO);
                            carRevenue.setTransportCharges(BigDecimal.ZERO);
                            carRevenue.setHazardFeeCharges(BigDecimal.ZERO);

                            carRevenue.setDiversionMulct(BigDecimal.valueOf(Integer.parseInt(Setup.getDivertMulct())));
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean updateSpurCapacity(RouteLocation rl) {
        if (rl == null || rl.getLocation() == null) {
            return false;
        }
        boolean changed = false;

        for (Track track : rl.getLocation().getTracksList()) {
            if (track.isSpur()) {
                String customer = TrainCommon.splitString(track.getName());
                if (spurCapacityByCustomer.get(customer) == null) {
                    spurCapacityByCustomer.put(customer, new HashMap<>());
                    changed = true;
                }
                Map<String, Integer> trackIdLengthMap = spurCapacityByCustomer.get(customer);
                if (trackIdLengthMap.get(track.getId()) == null) {
                    trackIdLengthMap.put(track.getId(), track.getLength());
                    changed = true;
                }
            }
        }

        return changed;
    }

    private boolean updateTrainCharges(RouteLocation rl) {
        boolean changed = false;
        List<Car> trainCarList = InstanceManager.getDefault(CarManager.class)
                .getList(train);
        Set<Car> trainCarSet = new HashSet<>(trainCarList);
        for (Car trainCar : trainCarSet) {
            if (!trainCar.isCaboose() && !trainCar.isPassenger()) {
                CarRevenue carRevenue = getCarRevenue(trainCar);

                if (setHazardFeeCharges(trainCar, carRevenue)) {
                    changed = true;
                }
                if (setSwitchingCharges(trainCar, carRevenue)) {
                    changed = true;
                }
                if (setTransportCharges(trainCar, carRevenue)) {
                    changed = true;
                }
                if (isCarPickUp(trainCar, rl)) {
                    log.debug("pick up freight car '{}' at location {}", trainCar, rl.getName());
                    if (!Boolean.TRUE.equals(carRevenue.isPickup())) {
                        carRevenue.setPickup(true);
                        changed = true;
                    }
                }
                if (isCarSetOut(trainCar, rl)) {
                    log.debug("set out freight car '{}' at location {}", trainCar, rl.getName());
                    if (!Boolean.FALSE.equals(carRevenue.isPickup())) {
                        carRevenue.setPickup(false);
                        changed = true;
                    }
                }
                log.debug("updateTrainCharges at location {}: {}", trainCar, rl.getName());
            }
        }
        return changed;
    }

}
