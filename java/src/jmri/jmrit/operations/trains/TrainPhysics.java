package jmri.jmrit.operations.trains;

import jmri.jmrit.operations.rollingstock.engines.Engine;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.setup.Setup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * These calculators are partly based Train Forces Calculator by Al Krug
 * <br>(https://web.archive.org/web/20090408120433/http://www.alkrug.vcn.com/rrfacts/RRForcesCalc.html)
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainPhysics implements Serializable {
    // protected basic constant values
    protected static final double COUPLER_PULL_LIMIT_TONS = 125; // working limit for older coupler knuckles
    protected static final double LBS_PER_TON = 2000.0;
    protected static final double STEAMER_DRIVER_WEIGHT_PER_ENGINE_WEIGHT = 0.35;
    static final long serialVersionUID = 1L;
    // basic constant values
    static final double BRAKE_DESIGN_LIMIT = 0.75;
    static final double FEET_PER_MILE = 5280.0;
    static final double FT_LBS_PER_SEC_PER_HP = 550;
    static final double G_SI = 9.80665; // gravitational acceleration in meters/sec/sec
    static final double G_US = 32.174; // gravitational acceleration in feet/sec/sec
    static final double KG_PER_LB = 0.45359237;
    static final double KM_PER_METER = 1000.0;
    static final double METERS_PER_FOOT = 0.0254 * 12.0; // 0.3048
    static final double MINIMUM_ACCELERATION = 0.01;
    static final double POWER_EFFICIENCY = 0.72; // factor especially for F7-A locomotives
    static final double SEC_PER_HOUR = 3600.0;
    static final double SLACK_PER_CAR_FEET = 1.8; // couplers plus drawbars
    static final double STRETCH_SPEED_LIMIT_MPH = 2.0;
    static final double TON_FORCE_BY_KMH_PER_NEWTON = 2650;
    static final double WATTS_PER_HP = 745.7;
    static final double WHEEL_TRACK_ADHESION = 0.25; // coefficient for steel wheel on sanded steel rail
    static final int YEAR_ROLLER_BEARINGS_REQUIRED = 1962;
    // derived constants and conversion factors
    static final double AIR_DRAG_FACTOR = 0.0005 / LBS_PER_TON;
    static final double BRAKE_APPLICATION_LIMIT = 0.25 * BRAKE_DESIGN_LIMIT;
    static final double FLANGE_ADHESION_PER_MPH = 0.045 / LBS_PER_TON;
    static final double METERS_PER_MILE = FEET_PER_MILE * METERS_PER_FOOT; // ~1609.344
    static final double MILES_PER_KM = KM_PER_METER / METERS_PER_MILE; // ~0.621371
    static final double MPH_PER_MPS = SEC_PER_HOUR / METERS_PER_MILE;// ~2.236936
    static final double MPH_PER_FPS = SEC_PER_HOUR / FEET_PER_MILE; // ~0.681818
    static final double STRETCH_SPEED_LIMIT_FPS = STRETCH_SPEED_LIMIT_MPH / MPH_PER_FPS;
    static final double STATIC_ADHESION = 1.3 / LBS_PER_TON; // rolling steel wheel on steel rail
    static final double NEWTONS_PER_LB = KG_PER_LB * G_SI;// ~4.4482
    static final double NEWTONS_PER_TON = NEWTONS_PER_LB * LBS_PER_TON; // ~8896.44
    // protected derived constants and conversion factors
    protected static final double TON_FORCE_BY_MPH_PER_HP = TON_FORCE_BY_KMH_PER_NEWTON * POWER_EFFICIENCY * MILES_PER_KM / NEWTONS_PER_TON;

    /**
     * Using Newton's 2nd law of motion in one dimension, which is force = mass times acceleration, to calculate
     * acceleration as force divided by mass. In this law, "force" is the "net force" on the object, the result of
     * adding all (vector) forces together. Under acceleration in one dimension, this would be the total of all
     * propelling forces subtracted by the total of opposed forces. Under deceleration, this would be the total of
     * braking forces plus the total of opposed forces.
     * <p>
     * The units must agree: both totalWeight and force should be in tons. A force:totalWeight ratio expresses
     * acceleration in "g's", the g-ratio pr "g's", which can be converted to FPS/second via G_US.
     * </p>
     *
     * @param netForce    in tons
     * @param totalWeight in tons
     * @return the calculated acceleration in MPH per sec
     */
    static double getAcceleration(double netForce, double totalWeight) {
        double gRatio = netForce / totalWeight; // acceleration per gravitational acceleration

        return gRatio * G_US * MPH_PER_FPS;
    }

    static double getAirDrag(double carCount, double speed, double carFaceArea) {
        return AIR_DRAG_FACTOR * carFaceArea * carCount * speed * speed;
    }

    static double getBearingDrag(double axleCount) {
        return 0.0145 * axleCount;
    }

    /**
     * Curve resistance depends on the degrees of a curve and the total train weight, assuming the length of the curve
     * exceeds the length of the train. The degree of curvature is based on 100 units of arc (chord) length, so the
     * degree of the curve = 180° * (100' / r) / π, where r is the radius of the curve in feet, i.e. about 5729.578 / r.
     * The value represents the angle of the change in direction per 100' of travel. The measurement is actually 100' of
     * chord, also known as a "station", not the length of the curved rail, easily measured as a straight line by tape
     * measure. For instance, in HO scale, an 18" track radius corresponds to 45° of curvature, while a 7" track radius
     * hits the 180° limit value of curvature.
     *
     * @param degreeOfCurvature direction change in degrees for a 100' (scale) chord on curve
     * @param weight            in tons
     * @return curve resistance in tons
     */
    static double getCurveDrag(double degreeOfCurvature, double weight) {
        return degreeOfCurvature / LBS_PER_TON * weight;
    }

    /**
     * The force due to the grade for weight, ignoring all other motion resistance
     * <p>
     * A positive grade (ascending) generates a force opposed to forward motion. A negative grade (descending) generates
     * a force assisting forward motion.
     *
     * @param weight       the weight in tons moving up or down a grade
     * @param gradePercent the grade in percentage units, positive for up hill (against the grade)
     * @return the calculated force in tons due to the grade
     */
    static double getGradeDrag(double weight, double gradePercent) {
        return gradePercent / 100.0 * weight; // in tons
    }

    /**
     * The net force available for inducing motion, either for acceleration or deceleration, is calculated considering
     * the tractive force limit and opposing forces (rolling and grade resistance forces). Under acceleration in one
     * dimension, net force would be the total of all propelling forces subtracted by the total of opposed forces. For
     * the sake of efficient operation, we assume the use of full engine power, without spinning driver wheels, is used
     * for acceleration.</p>
     *
     * @param fullPower    full power to accelerate motion in HP
     * @param speed        speed in MPH
     * @param totalWeight  total weight being moved tons or newtons
     * @param driverWeight driver wheels weight load available for starting force in tons or newtons
     * @param gradePercent the grade in percentage units
     * @return the calculated net force in tons
     */
    static double getNetForce(double fullPower, double speed, double totalWeight, double driverWeight, double gradePercent) {
        return Math.min(tractiveForceLimit(driverWeight), getTractiveForce(fullPower, speed)) - getRollingDrag(totalWeight, speed, 100, 25, 110) - getGradeDrag(totalWeight, gradePercent);
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
        double rollingDrag = getRollingDrag(totalWeight, v_0, 100, 25, 110);
        double gradeDrag = getGradeDrag(totalWeight, gradePercent);
        double netForce = d_v_signum * availTractiveForce - rollingDrag - gradeDrag; // +/- tons
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
            return new TrainMotion(dt, t_1, x_1, newSpeed, a_x, totalWeight, netForce, appliedPower, throttle, 0, "accelerating");
        } else {
            double brake = Math.abs(100 * availTractiveForce / brakingForceDesignLimit);
            return new TrainMotion(dt, t_1, x_1, newSpeed, a_x, totalWeight, netForce, 0, 0, brake, "decelerating");
        }
    }

    /**
     * The retarding force due to rolling drag (resistance) for weight borne by train wheels on track. There are 4
     * separate contributors to rolling drag:
     * <dl>
     *     <dt>- adhesion drag</dt><dd>adhesion due to the high-pressure, small contact area between a steel wheel and rail</dd>
     *     <dt>- air drag</dt><dd>speed-squared-dependent wind resistance by each car, for an average car face area</dd>
     *     <dt>- bearing drag</dt><dd>retarding force by each axle due to average axle loading</dd>
     *     <dt>- flange drag</dt><dd>speed-dependent force due to wheel flanges sliding against the inside rail head surfaces</dd>
     * </dl>
     *
     * @param weight      the train weight in tons
     * @param speed       train speed in MPH
     * @param axleCount   number of axles supporting the weight of the train
     * @param carCount    number of cars supporting the weight of the train
     * @param carFaceArea average area of the leading face of cars, in sq. ft.
     * @return the calculated rolling resistance force (always opposed to motion) in tons
     */
    static double getRollingDrag(double weight, double speed, double axleCount, double carCount, double carFaceArea) {
        double adhesionDrag = getAdhesionDrag(weight);
        double airDrag = getAirDrag(carCount, speed, carFaceArea);
        double bearingDrag = getBearingDrag(axleCount);
        double flangeDrag = getFlangeDrag(weight, speed);

        return adhesionDrag + airDrag + bearingDrag + flangeDrag;
    }

    static double getFlangeDrag(double weight, double speed) {
        return FLANGE_ADHESION_PER_MPH * Math.abs(speed) * weight;
    }

    static double getAdhesionDrag(double weight) {
        return STATIC_ADHESION * weight;
    }

    /**
     * Starting resistance due to wheel bearing static friction (when at rest)
     *
     * @param weight the weight applied to stationary axle bearings
     * @return the calculated starting resistance force for the total weight on wheel bearings
     */
    static double getStartingDrag(double weight) {
        return getStartingDrag(weight, isRoller(), isWarm());
    }

    /**
     * Starting resistance due to wheel bearing static friction (when at rest)
     * <p><table border = "2" cellspacing = "2">
     * <tr><th>Bearing</th><th>Warm</th><th>Cold</th><th>Units</th></tr>
     * <tr><td>Roller</td><td>5</td><td>15</td><td>lb/ton</td></tr>
     * <tr><td>Journal</td><td>25</td><td>35</td><td>lb/ton</td></tr>
     * </table>
     *
     * @param weight   the weight applied to stationary axle bearings
     * @param isRoller true if roller bearings, false if journal bearings
     * @param isWarm   true if temperature is above freezing, false if below freezing
     * @return the calculated starting resistance force for the total weight on wheel bearings
     */
    static double getStartingDrag(double weight, boolean isRoller, boolean isWarm) {
        return weight * ((isRoller ? (isWarm ? 5 : 15) : (isWarm ? 25 : 35)) / LBS_PER_TON);
    }

    /**
     * TrainMotion starting at rest with train initially bunched, to exploit Slack for possible low driver weight and/or
     * high bearing resistance, to overcome starting resistance, by stretching the train car by car.
     *
     * @param driverWeight driver wheels weight load available for applied force in tons
     * @param engineWeight engine weight including possible tender
     * @param carWeights   ordered list of all car weights in this train
     * @param fullPower    full power to accelerate motion in HP
     * @param gradePercent the grade in percentage units
     * @return TrainMotion covering the entire time interval to stretch this train's coupler assemblies
     */
    static TrainMotion getStretchMotion(double driverWeight, double engineWeight, List<Integer> carWeights, double fullPower, double gradePercent) {
        double totalWeight = engineWeight + carWeights.stream().mapToInt(w -> w).sum();
        if (getNetForce(fullPower, STRETCH_SPEED_LIMIT_MPH, totalWeight, driverWeight, gradePercent) < 0) {
            return new TrainMotion("no stretch motion possible");
        }

        double f = netForceAtRest(engineWeight, driverWeight, gradePercent);
        double stretchedWeight = engineWeight; // initial weight to pull
        // first accelerate engine only from rest to Slack feet down the track, in feet and second units
        double v2; // fps^2
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
            double mph = v * MPH_PER_FPS;
            f = getNetForce(fullPower, mph, newStretchedWeight, driverWeight, gradePercent); // tons
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
        double miles = x / FEET_PER_MILE;
        double mph = v * MPH_PER_FPS;
        double mphPerSecond = a * MPH_PER_FPS;
        return new TrainMotion(t, t, miles, mph, mphPerSecond, totalWeight, appliedForce, appliedHp, throttle, brake, "stretch cars");
    }

    /**
     * Tractive Effort (force) to change the train speed, given its applied engine power
     *
     * @param appliedPower in HP units
     * @param speed        speed in MPH
     * @return the calculated tractive force in tons
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
     * @param priorSpeed speed in MPH on entering this route location segment
     * @return Map of TrainMotion lists traversing a route location
     */
    static List<TrainMotion> getTrainMotions(Train train, RouteLocation rl, double priorSpeed) {
        TrainRevenues trainRevenues = train.getTrainRevenues();
        List<Integer> carWeights = trainRevenues.getTrainCarWeights().get(rl.getId());
        List<Engine> engines = trainRevenues.getTrainEngines().get(rl.getId());
        boolean endStop = endStop(trainRevenues, rl);

        return getTrainMotions(engines, carWeights, Setup.getCarFaceArea(), rl.getGrade(), rl.getDegreeOfCurvature(), priorSpeed, rl.getSpeedLimit(), rl.getDistance(), endStop);
    }

    /**
     * A Map of Lists of train TrainMotion instances is generated from the initial motion of stretching the train, to
     * remove all slack and achieve an initial (limited) speed, followed by an acceleration sequence, up to either the
     * route segment speed limit or arrival at the halfway point of the route segment, followed by a cruising speed
     * motion, if the speed limit was achieved during acceleration, then a deceleration sequence, the very opposite of
     * the acceleration sequence, ending with brunching the train, the very opposite of the stretching motion, and
     * finally coming to a stop at the given route segment end point.
     *
     * @param engines           List of all engines pulling this train
     * @param carWeights        ordered list of all car weights in this train
     * @param carFaceArea       average area of the leading face of cars, in sq. ft.
     * @param gradePercent      the grade in percentage units
     * @param degreeOfCurvature direction change in degrees for 100' scale chord on curve
     * @param priorSpeed        speed in MPH on entering this route location segment
     * @param speedLimit        route speed limit in MPH
     * @param distanceLimit     initial position in miles
     * @param endStop           true if this route location requires coming to a stop at its end
     * @return Map of TrainMotion lists traversing a route location
     */
    static List<TrainMotion> getTrainMotions(List<Engine> engines, List<Integer> carWeights, double carFaceArea, double gradePercent, double degreeOfCurvature, double priorSpeed, double speedLimit, double distanceLimit, boolean endStop) {
        List<TrainMotion> trainMotionList = new ArrayList<>();
        if (engines == null) {
            return trainMotionList;
        }

        double fullPower = 0;
        double driverWeight = 0;
        double engineWeight = 0;
        for (Engine engine : engines) {
            fullPower += engine.getHpInteger();
            driverWeight += driverWeight(engine);
            engineWeight += engine.getAdjustedWeightTons();
        }

        TrainMotion priorTrainMotion = new TrainMotion("segment: new");
        trainMotionList.add(priorTrainMotion);
        if (priorSpeed > 0) {
            priorTrainMotion.v = priorSpeed;
            if (speedLimit < priorSpeed) {
                priorTrainMotion = getTrainMotionAfterDeceleration(trainMotionList, priorTrainMotion, driverWeight, engineWeight, carWeights, gradePercent, fullPower, distanceLimit, speedLimit);
            } else if (priorSpeed < speedLimit) {
                priorTrainMotion = getTrainMotionAfterAcceleration(trainMotionList, priorTrainMotion, driverWeight, engineWeight, carWeights, gradePercent, fullPower, distanceLimit, speedLimit);
            }
        } else {
            priorTrainMotion = getStretchMotion(driverWeight, engineWeight, carWeights, fullPower, gradePercent);
            trainMotionList.add(priorTrainMotion);
            priorTrainMotion = getTrainMotionAfterAcceleration(trainMotionList, priorTrainMotion, driverWeight, engineWeight, carWeights, gradePercent, fullPower, distanceLimit, speedLimit);
        }
        List<TrainMotion> decelList = new ArrayList<>();
        if (endStop) {
            priorTrainMotion = getTrainMotionAfterDeceleration(decelList, priorTrainMotion, driverWeight, engineWeight, carWeights, fullPower, gradePercent, distanceLimit, 0);
        }
        double totalTravelDistance = priorTrainMotion.x;

        // check total travel distance and reduce top speed as needed
        while (distanceLimit < totalTravelDistance && trainMotionList.size() > 2 && decelList.size() > 2) {
            trainMotionList.remove(trainMotionList.size() - 1); // remove old accel top speed
            decelList.remove(0); // remove old decel top speed
            double x_AccelTopSpeed = TrainMotion.getFinalTrainMotion(trainMotionList).x;
            double x_DecelTopSpeed = decelList.get(0).x;
            double travelDistanceReduction = x_DecelTopSpeed - x_AccelTopSpeed;
            totalTravelDistance -= travelDistanceReduction;
        }

        double cruiseDistance = distanceLimit - totalTravelDistance;
        priorTrainMotion = TrainMotion.getFinalTrainMotion(trainMotionList);
        TrainMotion cruiseMotion = cruiseMotion(priorTrainMotion, engineWeight, carWeights, carFaceArea, fullPower, gradePercent, cruiseDistance, degreeOfCurvature);
        List<TrainMotion> cruiseList = new ArrayList<>();
        cruiseList.add(cruiseMotion);

        double addedTime = cruiseMotion.t - TrainMotion.getFinalTrainMotion(trainMotionList).t;
        double addedDistance = cruiseMotion.x - TrainMotion.getFinalTrainMotion(trainMotionList).x;
        // update time and distances in decelerate train motions
        for (TrainMotion tm : decelList) {
            tm.t += addedTime;
            tm.x += addedDistance;
        }

        trainMotionList.addAll(cruiseList);
        trainMotionList.addAll(decelList);
        priorTrainMotion = TrainMotion.getFinalTrainMotion(trainMotionList);
        //        if (endStop) {
        TrainMotion tm = new TrainMotion("segment: end");
        tm.t = priorTrainMotion.t;
        tm.x = priorTrainMotion.x;
        tm.v = priorTrainMotion.v;
        tm.w = priorTrainMotion.w;
        trainMotionList.add(tm);

        return trainMotionList;
    }

    static boolean isRoller() {
        try {
            String year = Setup.getYearModeled().trim();
            return Integer.parseInt(year) > YEAR_ROLLER_BEARINGS_REQUIRED;
        } catch (NumberFormatException e) {
            return true;
        }
    }
/*
    static boolean isSI() {
        return Setup.getLengthUnit().equals(Setup.METER);
    }
*/
    /**
     * rule for temperatures above freezing
     *
     * @return if JRMI timebase month is in meteorological winter
     */
    static boolean isWarm() {
        Calendar calendar = Calendar.getInstance();
        // use the JMRI Timebase (which may be a fast clock).
        calendar.setTime(jmri.InstanceManager.getDefault(jmri.Timebase.class).getTime());
        int month = calendar.get(Calendar.MONTH);
        return month < Calendar.DECEMBER && Calendar.FEBRUARY < month;
    }

    static TrainMotion getTrainMotionAfterDeceleration(List<TrainMotion> decelList, TrainMotion priorTrainMotion, double driverWeight, double engineWeight, List<Integer> carWeights, double fullPower, double gradePercent, double distanceLimit, double finalSpeed) {
        int newSpeed = (int) Math.round(priorTrainMotion.v);
        for (int speed = newSpeed - 1; speed >= finalSpeed; speed--) {
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

    static TrainMotion getTrainMotionAfterAcceleration(List<TrainMotion> accelList, TrainMotion priorTrainMotion, double driverWeight, double engineWeight, List<Integer> carWeights, double gradePercent, double fullPower, double distanceLimit, double speedLimit) {
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

    static boolean endStop(TrainRevenues trainRevenues, RouteLocation rl) {
        return rl == null || endStop(trainRevenues,rl.getId());
    }

    static boolean endStop(TrainRevenues trainRevenues, String rlId) {
        Integer switchingActions = trainRevenues.getTrainPickUpsOrDropOffs().get(rlId);
        return switchingActions == null ? true : switchingActions > 0;
    }

    /**
     * @param netForce    in tons or newtons
     * @param totalWeight in tons or newtons
     * @return acceleration in FPS, per second
     */
    static double accelerationFpsPerSecond(double netForce, double totalWeight) {
        double gRatio = netForce / totalWeight; // acceleration per gravitational acceleration

        return gRatio * G_US;
    }

    /**
     * Applied power is full power reduced by the ratio of applied force to available force
     *
     * @param fullPower    full power to accelerate motion in HP
     * @param speed        speed in MPH
     * @param driverWeight driver wheels weight load available for applied force in tons
     * @return power in HP
     */
    static double appliedPower(double fullPower, double speed, double driverWeight) {
        double tractiveForce = getTractiveForce(fullPower, speed);
        double tractiveForceLimit = tractiveForceLimit(driverWeight);
        double appliedForce = Math.min(tractiveForceLimit, tractiveForce);

        return appliedForce / tractiveForce * fullPower;
    }

    static double availTractiveForce(double driverWeight, double fullPower, double totalWeight, double v_0, boolean accelerate) {
        return accelerate ? Math.min(tractiveForceLimit(driverWeight), getTractiveForce(fullPower, v_0)) : brakingForceSafetyLimit(totalWeight);
    }

    /**
     * Typically, all the weight of the train puts adhesive pressure on the rails by wheels, and all axles have braking
     * capability. Brakes are designed to prevent the wheels from brake lock, which would damage both wheels and rails.
     * Brakes are designed to apply no more than 75% of this force.
     *
     * @param totalWeight weight load available for braking force
     * @return the calculated braking force in the same units as the weight
     */
    static double brakingForceDesignLimit(double totalWeight) {
        return WHEEL_TRACK_ADHESION * BRAKE_DESIGN_LIMIT * totalWeight;
    }

    /**
     * Typically, all the weight of the train puts adhesive pressure on the rails by wheels, and all axles have braking
     * capability. The safe braking limit is the maximum applied braking force limit used for safety considerations.
     *
     * @param totalWeight weight load available for braking force
     * @return the calculated braking force in the same units as the weight
     */
    static double brakingForceSafetyLimit(double totalWeight) {
        return WHEEL_TRACK_ADHESION * BRAKE_APPLICATION_LIMIT * totalWeight;
    }

    static TrainMotion cruiseMotion(TrainMotion priorTrainMotion, double engineWeight, List<Integer> carWeights, double carFaceArea, double fullPower, double gradePercent, double cruiseDistance, double degreeOfCurvature) {
        double carsWeight = carWeights.stream().mapToInt(w -> w).sum();
        int carCount = carWeights.size();
        int axleCount = Setup.getAxlesPerCar() * carCount;
        double totalWeight = engineWeight + carsWeight;
        // parameters for cruise (constant speed motion)
        double brakingForceLimit = brakingForceSafetyLimit(totalWeight);
        double priorDistance = priorTrainMotion.x;
        double cruiseSpeed = priorTrainMotion.v;
        double cruiseTime = 3600 * cruiseDistance / cruiseSpeed;
        double throttle = 0;
        double brake = 0;

        double gradeDrag = getGradeDrag(totalWeight, gradePercent);
        double curveDrag = getCurveDrag(degreeOfCurvature, totalWeight);
        double rollingDrag = getRollingDrag(totalWeight, cruiseSpeed, axleCount, carCount, carFaceArea);
        double totalDrag = rollingDrag + gradeDrag + curveDrag;
        double appliedPower = netCruisePower(totalDrag, cruiseSpeed);
        if (appliedPower > 0) {
            throttle = 100 * appliedPower / fullPower;
        } else {
            appliedPower = 0;
            brake = -100 * totalDrag / brakingForceLimit;
        }

        return new TrainMotion(cruiseTime, priorTrainMotion.t + cruiseTime, priorDistance + cruiseDistance, cruiseSpeed, 0, totalWeight, totalDrag, appliedPower, throttle, brake, "steady speed");
    }

    static double driverWeight(Engine engine) {
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
    static double netCruisePower(double netForce, double cruiseSpeed) {
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
     * @param driverWeight driver wheels weight load available for starting force in tons
     * @param gradePercent the grade in percentage units
     * @return the calculated net force in tons
     */
    static double netForceAtRest(double engineWeight, double driverWeight, double gradePercent) {
        double gradeDrag = getGradeDrag(engineWeight, gradePercent);
        double startingDrag = getStartingDrag(engineWeight);
        double tractiveForceLimit = tractiveForceLimit(driverWeight);

        double netForce = tractiveForceLimit - startingDrag - gradeDrag;
        return Math.max(0, netForce);
    }

    /**
     * Available force due to weight on driving wheels on track, using sand, without spinning.
     *
     * @param driversWeight wheel weight load available for traction force
     * @return the calculated tractive force in the same units as the weight
     */
    static double tractionForceLimit(double driversWeight) {
        return WHEEL_TRACK_ADHESION * driversWeight;
    }

    /**
     * The limit of available force, being the smaller of the drawbar pull limit and traction force limit. It is assumed
     * that any rail engine can generate enough torque to spin its wheels at rest, which damages wheels and rails, and
     * produces less tractive force. This is the higher static friction force an experienced engineer can obtain, by
     * careful throttle control to just avoid wheel spin.
     *
     * @param driversWeight driver wheel weight load available for starting force in tons
     * @return the calculated tractive force in tons
     */
    static double tractiveForceLimit(double driversWeight) {
        return Math.min(COUPLER_PULL_LIMIT_TONS, tractionForceLimit(driversWeight));
    }
/*
    private final StringBuilder reportSummary = new StringBuilder();
    private final List<TrainMotion> routeTrainMotions = new ArrayList<>();

    public List<TrainMotion> getRouteTrainMotions() {
        return routeTrainMotions;
    }

    @Override
    public String toString() {
        return "TrainPhysics Summary Report {" + reportSummary + '}';
    }

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
            double cruiseThrottle;
            double cruiseBrake;
            double cruiseSpeedTime;
            ctm = trainMotionList.get(endAccelIndex + 1);
            cruiseThrottle = ctm.tp;
            cruiseBrake = ctm.bp;
            cruiseSpeedTime = ctm.t - accelFinishTime;

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
                decelerationTime = finalTrainMotion(decelTrainMotions).t - ctm.t;
                finalTrainMotion = finalTrainMotion(decelTrainMotions);
            } else {
                finalTrainMotion = finalTrainMotion(trainMotionList);
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

    public TrainPhysics(Train train) {
        this(train, false);
    }

    public TrainPhysics(Train train, boolean detailed) {
        Route route = train.getRoute();

        List<RouteLocation> locationsBySequenceList = route.getLocationsBySequenceList();
        for (RouteLocation rl : locationsBySequenceList) {
            boolean terminationRl = rl.getSequenceNumber() == locationsBySequenceList.size();
            if (!terminationRl) {
                TrainMotion priorTm;
                if (routeTrainMotions.isEmpty()) {
                    priorTm = new TrainMotion("route: alpha");
                    routeTrainMotions.add(priorTm);
                } else {
                    priorTm = finalTrainMotion(routeTrainMotions);
                    priorTm.d = "segment: end";
                }
                List<TrainMotion> trainMotionList = getTrainMotions(train, rl, priorTm.v);
                for (TrainMotion tm : trainMotionList) {
                    tm.t += priorTm.t;
                    tm.x += priorTm.x;
                }
                TrainMotion z = finalTrainMotion(trainMotionList);
                if (terminationRl) {
                    trainMotionList.add(new TrainMotion(0, z.t, z.x, 0, 0, 0, 0, 0, 0, "route: omega"));
                    routeTrainMotions.addAll(trainMotionList);
                }
                updateMotionReport(train, rl, detailed, trainMotionList);
            } else {
                System.out.println("surprise");
            }
        }
    }
*/
}
