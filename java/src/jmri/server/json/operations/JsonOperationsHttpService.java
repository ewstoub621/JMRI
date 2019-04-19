package jmri.server.json.operations;

import static jmri.server.json.JSON.DATA;
import static jmri.server.json.JSON.ENGINES;
import static jmri.server.json.JSON.FORCE_DELETE;
import static jmri.server.json.JSON.LENGTH;
import static jmri.server.json.JSON.NAME;
import static jmri.server.json.JSON.NULL;
import static jmri.server.json.JSON.TYPE;
import static jmri.server.json.operations.JsonOperations.CAR;
import static jmri.server.json.operations.JsonOperations.CARS;
import static jmri.server.json.operations.JsonOperations.ENGINE;
import static jmri.server.json.operations.JsonOperations.KERNEL;
import static jmri.server.json.operations.JsonOperations.LOCATION;
import static jmri.server.json.operations.JsonOperations.LOCATIONS;
import static jmri.server.json.operations.JsonOperations.TRACK;
import static jmri.server.json.operations.JsonOperations.TRAIN;
import static jmri.server.json.operations.JsonOperations.TRAINS;
import static jmri.server.json.operations.JsonOperations.WEIGHT;

import java.util.Locale;

import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jmri.InstanceManager;
import jmri.jmrit.operations.locations.Location;
import jmri.jmrit.operations.locations.LocationManager;
import jmri.jmrit.operations.locations.Track;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.cars.Kernel;
import jmri.jmrit.operations.rollingstock.engines.EngineManager;
import jmri.jmrit.operations.trains.Train;
import jmri.jmrit.operations.trains.TrainManager;
import jmri.server.json.JsonException;
import jmri.server.json.JsonHttpService;

/**
 *
 * @author Randall Wood (C) 2016, 2018, 2019
 */
public class JsonOperationsHttpService extends JsonHttpService {

    // private final static Logger log = LoggerFactory.getLogger(JsonOperationsHttpService.class);
    private final JsonUtil utilities;

    public JsonOperationsHttpService(ObjectMapper mapper) {
        super(mapper);
        this.utilities = new JsonUtil(mapper);
    }

