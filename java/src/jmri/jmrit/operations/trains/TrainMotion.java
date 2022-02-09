package jmri.jmrit.operations.trains;

import jmri.jmrit.operations.setup.Setup;

import java.io.Serializable;
import java.util.List;

public class TrainMotion implements Serializable {
    static TrainMotion getFinalTrainMotion(List<TrainMotion> trainMotions) {
        return trainMotions.isEmpty() ? new TrainMotion() : trainMotions.get(trainMotions.size() - 1);
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
    static double getMinHp(double speedLimit, double totalWeight, double gradePercent) {
        double hpRequired = speedLimit * totalWeight * gradePercent / 12;
        if (hpRequired < Setup.getHorsePowerPerTon() * totalWeight) {
            hpRequired = Setup.getHorsePowerPerTon() * totalWeight; // minimum HPT
        }
        return hpRequired;
    }

    static String getMotionsHeader() {
        return String.format(
                "%12s,%9s,%9s,%7s,%13s,%8s,%8s,%9s,%9s,%7s,%14s" +
                        "\n%12s,%9s,%9s,%7s,%13s,%8s,%8s,%9s,%9s,%7s,%14s",
                "delta time", "time", "distance", "speed", "acceleration", "weight", "force", "power", "throttle", "brake", "motion",
                "HH:MM:SS.SSS", "HH:MM:SS", "miles", "mph", "mph/s", "tons", "tons", "HP", "%", "%", "description"
        );
    }

    static String getRawMotionsHeader() {
        return String.format("%21s,%21s,%21s,%21s,%21s,%21s,%21s,%21s,%21s,%21s,%21s",
                "delta time", "time", "miles", "mph", "mph/s", "weight", "force", "power", "throttle", "brake", "description");
    }

    static String timeString_12_Char(double seconds) {
        return String.format("%02d:%02d:%06.3f", (int) (seconds / 3600), (int) ((seconds % 3600) / 60), seconds % 60);
    }

    static String timeString_8_Char(double seconds) {
        return String.format("%02d:%02d:%02d", (int) (seconds / 3600), (int) ((seconds % 3600) / 60), (int) (seconds % 60));
    }

    /** double delta time in seconds */
    double dt;
    /** double elapsed time in seconds */
    double t;
    /** double distance from start in miles */
    double x;
    /** double speed in MPH */
    double v;
    /** double acceleration in MPH/second */
    double a;
    /** double train weight in tons */
    double w;
    /** double engine force in tons */
    double f;
    /** double engine power in HP */
    double p;
    /** double throttle in % of maximum HP */
    double tp;
    /** double braking in % of maximum brake force */
    double bp;
    /** String motion description */
    String d;

    public TrainMotion() {
        this.dt = 0;
        this.t = 0;
        this.x = 0;
        this.v = 0;
        this.a = 0;
        this.w = 0;
        this.f = 0;
        this.p = 0;
        this.tp = 0;
        this.bp = 0;
        this.d = "empty";
    }

    public TrainMotion(String d) {
        this();
        this.d = d;
    }

    public TrainMotion(double dt, double t, double x, double v, double a, double w, double f, double p, double tp, double bp, String d) {
        this.dt = dt;
        this.t = t;
        this.x = x;
        this.v = v;
        this.a = a;
        this.w = w;
        this.f = f;
        this.p = p;
        this.tp = tp;
        this.bp = bp;
        this.d = d;
    }

    public String getMotionData() {
        return String.format(
                "%12s,%9s,%9.4f,%7.3f,%13.3f,%8.2f,%8.2f,%9.1f,%9.1f,%7.1f,%14s",
                timeString_12_Char(dt), timeString_8_Char(t), x, v, a, w, f, p, tp, bp, d);
    }

    public String getRawMotionData() {
        return String.format("%21s,%21s,%21s,%21s,%21s,%21s,%21s,%21s,%21s,%21s,%21s", dt, t, x, v, a, w, f, p, tp, bp, d);
    }

    @Override
    public String toString() {
        return "TrainMotion {" +  getMotionData() + "}";
    }

}
