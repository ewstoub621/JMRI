package jmri.jmrit.operations.trains;

import java.io.Serializable;

/**
 * These calculators are mostly based on http://evilgeniustech.com/idiotsGuideToRailroadPhysics/TheBasics/
 * <p>That site is a partial reconstitution of the work of Al Krug, a.k.a. the Evil Genius, on train movement
 * physics. A second source is https://www.railelectrica.com/traction-mechanics/train-grade-curve-and-acceleration-resistance-2//p>
 *
 * <p>Careful vetting of these calculators and particularly the proper use of units allow easy shifting
 * of values between the US (aka "English Engineering System") and SI (aka "International System of Units") quantities.
 * The TrainMovementsTest class illustrates the use of the calculations and units conversions</p>
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainMovements implements Serializable {
    // constants
    protected static final double COUPLER_PULL_LIMIT_TONS = 125; // working limit for older coupler knuckles
    protected static final double DRIVER_ADHESION = 0.25; // coefficient for steel wheel on sanded steel rail
    protected static final double FLANGE_ADHESION = 0.00008; // steel flange sliding against steel rail face, per Kmph (speed)
    protected static final double G_US = 32.174; // gravitational acceleration in feet/sec/sec
    protected static final double G_SI = 9.8066; // gravitational acceleration in meters/sec/sec
    protected static final double POWER_EFFICIENCY = 0.72; // factor especially for F7-A locomotives
    protected static final double STATIC_ADHESION = 0.0016; // rolling steel wheel on steel rail

    // conversion factors
    protected static final double MPH_PER_FPS = 0.681818;
    protected static final double MPH_PER_MPS = 2.236936;
    protected static final double WATTS_PER_HP = 745.7;
    protected static final double MPH_PER_KMPH = 0.621371;
    protected static final double MPS_PER_KMPH = 0.277778;
    protected static final double LBS_PER_TON = 2000.0;
    protected static final double NEWTONS_PER_LB = 4.4482;
    protected static final double NEWTONS_PER_TON = NEWTONS_PER_LB * LBS_PER_TON;

    private static final long serialVersionUID = 1L;

    /**
     * Using Newton's 2nd law of motion in one dimension, which is force = mass times acceleration, to calculate
     * acceleration as force divided by mass. In this law, "force" is the "net force" on the object, the result of
     * adding all (vector) forces together. In one dimension, this would be the total of all propelling forces
     * subtracted by the total of opposed forces.
     *
     * @param weight if SI, in Newtons, else in tons
     * @param force  if SI, in Newtons, else in tons
     * @param SI     true to yield acceleration in meters per sec^2, else in mph per sec
     * @return the calculated acceleration
     */
    protected static double getAcceleration(double weight, double force, boolean SI) {
        if (SI) {
            return force / (weight / G_SI); // meters per second, per second
        } else {
            return force / (weight / G_US) * MPH_PER_FPS; // MPH, per sec
        }
    }

    /**
     * Force-limit to protect older couplers from breaking
     *
     * @param SI true to yield SI units (Newtons), false to yield ton-force units
     * @return the calculated limit of applied pulling force to safely pull cars behind leading engines
     */
    protected static double getDrawbarPullLimit(boolean SI) {
        if (SI) {
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
     * The net force available at rest, considering the tractive force limit and the starting resistance force
     *
     * @param weight             total weight borne on axles
     * @param journalBearing     axle bearing type: journal if true, else roller
     * @param aboveFreezing      axle temperature: above freezing if true, else below freezing
     * @param tractiveForceLimit the smaller of the maximum drawbar pull and the tractive effort forces
     * @return the calculated net force, if SI, in Newtons, else in tons
     */
    protected static double getNetForceAtRest(double weight, boolean journalBearing, boolean aboveFreezing, double tractiveForceLimit) {
        return tractiveForceLimit - getStartingResistance(weight, journalBearing, aboveFreezing);
    }

    /**
     * @param power              applied power to accelerate
     * @param speed              current speed
     * @param weight             total weight
     * @param gradePercent       the grade in percentage units
     * @param SI                 true: SI units (Newtons); false: ton-force units
     * @param tractiveForceLimit limit of available force = lesser of the drawbar pull limit and tractive effort forces
     * @return the calculated net force, if SI, in Newtons, else in tons
     */
    protected static double getNetForceInMotion(double power, double speed, double weight, double gradePercent, boolean SI, double tractiveForceLimit) {
        return Math.min(tractiveForceLimit, getTractiveEffort(power, speed, SI)) - getRollingResistance(weight, 0, true) - getGradeResistance(weight, gradePercent);
    }

    /**
     * The retarding force due to rolling resistance for weight borne by train wheels on track, using a coefficient of
     * (adhesive) friction for steel wheels on steel rails, which is independent of speed, plus (sliding) friction for
     * steel wheel flanges against steel rail faces, which is speed dependent and purely empirical. There is no account
     * for air resistance, as that is negligible for freight trains due to their relatively low speeds.
     *
     * @param weight the weight in arbitrary units
     * @param speed  if SI, in meters per second (MPS), otherwise MPH
     * @param SI     true to yield SI units (Newtons), false to yield ton-force units
     * @return the calculated rolling resistance force (always opposed to motion) in the same units as weight
     */
    protected static double getRollingResistance(double weight, double speed, boolean SI) {
        double drag = STATIC_ADHESION * weight;
        if (speed != 0) {
            drag += FLANGE_ADHESION * weight * (speed / (SI ? MPS_PER_KMPH : MPH_PER_KMPH));
        }

        return drag; // in the same units as the weight parameter
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
     * @param power if SI, in watts, else in HP units
     * @param speed if SI, in meters per second (MPS), otherwise MPH
     * @param SI    true to yield SI units (Newtons), false to yield ton-force units
     * @return the calculated tractive force, if SI units, in Newtons, else in tons
     */
    protected static double getTractiveEffort(double power, double speed, boolean SI) {
        if (SI) {
            double trainHp = power / WATTS_PER_HP;
            double speedKmPerHour = speed / MPS_PER_KMPH; // from m/s to km/hr

            return getStandardTractiveForce(trainHp, speedKmPerHour);// newtons
        } else {
            double speedKmPerHour = speed / MPH_PER_KMPH; // from MPH to km/hr

            return getStandardTractiveForce(power, speedKmPerHour) / NEWTONS_PER_TON; // tons;
        }
    }

    /**
     * The limit of available force, considering the drawbar pull limit and tractive effort force
     *
     * @param driverWeight driver wheel weight load available for starting force in Newtons (SI) or tons
     * @param SI           true to use SI units (Newtons), false to use ton-force units
     * @return the calculated tractive force, if SI units, in Newtons, else in tons
     */
    protected static double getTractiveForceLimit(double driverWeight, boolean SI) {
        return Math.min(getDrawbarPullLimit(SI), getTractionLimitForce(driverWeight));
    }

}
