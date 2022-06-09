package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.cars.CarRevenue;
import jmri.jmrit.operations.setup.Setup;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

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
    static final int COL_FIRST = 0;
    static final int COL_FINAL = 7;
    static final int COL_COUNT = 8;

    static final int TOTAL_COL = 0;
    static final int SWITCHING = 1;
    static final int TRANSPORT = 2;
    static final int HAZARDFEE = 3;
    static final int DEMURRAGE = 4;
    static final int CANCELLED = 5;
    static final int DIVERSION = 6;
    static final int DISCOUNTS = 7;

    static final String OPS = "OPS";
    static final String RP = "RP";
    static final Object REV = "REV";
    static final Object RDR = "RDR";
    static final String RTT = "RTT";
    static final String OTT = "OTT";
    static final String MT = "\u200F\u200F\u200E \u200E"; // clever prefixed space for CSV import
    private final Map<String, Car> allCarsByCarId = new HashMap<>();
    private CSVPrinter printer;
    private Map<String, BigDecimal[]> carChargesMap;
    private Map<String, BigDecimal[]> customerChargesMap;
    private Train train;
    private Map<String, Set<CarRevenue>> carRevenuesByCustomer;
    private Map<String, BigDecimal> customerDiscountRateMap;
    private BigDecimal[] trainRevenueValues;
    private TrainRevenues trainRevenues;
    private String noAction;
    public TrainCsvRevenue(Train train) throws IOException {
        if (!Setup.isSaveTrainRevenuesEnabled() || train == null) {
            return;
        }
        setup(train);
        writeCsvRevenueFile();

        TrainRevenues.deleteTrainRevenuesSerFile(train);
    }

    private static String getCurrencyString(BigDecimal value) {
        if (isZero(value)) {
            return NONE;
        }
        Locale aDefault = Locale.getDefault();
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(aDefault);

        return numberFormat.format(value);
    }

    private static String getCurrencyString(String amount) {
        return getCurrencyString(BigDecimal.valueOf(Double.parseDouble(amount)));
    }

    private static boolean isZero(BigDecimal value) {
        if (value == null) {
            return true;
        } else {
            return value.compareTo(BigDecimal.ZERO) == 0;
        }
    }

    private String getActionString(CarRevenue carRevenue) {
        Boolean pickup = carRevenue.isPickup();

        return pickup == null ? getNoAction() : Bundle.getMessage(pickup ? "Pickup" : "SetOut");
    }

    private void setAllCarsByCarId() {
        for (Car car : new HashSet<>(InstanceManager.getDefault(CarManager.class).getList())) {
            allCarsByCarId.put(car.getId(), car);
        }
    }

    private String getCarDescription(CarRevenue carRevenue) {
        Car car = allCarsByCarId.get(carRevenue.getCarId());
        if (carRevenue.getLoadName() == null) {
            carRevenue.setLoadName(car.getLoadName());
        }

        return String.format("%-" + getNoAction().length() + "s : (%-5s) %-5s %-7s - %s", getActionString(carRevenue), carRevenue.getLoadName(), car.getRoadName(), car.getNumber(), car.getTypeName());
    }

    private void setCarRevenueSetByCustomer() {
        carRevenuesByCustomer = trainRevenues.getCarRevenueSetByCustomer();
    }

    private void setCustomerDiscountRateMap() {
        int maxCapacity = 1;
        Map<String, Integer> customerCapacityMap = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : trainRevenues.getSpurCapacityMapByCustomer().entrySet()) {
            String customer = e.getKey();
            int customerCapacity = 0;
            for (Integer spurLength : e.getValue().values()) {
                customerCapacity += spurLength;
            }
            customerCapacityMap.put(customer, customerCapacity);
            maxCapacity = Math.max(maxCapacity, customerCapacity);
        }

        BigDecimal numerator = BigDecimal.valueOf(Double.parseDouble(Setup.getMaxDiscount()));
        BigDecimal denominator = BigDecimal.valueOf(100).multiply(BigDecimal.valueOf(maxCapacity));
        BigDecimal multiplicand = numerator.divide(denominator, MathContext.DECIMAL128);

        Map<String, BigDecimal> customerDiscountRateMap1 = new HashMap<>();
        for (String customer : trainRevenues.getCarRevenueSetByCustomer().keySet()) {
            Integer capacity = customerCapacityMap.get(customer);
            if (capacity != null) {
                BigDecimal spurCapacity = BigDecimal.valueOf(capacity);
                BigDecimal value = multiplicand.multiply(spurCapacity);
                customerDiscountRateMap1.put(customer, value);
            }
        }

        customerDiscountRateMap = customerDiscountRateMap1;
    }

    private void setDefaultLocale() {
        if (Locale.ENGLISH.equals(Locale.getDefault())) {
            Locale.setDefault(Locale.US);
            javax.swing.JComponent.setDefaultLocale(Locale.US);
        }
    }

    private String getNoAction() {
        if (noAction == null) {
            int pickUpLen = Bundle.getMessage("Pickup").length();
            int setOutLen = Bundle.getMessage("SetOut").length();
            int max = Math.max(setOutLen, pickUpLen);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < max; i++) {
                sb.append('-');
            }
            noAction = sb.toString();
        }
        return noAction;
    }

    private String getIntegerString(BigDecimal value) {
        return isZero(value) ? NONE : NumberFormat.getIntegerInstance().format(value);
    }

    private String getPercentageString(BigDecimal value) {
        return isZero(value) ? NONE : getPercentFormat().format(value);
    }

    private NumberFormat getPercentFormat() {
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(2);
        percentFormat.setMinimumFractionDigits(2);

        return percentFormat;
    }

    private void setTrain(Train train) {
        this.train = train;
    }

    private void setTrainRevenues(Train train) {
        trainRevenues = train.getTrainRevenues();
    }

    private void addBigDecimalValues(BigDecimal[] customerValues, BigDecimal[] carValues) {
        for (int i = COL_FIRST; i <= COL_FINAL; i++) {
            customerValues[i] = customerValues[i].add(carValues[i]);
        }
    }

    private BigDecimal calcDiscount(BigDecimal[] carValues, BigDecimal discountRate) {
        BigDecimal discount = BigDecimal.ZERO;
        for (int i = SWITCHING; i < DEMURRAGE; i++) {
            discount = discount.add(carValues[i]);
        }

        return discount.multiply(discountRate);
    }

    private BigDecimal calcBigDecimalTotal(BigDecimal[] bigDecimals) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = SWITCHING; i < DISCOUNTS; i++) {
            total = total.add(bigDecimals[i]);
        }
        total = total.subtract(bigDecimals[DISCOUNTS]);

        return total;
    }

    private BigDecimal[] loadCarCharges(CarRevenue carRevenue, BigDecimal discountRate) {
        BigDecimal[] carCharges = new BigDecimal[COL_COUNT];

        carCharges[SWITCHING] = carRevenue.getSwitchingCharges();
        carCharges[TRANSPORT] = carRevenue.getTransportCharges();
        carCharges[HAZARDFEE] = carRevenue.getHazardFeeCharges();
        carCharges[DEMURRAGE] = carRevenue.getDemurrageCharges();
        carCharges[CANCELLED] = carRevenue.getCancellationMulct();
        carCharges[DIVERSION] = carRevenue.getDiversionMulct();
        carCharges[DISCOUNTS] = calcDiscount(carCharges, discountRate);
        carCharges[TOTAL_COL] = calcBigDecimalTotal(carCharges);

        return carCharges;
    }

    private void printBigDecimalValues(CSVPrinter printer, BigDecimal[] bigDecimals) throws IOException {
        for (int i = COL_FIRST; i <= COL_FINAL; i++) {
            printer.print(getCurrencyString(bigDecimals[i]));
        }
    }

    private void printHeaderBlock() throws IOException {
        printer.printRecord(Bundle.getMessage("csvOperator"), Bundle.getMessage("csvDescription"), Bundle.getMessage("csvParameters")); // NOI18N
        printer.printRecord(REV, Setup.getMessage("CostRevenueReport"), "Locale: " + Locale.getDefault().toString());

        printRailroadName(printer, train.getRailroadName().isEmpty() ? Setup.getRailroadName() : train.getRailroadName());
        printTrainName(printer, train.getName());
        printTrainRoute(printer, train.getRoute().getName());
        printTrainDescription(printer, train.getDescription());
        printValidity(printer, getDate(true));
        // train comment can have multiple lines
        if (!train.getComment().equals(NONE)) {
            for (String comment : train.getComment().split(NEW_LINE)) {
                printer.printRecord("TC", Bundle.getMessage("csvTrainComment"), comment); // NOI18N
            }
        }
        if (Setup.isPrintRouteCommentsEnabled()) {
            printer.printRecord("RC", Bundle.getMessage("csvRouteComment"), train.getRoute().getComment()); // NOI18N
        }
    }

    private void printCostParameterBlock() throws IOException {
        printer.printRecord(NONE);
        printer.printRecord(REV, Setup.getMessage("CostParamTitle"), Setup.getMessage("CustomValue"));
    }

    private void printRevenueParameterBlock() throws IOException {
        // TODO EWS expand to include cost parameters TBD and separate rev / cost print methods
        printer.printRecord(NONE);
        printer.printRecord(REV, Setup.getMessage("RevenueParamTitle"), Setup.getMessage("CustomValue"));
        printer.printRecord(NONE, Setup.getMessage("SwitchingTitle"));
        printer.printRecord(RP, " - " + Setup.getMessage("SwitchingEmpty"), getCurrencyString(Setup.getSwitchEmpty()));
        printer.printRecord(RP, " - " + Setup.getMessage("SwitchingLoads"), getCurrencyString(Setup.getSwitchLoads()));
        printer.printRecord(RP, " - " + Setup.getMessage("SwitchingAggrs"), getCurrencyString(Setup.getSwitchAggrs()));
        printer.printRecord(RP, " - " + Setup.getMessage("SwitchingGrain"), getCurrencyString(Setup.getSwitchGrain()));
        printer.printRecord(RP, " - " + Setup.getMessage("SwitchingMetal"), getCurrencyString(Setup.getSwitchMetal()));
        printer.printRecord(RP, " - " + Setup.getMessage("SwitchingWoody"), getCurrencyString(Setup.getSwitchWoody()));
        printer.printRecord(RP, " - " + Setup.getMessage("SwitchingTanks"), getCurrencyString(Setup.getSwitchTanks()));
        printer.printRecord(NONE, Setup.getMessage("MulctTitle"));
        printer.printRecord(RP, " - " + Setup.getMessage("CancelledMulct"), getCurrencyString(Setup.getCancelMulct()));
        printer.printRecord(RP, " - " + Setup.getMessage("DiversionMulct"), getCurrencyString(Setup.getDivertMulct()));
        printer.printRecord(RP, Setup.getMessage("HazardTariff"), getCurrencyString(Setup.getHazardFee()));
        printer.printRecord(NONE, Setup.getMessage("DemurrageTitle"));
        printer.printRecord(RP, " - " + Setup.getMessage("DemurrageRR"), getCurrencyString(Setup.getDemurrageRR()));
        printer.printRecord(RP, " - " + Setup.getMessage("DemurrageXX"), getCurrencyString(Setup.getDemurrageXX()));
        printer.printRecord(RP, Setup.getMessage("DemurrageCredits"), Setup.getDemurCredits());
        printer.printRecord(RP, Setup.getMessage("MaximumDiscount"), Setup.getMaxDiscount() + "%");
    }

    private void printRevenueDetailByCarValues() throws IOException {
        printRevenueDetailHeader("Car", "Customer");
/*
        printRevenueDetailSubHeader(printer, "ByCar", "ForCustomer");
*/
        for (Map.Entry<String, Set<CarRevenue>> e : trainRevenues.getCarRevenueSetByCustomer().entrySet()) {

            String customer = e.getKey();
            Map<String, CarRevenue> printMap = new TreeMap<>();
            for (CarRevenue carRevenue : e.getValue()) {
                printMap.put(getCarDescription(carRevenue), carRevenue);
            }

            for (Map.Entry<String, CarRevenue> kv : printMap.entrySet()) {
                String description = kv.getKey();
                CarRevenue carRevenue = kv.getValue();
                printer.print(RDR);
                printer.print(description);
                printer.print(customer);
                printBigDecimalValues(printer, carChargesMap.get(carRevenue.getUniqueId()));
                printer.println();
            }
        }
    }

    private void printRevenueDetailByCustomerValues() throws IOException {
        printer.printRecord(NONE);
        printRevenueDetailHeader("Customer", "Discount");
        for (Map.Entry<String, Set<CarRevenue>> e : carRevenuesByCustomer.entrySet()) {
            String customerName = e.getKey();
            BigDecimal customerDiscountRate = customerDiscountRateMap.get(customerName);
            if (customerDiscountRate == null) {
                customerDiscountRate = BigDecimal.ZERO;
            }

            printer.print(RDR);
            printer.print(customerName);
            printer.print(getPercentageString(customerDiscountRate));
            printBigDecimalValues(printer, customerChargesMap.get(customerName));
            printer.println();
        }
    }

    private void printRevenueDetailForTrainValues() throws IOException {
        printRevenueDetailHeader("ByTrain", "RouteRate");
        printer.print(RTT);
        printer.print(train.getDescription());
        printer.print(getCurrencyString(trainRevenues.getMaxRouteTransportFee()));
        printBigDecimalValues(printer, trainRevenueValues);
    }

    private void printCostDetailBlock() throws IOException {
        double revenueTotal = trainRevenueValues[TOTAL_COL].doubleValue();
        double routeTotalCost = trainRevenues.getRouteTotalCost().doubleValue();
        double netProfit = revenueTotal - routeTotalCost;
        double opsRatio = routeTotalCost / revenueTotal;

        String message = Setup.getMessage("OpsRatio") + ": " + getPercentageString(BigDecimal.valueOf(opsRatio));
        int blanks = Setup.getMessage("OpsReport").length() - 1 - message.length();
        String pad = String.format("%" + blanks + "s", " ");
        String opsRatioSubtitle = MT + pad + message;

        printer.print(OTT);
        printer.print(opsRatioSubtitle);
        printer.print(getCurrencyString(BigDecimal.valueOf(revenueTotal)));
        printer.print(getCurrencyString(trainRevenues.getRouteTotalCost()));
        printer.print(getCurrencyString(trainRevenues.getRouteOverheadCost()));
        printer.print(getCurrencyString(trainRevenues.getRouteLaborCost()));
        printer.print(getCurrencyString(trainRevenues.getRouteMoeCost()));
        printer.print(getCurrencyString(trainRevenues.getRouteMowCost()));
        printer.print(getCurrencyString(BigDecimal.valueOf(netProfit)));
        printer.print(getCurrencyString(trainRevenues.getRouteFuelCost()));
        printer.print(getIntegerString(trainRevenues.getRouteTonMiles()));
        printer.println();
    }

    private void printCostDetailHeader() throws IOException {
        printer.printRecord(NONE);
        printer.print(OPS);
        printer.print(Setup.getMessage("OpsReport"));
        printer.print(Setup.getMessage("TotalRevenue"));
        printer.print(Setup.getMessage("TotalCost"));
        printer.print(Setup.getMessage("NetProfit"));
        printer.print(Setup.getMessage("Labor"));
        printer.print(Setup.getMessage("MOE"));
        printer.print(Setup.getMessage("MOW"));
        printer.print(Setup.getMessage("Overhead"));
        printer.print(Setup.getMessage("Fuel"));
        printer.print(Setup.getMessage("TonMiles"));
        printer.println();
    }

    private void printRevenueDetailHeader(String topic, String subTopic) throws IOException {
        printer.printRecord(NONE);
        printer.print(REV);
        printer.print(Setup.getMessage("RevenueDetail") + ": " + Setup.getMessage(topic));
        printer.print(Setup.getMessage(subTopic));
        printer.print(Setup.getMessage("Total"));
        printer.print(Setup.getMessage("Switching"));
        printer.print(Setup.getMessage("Transport"));
        printer.print(Setup.getMessage("Hazard"));
        printer.print(Setup.getMessage("Demurrage"));
        printer.print(Setup.getMessage("Cancellation"));
        printer.print(Setup.getMessage("Diversion"));
        printer.print(Setup.getMessage("Discount"));
        printer.println();
    }

    private void setup(Train train) throws IOException {
        File csvFile = InstanceManager.getDefault(TrainManagerXml.class).createTrainCsvRevenueFile(train);
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8));
        CSVFormat format = CSVFormat.Builder.create().setIgnoreSurroundingSpaces(false).build();
        printer = new CSVPrinter(bufferedWriter, format);

        setTrain(train);
        setTrainRevenues(train);
        setCarRevenueSetByCustomer();
        setCustomerDiscountRateMap();
        setAllCarsByCarId();
        setDefaultLocale();

        loadCarCharges();
        loadCustomerCharges();
        loadTrainCharges();
    }

    private void writeCsvRevenueFile() throws IOException {
        printHeaderBlock();
        printCostDetailHeader();
        printCostDetailBlock();
        printRevenueDetailForTrainValues();
        printRevenueDetailByCustomerValues();
        printRevenueDetailByCarValues();
        printCostParameterBlock();
        printRevenueParameterBlock();
        printer.flush();
    }

    private void loadCarCharges() {
        carChargesMap = new HashMap<>();
        for (Map.Entry<String, Set<CarRevenue>> e : trainRevenues.getCarRevenueSetByCustomer().entrySet()) {
            String customer = e.getKey();
            BigDecimal discountRate = customerDiscountRateMap.get(customer);
            if (discountRate == null) {
                discountRate = BigDecimal.ZERO;
            }
            for (CarRevenue carRevenue : e.getValue()) {
                carChargesMap.put(carRevenue.getUniqueId(), loadCarCharges(carRevenue, discountRate));
            }
        }
    }

    private void loadCustomerCharges() {
        customerChargesMap = new HashMap<>();
        for (Map.Entry<String, Set<CarRevenue>> e : trainRevenues.getCarRevenueSetByCustomer().entrySet()) {
            String customer = e.getKey();

            BigDecimal[] customerCharges = new BigDecimal[COL_COUNT];
            for (int i = COL_FIRST; i <= COL_FINAL; i++) {
                customerCharges[i] = BigDecimal.ZERO;
            }

            for (CarRevenue carRevenue : e.getValue()) {
                addBigDecimalValues(customerCharges, carChargesMap.get(carRevenue.getUniqueId()));
            }
            customerChargesMap.put(customer, customerCharges);
        }
    }

    private void loadTrainCharges() {
        trainRevenueValues = new BigDecimal[COL_COUNT];
        for (int i = COL_FIRST; i <= COL_FINAL; i++) {
            trainRevenueValues[i] = BigDecimal.ZERO;
        }

        for (BigDecimal[] values : customerChargesMap.values()) {
            addBigDecimalValues(trainRevenueValues, values);
        }
    }

}
