package jmri.jmrit.operations.trains;

import jmri.jmrit.operations.routes.Route;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.setup.Setup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jmri.jmrit.operations.trains.TrainMotion.getFormattedTime;

/**
 * These calculators are mostly based on http://evilgeniustech.com/idiotsGuideToRailroadPhysics/TheBasics/
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
    public static final double STEAMER_DRIVER_TO_ENGINE_WEIGHT_RATIO = 0.35;
    // constant values
    protected static final double BRAKE_DESIGN_LIMIT = 0.75;
    protected static final double COUPLER_PULL_LIMIT_TONS = 125; // working limit for older coupler knuckles
    protected static final double FLANGE_ADHESION_PER_KMPH = 0.00008; // steel flange sliding against steel rail face, per Kmph (speed)
    protected static final double FT_LBS_PER_SEC_PER_HP = 550;
    protected static final double G_SI = 9.80665; // gravitational acceleration in meters/sec/sec
    protected static final double G_US = 32.174; // gravitational acceleration in feet/sec/sec
    protected static final double POWER_EFFICIENCY = 0.72; // factor especially for F7-A locomotives
    protected static final double STATIC_ADHESION = 0.0016; // rolling steel wheel on steel rail
    protected static final double WHEEL_TRACK_ADHESION = 0.25; // coefficient for steel wheel on sanded steel rail
    protected static final double FEET_PER_MILE = 5280.0;
    protected static final double KG_PER_LB = 0.45359237;
    protected static final double KM_PER_METER = 1000.0;
    protected static final double LBS_PER_TON = 2000.0;
    protected static final double METERS_PER_FOOT = 0.0254 * 12.0; // 0.3048
    protected static final double SEC_PER_HOUR = 3600.0;
    protected static final double SLACK_PER_CAR_FEET = 1.8; // couplers plus drawbars
    protected static final double WATTS_PER_HP = 745.7;
    // derived constants and conversion factors
    protected static final double METERS_PER_MILE = FEET_PER_MILE * METERS_PER_FOOT; // ~1609.344
    protected static final double MILES_PER_KM = KM_PER_METER / METERS_PER_MILE; // ~0.621371
    protected static final double MPH_PER_MPS = SEC_PER_HOUR / METERS_PER_MILE;// ~2.236936
    protected static final double MPS_PER_KMPH = KM_PER_METER / SEC_PER_HOUR; // ~0.277778
    protected static final double MPH_PER_FPS = SEC_PER_HOUR / FEET_PER_MILE; // ~0.681818
    protected static final double STRETCH_SPEED_LIMIT_FPS = 2.0 / MPH_PER_FPS;
    protected static final double NEWTONS_PER_LB = KG_PER_LB * G_SI;// ~4.4482
    protected static final double NEWTONS_PER_TON = NEWTONS_PER_LB * LBS_PER_TON; // ~8896.44

    static final int ACCELERATE = 0;
    static final int CRUISE = 1;
    static final int DECELERATE = 2;

    private static final long serialVersionUID = 1L;
    private static final double MINIMUM_ACCELERATION = 0.01;
    private final StringBuilder reportSummary = new StringBuilder();

    public TrainPhysics(Train train) {
        Route route = train.getRoute();
        TrainRevenues trainRevenues = train.getTrainRevenues();
        for (int i = 1; i < route.getLocationsBySequenceList().size(); i++) {
            updateMotionReport(train, trainRevenues, route.getRouteLocationBySequenceNumber(i));
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

        return gRatio * (isSI() ? G_SI : G_US * MPH_PER_FPS);
    }

    /**
     * Force-limit to protect older couplers from breaking
     *
     * @return the calculated limit of applied pulling force to safely pull cars behind leading engines in Newtons or
     * tons
     */
    static double getDrawbarPullLimit() {
        if (isSI()) {
            return COUPLER_PULL_LIMIT_TONS * NEWTONS_PER_TON; // in Newtons
        } else {
            return COUPLER_PULL_LIMIT_TONS;
        }
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
     * the tractive force limit and opposing forces (rolling and grade resistance forces).
     * <ul><li>
     * Under acceleration in one dimension, net force would be the total of all propelling forces subtracted by the
     * total of opposed forces. Under deceleration, net force would be the total of braking forces plus the total of
     * opposed forces.
     * </li><li>
     * For the sake of efficient operation, we assume the use of full engine power, without spinning driver wheels, is
     * used for acceleration and, for deceleration, full train braking power is used.</p>
     * </li><li>
     * During deceleration, we assume that the tractive force is the available traction force for the total weight,
     * since all axles have brakes, and which does not include drawbar tension limits, since the tension or compression
     * is widely distributed. Please note that train brakes are designed to apply only 75% of the maximum braking force,
     * since locking wheels cause significant damage to both rails and wheels.
     * </li>
     *
     * @param fullPower    full power to accelerate motion in watts (SI) or HP
     * @param speed        speed in MPS (SI) or MPH
     * @param trainWeight  total weight being moved tons or newtons
     * @param driverWeight driver wheel weight load available for starting force in tons or newtons
     * @param gradePercent the grade in percentage units
     * @return the calculated net force in Newtons (SI) or tons
     */
    static double getNetForce(double fullPower, double speed, double trainWeight, double driverWeight, double gradePercent) {
        double tractiveForceLimit = tractiveForceLimit(driverWeight);
        double tractiveForce = getTractiveForce(fullPower, speed);
        double availTractiveForce = Math.min(tractiveForceLimit, tractiveForce);
        double rollingResistance = getRollingResistance(trainWeight, speed);
        double gradeResistance = getGradeResistance(trainWeight, gradePercent);

        return availTractiveForce - rollingResistance - gradeResistance;
    }

    /**
     * The new distance along the track after acceleration (or deceleration, if negative), for a period of one second.
     * <p>Complementary units are required:</p>
     * <ul>
     *     <li>if SI, then express acceleration in MPS/sec AND speed in MPS AND position in meters</li>
     *     <li>else express acceleration in MPH/sec AND position in Miles AND speed in MPH</li>
     * </ul>
     *
     * @param distance     initial position in meters (SI) or miles
     * @param speed        initial speed in MPS (SI) or MPH
     * @param acceleration acceleration, either positive or negative in MPS/sec (SI) or MPH/sec
     * @return the calculated new position after 1 second of acceleration in meters (SI) or miles
     */
    static double getNewDistance(double distance, double speed, double acceleration) {
        if (isSI()) {
            return distance + speed + acceleration / 2;
        } else {
            double milesPerSecond = speed / 3600;
            double milesPerSecondPerSecond = acceleration / 3600;
            return distance + milesPerSecond + milesPerSecondPerSecond / 2;
        }
    }

    /**
     * The new speed along the track after acceleration (or deceleration, if negative), for a period of one second.
     * <p>Complementary units are required:</p>
     * <ul>
     *     <li>if SI, then express acceleration in MPS/sec AND speed in MPS AND position in meters</li>
     *     <li>else express acceleration in MPH/sec AND position in miles AND speed in MPH</li>
     * </ul>
     *
     * @param speed        initial speed in MPS (SI) or MPH
     * @param acceleration acceleration, either positive or negative in MPS/sec (SI) or MPH/sec
     * @return the calculated new speed after 1 second of acceleration
     */
    static double getNewSpeed(double speed, double acceleration) {
        return speed + acceleration;
    }

    /**
     * The retarding force due to rolling resistance for weight borne by train wheels on track, using a coefficient of
     * (adhesive) friction for steel wheels on steel rails, which is independent of speed, plus (sliding) friction for
     * steel wheel flanges against steel rail faces, which is speed dependent and purely empirical. There is no account
     * for air resistance, as that is negligible for freight trains due to their relatively low speeds.
     *
     * @param weight the weight in arbitrary units
     * @param speed  speed in MPS (SI) or MPH
     * @return the calculated rolling resistance force (always opposed to motion) in the same units as weight
     */
    static double getRollingResistance(double weight, double speed) {
        double speedKmph = speed / (isSI() ? MPS_PER_KMPH : MILES_PER_KM);
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
     * @param tmp TrainMotionParams
     * @return TrainMotion representing complete stretch period for starting train motion
     */
    static TrainMotion getStretchMotion(TrainMotionParams tmp) {
        double fullPower = tmp.fullPower;
        double driverWeight = tmp.driverWeight;
        double totalWeight = tmp.trainWeight;
        double gradePercent = tmp.gradePercent;

        if (getNetForce(fullPower, STRETCH_SPEED_LIMIT_FPS * MPH_PER_FPS, totalWeight, driverWeight, gradePercent) < 0) {
            return TrainMotion.ZERO;
        }
        double engineWeight = tmp.engineWeight;
        boolean journal = tmp.journal;
        boolean warm = tmp.warm;

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
        for (double carWeight : tmp.carWeights) {
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
        x = SLACK_PER_CAR_FEET * tmp.carWeights.size(); // feet
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
            brake = 100 * appliedForce / brakingForceLimit(totalWeight);
        }
        if (throttle > 100) {
            String format = String.format("excessive applied power %f: full power %f", appliedHp, fullPower);
            System.out.println(format);
        }
        return new TrainMotion(t, t,
                x / FEET_PER_MILE,
                v * MPH_PER_FPS,
                a * MPH_PER_FPS,
                appliedForce, appliedHp, throttle, brake);
    }

    /**
     * Tractive Effort (force) to change the train speed, given its applied engine power
     *
     * @param appliedPower if SI, in watts, else in HP units
     * @param speed        speed: if SI, in meters per second (MPS), otherwise MPH
     * @return the calculated tractive force: if SI units, in Newtons, else in tons
     */
    static double getTractiveForce(double appliedPower, double speed) {
        if (isSI()) {
            return 2650 * POWER_EFFICIENCY * MPS_PER_KMPH / WATTS_PER_HP * appliedPower / speed; // Newtons
        } else {
            return 2650 * POWER_EFFICIENCY * MILES_PER_KM / NEWTONS_PER_TON * appliedPower / speed; // tons;
        }
    }

    /**
     * A Map of Lists of train TrainMotion instances is generated from the initial motion of stretching the train, to
     * remove all slack and achieve an initial (limited) speed, followed by an acceleration sequence, up to either the
     * route segment speed limit or arrival at the halfway point of the route segment, followed by a cruising speed
     * motion, if the speed limit was achieved during acceleration, then a deceleration sequence, the very opposite of
     * the acceleration sequence, ending with brunching the train, the very opposite of the stretching motion, and
     * finally coming to a stop at the given route segment end point.
     *
     * @param tmp TrainMotionParams
     * @return a map of lists of train TrainMotion objects from start to finish
     */
    static Map<Integer, List<TrainMotion>> getTrainMotions(TrainMotionParams tmp) {
        Map<Integer, List<TrainMotion>> motionMap = new HashMap<>();
        motionMap.put(ACCELERATE, new ArrayList<>());
        motionMap.put(CRUISE, new ArrayList<>());
        motionMap.put(DECELERATE, new ArrayList<>());

        motionMap.get(ACCELERATE).add(TrainMotion.ZERO);
        TrainMotion stretchTrainMotion = getStretchMotion(tmp);
        motionMap.get(ACCELERATE).add(stretchTrainMotion);

        TrainMotion finalAccelTrainMotion = updateAccelMotions(tmp, motionMap.get(ACCELERATE), stretchTrainMotion);
        TrainMotion cruiseTrainMotion = cruiseMotion(tmp, finalAccelTrainMotion);
        motionMap.get(CRUISE).add(cruiseTrainMotion);

        List<TrainMotion> decelerationList = invertAccelerationMotions(tmp, motionMap.get(ACCELERATE), cruiseTrainMotion);
        motionMap.get(DECELERATE).addAll(decelerationList);

        return motionMap;
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
     * @param driverWeight driver wheel weight load available for applied force in Newtons (SI) or tons
     * @return power in watts (SI) or PH
     */
    private static double appliedPower(double fullPower, double speed, double driverWeight) {
        double tractiveForce = getTractiveForce(fullPower, speed);
        double tractiveForceLimit = tractiveForceLimit(driverWeight);
        double appliedForce = Math.min(tractiveForceLimit, tractiveForce);

        return appliedForce / tractiveForce * fullPower;
    }

    /**
     * Typically, all the weight of the train puts adhesive pressure on the rails by wheels, and all axles have braking
     * capability. Brakes are designed to prevent the wheels from brake lock, which would damage both wheels and rails.
     * Brakes are designed to apply no more than 75% of this force.
     *
     * @param totalWeight weight load available for braking force
     * @return the calculated braking force in the same units as the weight
     */
    private static double brakingForceLimit(double totalWeight) {
        return WHEEL_TRACK_ADHESION * BRAKE_DESIGN_LIMIT * totalWeight;
    }

    private static TrainMotion cruiseMotion(TrainMotionParams tmp, TrainMotion priorTrainMotion) {
        double totalDistance = tmp.distance;
        double totalWeight = tmp.trainWeight;
        double fullPower = tmp.fullPower;

        // parameters for cruise (constant speed motion)
        double brakingForceLimit = brakingForceLimit(totalWeight);
        double priorDistance = priorTrainMotion.x;
        double cruiseDistance = totalDistance - 2 * priorDistance;
        double cruiseSpeed = priorTrainMotion.v;
        double gradeResistance = getGradeResistance(tmp.trainWeight, tmp.gradePercent);
        double rollingResistance = getRollingResistance(totalWeight, cruiseSpeed);
        double cruiseTime = 3600 * cruiseDistance / cruiseSpeed;
        double throttle = 0;
        double brake = 0;

        double netCruiseForce = gradeResistance + rollingResistance;
        double appliedPower = netCruisePower(netCruiseForce, cruiseSpeed);
        if (appliedPower > 0) {
            throttle = 100 * appliedPower / fullPower;
        } else {
            appliedPower = 0;
            brake = -100 * netCruiseForce / brakingForceLimit;
        }
        if (throttle > 100) {
            String format = String.format("excessive applied power %f: full power %f", appliedPower, fullPower);
            System.out.println(format);
        }

        return new TrainMotion(cruiseTime, priorTrainMotion.t + cruiseTime, priorDistance + cruiseDistance, cruiseSpeed, 0, netCruiseForce, appliedPower, throttle, brake);
    }

    private static List<TrainMotion> invertAccelerationMotions(TrainMotionParams tmp, List<TrainMotion> accelerateTrainMotions, TrainMotion cruiseTrainMotion) {
        double totalWeight = tmp.trainWeight;
        double fullPower = tmp.fullPower;

        TrainMotion priorAccelTrainMotion = accelerateTrainMotions.get(accelerateTrainMotions.size() - 1);
        TrainMotion thisAccelTrainMotion;
        TrainMotion decelerationTrainMotion;
        double brakingForceLimit = brakingForceLimit(totalWeight);
        List<TrainMotion> decelerationList = new ArrayList<>();
        double dt;
        double time = cruiseTrainMotion == null ? priorAccelTrainMotion.t : cruiseTrainMotion.t;
        double oldDistance = cruiseTrainMotion == null ? priorAccelTrainMotion.x : cruiseTrainMotion.x;
        double newDistance;
        double newSpeed;
        double acceleration;
        double newForce;
        double netPower;
        double throttle;
        double brake;

        for (int i = accelerateTrainMotions.size() - 2; i >= 0; i--) {
            dt = priorAccelTrainMotion.dt;
            time += dt;
            thisAccelTrainMotion = accelerateTrainMotions.get(i);
            newSpeed = thisAccelTrainMotion.v;
            acceleration = -priorAccelTrainMotion.a;
            newDistance = oldDistance + priorAccelTrainMotion.x - thisAccelTrainMotion.x;
            newForce = -priorAccelTrainMotion.f;
            netPower = -priorAccelTrainMotion.p;
            throttle = 0;
            brake = 0;

            if (netPower > 0) {
                throttle = 100 * netPower / fullPower;
            } else {
                netPower = 0;
                brake = Math.abs(100 * newForce / brakingForceLimit);
            }
            if (throttle > 100) {
                String format = String.format("excessive applied power %f: full power %f", netPower, fullPower);
                System.out.println(format);
            }
            decelerationTrainMotion = new TrainMotion(dt, time, newDistance, newSpeed, acceleration, newForce, netPower, throttle, brake);
            decelerationList.add(decelerationTrainMotion);
            // prep for next cycle
            oldDistance = newDistance;
            priorAccelTrainMotion = thisAccelTrainMotion;
        }

        return decelerationList;
    }

    private static boolean isSI() {
        return Setup.getLengthUnit().equals(Setup.METER);
    }

    /**
     * @param netForce    in Newtons (SI) or tons
     * @param cruiseSpeed in MPS (SI) or MPH under zero acceleration
     * @return watts or HP
     */
    private static double netCruisePower(double netForce, double cruiseSpeed) {
        double netForceSI = netForce * (isSI() ? 1 : NEWTONS_PER_TON); // newtons
        double cruiseSpeedSI = cruiseSpeed / (isSI() ? 1 : MPH_PER_MPS); // mps
        double powerSI = netForceSI * cruiseSpeedSI; // Watts

        return isSI() ? powerSI : (powerSI / WATTS_PER_HP); // watts or HP
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
     * @param driverWeight driver wheel weight load available for starting force in Newtons (SI) or tons
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
        return Math.min(getDrawbarPullLimit(), tractionForceLimit(driversWeight));
    }

    private static TrainMotion updateAccelMotions(TrainMotionParams tmp, List<TrainMotion> accelerateTrainMotions, TrainMotion lastTrainMotion) {
        double oldDistance;
        double oldSpeed;
        double netForce;
        double appliedPower;
        double target = 0.5 * tmp.distance;

        double acceleration = lastTrainMotion.a;
        double newDistance = lastTrainMotion.x;
        double newSpeed = oldSpeed = lastTrainMotion.v;
        double time = lastTrainMotion.t;

        // accelerate in 1-second steps until limits are hit
        double dt = 1;
        boolean done = false;
        while (!done && MINIMUM_ACCELERATION < acceleration && newSpeed < tmp.speedLimit && newDistance < target) {
            netForce = getNetForce(tmp.fullPower, newSpeed, tmp.trainWeight, tmp.driverWeight, tmp.gradePercent);
            acceleration = getAcceleration(netForce, tmp.trainWeight);

            oldDistance = newDistance;
            newDistance = getNewDistance(newDistance, newSpeed, acceleration);
            if (target < newDistance) {
                dt = (target - oldDistance) / (newDistance - oldDistance);
                newDistance = target;
                newSpeed = oldSpeed + dt * (newSpeed - oldSpeed);
                done = true;
            }

            oldSpeed = newSpeed;
            newSpeed = getNewSpeed(newSpeed, acceleration);
            if (tmp.speedLimit < newSpeed) {
                dt = (tmp.speedLimit - oldSpeed) / (newSpeed - oldSpeed);
                newSpeed = tmp.speedLimit;
                newDistance = oldDistance + dt * (newDistance - oldDistance);
                netForce = getNetForce(tmp.fullPower, newSpeed, tmp.trainWeight, tmp.driverWeight, tmp.gradePercent);
                acceleration = getAcceleration(netForce, tmp.trainWeight);
                done = true;
            }

            appliedPower = appliedPower(tmp.fullPower, newSpeed, tmp.driverWeight);
            double throttle = 100 * appliedPower / tmp.fullPower;
            if (throttle > 100) {
                String format = String.format("excessive applied power %f: driver weight %f, full power %f", appliedPower, tmp.driverWeight, tmp.fullPower);
                System.out.println(format);
            }
            time += dt;
            lastTrainMotion = new TrainMotion(dt, time, newDistance, newSpeed, acceleration, netForce, appliedPower, throttle, 0);
            accelerateTrainMotions.add(lastTrainMotion);
        }

        return lastTrainMotion;
    }

    public String getReportSummary() {
        return reportSummary.toString();
    }

    @Override
    public String toString() {
        return "TrainMotion Summary {" + reportSummary + '}';
    }

    private void updateMotionReport(Train train, TrainRevenues trainRevenues, RouteLocation rl) {
        String rlId = rl.getId();

        TrainMotionParams tmp = new TrainMotionParams( //
                trainRevenues.getTrainCarWeights().get(rlId),  //
                trainRevenues.getTrainEngineWeight().get(rlId), //
                trainRevenues.getTrainEngineDriverWeight().get(rlId), //
                trainRevenues.getTrainEngineHP().get(rlId), //
                rl.getGrade(), //
                rl.getSpeedLimit(), //
                rl.getDistance(), //
                true, //
                false //
        );

        updateMotionReport(tmp, train, trainRevenues.getTrainEngineModel().get(rlId), trainRevenues.getTrainEngineType().get(rlId));
    }

    private void updateMotionReport(TrainMotionParams tmp, Train train, String engineModel, String engineType) {
        Map<Integer, List<TrainMotion>> motionMap = getTrainMotions(tmp);

        double power = tmp.fullPower;
        double speedLimit = tmp.speedLimit;
        double driverWeight = tmp.driverWeight;
        double engineWeight = tmp.engineWeight;
        double totalWeight = tmp.trainWeight;
        double grade = tmp.gradePercent;
        int trainCarCount = tmp.carCount;

        if (motionMap.isEmpty()) {
            reportSummary.append(String.format("\n\tNo motion for engine model %s:" + "\n\t\tpower = %5.3f (HP)" + "\n\t\tdriver weight = %5.3f" + "\n\t\tengine weight = %5.3f" + "\n\t\ttotal weight = %5.3f (tons)" + "\n\t\tgrade = %5.3f%%\n", engineModel, power, driverWeight, engineWeight, totalWeight, grade));
        } else {
            int motionCount = 0;

            List<TrainMotion> trainMotions = motionMap.get(ACCELERATE);
            double maxAccelerationThrottle = 0;
            double maxAccelerationBrake = 0;
            motionCount += trainMotions.size();
            for (TrainMotion tm : trainMotions) {
                maxAccelerationThrottle = Math.max(maxAccelerationThrottle, tm.tp);
                maxAccelerationBrake = Math.max(maxAccelerationBrake, tm.bp);
            }
            double accelerationTime = trainMotions.get(trainMotions.size() - 1).t - trainMotions.get(0).t;

            trainMotions = motionMap.get(CRUISE);
            TrainMotion ctm = trainMotions.get(0);
            double cruiseThrottle = ctm.tp;
            double cruiseBrake = ctm.bp;
            double cruiseSpeedTime = ctm.t;
            motionCount++;

            trainMotions = motionMap.get(DECELERATE);

            double maxDecelerationThrottle = 0;
            double maxDecelerationBrake = 0;
            motionCount += trainMotions.size();
            for (TrainMotion tm : trainMotions) {
                maxDecelerationThrottle = Math.max(maxDecelerationThrottle, tm.tp);
                maxDecelerationBrake = Math.max(maxDecelerationBrake, tm.bp);
            }
            double decelerationTime = trainMotions.get(trainMotions.size() - 1).t - trainMotions.get(0).t;

            TrainMotion finalTrainMotion = motionMap.get(2).get(motionMap.get(2).size() - 1);
            double seconds = finalTrainMotion.t;
            double elapsedTime = seconds / 60.0; // minutes
            double totalDistance = finalTrainMotion.x;

            String gradeString = String.format("%s%s grade",
                    grade < 0 ? "down a " : grade > 0 ? "into a " : "with no",
                    (grade == 0 ? "" : String.format("%.1f%%", Math.abs(grade))));
            double minHp = minHp(speedLimit, totalWeight, grade);
            double averageSpeed = totalDistance * 60.0 / elapsedTime;

            StringBuilder thisSummary = new StringBuilder();
            thisSummary.append(String.format("\n\t\tRoute segment: %.0f mile distance in %s (HH:MM:SS), %s, under a %.0f MPH speed limit", totalDistance, getFormattedTime(seconds), gradeString, speedLimit));
            thisSummary.append(String.format("\n\t\tTrain \"%s\": %d cars, %.0f tons, recommend at least %.0f HP; route average %.1f MPH", train.getName(), trainCarCount, totalWeight, minHp, averageSpeed));
            thisSummary.append(String.format("\n\t\tEngine model \"%s\" (%s): %.0f HP, %.0f ton engine: %.0f tons on drivers, HTP = %4.2f", engineModel, engineType, power, engineWeight, driverWeight, power / totalWeight));
            thisSummary.append(String.format("\n\t\t%-13s | %8s | %8s | %8s |", "Controls:", "start", "cruise", "finish"));
            thisSummary.append(String.format("\n\t\t%-13s | %8s | %8s | %8s |", "HH:MM:SS:", getFormattedTime(accelerationTime), getFormattedTime(cruiseSpeedTime), getFormattedTime(decelerationTime)));
            thisSummary.append(String.format("\n\t\t%-13s | %7.1f%% | %7.1f%% | %7.1f%% |", "Max Power:", maxAccelerationThrottle, cruiseThrottle, maxDecelerationThrottle));
            thisSummary.append(String.format("\n\t\t%-13s | %7.1f%% | %7.1f%% | %7.1f%% |\n", "Max Brake:", maxAccelerationBrake, cruiseBrake, maxDecelerationBrake));

            if (false) {
                thisSummary.append(String.format("\tTrainMotion count = %d {\n\t\t%s\n", motionCount, TrainMotion.getMotionsHeader()));
                for (int i = 0; i < 3; i++) {
                    for (TrainMotion tm : motionMap.get(i)) {
                        thisSummary.append("\t\t").append(tm.getMotionData()).append('\n');
                    }
                }
                thisSummary.append("\t}\n");
            }

            reportSummary.append(thisSummary);
        }
    }

    private double minHp(double speedLimit, double totalWeight, double grade) {
        double hpRequired = speedLimit * totalWeight * grade / 12;
        if (hpRequired < Setup.getHorsePowerPerTon() * totalWeight) {
            hpRequired = Setup.getHorsePowerPerTon() * totalWeight; // minimum HPT
        }
        return hpRequired;
    }

}
