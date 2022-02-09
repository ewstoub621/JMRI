package jmri.jmrit.operations.rollingstock.cars;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * CarRevenue - POJO for revenue charges and pickup status
 * <p>
 * Version 1
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class CarRevenue implements Serializable, Comparable<CarRevenue> {
    static final long serialVersionUID = 1L;

    private final String carId;
    private String customerName;
    private String uniqueId;
    private String loadName;
    private Boolean pickup;
    private BigDecimal cancellationMulct = BigDecimal.ZERO;
    private BigDecimal demurrageCharges = BigDecimal.ZERO;
    private BigDecimal diversionMulct = BigDecimal.ZERO;
    private BigDecimal hazardFeeCharges = BigDecimal.ZERO;
    private BigDecimal switchingCharges = BigDecimal.ZERO;
    private BigDecimal transportCharges = BigDecimal.ZERO;

    public static String getDefaultEmptyName() {
        return Bundle.getMessage("EmptyCar");
    }

    public static String getDefaultLoadedName() {
        return Bundle.getMessage("LoadedCar");
    }

    public CarRevenue(String carId, String customerName, String loadName) {
        if (carId == null || customerName == null) {
            throw new IllegalArgumentException("Parameters carId and customer may not be null");
        }
        this.carId = carId;
        this.customerName = customerName;
        this.uniqueId = carId + customerName;
        this.loadName = loadName;
    }

    public String getUniqueId() { return uniqueId; }

    public String getLoadName() {
        return loadName;
    }

    public void setLoadName(String loadName) {
        this.loadName = loadName;
    }

    public BigDecimal getDemurrageCharges() {
        return demurrageCharges;
    }

    public void setDemurrageCharges(BigDecimal demurrageCharges) {
        this.demurrageCharges = demurrageCharges;
    }

    public BigDecimal getHazardFeeCharges() {
        return hazardFeeCharges;
    }

    public void setHazardFeeCharges(BigDecimal hazardFeeCharges) {
        this.hazardFeeCharges = hazardFeeCharges;
    }

    public BigDecimal getSwitchingCharges() {
        return switchingCharges;
    }

    public void setSwitchingCharges(BigDecimal switchingCharges) {
        this.switchingCharges = switchingCharges;
    }

    public BigDecimal getTransportCharges() {
        return transportCharges;
    }

    public void setTransportCharges(BigDecimal transportCharges) {
        this.transportCharges = transportCharges;
    }

    public BigDecimal getCancellationMulct() {
        return cancellationMulct;
    }

    public void setCancellationMulct(BigDecimal cancellationMulct) {
        this.cancellationMulct = cancellationMulct;
    }

    public BigDecimal getDiversionMulct() {
        return diversionMulct;
    }

    public void setDiversionMulct(BigDecimal diversionMulct) {
        this.diversionMulct = diversionMulct;
    }

    public String getCarId() {
        return carId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Boolean isPickup() {
        return pickup;
    }

    public void setPickup(Boolean pickup) {
        this.pickup = pickup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CarRevenue that = (CarRevenue) o;
        return carId.equals(that.carId) && customerName.equals(that.customerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carId, customerName);
    }

    @Override
    public int compareTo(CarRevenue that) {
        int compareCarId = this.getCarId().compareTo(that.getCarId());
        if (compareCarId != 0) {
            return compareCarId;
        }
        return this.getCustomerName().compareTo(that.getCustomerName());
    }

    @Override
    public String toString() {
        return "\nCarRevenue{" + "carId='" + carId + '\'' + ", customerName='" + customerName + '\'' +
                ", loadName='" + loadName + '\'' + ", pickup=" + pickup
                + ", \n\tcancellationMulct=" + cancellationMulct
                + ", \n\tdiversionMulct=" + diversionMulct
                + ", \n\tdemurrageCharges=" + demurrageCharges
                + ", \n\thazardFeeCharges=" + hazardFeeCharges
                + ", \n\tswitchingCharges=" + switchingCharges
                + ", \n\ttransportCharges=" + transportCharges + "}";
    }

}
