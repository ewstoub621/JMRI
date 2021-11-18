package jmri.jmrit.operations.trains;

import jmri.jmrit.operations.routes.Route;
import jmri.jmrit.operations.setup.TrainRevenues;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * These calculators are mostly based on http://evilgeniustech.com/idiotsGuideToRailroadPhysics/TheBasics/
 * <p>That site is a partial reconstitution of the work of Al Krug, a.k.a. the Evil Genius, on train movement
 * physics. A second source is https://www.railelectrica.com/traction-mechanics/train-grade-curve-and-acceleration-resistance-2//p>
 *
 * <p>Careful vetting of these calculators and particularly the proper use of units allow easy shifting
 * of values between the US (aka "English Engineering System") and sI (aka "International System of Units") quantities.
 * The TrainMovementsTest class illustrates the use of the calculations and units conversions</p>
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainMovements implements Serializable {
    // constants
    protected static final double COUPLER_PULL_LIMIT_TONS = 125; // working limit for older coupler knuckles
    protected static final double DRIVER_ADHESION = 0.25; // coefficient for steel wheel on sanded steel rail
    protected static final double FLANGE_ADHESION_PER_KMPH = 0.00008; // steel flange sliding against steel rail face, per Kmph (speed)
    protected static final double G_US = 32.174; // gravitational acceleration in feet/sec/sec
    protected static final double G_SI = 9.8066; // gravitational acceleration in meters/sec/sec
    protected static final double POWER_EFFICIENCY = 0.72; // factor especially for F7-A locomotives
    protected static final double STATIC_ADHESION = 0.0016; // rolling steel wheel on steel rail

    // conversion factors
    protected static final double LBS_PER_TON = 2000.0;
    protected static final double METERS_PER_MILE = 1609.344;
    protected static final double MILES_PER_KM = 0.621371;
    protected static final double MPH_PER_FPS = 0.681818;
    protected static final double MPH_PER_MPS = 2.236936;
    protected static final double MPS_PER_KMPH = 0.277778;
    protected static final double NEWTONS_PER_LB = 4.4482;
    protected static final double NEWTONS_PER_TON = 8896.44;
    protected static final double WATTS_PER_HP = 745.7;

    private static final long serialVersionUID = 1L;

    private final Route route;
    private final TrainRevenues trainRevenues;

    public TrainMovements(TrainRevenues trainRevenues) {
        this.trainRevenues = trainRevenues;
        route = trainRevenues.getTrain().getRoute();
    }

    /**
     * Using Newton's 2nd law of motion in one dimension, which is force = mass times acceleration, to calculate
     * acceleration as force divided by mass. In this law, "force" is the "net force" on the object, the result of
     * adding all (vector) forces together. In one dimension, this would be the total of all propelling forces
     * subtracted by the total of opposed forces. The units must agree: if sI, both weight and force should be in
     * Newtons, else if US, both should be in tons. A force:weight ratio expresses acceleration in "g's", the g-ratio pr
     * "g's", which can be converted to MPS/second via G_SI or FPS/second via G_US.
     *
     * @param force  if sI, in Newtons, else in tons
     * @param weight if sI, in Newtons, else in tons
     * @param sI     true to yield acceleration in meters per sec^2, else in mph per sec
     * @return the calculated acceleration
     */
    protected static double getAcceleration(double force, double weight, boolean sI) {
        double gRatio = force / weight; // dimensionless acceleration with respect to gravitational attraction
        if (sI) {
            return gRatio * G_SI; // in MPS, per second
        } else {
            return gRatio * G_US * MPH_PER_FPS; // MPH per sec, because G_US is in FPS, per second
        }
    }

    /**
     * The new distance along the track after acceleration for a period of one second.
     * <p>Complementary units are required:</p>
     * <ul>
     *     <li>if sI, then express acceleration in MPS/sec AND speed in MPS AND position in meters</li>
     *     <li>else express acceleration in MPH/sec AND position in Miles AND speed in MPH</li>
     * </ul>
     *
     * @param distance     initial position: if sI, in meters, else in miles
     * @param speed        initial speed: if sI, in MPS, otherwise MPH
     * @param acceleration acceleration: if sI, in MPS/sec, else in MPH/sec
     * @param sI
     * @return the calculated new position after 1 second of acceleration
     */
    protected static double getDistanceAfterOneSecond(double distance, double speed, double acceleration, boolean sI) {
        if (sI) {
            return distance + speed + acceleration / 2;
        } else {
            return distance + speed / 3600 + acceleration / 3600 / 2;
        }
    }

    /**
     * Force-limit to protect older couplers from breaking
     *
     * @param sI true to yield sI units (Newtons), false to yield ton-force units
     * @return the calculated limit of applied pulling force to safely pull cars behind leading engines
     */
    protected static double getDrawbarPullLimit(boolean sI) {
        if (sI) {
            return COUPLER_PULL_LIMIT_TONS * NEWTONS_PER_TON; // in Newtons
        } else {
            return COUPLER_PULL_LIMIT_TONS; // in ton-force units
        }
    }

    /**
     * The force due to the grade for weight, ignoring all movement resistance
     * <p>
     * A positive grade (climbing upward) generates a force opposed to forward motion. A negative grade (climbing
     * downward) generates a force assisting forward motion.
     *
     * @param weight       the weight moving freely along a grade
     * @param gradePercent the grade in percentage units
     * @return the calculated force due to the grade
     */
    protected static double getGradeResistance(double weight, double gradePercent) {
        return gradePercent / 100.0 * weight; // in weight units
    }

    /**
     * The net force available at rest, considering the calculated tractive force limit and the calculated starting
     * resistance force. This net force is constrained not to be negative, since a tractive force less than the starting
     * resistance force will not result in any movement, certainly not backward movement.
     *
     * @param totalWeight    total weight borne on axles
     * @param driverWeight   driver wheel weight load available for starting force
     * @param journalBearing axle bearing type: journal if true, else roller
     * @param aboveFreezing  axle temperature: above freezing if true, else below freezing
     * @param gradePercent   the grade in percentage units
     * @param sI             true to yield sI units (Newtons), false to yield ton-force units
     * @return the calculated net force: if sI, in Newtons, else in tons
     */
    protected static double getNetForceAtRest(double totalWeight, double driverWeight, boolean journalBearing, boolean aboveFreezing, double gradePercent, boolean sI) {
        double tractiveForceLimit = getTractiveForceLimit(driverWeight, sI);
        double startingResistance = getStartingResistance(totalWeight, journalBearing, aboveFreezing);
        double gradeResistance = getGradeResistance(totalWeight, gradePercent);

        return Math.max(0, tractiveForceLimit - startingResistance - gradeResistance);
    }

    /**
     * The net force available in motion, considering the calculated tractive force limit and the calculated starting
     * resistance force. This net force is constrained not to be negative, since a tractive force less than the starting
     * resistance force will not result in any movement, certainly not backward movement.
     *
     * @param power              applied power to accelerate
     * @param speed              speed: if sI, in meters per second (MPS), otherwise MPH
     * @param weight             total weight being displaced
     * @param tractiveForceLimit limit of available force = lesser of the drawbar pull limit and tractive effort forces
     * @param gradePercent       the grade in percentage units
     * @param sI                 true: sI units (Newtons); false: ton-force units
     * @return the calculated net force: if sI, in Newtons, else in tons
     */
    protected static double getNetForceInMotion(double power, double speed, double weight, double tractiveForceLimit, double gradePercent, boolean sI) {
        double tractiveForce = Math.min(tractiveForceLimit, getTractiveEffort(power, speed, sI));
        double rollingResistance = getRollingResistance(weight, 0, true);
        double gradeResistance = getGradeResistance(weight, gradePercent);

        return Math.max(0, tractiveForce - rollingResistance - gradeResistance);
    }

    /**
     * The new speed along the track after acceleration for a period of one second.
     * <p>Complementary units are required:</p>
     * <ul>
     *     <li>if sI, then express acceleration in MPS/sec AND speed in MPS AND position in meters</li>
     *     <li>else express acceleration in MPH/sec AND position in miles AND speed in MPH</li>
     * </ul>
     *
     * @param speed        initial speed: if sI, in MPS, otherwise MPH
     * @param acceleration acceleration: if sI, in MPS/sec, else in MPH/sec
     * @return the calculated new speed after 1 second of acceleration
     */
    protected static double getSpeedAfterOneSecond(double speed, double acceleration) {
        return speed + acceleration;
    }

    /**
     * The retarding force due to rolling resistance for weight borne by train wheels on track, using a coefficient of
     * (adhesive) friction for steel wheels on steel rails, which is independent of speed, plus (sliding) friction for
     * steel wheel flanges against steel rail faces, which is speed dependent and purely empirical. There is no account
     * for air resistance, as that is negligible for freight trains due to their relatively low speeds.
     *
     * @param weight the weight in arbitrary units
     * @param speed  speed: if sI, in meters per second (MPS), otherwise MPH
     * @param sI     true to yield sI units (Newtons), false to yield ton-force units
     * @return the calculated rolling resistance force (always opposed to motion) in the same units as weight
     */
    protected static double getRollingResistance(double weight, double speed, boolean sI) {
        return (STATIC_ADHESION + FLANGE_ADHESION_PER_KMPH * Math.abs(speed) / (sI ? MPS_PER_KMPH : MILES_PER_KM)) * weight;
    }

    /**
     * Standard calculation for Tractive Effort (force) in Newtons, given power in HP units and speed in Kmph
     *
     * @param power available in HP units
     * @param speed speed in Kmph
     * @return the calculated tractive force in Newtons
     */
    protected static double getStandardTractiveForce(double power, double speed) {
        return 2650 * POWER_EFFICIENCY * power / speed;
    }

    /**
     * Starting resistance due to wheel bearing static friction (when at rest)
     * <p><table>
     * <tr><th>Type of Bearings</th><th>Above Freezing</th><th>Below Freezing</th></th>
     * <tr><td>Journal Bearing</td><td>25 lb / ton</td><td>35 lb / ton</td>
     * <tr><td>Roller Bearing</td><td>5 lb / ton</td><td>15 lb / ton</td></table>
     *
     * @param weight         the weight applied to axle bearings
     * @param journalBearing true uses the static friction oj journal bearings else of roller bearings
     * @param aboveFreezing  true uses the static friction of bearings when above freezing else below freezing
     * @return the calculated starting resistance force for the total weight on wheel bearings
     */
    protected static double getStartingResistance(double weight, boolean journalBearing, boolean aboveFreezing) {
        double bearingFactor = (journalBearing ? (aboveFreezing ? 25 : 35) : (aboveFreezing ? 5 : 15)) / LBS_PER_TON;
        return bearingFactor * weight;
    }

    protected static List<Double> getTimeDistanceList(double power, double speedLimit, double driverWeight, double totalWeight,
                                                      double gradePercent, boolean journalBearing, boolean aboveFreezing, boolean sI) {
        List<Double> timeDistanceList = new ArrayList<>();

        double distance = 0;
        double speed = 0;

        double netForceAtRest = getNetForceAtRest(totalWeight, driverWeight, journalBearing, aboveFreezing, 0, sI);
        double acceleration = getAcceleration(netForceAtRest, totalWeight, sI);

        distance = getDistanceAfterOneSecond(0, speed, acceleration, sI);
        timeDistanceList.add(distance);

        speed = getSpeedAfterOneSecond(speed, acceleration);
        if (speed > 0) {
            double tractiveForceLimit = getTractiveForceLimit(driverWeight, sI);
            double netForceInMotion = getNetForceInMotion(power, speed, totalWeight, tractiveForceLimit, gradePercent, sI);
            while (speed < speedLimit && netForceInMotion > 0) {
                distance = getDistanceAfterOneSecond(distance, speed, acceleration, sI);
                speed = getSpeedAfterOneSecond(speed, acceleration);
                timeDistanceList.add(distance);
                // for next round:
                acceleration = getAcceleration(netForceInMotion, totalWeight, sI);
                netForceInMotion = getNetForceInMotion(power, speed, totalWeight, tractiveForceLimit, gradePercent, sI);
            }
        }

        return timeDistanceList;
    }

    /**
     * Available force due to weight of driving wheels on track, using sand, without spinning
     *
     * @param driverWeight driver wheel weight load available for starting force
     * @return the calculated tractive starting force in the same units as the driverWeight
     */
    protected static double getTractionLimitForce(double driverWeight) {
        return DRIVER_ADHESION * driverWeight;
    }

    /**
     * Tractive Effort (force) to accelerate the train given its applied engine power
     *
     * @param power if sI, in watts, else in HP units
     * @param speed speed: if sI, in meters per second (MPS), otherwise MPH
     * @param sI    true to yield sI units (Newtons), false to yield ton-force units
     * @return the calculated tractive force: if sI units, in Newtons, else in tons
     */
    protected static double getTractiveEffort(double power, double speed, boolean sI) {
        if (sI) {
            double trainHp = power / WATTS_PER_HP;
            double speedKmPerHour = speed / MPS_PER_KMPH; // from m/s to km/hr

            return getStandardTractiveForce(trainHp, speedKmPerHour);// newtons
        } else {
            double speedKmPerHour = speed / MILES_PER_KM; // from MPH to km/hr

            return getStandardTractiveForce(power, speedKmPerHour) / NEWTONS_PER_TON; // tons;
        }
    }

    /**
     * The limit of available force, considering the drawbar pull limit and tractive effort force
     *
     * @param driverWeight driver wheel weight load available for starting force in Newtons (sI) or tons
     * @param sI           true to use sI units (Newtons), false to use ton-force units
     * @return the calculated tractive force: if sI units, in Newtons, else in tons
     */
    protected static double getTractiveForceLimit(double driverWeight, boolean sI) {
        return Math.min(getDrawbarPullLimit(sI), getTractionLimitForce(driverWeight));
    }

}
