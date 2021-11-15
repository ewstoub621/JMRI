package jmri.jmrit.operations.trains;

import java.io.Serializable;

public class TrainMovements implements Serializable {
    protected static final double COUPLER_PULL_LIMIT_TONS = 125;
    protected static final double HP_TO_WATTS = 745.7;
    protected static final double KM_PER_HR_TO_MPS = 0.277778;
    protected static final double LBS_PER_TON = 2000.0;
    protected static final double NEWTONS_PER_TON = 8896.4;
    protected static final double POWER_EFFICIENCY = 0.72;
    protected static final double DRIVER_ADHESION = 0.25;

    private static final long serialVersionUID = 1L;

    /**
     * Using Newton's 2nd law of motion in one dimension, which is force = mass times acceleration, to calculate
     * acceleration as force divided by mass. In this law, "force" is the "net force" on the object, the result of
     * adding all (vector) forces together. In one dimension, this would be the total of all propelling forces
     * subtracted by the total of opposed forces.
     *
     * @param mass  in MKS units of kilograms
     * @param force in MKS units of newtons, which are kilogram meters per second, per second
     * @return the calculated acceleration, in meters per sec, per sec, of the mass due to the (net) force acting on it
     */
    protected static double getAcceleration(double mass, double force) {
        return force / mass;
    }

    /**
     * Force-limit to protect older couplers from breaking
     *
     * @param mks true to yield MKS (Newtons) units, false to yield ton-force units
     * @return the calculated limit of applied pulling force to safely pull cars behind leading engines
     */
    protected static double getDrawbarPullLimit(boolean mks) {
        if (mks) {
            return COUPLER_PULL_LIMIT_TONS * NEWTONS_PER_TON; // in Newtons
        } else {
            return COUPLER_PULL_LIMIT_TONS; // in ton-force units
        }
    }

    /**
     * The force due to the grade for (frictionless) weight
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
     * The net force to move the train
     *
     * @param power          applied power to accelerate
     * @param speed          current speed
     * @param driverWeight   driver wheel weight available for starting force
     * @param weight         total weight
     * @param gradePercent   grade: positive inhibits movement, negative enhances movement
     * @param journalBearing axle bearing type: journal if true, else roller
     * @param aboveFreezing  axle temperature: above freezing if true, else below freezing
     * @param mks            true to yield MKS (Newtons) units, false to yield ton-force units
     * @return the calculated net force, if MKS, in Newtons, else in tons
     */
    protected static double getNetForce(double power, double speed, double driverWeight, double weight, double gradePercent, boolean journalBearing, boolean aboveFreezing, boolean mks) {
        double netForce = 0;

        double forceLimit = Math.min(getDrawbarPullLimit(mks), getTractionLimitForce(driverWeight));
        if (speed > 0) {
            netForce += Math.min(forceLimit, getTractiveEffort(power, speed, mks));
            netForce -= getRollingResistance(weight);
            netForce -= getGradeResistance(weight, gradePercent);
        } else {
            netForce += forceLimit;
            netForce -= getStartingResistance(weight, journalBearing, aboveFreezing);
        }

        return netForce;
    }

    /**
     * The retarding force due to rolling resistance for weight borne by train wheels on track, using a coefficient of
     * (adhesive) friction for steel on steel
     *
     * @param weight the weight
     * @return the calculated rolling resistance force (always opposed to motion)
     */
    protected static double getRollingResistance(double weight) {
        return 0.0015 * weight; // in the same units as the weight parameter
    }

    /**
     * Standard calculation for Tractive Effort (force) in Newtons, given power in HP units and speed in Km/hr
     *
     * @param power available in HP units
     * @param speed speed in Km/hr
     * @return the calculated tractive force in Newtons
     */
    protected static double getStandardTractiveForce(double power, double speed) {
        return 2650 * POWER_EFFICIENCY * power / speed;
    }

    /**
     * Starting resistance due to wheel bearing static friction (when at rest)
     * <p>
     * Type of Bearings - Above Freezing    - Below Freezing Journal Bearing      25 lb / ton         35 lb / ton Roller
     * Bearing        5 lb / ton         15 lb / ton
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
     * @param driverWeight if MKS, in newtons, else in tons
     * @return the calculated tractive starting force, if MKS, in Newtons, else in tons
     */
    protected static double getTractionLimitForce(double driverWeight) {
        return DRIVER_ADHESION * driverWeight;
    }

    /**
     * Tractive Effort (force) to accelerate the train given its applied engine power
     *
     * @param power if MKS, in watts, else in HP units
     * @param speed if MKS, in meters per second (MPS), otherwise MPH
     * @param mks   true to yield MKS (Newtons) units, false to yield ton-force units
     * @return the calculated tractive force, if MKS, in Newtons, else in tons
     */
    protected static double getTractiveEffort(double power, double speed, boolean mks) {
        if (mks) {
            double trainHp = power / HP_TO_WATTS;
            double speedKmPerHour = speed / KM_PER_HR_TO_MPS; // from m/s to km/hr

            return getStandardTractiveForce(trainHp, speedKmPerHour);// newtons
        } else {
            double speedKmPerHour = 1.609344 * speed; // from MPH to km/hr

            return getStandardTractiveForce(power, speedKmPerHour) / NEWTONS_PER_TON; // tons;
        }
    }

}
