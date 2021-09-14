package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.cars.CarRevenue;
import jmri.jmrit.operations.setup.Setup;
import jmri.jmrit.operations.setup.TrainRevenues;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;

import static jmri.jmrit.operations.trains.Train.NONE;

/**
 * TrainCsvRevenue builds a train's revenue report as a (csv) file from a Train and its TrainRevenues POJO
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainCsvRevenue extends TrainCsvCommon {
    public static final String RDR = "RDR";

    public static final int SWITCHING = 0;
    public static final int TRANSPORT = 1;
    public static final int HAZARDFEE = 2;
    public static final int DISCOUNTS = 3;
    public static final int DEMURRAGE = 4;
    public static final int CANCELLED = 5;
    public static final int DIVERSION = 6;
    private final Map<String, Car> allCarsByCarKey = new HashMap<>();
    private Train train;
    private Map<String, BigDecimal> customerDiscountRateMap;
    private Map<String, Set<CarRevenue>> carRevenuesByCustomer;
    private boolean[] use;
    private BigDecimal[] revenueValues;
    private TrainRevenues trainRevenues;

    public TrainCsvRevenue(Train train) {
        if (!Setup.isSaveTrainRevenuesEnabled() || train == null) {
            return;
        }
        setup(train);

        // create comma separated value revenue file
        File file = InstanceManager.getDefault(TrainManagerXml.class).createTrainCsvRevenueFile(train);
        try (CSVPrinter fileOut = new CSVPrinter(new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)), CSVFormat.DEFAULT)) {
            printHeaderBlock(fileOut);
            printParameterBlock(fileOut);

            printRevenueDetailHeader(fileOut, "ByCar", "ForCustomer");
            printRevenueDetailByCarValues(fileOut);

            printRevenueDetailHeader(fileOut, "ByCustomer", "DiscountRate");
            printRevenueDetailByCustomerValues(fileOut);

            printRevenueDetailHeader(fileOut, "ByTrain", "RouteRate");
            printRevenueDetailForTrainValues(fileOut);

            fileOut.flush();
        } catch (IOException e) {
            log.error("Can not open CSV revenue file: {}", file.getName());
        }
        trainRevenues.deleteTrainRevenuesSerFile(train);
    }

    private void setup(Train train) {
        this.train = train;
        trainRevenues = train.getTrainRevenues();
        carRevenuesByCustomer = trainRevenues.getCarRevenuesByCustomer();
        customerDiscountRateMap = getCustomerDiscountRateMap();
        for (Car car : new HashSet<>(InstanceManager.getDefault(CarManager.class).getList())) {
            allCarsByCarKey.put(car.toString(), car);
        }
        use = new boolean[7];
        for (CarRevenue carRevenue : trainRevenues.getCarRevenues()) {
            BigDecimal customerDiscountRate = customerDiscountRateMap.get(carRevenue.getCustomerName());

            if (isPositive(carRevenue.getSwitchingCharges())) {
                use[SWITCHING] = true;
            }
            if (isPositive(carRevenue.getTransportCharges())) {
                use[TRANSPORT] = true;
            }
            if (isPositive(carRevenue.getHazardFeeCharges())) {
                use[HAZARDFEE] = true;
            }
            if (isPositive(carRevenue.getDemurrageCharges())) {
                use[DEMURRAGE] = true;
            }
            if (isPositive(customerDiscountRate)) {
                use[DISCOUNTS] = true;
            }
            if (isPositive(carRevenue.getCancellationMulct())) {
                use[CANCELLED] = true;
            }
            if (isPositive(carRevenue.getDiversionMulct())) {
                use[DIVERSION] = true;
            }
        }
        revenueValues = loadBigDecimalZeroValues();
        if (Locale.ENGLISH.equals(Locale.getDefault())) {
            Locale.setDefault(Locale.US);
            javax.swing.JComponent.setDefaultLocale(Locale.US);
        }
    }

    private String getActionString(CarRevenue carRevenue) {
        Boolean pickup = carRevenue.isPickup();

        return Bundle.getMessage(pickup == null ? "csvNoWork" : pickup ? "Pickup" : "SetOut");
    }

    private String getCarDescription(Map<String, Car> allCarsByCarKey, CarRevenue carRevenue) {
        Car car = allCarsByCarKey.get(carRevenue.getCarKey());
        return String.format("%-7s: %-7s %-5s - %s(%s)",
                             getActionString(carRevenue),
                             car.getNumber(),
                             car.getRoadName(),
                             car.getTypeName(),
                             car.getLoadName()
        );
    }

    private Map<String, BigDecimal> getCustomerDiscountRateMap() {
        int maxCapacity = 1;
        Map<String, Integer> customerCapacityMap = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : trainRevenues.getSpurCapacityByCustomer().entrySet()) {
            String customer = e.getKey();
            int customerCapacity = 0;
            Map<String, Integer> trackIdLengthMap = e.getValue();
            for (Integer spurLength : trackIdLengthMap.values()) {
                customerCapacity += spurLength;
            }
            customerCapacityMap.put(customer, customerCapacity);
            maxCapacity = Math.max(maxCapacity, customerCapacity);
        }

        BigDecimal numerator = BigDecimal.valueOf(Double.parseDouble(Setup.getMaxDiscount()));
        BigDecimal denominator = BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(maxCapacity));
        BigDecimal multiplicand = numerator.divide(denominator, MathContext.DECIMAL128);

        Map<String, BigDecimal> customerDiscountRateMap = new HashMap<>();
        for (CarRevenue carRevenue : trainRevenues.getCarRevenues()) {
            String customer = carRevenue.getCustomerName();
            Integer capacity = customerCapacityMap.get(customer);
            if (capacity != null) {
                BigDecimal spurCapacity = BigDecimal.valueOf(capacity);
                BigDecimal value = multiplicand.multiply(spurCapacity);
                customerDiscountRateMap.put(carRevenue.getCustomerName(), value);
            }
        }

        return customerDiscountRateMap;
    }

    private Object getCurrencyString(String amount) {
        return getCurrencyString(BigDecimal.valueOf(Double.parseDouble(amount)));
    }

    private String getCurrencyString(BigDecimal value) {
        if (!isPositive(value)) {
            return NONE;
        }
        Locale aDefault = Locale.getDefault();
        log.debug("getCurrencyString({}): default locale {}", value, aDefault);
        return NumberFormat.getCurrencyInstance(aDefault).format(value);
    }

    private boolean isPositive(BigDecimal value) {
        if (value == null) {
            return false;
        } else {
            return value.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    private String getPercentageString(BigDecimal value) {
        return isPositive(value) ? NONE : getPercentFormat().format(value);
    }

    private NumberFormat getPercentFormat() {
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(HAZARDFEE);
        percentFormat.setMinimumFractionDigits(HAZARDFEE);

        return percentFormat;
    }

    private void printHeaderBlock(CSVPrinter fileOut) throws IOException {
        fileOut.printRecord(
                Bundle.getMessage("csvOperator"),
                Bundle.getMessage("csvDescription"),
                Bundle.getMessage("csvParameters")
        ); // NOI18N
        fileOut.printRecord(
                Bundle.getMessage("Report"),
                Setup.getMessage("RevenueReport"),
                Locale.getDefault().toString()
        );

        printRailroadName(
                fileOut,
                train.getRailroadName().isEmpty() ? Setup.getRailroadName() : train.getRailroadName()
        );
        printTrainName(fileOut, train.getName());
        printTrainDescription(fileOut, train.getDescription());
        printValidity(fileOut, getDate(true));
        // train comment can have multiple lines
        if (!train.getComment().equals(NONE)) {
            String[] comments = train.getComment().split(NEW_LINE);
            for (String comment : comments) {
                fileOut.printRecord("TC", Bundle.getMessage("csvTrainComment"), comment); // NOI18N
            }
        }
        if (Setup.isPrintRouteCommentsEnabled()) {
            fileOut.printRecord("RC", Bundle.getMessage("csvRouteComment"), train.getRoute().getComment()); // NOI18N
        }
    }

    private void printParameterBlock(CSVPrinter fileOut) throws IOException {
        fileOut.printRecord(NONE);
        fileOut.printRecord(Setup.getMessage("RevenueParameters"));
        fileOut.printRecord(NONE, Setup.getMessage("ParameterDescription"), Setup.getMessage("ParameterValue"));
        fileOut.printRecord(NONE, Setup.getMessage("DiscountTitle"));
        fileOut.printRecord("RP", " - " + Setup.getMessage("MaximumDiscount"), Setup.getMaxDiscount() + "%");
        fileOut.printRecord(NONE, Setup.getMessage("SwitchingTitle"));
        fileOut.printRecord("RP", " - " + Setup.getMessage("SwitchingEmpty"),
                            getCurrencyString(Setup.getSwitchEmpty())
        );
        fileOut.printRecord("RP", " - " + Setup.getMessage("SwitchingLoads"),
                            getCurrencyString(Setup.getSwitchLoads())
        );
        fileOut.printRecord("RP", " - " + Setup.getMessage("SwitchingAggrs"),
                            getCurrencyString(Setup.getSwitchAggrs())
        );
        fileOut.printRecord("RP", " - " + Setup.getMessage("SwitchingGrain"),
                            getCurrencyString(Setup.getSwitchGrain())
        );
        fileOut.printRecord("RP", " - " + Setup.getMessage("SwitchingMetal"),
                            getCurrencyString(Setup.getSwitchMetal())
        );
        fileOut.printRecord("RP", " - " + Setup.getMessage("SwitchingWoody"),
                            getCurrencyString(Setup.getSwitchWoody())
        );
        fileOut.printRecord("RP", " - " + Setup.getMessage("SwitchingTanks"),
                            getCurrencyString(Setup.getSwitchTanks())
        );
        fileOut.printRecord(NONE, Setup.getMessage("MulctTitle"));
        fileOut.printRecord("RP", " - " + Setup.getMessage("CancelledMulct"),
                            getCurrencyString(Setup.getCancelMulct())
        );
        fileOut.printRecord("RP", " - " + Setup.getMessage("DiversionMulct"),
                            getCurrencyString(Setup.getDivertMulct())
        );
        fileOut.printRecord(NONE, Setup.getMessage("DemurrageTitle"));
        fileOut.printRecord("RP", " - " + Setup.getMessage("DemurrageRR"),
                            getCurrencyString(Setup.getDemurrageRR()));
        fileOut.printRecord("RP", " - " + Setup.getMessage("DemurrageXX"),
                            getCurrencyString(Setup.getDemurrageXX()));
        fileOut.printRecord("RP", " - " + Setup.getMessage("DemurrageCredits"), Setup.getDemurCredits());
        fileOut.printRecord(NONE, Setup.getMessage("HandlingTitle"));
        fileOut.printRecord("RP", " - " + Setup.getMessage("AddedHazardFee"),
                            getCurrencyString(Setup.getHazardFee()));
    }

    private void printRevenueDetailByCarValues(CSVPrinter fileOut) throws IOException {
        for (Map.Entry<String, Set<CarRevenue>> e : carRevenuesByCustomer.entrySet()) {
            String customer = e.getKey();
            BigDecimal discountRate = customerDiscountRateMap.get(customer);
            if (discountRate == null) {
                discountRate = BigDecimal.ZERO;
            }

            Set<CarRevenue> carRevenues = e.getValue();
            for (CarRevenue carRevenue : carRevenues) {
                fileOut.print(RDR);
                fileOut.print(getCarDescription(allCarsByCarKey, carRevenue));
                fileOut.print(customer);
                printBigDecimalValues(fileOut, loadCarCharges(carRevenue, discountRate));
                fileOut.print(getCurrencyString(calcBigDecimalTotal(loadCarCharges(carRevenue, discountRate))));
                fileOut.println();
            }
        }
    }

    private void printRevenueDetailByCustomerValues(CSVPrinter fileOut) throws IOException {
        for (Map.Entry<String, Set<CarRevenue>> e : carRevenuesByCustomer.entrySet()) {
            String customerName = e.getKey();
            BigDecimal customerDiscountRate = customerDiscountRateMap.get(customerName);
            if (customerDiscountRate == null) {
                customerDiscountRate = BigDecimal.ZERO;
            }
            BigDecimal[] customerCharges = loadBigDecimalZeroValues();

            for (CarRevenue carRevenue : e.getValue()) {
                addBigDecimalValues(customerCharges, loadCarCharges(carRevenue, customerDiscountRate));
            }

            fileOut.print(RDR);
            fileOut.print(customerName);
            fileOut.print(getPercentageString(customerDiscountRate));
            printBigDecimalValues(fileOut, customerCharges);
            fileOut.print(getCurrencyString(calcBigDecimalTotal(customerCharges)));
            fileOut.println();

            addBigDecimalValues(revenueValues, customerCharges);
        }
    }

    private void printRevenueDetailForTrainValues(CSVPrinter fileOut) throws IOException {
        fileOut.print(RDR);
        fileOut.print(train.getName() + " - " + train.getDescription());
        fileOut.print(getCurrencyString(trainRevenues.getMaxRouteTransportFee()));
        printBigDecimalValues(fileOut, revenueValues);
        fileOut.print(getCurrencyString(calcBigDecimalTotal(revenueValues)));
        fileOut.println();
    }

    private void printBigDecimalValues(CSVPrinter fileOut, BigDecimal[] bigDecimals) throws IOException {
        for (int i = 0; i < use.length; i++) {
            if (use[i]) {
                fileOut.print(getCurrencyString(bigDecimals[i]));
            }
        }
    }

    private BigDecimal[] loadCarCharges(CarRevenue carRevenue, BigDecimal discountRate) {
        BigDecimal[] carCharges = new BigDecimal[use.length];
        for (int i = 0; i < use.length; i++) {
            switch (i) {
            case SWITCHING:
                carCharges[i] = carRevenue.getSwitchingCharges();
                break;
            case TRANSPORT:
                carCharges[i] = carRevenue.getTransportCharges();
                break;
            case HAZARDFEE:
                carCharges[i] = carRevenue.getHazardFeeCharges();
                break;
            case DEMURRAGE:
                carCharges[i] = carRevenue.getDemurrageCharges();
                break;
            case DISCOUNTS:
                carCharges[i] = calcDiscount(carCharges, discountRate);
                break;
            case CANCELLED:
                carCharges[i] = carRevenue.getCancellationMulct();
                break;
            case DIVERSION:
                carCharges[i] = carRevenue.getDiversionMulct();
                break;
            }
        }
        return carCharges;
    }

    private BigDecimal[] loadBigDecimalZeroValues() {
        BigDecimal[] bigDecimals = new BigDecimal[use.length];
        for (int i = 0; i < use.length; i++) {
            bigDecimals[i] = BigDecimal.ZERO;
        }
        return bigDecimals;
    }

    private void addBigDecimalValues(BigDecimal[] customerValues, BigDecimal[] carValues) {
        for (int i = 0; i < use.length; i++) {
            customerValues[i] = customerValues[i].add(carValues[i]);
        }
    }

    private BigDecimal calcDiscount(BigDecimal[] carValues, BigDecimal discountRate) {
        BigDecimal discount = BigDecimal.ZERO;
        for (int i = SWITCHING; i < DISCOUNTS; i++) {
            discount = discount.add(carValues[i]);
        }
        return discountRate.multiply(discount);
    }

    private BigDecimal calcBigDecimalTotal(BigDecimal[] bigDecimals) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < use.length; i++) {
            total = total.add(bigDecimals[i]);
        }
        return total;
    }

    private void printRevenueDetailHeader(CSVPrinter fileOut, String col2, String col3) throws IOException {
        fileOut.printRecord(NONE);
        fileOut.print(Setup.getMessage("Revenue"));
        fileOut.print(Setup.getMessage(col2));
        fileOut.print(Setup.getMessage(col3));
        if (use[SWITCHING]) {
            fileOut.print(Setup.getMessage("Switching"));
        }
        if (use[TRANSPORT]) {
            fileOut.print(Setup.getMessage("Transport"));
        }
        if (use[HAZARDFEE]) {
            fileOut.print(Setup.getMessage("HazardFee"));
        }
        if (use[DEMURRAGE]) {
            fileOut.print(Setup.getMessage("Demurrage"));
        }
        if (use[DISCOUNTS]) {
            fileOut.print(Setup.getMessage("Discount"));
        }
        if (use[CANCELLED]) {
            fileOut.print(Setup.getMessage("Cancelled"));
        }
        if (use[DIVERSION]) {
            fileOut.print(Setup.getMessage("Diverted"));
        }
        fileOut.print(Setup.getMessage("Total"));
        fileOut.println();
    }

    private final static Logger log = LoggerFactory.getLogger(TrainCsvRevenue.class);
}
