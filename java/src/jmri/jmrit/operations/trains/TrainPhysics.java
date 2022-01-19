package jmri.jmrit.operations.trains;

import jmri.jmrit.operations.rollingstock.engines.Engine;
import jmri.jmrit.operations.routes.Route;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.setup.Setup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static jmri.jmrit.operations.trains.TrainMotion.timeString_12_Char;
import static jmri.jmrit.operations.trains.TrainMotion.timeString_8_Char;

/**
 * These calculators are partly based on http://evilgeniustech.com/idiotsGuideToRailroadPhysics/TheBasics/
 * <p>That site is a partial reconstitution of the work of Al Krug, a.k.a. the Evil Genius, on train motion
 * physics. A second source is https://www.railelectrica.com/traction-mechanics/train-grade-curve-and-acceleration-resistance-2//p>
 *
 * <p>Careful vetting of these calculators and particularly the proper use of units allow easy shifting
 * of values between the US (aka "English Engineering System") and SI (aka "International System of Units") quantities.
 * The TrainPhysicsTest class illustrates the use of the calculations and unit conversions</p>
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainPhysics implements Serializable {
    // private basic constant values
    private static final double BRAKE_DESIGN_LIMIT = 0.75;
    private static final double FEET_PER_MILE = 5280.0;
    private static final double FLANGE_ADHESION_PER_KMPH = 0.00008; // steel flange sliding against steel rail face, per Kmph (speed)
    private static final double FT_LBS_PER_SEC_PER_HP = 550;
    private static final double G_SI = 9.80665; // gravitational acceleration in meters/sec/sec
    private static final double G_US = 32.174; // gravitational acceleration in feet/sec/sec
    private static final double KG_PER_LB = 0.45359237;
    private static final double KM_PER_METER = 1000.0;
    private static final double METERS_PER_FOOT = 0.0254 * 12.0; // 0.3048
    private static final double MINIMUM_ACCELERATION = 0.01;
    private static final double POWER_EFFICIENCY = 0.72; // factor especially for F7-A locomotives
    private static final double SEC_PER_HOUR = 3600.0;
    private static final double SLACK_PER_CAR_FEET = 1.8; // couplers plus drawbars
    private static final double STATIC_ADHESION = 0.0016; // rolling steel wheel on steel rail
    private static final double STRETCH_SPEED_LIMIT_MPH = 2.0;
    private static final double TON_FORCE_BY_KMH_PER_NEWTON = 2650;
    private static final double WATTS_PER_HP = 745.7;
    private static final double WHEEL_TRACK_ADHESION = 0.25; // coefficient for steel wheel on sanded steel rail
    // protected basic constant values
    protected static final double COUPLER_PULL_LIMIT_TONS = 125; // working limit for older coupler knuckles
    protected static final double LBS_PER_TON = 2000.0;
    protected static final double STEAMER_DRIVER_WEIGHT_PER_ENGINE_WEIGHT = 0.35;
    // private derived constants and conversion factors
    private static final double BRAKE_APPLICATION_LIMIT = 0.25 * BRAKE_DESIGN_LIMIT;
    private static final double METERS_PER_MILE = FEET_PER_MILE * METERS_PER_FOOT; // ~1609.344
    private static final double MILES_PER_KM = KM_PER_METER / METERS_PER_MILE; // ~0.621371
    private static final double MPH_PER_MPS = SEC_PER_HOUR / METERS_PER_MILE;// ~2.236936
    private static final double MPH_PER_FPS = SEC_PER_HOUR / FEET_PER_MILE; // ~0.681818
    private static final double NEWTONS_PER_LB = KG_PER_LB * G_SI;// ~4.4482
    private static final double NEWTONS_PER_TON = NEWTONS_PER_LB * LBS_PER_TON; // ~8896.44
    private static final double STRETCH_SPEED_LIMIT_FPS = STRETCH_SPEED_LIMIT_MPH / MPH_PER_FPS;
    // protected derived constants and conversion factors
    protected static final double TON_FORCE_BY_MPH_PER_HP = TON_FORCE_BY_KMH_PER_NEWTON * POWER_EFFICIENCY * MILES_PER_KM / NEWTONS_PER_TON;

    private static final long serialVersionUID = 1L;
    private final StringBuilder reportSummary = new StringBuilder();
    private final List<TrainMotion> routeTrainMotions = new ArrayList<>();

    public TrainPhysics(Train train, boolean detailed) {
        Route route = train.getRoute();
        boolean journal = true; // worst case rolling resistance
        boolean warm = false; // worst case rolling resistance

        for (RouteLocation rl : route.getLocationsBySequenceList()) {
            if (rl.getTrainWeight() > 0 && rl.getTrainLength() > 0) {
                TrainMotion priorTm = routeTrainMotions.isEmpty() ? new TrainMotion() : routeTrainMotions.get(routeTrainMotions.size() - 1);
                List<TrainMotion> trainMotionList = getTrainMotions(train, rl, journal, warm, priorTm.v);
                for (TrainMotion tm : trainMotionList) {
                    tm.t += priorTm.t;
                    tm.x += priorTm.x;
                }
                routeTrainMotions.addAll(trainMotionList);
                updateMotionReport(train, rl, detailed, trainMotionList);
            }
        }
    }

    /**
     * Using Newton's 2nd law of motion in one dimension, which is force = mass times acceleration, to calculate
     * acceleration as force divided by mass. In this law, "force" is the "net force" on the object, the result of
     * adding all (vector) forces together. Under acceleration in one dimension, this would be the total of all
     * propelling forces subtracted by the total of opposed forces. Under deceleration, this would be the total of
     * braking forces plus the total of opposed forces.
     * <p>
     * The units must agree: if SI, both totalWeight and force should be in Newtons, else if US, both should be in tons.
     * A force:totalWeight ratio expresses acceleration in "g's", the g-ratio pr "g's", which can be converted to
     * MPS/second via G_SI or FPS/second via G_US.
     * </p>
     *
     * @param netForce    in Newtons (SI) or tons
     * @param totalWeight in Newtons (SI) or tons
     * @return the calculated acceleration in MPS/sec (SI) or MPH per sec
     */
    static double getAcceleration(double netForce, double totalWeight) {
        double gRatio = netForce / totalWeight; // acceleration per gravitational acceleration

        return gRatio * G_US * MPH_PER_FPS;
    }

    /**
     * The force due to the grade for weight, ignoring all motion resistance
     * <p>
     * A positive grade (climbing upward) generates a force opposed to forward motion. A negative grade (climbing
     * downward) generates a force assisting forward motion.
     *
     * @param weight       the weight moving freely along a grade
     * @param gradePercent the grade in percentage units
     * @return the calculated force due to the grade
     */
    static double getGradeResistance(double weight, double gradePercent) {
        return gradePercent / 100.0 * weight; // in weight units
    }

    /**
     * The net force available for inducing motion, either for acceleration or deceleration, is calculated considering
     * the tractive force limit and opposing forces (rolling and grade resistance forces). Under acceleration in one
     * dimension, net force would be the total of all propelling forces subtracted by the total of opposed forces. For
     * the sake of efficient operation, we assume the use of full engine power, without spinning driver wheels, is used
     * for acceleration.</p>
     *
     * @param fullPower    full power to accelerate motion in watts (SI) or HP
     * @param speed        speed in MPS (SI) or MPH
     * @param totalWeight  total weight being moved tons or newtons
     * @param driverWeight driver wheels weight load available for starting force in tons or newtons
     * @param gradePercent the grade in percentage units
     * @return the calculated net force in Newtons (SI) or tons
     */
    static double getNetForce(double fullPower, double speed, double totalWeight, double driverWeight, double gradePercent) {
        return Math.min(tractiveForceLimit(driverWeight), getTractiveForce(fullPower, speed)) - getRollingResistance(totalWeight, speed) - getGradeResistance(totalWeight, gradePercent);
    }

    static TrainMotion getNewTrainMotion(TrainMotion priorTrainMotion, double newSpeed, double driverWeight, double engineWeight, List<Integer> carWeights, double fullPower, double gradePercent, double distanceLimit) {
        double carsWeight = carWeights.stream().mapToInt(w -> w).sum();
        double totalWeight = engineWeight + carsWeight;
        double brakingForceDesignLimit = brakingForceDesignLimit(totalWeight);

        double t_0 = priorTrainMotion.t; // seconds
        double x_0 = priorTrainMotion.x; // miles
        double v_0 = priorTrainMotion.v; // MPH

        // MPH
        double d_v = newSpeed - v_0;
        double d_v_signum = Math.signum(d_v);
        boolean accelerate = d_v_signum > 0;

        double availTractiveForce = availTractiveForce(driverWeight, fullPower, totalWeight, v_0, accelerate);
        double rollingResistance = getRollingResistance(totalWeight, v_0);
        double gradeResistance = getGradeResistance(totalWeight, gradePercent);
        double netForce = d_v_signum * availTractiveForce - rollingResistance - gradeResistance; // +/- tons
        double a_x = getAcceleration(netForce, totalWeight); // +/- MPH/s
        if (accelerate && a_x < MINIMUM_ACCELERATION) {
            return null;
        }
        double dt = d_v / a_x; // seconds
        if (dt < 0) {
            a_x = getAcceleration(netForce, totalWeight);
            System.out.println("dt is negative");
        }
        double x_1 = x_0 + (v_0 + 0.5 * d_v_signum * a_x * dt) * dt / SEC_PER_HOUR;
        if ((float) (x_1) > (float) (distanceLimit)) {
            return null;
        }
        double t_1 = t_0 + dt;
        double appliedPower = appliedPower(fullPower, newSpeed, driverWeight);
        if (accelerate) {
            double throttle = 100 * appliedPower / fullPower;
            return new TrainMotion(dt, t_1, x_1, newSpeed, a_x, netForce, appliedPower, throttle, 0);
        } else {
            double brake = Math.abs(100 * availTractiveForce / brakingForceDesignLimit);
            return new TrainMotion(dt, t_1, x_1, newSpeed, a_x, netForce, 0, 0, brake);
        }
    }

    /**
     * The retarding force due to rolling resistance for weight borne by train wheels on track, using a coefficient of
     * (adhesive) friction for steel wheels on steel rails, which is independent of speed, plus (sliding) friction for
     * steel wheel flanges against steel rail faces, which is speed dependent and purely empirical. There is no account
     * for air resistance, as that is negligible for freight trains due to their relatively low speeds.
     *
     * @param weight the weight in arbitrary units
     * @param speed  speed in MPH
     * @return the calculated rolling resistance force (always opposed to motion) in the same units as weight
     */
    static double getRollingResistance(double weight, double speed) {
        double speedKmph = speed / MILES_PER_KM;
        double flangeDrag = FLANGE_ADHESION_PER_KMPH * Math.abs(speedKmph) * weight;
        double staticDrag = STATIC_ADHESION * weight;

        return staticDrag + flangeDrag;
    }

    /**
     * Starting resistance due to wheel bearing static friction (when at rest)
     * <p><table border = "2" cellpadding = "6" cellspacing = "6">
     * <tr><th>Bearing</th><th>Warm</th><th>Cold</th><th>Units</th></tr>
     * <tr><td>Journal</td><td>25</td><td>35</td><td>lb/ton</td></tr>
     * <tr><td>Roller</td><td>5</td><td>15</td><td>lb/ton</td></tr>
     * </table>
     *
     * @param weight    the weight applied to stationary axle bearings
     * @param isJournal true uses the static friction oj journal bearings else of roller bearings
     * @param isWarm    true uses the static friction of bearings when above freezing else below freezing
     * @return the calculated starting resistance force for the total weight on wheel bearings
     */
    static double getStartingResistance(double weight, boolean isJournal, boolean isWarm) {
        double bearingFactor = (isJournal ? (isWarm ? 25 : 35) : (isWarm ? 5 : 15)) / LBS_PER_TON;

        return bearingFactor * weight;
    }

    /**
     * TrainMotion starting at rest with train initially bunched, to exploit Slack for possible low driver weight and/or
     * high bearing resistance, to overcome starting resistance, by stretching the train car by car.
     *
     * @param driverWeight driver wheels weight load available for applied force in Newtons (SI) or tons
     * @param engineWeight engine weight including possible tender
     * @param carWeights   ordered list of all car weights in this train
     * @param fullPower    full power to accelerate motion in watts (SI) or HP
     * @param gradePercent the grade in percentage units
     * @param journal      type of bearing: false for roller, true for journal
     * @param warm         bearing temp: false for below freezing, true for above freezing
     * @return TrainMotion convering the entire time interval to stretch this train's coupler assemblies
     */
    static TrainMotion getStretchMotion(double driverWeight, double engineWeight, List<Integer> carWeights, double fullPower, double gradePercent, boolean journal, boolean warm) {
        double totalWeight = engineWeight + carWeights.stream().mapToInt(w -> w).sum();
        if (getNetForce(fullPower, STRETCH_SPEED_LIMIT_MPH, totalWeight, driverWeight, gradePercent) < 0) {
            return new TrainMotion();
        }

        double f = netForceAtRest(engineWeight, driverWeight, journal, warm, gradePercent);
        double stretchedWeight = engineWeight; // initial weight to pull
        // first accelerate engine only from rest to Slack feet down the track, in feet and second units
        double v2;
        double l2 = STRETCH_SPEED_LIMIT_FPS * STRETCH_SPEED_LIMIT_FPS; // fps^2 speed limit squared
        double aLimit = l2 / (2 * SLACK_PER_CAR_FEET); // fps/s
        double aAvail = accelerationFpsPerSecond(f, stretchedWeight); // fps/s
        double a = Math.min(aLimit, aAvail); // fps/s
        double t = Math.sqrt(2 * SLACK_PER_CAR_FEET / a); // seconds
        double v = a * t; // fps
        double x = SLACK_PER_CAR_FEET; // feet
        double dt = 0;
        double m;
        // add each car to stretch
        for (double carWeight : carWeights) {
            double newStretchedWeight = stretchedWeight + carWeight;
            v *= stretchedWeight / newStretchedWeight; // fully inelastic stretch conserves momentum
            f = getNetForce(fullPower, v * MPH_PER_FPS, newStretchedWeight, driverWeight, gradePercent);
            v2 = v * v; // fps^2
            aLimit = (l2 - v2) / (2 * SLACK_PER_CAR_FEET); // fps/s
            aAvail = accelerationFpsPerSecond(f, stretchedWeight); // fps/s
            a = Math.min(aLimit, aAvail); // fps/s
            // equation of motion for this time interval is x(∆t) = x(t0) + v(t0) * ∆t + a/2 * ∆t^2
            // where t0 is the cycle start time, v(t0) is the cycle start speed, and 'a' is the cycle acceleration,
            // get ∆t as a root of this quadratic equation
            dt = (Math.sqrt(v2 + 2 * a * SLACK_PER_CAR_FEET) - v) / (4 * a); // quadratic root for ∆t, in seconds
            v += a * dt; // fps
            // set up for next cycle
            x += SLACK_PER_CAR_FEET; // feet
            t += dt; // seconds
            stretchedWeight = newStretchedWeight;
        }

        t -= dt; // seconds
        x = SLACK_PER_CAR_FEET * carWeights.size(); // feet
        // compute stretch TrainMotion as if under constant force and constant acceleration
        a = v * v / 2 / x; // fps/s
        m = totalWeight * LBS_PER_TON / G_US; // slugs
        f = m * a; // pounds (slug-fps/s)
        double ke = 0.5 * m * v * v; // final kinetic energy: ft-lbs
        double pe = m * G_US * x * gradePercent / 100; // final potential energy: ft-lbs
        double w = ke + pe; // final energy: ft-lbs
        double p = w / t; // average power: ft-lb/s
        double appliedForce = f / LBS_PER_TON;
        double appliedHp = p / FT_LBS_PER_SEC_PER_HP;
        double throttle = 0;
        double brake = 0;
        if (appliedHp > 0) {
            throttle = 100 * appliedHp / fullPower;
        } else {
            appliedHp = 0;
            brake = 100 * appliedForce / brakingForceDesignLimit(totalWeight);
        }
        return new TrainMotion(t, t, x / FEET_PER_MILE, v * MPH_PER_FPS, a * MPH_PER_FPS, appliedForce, appliedHp, throttle, brake);
    }

    /**
     * Tractive Effort (force) to change the train speed, given its applied engine power
     *
     * @param appliedPower if SI, in watts, else in HP units
     * @param speed        speed: if SI, in meters per second (MPS), otherwise MPH
     * @return the calculated tractive force: if SI units, in Newtons, else in tons
     */
    static double getTractiveForce(double appliedPower, double speed) {
        return TON_FORCE_BY_MPH_PER_HP / speed * appliedPower; // tons;
    }

    /**
     * A Map of Lists of train TrainMotion instances is generated from the initial motion of stretching the train, to
     * remove all slack and achieve an initial (limited) speed, followed by an acceleration sequence, up to either the
     * route segment speed limit or arrival at the halfway point of the route segment, followed by a cruising speed
     * motion, if the speed limit was achieved during acceleration, then a deceleration sequence, the very opposite of
     * the acceleration sequence, ending with brunching the train, the very opposite of the stretching motion, and
     * finally coming to a stop at the given route segment end point.
     *
     * @param train      Train instance en route
     * @param rl         current route location
     * @param journal    type of bearing: false for roller, true for journal
     * @param warm       bearing temp: false for below freezing, true for above freezing
     * @param priorSpeed
     * @return Map of TrainMotion lists traversing a route location in 3 phases (ACCEL, CRUISE, DECEL)
     */
    static List<TrainMotion> getTrainMotions(Train train, RouteLocation rl, boolean journal, boolean warm, double priorSpeed) {
        TrainRevenues trainRevenues = train.getTrainRevenues();
        List<Integer> carWeights = trainRevenues.getTrainCarWeights().get(rl.getId());
        List<Engine> engines = trainRevenues.getTrainEngines().get(rl.getId());
        boolean endStop = endStop(trainRevenues, rl);

        return getTrainMotions(engines, carWeights, rl.getGrade(), rl.getSpeedLimit(), rl.getDistance(), journal, warm, endStop, priorSpeed);
    }

    /**
     * A Map of Lists of train TrainMotion instances is generated from the initial motion of stretching the train, to
     * remove all slack and achieve an initial (limited) speed, followed by an acceleration sequence, up to either the
     * route segment speed limit or arrival at the halfway point of the route segment, followed by a cruising speed
     * motion, if the speed limit was achieved during acceleration, then a deceleration sequence, the very opposite of
     * the acceleration sequence, ending with brunching the train, the very opposite of the stretching motion, and
     * finally coming to a stop at the given route segment end point.
     *
     * @param engines       List of all engines pulling this train
     * @param carWeights    ordered list of all car weights in this train
     * @param gradePercent  the grade in percentage units
     * @param speedLimit    route speed limit in MPS (SI) or MPH
     * @param distanceLimit initial position in meters (SI) or miles
     * @param journal       type of bearing: false for roller, true for journal
     * @param warm          bearing temp: false for below freezing, true for above freezing
     * @param endStop
     * @param priorSpeed
     * @return Map of TrainMotion lists traversing a route location in 3 phases (ACCEL, CRUISE, DECEL)
     */
    static List<TrainMotion> getTrainMotions(List<Engine> engines, List<Integer> carWeights, double gradePercent, int speedLimit, double distanceLimit, boolean journal, boolean warm, boolean endStop, double priorSpeed) {
        List<TrainMotion> trainMotionList = new ArrayList<>();

        List<TrainMotion> cruiseList = new ArrayList<>();
        List<TrainMotion> decelList = new ArrayList<>();

        double fullPower = 0;
        double driverWeight = 0;
        double engineWeight = 0;
        if (engines == null) {
            System.out.println("Alert: engines is null in getTrainMotions()");
            return trainMotionList;
        } else {
            for (Engine engine : engines) {
                fullPower += engine.getHpInteger();
                driverWeight += driverWeight(engine);
                engineWeight += engine.getAdjustedWeightTons();
            }
        }

        TrainMotion priorTrainMotion = new TrainMotion();
        trainMotionList.add(priorTrainMotion);
        if (priorSpeed > 0) {
            priorTrainMotion.v = priorSpeed;
            if (speedLimit < priorSpeed) {
                priorTrainMotion = getTrainMotionAfterDeceleration(trainMotionList, priorTrainMotion, driverWeight, engineWeight, carWeights, gradePercent, fullPower, distanceLimit, speedLimit);
            } else if (priorSpeed < speedLimit) {
                priorTrainMotion = getTrainMotionAfterAcceleration(trainMotionList, priorTrainMotion, driverWeight, engineWeight, carWeights, gradePercent, fullPower, distanceLimit, speedLimit);
            }
        } else {
            priorTrainMotion = getStretchMotion(driverWeight, engineWeight, carWeights, fullPower, gradePercent, journal, warm);
            trainMotionList.add(priorTrainMotion);

            priorTrainMotion = getTrainMotionAfterAcceleration(trainMotionList, priorTrainMotion, driverWeight, engineWeight, carWeights, gradePercent, fullPower, distanceLimit, speedLimit);
        }
        if (endStop) {
            priorTrainMotion = getTrainMotionAfterDeceleration(decelList, priorTrainMotion, driverWeight, engineWeight, carWeights, fullPower, gradePercent, distanceLimit, 0);
        }
        double totalTravelDistance = priorTrainMotion.x;

        // check total travel distance and reduce top speed as needed
        while (distanceLimit < totalTravelDistance && trainMotionList.size() > 2 && decelList.size() > 2) {
            trainMotionList.remove(trainMotionList.size() - 1); // remove old accel top speed
            decelList.remove(0); // remove old decel top speed
            double x_AccelTopSpeed = trainMotionList.get(trainMotionList.size() - 1).x;
            double x_DecelTopSpeed = decelList.get(0).x;
            double travelDistanceReduction = x_DecelTopSpeed - x_AccelTopSpeed;
            totalTravelDistance -= travelDistanceReduction;
        }

        double cruiseDistance = distanceLimit - totalTravelDistance;
        priorTrainMotion = trainMotionList.get(trainMotionList.size() - 1);
        TrainMotion cruiseMotion = cruiseMotion(priorTrainMotion, engineWeight, carWeights, fullPower, gradePercent, cruiseDistance);
        cruiseList.add(cruiseMotion);

        double addedTime = cruiseMotion.t - trainMotionList.get(trainMotionList.size() - 1).t;
        double addedDistance = cruiseMotion.x - trainMotionList.get(trainMotionList.size() - 1).x;
        // update time and distances in decelerate train motions
        for (TrainMotion tm : decelList) {
            tm.t += addedTime;
            tm.x += addedDistance;
        }

        trainMotionList.addAll(cruiseList);
        trainMotionList.addAll(decelList);

        return trainMotionList;
    }

    public static boolean endStop(TrainRevenues trainRevenues, RouteLocation rl) {
        return trainRevenues.getTrainPickUpsOrDropOffs().contains(rl.getId());
    }

    public static boolean isSI() {
        return Setup.getLengthUnit().equals(Setup.METER);
    }

    private static TrainMotion getTrainMotionAfterDeceleration(List<TrainMotion> decelList, TrainMotion priorTrainMotion, double driverWeight, double engineWeight, List<Integer> carWeights, double fullPower, double gradePercent, double distanceLimit, double speedLimit) {
        int newSpeed = (int) Math.round(priorTrainMotion.v);
        for (int speed = newSpeed - 1; speed >= speedLimit; speed--) {
            TrainMotion newTrainMotion = getNewTrainMotion(priorTrainMotion, speed, driverWeight, engineWeight, carWeights, fullPower, gradePercent, distanceLimit);
            if (newTrainMotion == null) { // requested new speed value not achieved
                break;
            } else {
                decelList.add(newTrainMotion);
                priorTrainMotion = newTrainMotion;
            }
        }
        return priorTrainMotion;
    }

    private static TrainMotion getTrainMotionAfterAcceleration(List<TrainMotion> accelList, TrainMotion priorTrainMotion, double driverWeight, double engineWeight, List<Integer> carWeights, double gradePercent, double fullPower, double distanceLimit, int speedLimit) {
        int topSpeed = 1 + (int) priorTrainMotion.v;
        for (int newSpeed = topSpeed; newSpeed <= speedLimit; newSpeed++) {
            TrainMotion newTrainMotion = getNewTrainMotion(priorTrainMotion, newSpeed, driverWeight, engineWeight, carWeights, fullPower, gradePercent, distanceLimit);
            if (newTrainMotion == null) { // requested new speed value not achieved
                break;
            } else {
                accelList.add(newTrainMotion);
                priorTrainMotion = newTrainMotion;
            }
        }
        return priorTrainMotion;
    }

    /**
     * @param netForce    in tons or newtons
     * @param totalWeight in tons or newtons
     * @return acceleration in FPS, per second
     */
    private static double accelerationFpsPerSecond(double netForce, double totalWeight) {
        double gRatio = netForce / totalWeight; // acceleration per gravitational acceleration

        return gRatio * G_US;
    }

    /**
     * Applied power is full power reduced by the ratio of applied force to available force
     *
     * @param fullPower    full power to accelerate motion in watts (SI) or HP
     * @param speed        speed in MPS (SI) or MPH
     * @param driverWeight driver wheels weight load available for applied force in Newtons (SI) or tons
     * @return power in watts (SI) or PH
     */
    private static double appliedPower(double fullPower, double speed, double driverWeight) {
        double tractiveForce = getTractiveForce(fullPower, speed);
        double tractiveForceLimit = tractiveForceLimit(driverWeight);
        double appliedForce = Math.min(tractiveForceLimit, tractiveForce);

        return appliedForce / tractiveForce * fullPower;
    }

    private static double availTractiveForce(double driverWeight, double fullPower, double totalWeight, double v_0, boolean accelerate) {
        return accelerate
                ? Math.min(tractiveForceLimit(driverWeight), getTractiveForce(fullPower, v_0))
                : brakingForceSafetyLimit(totalWeight);
    }

    /**
     * Typically, all the weight of the train puts adhesive pressure on the rails by wheels, and all axles have braking
     * capability. Brakes are designed to prevent the wheels from brake lock, which would damage both wheels and rails.
     * Brakes are designed to apply no more than 75% of this force.
     *
     * @param totalWeight weight load available for braking force
     * @return the calculated braking force in the same units as the weight
     */
    private static double brakingForceDesignLimit(double totalWeight) {
        return WHEEL_TRACK_ADHESION * BRAKE_DESIGN_LIMIT * totalWeight;
    }

    /**
     * Typically, all the weight of the train puts adhesive pressure on the rails by wheels, and all axles have braking
     * capability. The safe braking limit is the maximum applied braking force limit used for safety considerations.
     *
     * @param totalWeight weight load available for braking force
     * @return the calculated braking force in the same units as the weight
     */
    private static double brakingForceSafetyLimit(double totalWeight) {
        return WHEEL_TRACK_ADHESION * BRAKE_APPLICATION_LIMIT * totalWeight;
    }

    private static TrainMotion cruiseMotion(TrainMotion priorTrainMotion, double engineWeight, List<Integer> carWeights, double fullPower, double gradePercent, double cruiseDistance) {
        double carsWeight = carWeights.stream().mapToInt(w -> w).sum();
        double totalWeight = engineWeight + carsWeight;
        // parameters for cruise (constant speed motion)
        double brakingForceLimit = brakingForceSafetyLimit(totalWeight);
        double priorDistance = priorTrainMotion.x;
        double cruiseSpeed = priorTrainMotion.v;
        double cruiseTime = 3600 * cruiseDistance / cruiseSpeed;
        double throttle = 0;
        double brake = 0;

        double gradeResistance = getGradeResistance(totalWeight, gradePercent);
        double rollingResistance = getRollingResistance(totalWeight, cruiseSpeed);
        double netCruiseForce = gradeResistance + rollingResistance;
        double appliedPower = netCruisePower(netCruiseForce, cruiseSpeed);
        if (appliedPower > 0) {
            throttle = 100 * appliedPower / fullPower;
        } else {
            appliedPower = 0;
            brake = -100 * netCruiseForce / brakingForceLimit;
        }

        return new TrainMotion(cruiseTime, priorTrainMotion.t + cruiseTime, priorDistance + cruiseDistance, cruiseSpeed, 0, netCruiseForce, appliedPower, throttle, brake);
    }

    private static double driverWeight(Engine engine) {
        String engineTypeName = engine.getTypeName();
        boolean steamType = engineTypeName != null && (engineTypeName.contains("Steam"));
        double factor = steamType ? TrainPhysics.STEAMER_DRIVER_WEIGHT_PER_ENGINE_WEIGHT : 1;

        return factor * engine.getAdjustedWeightTons();
    }

    /**
     * @param netForce    in tons
     * @param cruiseSpeed in MPH under zero acceleration
     * @return HP
     */
    private static double netCruisePower(double netForce, double cruiseSpeed) {
        double netForceSI = netForce * (NEWTONS_PER_TON); // newtons
        double cruiseSpeedSI = cruiseSpeed / (MPH_PER_MPS); // mps
        double powerSI = netForceSI * cruiseSpeedSI; // Watts

        return (powerSI / WATTS_PER_HP); // HP
    }

    /**
     * The net force available at rest, considering the calculated tractive force limit and the calculated starting
     * resistance force. This net force is constrained not to be negative, since a tractive force less than the starting
     * resistance force will not result in any motion, certainly not backward motion. Since couplers, together with
     * draft gear, have Slack, an engineer can reverse the engine to close all slack, or have previously bunched the
     * train when stopping, then proceed by pulling one car at a time into motion, starting with the engine itself. This
     * approach will be used as standard, since it reduces the stress on couplers and initial traction dependencies.
     *
     * @param engineWeight engine weight including possible tender
     * @param driverWeight driver wheels weight load available for starting force in Newtons (SI) or tons
     * @param isJournal    axle bearing type: journal if true, else roller
     * @param isWarm       axle temperature: above freezing if true, else below freezing
     * @param gradePercent the grade in percentage units
     * @return the calculated net force in Newtons (SI) or tons
     */
    private static double netForceAtRest(double engineWeight, double driverWeight, boolean isJournal, boolean isWarm, double gradePercent) {
        double gradeResistance = getGradeResistance(engineWeight, gradePercent);
        double startingResistance = getStartingResistance(engineWeight, isJournal, isWarm);
        double tractiveForceLimit = tractiveForceLimit(driverWeight);

        double netForce = tractiveForceLimit - startingResistance - gradeResistance;
        return Math.max(0, netForce);
    }

    /**
     * Available force due to weight on driving wheels on track, using sand, without spinning.
     *
     * @param driversWeight wheel weight load available for traction force
     * @return the calculated tractive force in the same units as the weight
     */
    private static double tractionForceLimit(double driversWeight) {
        return WHEEL_TRACK_ADHESION * driversWeight;
    }

    /**
     * The limit of available force, being the smaller of the drawbar pull limit and traction force limit. It is assumed
     * that any rail engine can generate enough torque to spin its wheels at rest, which damages wheels and rails, and
     * produces less tractive force. This is the higher static friction force an experienced engineer can obtain, by
     * careful throttle control to just avoid wheel spin.
     *
     * @param driversWeight driver wheel weight load available for starting force in Newtons (SI) or tons
     * @return the calculated tractive force in Newtons (SI) or tons
     */
    private static double tractiveForceLimit(double driversWeight) {
        return Math.min(COUPLER_PULL_LIMIT_TONS, tractionForceLimit(driversWeight));
    }

    public List<TrainMotion> getRouteTrainMotions() {
        return routeTrainMotions;
    }

    @Override
    public String toString() {
        return "TrainPhysics Summary Report {" + reportSummary + '}';
    }

    /*
        private TrainMotion priorTrainMotion(Train train, RouteLocation rl) {
            for (RouteLocation rlPrior : train.getRoute().getLocationsBySequenceList()) {
                if (rlPrior.getSequenceNumber() == rl.getSequenceNumber() - 1) {
                    List<TrainMotion> trainMotionList = trainMotionMap.get(rlPrior);
                    return trainMotionList.get(trainMotionList.size() - 1);
                }
            }

            return new TrainMotion();
        }
    */
    private void updateMotionReport(Train train, RouteLocation rl, boolean detailed, List<TrainMotion> trainMotionList) {
        TrainRevenues trainRevenues = train.getTrainRevenues();
        String rlId = rl.getId();

        double fullPower = 0;
        double driverWeight = 0;
        double engineWeight = 0;
        StringBuilder engineModel = new StringBuilder();
        StringBuilder engineType = new StringBuilder();
        List<Engine> engines = trainRevenues.getTrainEngines().get(rlId);
        if (engines == null) {
            System.out.println("Alert: engines is null in updateMotionReport()");
            return;
        }
        for (Engine engine : engines) {
            fullPower += engine.getHpInteger();
            driverWeight += driverWeight(engine);
            engineWeight += engine.getAdjustedWeightTons();

            if (engineModel.length() == 0) {
                engineModel = new StringBuilder(engine.getModel());
            } else if (engineModel.toString().contains(engine.getModel())) {
                engineModel.append("'s");
            } else {
                engineModel.append(", ").append(engine.getModel());
            }

            if (engineType.length() == 0) {
                engineType = new StringBuilder(engine.getTypeName());
            } else if (!engineType.toString().contains(engine.getTypeName())) {
                engineType.append(", ").append(engine.getTypeName());
            }
        }

        List<Integer> carWeights = trainRevenues.getTrainCarWeights().get(rlId);
        double gradePercent = rl.getGrade();
        double speedLimit = rl.getSpeedLimit();

        double carsWeight = carWeights.stream().mapToInt(w -> w).sum();
        double totalWeight = engineWeight + carsWeight;

        if (trainMotionList.isEmpty()) {
            reportSummary.append(String.format("\n\tNo motion for engine model %s:" + "\n\t\tfull power = %5.3f (HP)" + "\n\t\tdriver weight = %5.3f (tons)" + "\n\t\tengine weight = %5.3f (tons)" + "\n\t\ttrain weight = %5.3f (tons)" + "\n\t\tgrade = %5.3f%%\n", engineModel, fullPower, driverWeight, engineWeight, totalWeight, gradePercent));
        } else {
            int motionCount = trainMotionList.size();
            int endAccelIndex = 0;
            for (TrainMotion tm : trainMotionList) {
                if (tm.a > 0) {
                    endAccelIndex++;
                }
            }

            double maxAccelerationThrottle = 0;
            double maxAccelerationBrake = 0;
            List<TrainMotion> accelTmList = trainMotionList.subList(0, endAccelIndex + 1);
            for (TrainMotion tm : accelTmList) {
                maxAccelerationThrottle = Math.max(maxAccelerationThrottle, tm.tp);
                maxAccelerationBrake = Math.max(maxAccelerationBrake, tm.bp);
            }

            double accelStartTime = trainMotionList.get(0).t;
            double accelFinishTime = trainMotionList.get(endAccelIndex).t;
            double accelElapsedTime = accelFinishTime - accelStartTime;

            TrainMotion ctm;
            double cruiseThrottle = 0;
            double cruiseBrake = 0;
            double cruiseSpeedTime = 0;
            ctm = trainMotionList.get(endAccelIndex + 1);
            cruiseThrottle = ctm.tp;
            cruiseBrake = ctm.bp;
            cruiseSpeedTime = ctm.t - accelFinishTime;
/*
            if (endAccelIndex < trainMotionList.size()) {
            } else {
                ctm = trainMotionList.get(endAccelIndex);
            }
*/
            List<TrainMotion> decelTrainMotions = trainMotionList.subList(endAccelIndex + 2, trainMotionList.size());

            double maxDecelerationThrottle = 0;
            double maxDecelerationBrake = 0;
            motionCount += decelTrainMotions.size();
            for (TrainMotion tm : decelTrainMotions) {
                maxDecelerationThrottle = Math.max(maxDecelerationThrottle, tm.tp);
                maxDecelerationBrake = Math.max(maxDecelerationBrake, tm.bp);
            }
            double decelerationTime = 0;
            TrainMotion finalTrainMotion;
            if (!decelTrainMotions.isEmpty()) {
                decelerationTime = decelTrainMotions.get(decelTrainMotions.size() - 1).t - ctm.t;
                finalTrainMotion = decelTrainMotions.get(decelTrainMotions.size() - 1);
            } else {
                finalTrainMotion = trainMotionList.get(trainMotionList.size() - 1);
            }

            double totalSeconds = finalTrainMotion.t;
            double totalDistance = finalTrainMotion.x;

            String gradeString = String.format("%s%s grade", gradePercent < 0 ? "down a " : gradePercent > 0 ? "into a " : "with no", (gradePercent == 0 ? "" : String.format("%.1f%%", Math.abs(gradePercent))));
            double minHp = minHp(speedLimit, totalWeight, gradePercent);
            double totalHours = totalSeconds / SEC_PER_HOUR;
            double averageSpeed = totalDistance / totalHours;

            boolean endStop = endStop(trainRevenues, rl);
            String note = endStop ? "" : "n't";

            StringBuilder thisSummary = new StringBuilder();
            thisSummary.append(String.format("\n\t\tRoute segment %s: %.0f mile distance in %s (HH:MM:SS), %s, under a %.0f MPH speed limit, end stop is%s required", rl.getSequenceNumber(), totalDistance, timeString_8_Char(totalSeconds), gradeString, speedLimit, note));
            thisSummary.append(String.format("\n\t\tTrain \"%s\": %.0f HP required; %d cars: %.0f tons; route average %.1f MPH", train.getName(), minHp, carWeights.size(), carsWeight, averageSpeed));
            thisSummary.append(String.format("\n\t\tDrive \"%s\", %.0f HP, %.0f ton engine weight (%s): %.0f tons on drivers, HPT = %3.1f", engineModel, fullPower, engineWeight, engineType, driverWeight, fullPower / totalWeight));
            thisSummary.append(String.format("\n\t\t%-10s | %8s | %8s | %8s |", "Controls:", "start", "cruise", "finish"));
            thisSummary.append(String.format("\n\t\t%-10s | %8s | %8s | %8s |", "HH:MM:SS:", timeString_8_Char(accelElapsedTime), timeString_8_Char(cruiseSpeedTime), timeString_8_Char(decelerationTime)));
            thisSummary.append(String.format("\n\t\t%-10s | %7.1f%% | %7.1f%% | %7.1f%% |", "Max Power:", maxAccelerationThrottle, cruiseThrottle, maxDecelerationThrottle));
            thisSummary.append(String.format("\n\t\t%-10s | %7.1f%% | %7.1f%% | %7.1f%% |\n", "Max Brake:", maxAccelerationBrake, cruiseBrake, maxDecelerationBrake));

            if (detailed) {
                thisSummary.append(String.format("TrainMotion: %d steps {\n\t%s\n", motionCount, TrainMotion.getMotionsHeader()));
                for (TrainMotion tm : trainMotionList) {
                    thisSummary.append("\t").append(tm.getMotionData()).append('\n');
                }
                thisSummary.append("\t}\n");
            }

            reportSummary.append(thisSummary);
        }
    }

    /**
     * Using the standard prototype formula for the Horsepower per Ton (HPT) ratio, defined as by the formula, Speed
     * (MPH) * Grade (%) / 12, the weight in tons is used to calculate the minimum required horsepower to pull this
     * train (up) the given grade. If the result is less than the Setup HPT, perhaps due to a non-positive grade, the
     * Setup HPT is used, ignoring the speed and weight parameter values
     *
     * @param speedLimit   route speed limit in MPH
     * @param totalWeight  in tons
     * @param gradePercent the grade in percentage units
     * @return recommended minimum horsepower for this situation
     */
    private double minHp(double speedLimit, double totalWeight, double gradePercent) {
        double hpRequired = speedLimit * totalWeight * gradePercent / 12;
        if (hpRequired < Setup.getHorsePowerPerTon() * totalWeight) {
            hpRequired = Setup.getHorsePowerPerTon() * totalWeight; // minimum HPT
        }
        return hpRequired;
    }

}
