package jmri.jmrit.operations.trains;

import jmri.InstanceManager;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.cars.CarManager;
import jmri.jmrit.operations.rollingstock.cars.CarRevenue;
import jmri.jmrit.operations.setup.Setup;
import jmri.jmrit.operations.setup.TrainRevenues;
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
    private static final int SWITCHING = 0;
    private static final int TRANSPORT = 1;
    private static final int HAZARDFEE = 2;
    private static final int DEMURRAGE = 3;
    private static final int CANCELLED = 4;
    private static final int DIVERSION = 5;
    private static final int DISCOUNTS = 6;
    private static final int TOTAL_COL = 7;

    private static final String RP = "RP";
    private static final Object REV = "REV";
    private static final Object RDR = "RDR";

    private final Map<String, Car> allCarsByCarId = new HashMap<>();
    private Train train;
    private Map<String, BigDecimal> customerDiscountRateMap;
    private Map<String, Set<CarRevenue>> carRevenuesByCustomer;
    private BigDecimal[] revenueValues;
    private TrainRevenues trainRevenues;
    private String noAction;

    public TrainCsvRevenue(Train train) throws IOException {
        if (!Setup.isSaveTrainRevenuesEnabled() || train == null) {
            return;
        }
        setup(train);
        writeCsvRevenueFile(train);
        TrainRevenues.deleteTrainRevenuesSerFile(train);
    }

    private void addBigDecimalValues(BigDecimal[] customerValues, BigDecimal[] carValues) {
        for (int i = SWITCHING; i <= TOTAL_COL; i++) {
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
        for (int i = SWITCHING; i < TOTAL_COL; i++) {
            if (i == DISCOUNTS) {
                total = total.subtract(bigDecimals[i]);
            } else {
                total = total.add(bigDecimals[i]);
            }
        }

        return total;
    }

    private static String getCurrencyString(BigDecimal value) {
        if (!isPositive(value)) {
            return NONE;
        }
        Locale aDefault = Locale.getDefault();
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(aDefault);

        return numberFormat.format(value);
    }

    private static String getCurrencyString(String amount) {
        return getCurrencyString(BigDecimal.valueOf(Double.parseDouble(amount)));
    }

    private static boolean isPositive(BigDecimal value) {
        if (value == null) {
            return false;
        } else {
            return value.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    private String getActionString(CarRevenue carRevenue) {
        Boolean pickup = carRevenue.isPickup();

        return pickup == null
                ? getNoAction()
                : Bundle.getMessage(pickup ? "Pickup" : "SetOut");
    }

    private String getCarDescription(CarRevenue carRevenue) {
        Car car = allCarsByCarId.get(carRevenue.getCarId());
        if (carRevenue.getLoadName() == null) {
            carRevenue.setLoadName(car.getLoadName());
        }

        return String.format("%-" + getNoAction().length() + "s : (%-5s) %-5s %-7s - %s",
                getActionString(carRevenue),
                carRevenue.getLoadName(),
                car.getRoadName(),
                car.getNumber(),
                car.getTypeName()
        );
    }

    private Map<String, BigDecimal> getCustomerDiscountRateMap() {
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

        Map<String, BigDecimal> customerDiscountRateMap = new HashMap<>();
        for (String customer : trainRevenues.getCarRevenueSetByCustomer().keySet()) {
            Integer capacity = customerCapacityMap.get(customer);
            if (capacity != null) {
                BigDecimal spurCapacity = BigDecimal.valueOf(capacity);
                BigDecimal value = multiplicand.multiply(spurCapacity);
                customerDiscountRateMap.put(customer, value);
            }
        }

        return customerDiscountRateMap;
    }

    private NumberFormat getPercentFormat() {
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(2);
        percentFormat.setMinimumFractionDigits(2);

        return percentFormat;
    }

    private String getPercentageString(BigDecimal value) {
        return isPositive(value) ? getPercentFormat().format(value) : NONE;
    }

    private BigDecimal[] loadBigDecimalZeroValues() {
        BigDecimal[] bigDecimals = new BigDecimal[TOTAL_COL + 1];
        for (int i = SWITCHING; i <= TOTAL_COL; i++) {
            bigDecimals[i] = BigDecimal.ZERO;
        }
        return bigDecimals;
    }

    private BigDecimal[] loadCarCharges(CarRevenue carRevenue, BigDecimal discountRate) {
        BigDecimal[] carCharges = new BigDecimal[TOTAL_COL + 1];

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

    private void printBigDecimalValues(CSVPrinter fileOut, BigDecimal[] bigDecimals) throws IOException {
        for (int i = SWITCHING; i <= TOTAL_COL; i++) {
            fileOut.print(getCurrencyString(bigDecimals[i]));
        }
    }

    private void printHeaderBlock(CSVPrinter fileOut) throws IOException {
        fileOut.printRecord(Bundle.getMessage("csvOperator"), Bundle.getMessage("csvDescription"), Bundle.getMessage("csvParameters")); // NOI18N
        fileOut.printRecord(REV, Setup.getMessage("RevenueReport"), Locale.getDefault().toString());

        printRailroadName(fileOut, train.getRailroadName().isEmpty() ? Setup.getRailroadName() : train.getRailroadName());
        printTrainName(fileOut, train.getName());
        printTrainDescription(fileOut, train.getDescription());
        printValidity(fileOut, getDate(true));
        // train comment can have multiple lines
        if (!train.getComment().equals(NONE)) {
            for (String comment : train.getComment().split(NEW_LINE)) {
                fileOut.printRecord("TC", Bundle.getMessage("csvTrainComment"), comment); // NOI18N
            }
        }
        if (Setup.isPrintRouteCommentsEnabled()) {
            fileOut.printRecord("RC", Bundle.getMessage("csvRouteComment"), train.getRoute().getComment()); // NOI18N
        }
    }

    private void printParameterBlock(CSVPrinter fileOut) throws IOException {
        fileOut.printRecord(NONE);
        fileOut.printRecord(REV, Setup.getMessage("ParameterDescription"), Setup.getMessage("ParameterValue"));
        fileOut.printRecord(NONE, Setup.getMessage("DiscountTitle"));
        fileOut.printRecord(RP, " - " + Setup.getMessage("MaximumDiscount"), Setup.getMaxDiscount() + "%");
        fileOut.printRecord(NONE, Setup.getMessage("SwitchingTitle"));
        fileOut.printRecord(RP, " - " + Setup.getMessage("SwitchingEmpty"), getCurrencyString(Setup.getSwitchEmpty()));
        fileOut.printRecord(RP, " - " + Setup.getMessage("SwitchingLoads"), getCurrencyString(Setup.getSwitchLoads()));
        fileOut.printRecord(RP, " - " + Setup.getMessage("SwitchingAggrs"), getCurrencyString(Setup.getSwitchAggrs()));
        fileOut.printRecord(RP, " - " + Setup.getMessage("SwitchingGrain"), getCurrencyString(Setup.getSwitchGrain()));
        fileOut.printRecord(RP, " - " + Setup.getMessage("SwitchingMetal"), getCurrencyString(Setup.getSwitchMetal()));
        fileOut.printRecord(RP, " - " + Setup.getMessage("SwitchingWoody"), getCurrencyString(Setup.getSwitchWoody()));
        fileOut.printRecord(RP, " - " + Setup.getMessage("SwitchingTanks"), getCurrencyString(Setup.getSwitchTanks()));
        fileOut.printRecord(NONE, Setup.getMessage("MulctTitle"));
        fileOut.printRecord(RP, " - " + Setup.getMessage("CancelledMulct"), getCurrencyString(Setup.getCancelMulct()));
        fileOut.printRecord(RP, " - " + Setup.getMessage("DiversionMulct"), getCurrencyString(Setup.getDivertMulct()));
        fileOut.printRecord(NONE, Setup.getMessage("DemurrageTitle"));
        fileOut.printRecord(RP, " - " + Setup.getMessage("DemurrageRR"), getCurrencyString(Setup.getDemurrageRR()));
        fileOut.printRecord(RP, " - " + Setup.getMessage("DemurrageXX"), getCurrencyString(Setup.getDemurrageXX()));
        fileOut.printRecord(RP, " - " + Setup.getMessage("DemurrageCredits"), Setup.getDemurCredits());
        fileOut.printRecord(NONE, Setup.getMessage("HandlingTitle"));
        fileOut.printRecord(RP, " - " + Setup.getMessage("AddedHazardFee"), getCurrencyString(Setup.getHazardFee()));
    }

    private void printRevenueDetailByCarValues(CSVPrinter fileOut) throws IOException {
        printRevenueDetailSubHeader(fileOut, "ByCar", "ForCustomer");
        for (Map.Entry<String, Set<CarRevenue>> e : trainRevenues.getCarRevenueSetByCustomer().entrySet()) {

            String customer = e.getKey();
            BigDecimal discountRate = customerDiscountRateMap.get(customer);
            if (discountRate == null) {
                discountRate = BigDecimal.ZERO;
            }

            Map<String, CarRevenue> printMap = new TreeMap<>();
            for (CarRevenue carRevenue : e.getValue()) {
                printMap.put(getCarDescription(carRevenue), carRevenue);
            }

            for (Map.Entry<String, CarRevenue> kv : printMap.entrySet()) {
                String description = kv.getKey();
                CarRevenue carRevenue = kv.getValue();
                fileOut.print(RDR);
                fileOut.print(description);
                fileOut.print(customer);
                printBigDecimalValues(fileOut, loadCarCharges(carRevenue, discountRate));
                fileOut.println();
            }
        }
        fileOut.println();
    }

    private void printRevenueDetailSubHeader(CSVPrinter fileOut, String col2, String col3) throws IOException {
        fileOut.print("REV");
        fileOut.print(Setup.getMessage(col2));
        fileOut.print(Setup.getMessage(col3));
        fileOut.println();
    }

    private void printRevenueDetailByCustomerValues(CSVPrinter fileOut) throws IOException {
        printRevenueDetailSubHeader(fileOut, "ByCustomer", "DiscountRate");
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
            fileOut.println();

            addBigDecimalValues(revenueValues, customerCharges);
        }
        fileOut.println();
    }

    private void printRevenueDetailForTrainValues(CSVPrinter fileOut) throws IOException {
        printRevenueDetailSubHeader(fileOut, "ByTrain", "RouteRate");

        fileOut.print(RDR);
        fileOut.print(train.getDescription());
        fileOut.print(getCurrencyString(trainRevenues.getMaxRouteTransportFee()));
        printBigDecimalValues(fileOut, revenueValues);
        fileOut.println();
    }

    private void printRevenueDetailHeader(CSVPrinter fileOut) throws IOException {
        fileOut.println();
        fileOut.print(NONE);
        fileOut.print(NONE);
        fileOut.print(NONE);
        fileOut.print(Setup.getMessage("Switching"));
        fileOut.print(Setup.getMessage("Transport"));
        fileOut.print(Setup.getMessage("Hazard"));
        fileOut.print(Setup.getMessage("Demurrage"));
        fileOut.print(Setup.getMessage("Cancelled"));
        fileOut.print(Setup.getMessage("Diverted"));
        fileOut.print(Setup.getMessage("Customer"));
        fileOut.print(Setup.getMessage("Total"));
        fileOut.println();

        fileOut.print(NONE);
        fileOut.print(NONE);
        fileOut.print(NONE);
        fileOut.print(Setup.getMessage("Tariff"));
        fileOut.print(Setup.getMessage("Tariff"));
        fileOut.print(Setup.getMessage("Tariff"));
        fileOut.print(Setup.getMessage("Charge"));
        fileOut.print(Setup.getMessage("Mulct"));
        fileOut.print(Setup.getMessage("Mulct"));
        fileOut.print(Setup.getMessage("Discount"));
        fileOut.print(Setup.getMessage("Revenue"));
        fileOut.println();
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

    private void setup(Train train) {
        this.train = train;
        trainRevenues = train.getTrainRevenues();
        carRevenuesByCustomer = trainRevenues.getCarRevenueSetByCustomer();
        customerDiscountRateMap = getCustomerDiscountRateMap();
        for (Car car : new HashSet<>(InstanceManager.getDefault(CarManager.class).getList())) {
            allCarsByCarId.put(car.getId(), car);
        }
        revenueValues = loadBigDecimalZeroValues();
        if (Locale.ENGLISH.equals(Locale.getDefault())) {
            Locale.setDefault(Locale.US);
            javax.swing.JComponent.setDefaultLocale(Locale.US);
        }
    }
    private void writeCsvRevenueFile(Train train) throws IOException {
        File csvFile = InstanceManager.getDefault(TrainManagerXml.class).createTrainCsvRevenueFile(train);
        CSVPrinter fileOut = new CSVPrinter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8)), CSVFormat.DEFAULT);
        printHeaderBlock(fileOut);
        printParameterBlock(fileOut);

        printRevenueDetailHeader(fileOut);
        printRevenueDetailByCarValues(fileOut);
        printRevenueDetailByCustomerValues(fileOut);
        printRevenueDetailForTrainValues(fileOut);

        fileOut.flush();
    }

}
