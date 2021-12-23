package jmri.jmrit.operations.trains;

import java.util.List;

public class TrainMotionParams {
    final double driverWeight;
    final double engineWeight;
    final double trainWeight;
    final double fullPower;
    final double gradePercent;
    final double speedLimit;
    final int carCount;
    final double distance;
    final boolean journal;
    final boolean warm;
    final List<Integer> carWeights;

    public TrainMotionParams(
            List<Integer> carWeights, double engineWeight, double driverWeight, double fullPower,
            double gradePercent, double speedLimit, double distance, boolean journal, boolean warm) {
        this.carWeights = carWeights;
        this.trainWeight = carWeights.stream().mapToInt(w -> w).sum();
        this.engineWeight = engineWeight;
        this.driverWeight = driverWeight;
        this.fullPower = fullPower;
        this.gradePercent = gradePercent;
        this.speedLimit = speedLimit;
        this.carCount = carWeights.size();
        this.distance = distance;
        this.journal = journal;
        this.warm = warm;
    }

    static String getMotionParamsHeader() {
        return String.format("%9s,%9s,%9s,%8s,%7s,%10s,%5s,%9s,%8s,%6s,","train", "engine", "drivers", "power", "grade", "maximum", "cars", "distance", "journal", "warm");
    }

    public String getMotionParamsData() {
        return String.format("%8.2fT,%8.2fT,%8.2fT,%6.0fHP,%6.2f%%,%6.2f MPH,%5s,%6.2f mi,%8s,%6s,",
                trainWeight, engineWeight, driverWeight, fullPower, gradePercent, speedLimit, carCount, distance, journal, warm);
    }

    @Override
    public String toString() {
        return String.format("TrainMotionParams {\n%s\n%s\n}", getMotionParamsHeader(), getMotionParamsData());
    }

}
