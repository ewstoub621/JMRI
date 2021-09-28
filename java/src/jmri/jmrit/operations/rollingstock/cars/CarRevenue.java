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

    private final String carKey;
    private String customerName;
    private String loadName;
    private Boolean pickup;
    private BigDecimal cancellationMulct = BigDecimal.ZERO;
    private BigDecimal demurrageCharges = BigDecimal.ZERO;
    private BigDecimal diversionMulct = BigDecimal.ZERO;
    private BigDecimal hazardFeeCharges = BigDecimal.ZERO;
    private BigDecimal switchingCharges = BigDecimal.ZERO;
    private BigDecimal transportCharges = BigDecimal.ZERO;

    public CarRevenue(String carKey, String customerName) {
        if (carKey == null || customerName == null) {
            throw new IllegalArgumentException("Parameters carKey and customer may not be null");
        }
        this.carKey = carKey;
        this.customerName = customerName;
    }

    public String getLoadName() {
        return loadName;
    }

    public void setLoadName(String loadName) {
        this.loadName = loadName;
    }

    public void addTransportCharges(BigDecimal transportCharges) {
        this.transportCharges = this.transportCharges.add(transportCharges);
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

    public String getCarKey() {
        return carKey;
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
        return carKey.equals(that.carKey) && customerName.equals(that.customerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carKey, customerName);
    }

    @Override
    public int compareTo(CarRevenue that) {
        int compareCarKey = this.getCarKey().compareTo(that.getCarKey());
        if (compareCarKey != 0) {
            return compareCarKey;
        }
        return this.getCustomerName().compareTo(that.getCustomerName());
    }

}
