package jmri.jmrit.operations.trains;

public class TrainMotion {
    static final TrainMotion ZERO = new TrainMotion();

    final double dt; // seconds
    final double t; // seconds
    final double x; // miles
    final double v; // mph
    final double a; // mph/second
    final double f; // tons
    final double p; // horsepower
    final double tp; // % of maximum power
    final double bp; // % of maximum brake

    private TrainMotion() {
        this.dt = 0;
        this.t = 0;
        this.x = 0;
        this.v = 0;
        this.a = 0;
        this.f = 0;
        this.p = 0;
        this.tp = 0;
        this.bp = 0;
    }

    public TrainMotion(double dt, double t, double x, double v, double a, double f, double p, double tp, double bp) {
        this.dt = dt;
        this.t = t;
        this.x = x;
        this.v = v;
        this.a = a;
        this.f = f;
        this.p = p;
        this.tp = tp;
        this.bp = bp;
    }

    static String getFormattedTime(double seconds) {
        return String.format("%02.0f:%02.0f:%02.0f", seconds / 3600, (seconds % 3600) / 60, (seconds % 60));
    }

    static String getMotionsHeader() {
        return String.format("%9s,%9s,%8s,%7s,%7s,%8s,%9s,%9s,%7s,", "∆time", "time", "miles", "mph", "mph/s", "force", "power", "throttle", "brake");
    }

    public String getMotionData() {
        return String.format("%9s,%9s,%8.4f,%7.3f,%7.3f,%7.2fT,%7.1fHP,%8.1f%%,%6.1f%%,",
                getFormattedTime(dt), getFormattedTime(t), x, v, a, f, p, tp, bp);
    }

    @Override
    public String toString() {
        return String.format("TrainMotion {\n\t%s\n\t%s\n}", getMotionsHeader(), getMotionData());
    }

}