    @Override
    public JsonNode doGet(String type, String name, JsonNode data, Locale locale) throws JsonException {
        switch (type) {
            case CAR:
                return this.utilities.getCar(locale, name);
            case ENGINE:
                return this.utilities.getEngine(locale, name);
            case KERNEL:
                Kernel kernel = InstanceManager.getDefault(CarManager.class).getKernelByName(name);
                if (kernel == null) {
                    throw new JsonException(HttpServletResponse.SC_NOT_FOUND,
                            Bundle.getMessage(locale, "ErrorNotFound", type, name));
                }
                return getKernel(kernel, locale);
            case LOCATION:
                return this.utilities.getLocation(locale, name);
            case TRAIN:
            case TRAINS:
                return this.utilities.getTrain(locale, name);
            default:
                throw new JsonException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        Bundle.getMessage(locale, "ErrorInternal", type)); // NOI18N
        }
    }

    @Override
    public JsonNode doPost(String type, String name, JsonNode data, Locale locale) throws JsonException {
        switch (type) {
            case TRAIN:
                this.setTrain(locale, name, data);
                return this.utilities.getTrain(locale, name);
            case CAR:
                this.setCar(locale, name, data);
                return this.utilities.getCar(locale, name);
            case ENGINE:
            case LOCATION:
            case TRAINS:
                return this.doGet(type, name, data, locale);
            default:
                throw new JsonException(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                        Bundle.getMessage(locale, "PostNotAllowed", type)); // NOI18N
        }
    }

    @Override
    public JsonNode doPut(String type, String name, JsonNode data, Locale locale) throws JsonException {
        switch (type) {
            case KERNEL:
                Kernel kernel = getCarManager().newKernel(name);
                return getKernel(kernel, locale);
            default:
                return super.doPut(type, name, data, locale);
        }
    }

    @Override
    public ArrayNode doGetList(String type, JsonNode data, Locale locale) throws JsonException {
        switch (type) {
            case CAR:
            case CARS:
                return this.getCars(locale);
            case ENGINE:
            case ENGINES:
                return this.getEngines(locale);
            case KERNEL:
                return this.getKernels(locale);
            case LOCATION:
            case LOCATIONS:
                return this.getLocations(locale);
            case TRAIN:
            case TRAINS:
                return this.utilities.getTrains(locale);
            default:
                throw new JsonException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        Bundle.getMessage(locale, "ErrorInternal", type)); // NOI18N
        }
    }

    @Override
    public void doDelete(String type, String name, JsonNode data, Locale locale) throws JsonException {
        switch (type) {
            case KERNEL:
                Kernel kernel = InstanceManager.getDefault(CarManager.class).getKernelByName(name);
                if (kernel == null) {
                    throw new JsonException(HttpServletResponse.SC_NOT_FOUND,
                            Bundle.getMessage(locale, "ErrorNotFound", type, name));
                }
                if (kernel.getSize() != 0 && !acceptForceDeleteToken(type, name, data.path(FORCE_DELETE).asText())) {
                    throwDeleteConflictException(type, name, getKernelCars(kernel, locale), locale);
                }
                InstanceManager.getDefault(CarManager.class).deleteKernel(name);
                break;
            default:
                super.doDelete(type, name, data, locale);
        }
    }

    private ObjectNode getKernel(Kernel kernel, Locale locale) {
        ObjectNode root = mapper.createObjectNode();
        root.put(TYPE, KERNEL);
        ObjectNode data = root.putObject(DATA);
        data.put(NAME, kernel.getName());
        data.put(WEIGHT, kernel.getAdjustedWeightTons());
        data.put(LENGTH, kernel.getTotalLength());
        data.set(CARS, getKernelCars(kernel, locale));
        return root;
    }

    private ArrayNode getKernelCars(Kernel kernel, Locale locale) {
        ArrayNode array = mapper.createArrayNode();
        kernel.getCars().forEach((car) -> {
            ObjectNode root = array.addObject();
            root.put(TYPE, CAR);
            root.set(DATA, utilities.getCar(car));
        });
        return array;
    }

    private ArrayNode getKernels(Locale locale) {
        ArrayNode root = mapper.createArrayNode();
        getCarManager().getKernelNameList().forEach((kernel) -> {
            root.add(getKernel(getCarManager().getKernelByName(kernel), locale));
        });
        return root;
    }

    public ArrayNode getCars(Locale locale) {
        ArrayNode root = mapper.createArrayNode();
        InstanceManager.getDefault(CarManager.class).getByIdList().forEach((rs) -> {
            root.add(this.utilities.getCar(locale, rs.getId()));
        });
        return root;
    }

    public ArrayNode getEngines(Locale locale) {
        ArrayNode root = mapper.createArrayNode();
        InstanceManager.getDefault(EngineManager.class).getByIdList().forEach((rs) -> {
            root.add(this.utilities.getEngine(locale, rs.getId()));
        });
        return root;
    }

    public ArrayNode getLocations(Locale locale) throws JsonException {
        ArrayNode root = mapper.createArrayNode();
        for (Location location : InstanceManager.getDefault(LocationManager.class).getLocationsByIdList()) {
            root.add(this.utilities.getLocation(locale, location.getId()));
        }
        return root;
    }

    /**
     * Set the properties in the data parameter for the train with the given id.
     * <p>
     * Currently only moves the train to the location given with the key
     * {@value jmri.server.json.operations.JsonOperations#LOCATION}. If the move
     * cannot be completed, throws error code 428.
     *
     * @param locale The locale to throw exceptions in.
     * @param id     The id of the train.
     * @param data   Train data to change.
     * @throws jmri.server.json.JsonException if the train cannot move to the
     *                                            location in data.
     */
    public void setTrain(Locale locale, String id, JsonNode data) throws JsonException {
        Train train = InstanceManager.getDefault(TrainManager.class).getTrainById(id);
        if (!data.path(LOCATION).isMissingNode()) {
            String location = data.path(LOCATION).asText();
            if (location.equals(NULL)) {
                train.terminate();
            } else if (!train.move(location)) {
                throw new JsonException(428, Bundle.getMessage(locale, "ErrorTrainMovement", id, location));
            }
        }
    }

    /**
     * Set the properties in the data parameter for the car with the given id.
     * <p>
     * Currently only sets the location of the car.
     *
     * @param locale locale to throw exceptions in
     * @param id     id of the car
     * @param data   car data to change
     * @throws jmri.server.json.JsonException if the car cannot be set to the
     *                                            location in data
     */
    public void setCar(Locale locale, String id, JsonNode data) throws JsonException {
        Car car = InstanceManager.getDefault(CarManager.class).getById(id);
        if (!data.path(LOCATION).isMissingNode()) {
            String locationId = data.path(LOCATION).asText();
            String trackId = data.path(TRACK).asText();
            Location location = InstanceManager.getDefault(LocationManager.class).getLocationById(locationId);
            Track track = (trackId != null) ? location.getTrackById(trackId) : null;
            if (!car.setLocation(location, track, true).equals(Track.OKAY)) {
                throw new JsonException(428, Bundle.getMessage(locale, "ErrorMovingCar", id, locationId, trackId));
            }
        }
    }

    private CarManager getCarManager() {
        return InstanceManager.getDefault(CarManager.class);
    }

    @Override
    public JsonNode doSchema(String type, boolean server, Locale locale) throws JsonException {
        switch (type) {
            case CAR:
            case CARS:
                return doSchema(type,
                        server,
                        "jmri/server/json/operations/car-server.json",
                        "jmri/server/json/operations/car-client.json");
            case ENGINE:
            case ENGINES:
                return doSchema(type,
                        server,
                        "jmri/server/json/operations/engine-server.json",
                        "jmri/server/json/operations/engine-client.json");
            case KERNEL:
                return doSchema(type,
                        server,
                        "jmri/server/json/operations/kernel-server.json",
                        "jmri/server/json/operations/kernel-client.json");
            case LOCATION:
            case LOCATIONS:
                return doSchema(type,
                        server,
                        "jmri/server/json/operations/location-server.json",
                        "jmri/server/json/operations/location-client.json");
            case TRAIN:
            case TRAINS:
                return doSchema(type,
                        server,
                        "jmri/server/json/operations/train-server.json",
                        "jmri/server/json/operations/train-client.json");
            default:
                throw new JsonException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        Bundle.getMessage(locale, "ErrorUnknownType", type));
        }
    }

}
