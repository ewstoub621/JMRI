package jmri.jmrit.operations.setup;

import jmri.InstanceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TrainRevenueParametersPanel for user edit of revenue parameter values
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainRevenueParametersPanel extends OperationsPreferencesPanel implements java.beans.PropertyChangeListener {
    public static final String SPACE = " ";
    private static final List<String> FIELD_NAME_LIST =
            Arrays.asList( // panel display line order
                           "SwitchingEmpty",
                           "SwitchingLoads",
                           "SwitchingAggrs",
                           "SwitchingGrain",
                           "SwitchingMetal",
                           "SwitchingTanks",
                           "SwitchingWoody",
                           "AddedHazardFee",
                           "CancelledMulct",
                           "DiversionMulct",
                           "DemurrageRR",
                           "DemurrageXX",
                           "DemurrageCredits",
                           "MaximumDiscount"
            );
    private final Map<String, JTextField> jTextFieldMap = new HashMap<>();
    JButton saveButton = new JButton(Bundle.getMessage("ButtonSave"));

    public TrainRevenueParametersPanel() {
        loadPanel();
        Setup.getDefault().addPropertyChangeListener(this);
    }

    @Override
    public void buttonActionPerformed(java.awt.event.ActionEvent ae) {
        if (ae.getSource() == saveButton) {
            if (isDirty()) {
                savePreferences();
            }
            Container container = this;
            while (container != null && !(container instanceof JFrame)) {
                container = container.getParent();
            }
            if (container != null) {
                ((JFrame) container).dispose();
            }
        }
    }

    @Override
    public String getPreferencesTooltip() {
        return null;
    }

    @Override
    public String getTabbedPreferencesTitle() {
        return Bundle.getMessage("EditRevenueParameters");
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        String ePropName = e.getPropertyName();
        String eOldValue = (String) e.getOldValue();
        String eNewValue = (String) e.getNewValue();
        if (Control.SHOW_PROPERTY) {
            log.debug("Property change: ({}) old: ({}) new: ({})", ePropName, eOldValue, eNewValue);
        }
        boolean validInput = isValidInput(ePropName, eNewValue);
        if (validInput) {
            switch (ePropName) {
            case Setup.REVENUE_CANCELLED_MULCT_CHANGE:
                Setup.setCancelMulct(eNewValue);
                break;
            case Setup.REVENUE_DEMURRAGE_RR_CHANGE:
                Setup.setDemurrageRR(eNewValue);
                break;
            case Setup.REVENUE_DEMURRAGE_XX_CHANGE:
                Setup.setDemurrageXX(eNewValue);
                break;
            case Setup.REVENUE_DEMUR_CREDIT_DAYS_CHANGE:
                Setup.setDemurCredits(eNewValue);
                break;
            case Setup.REVENUE_DIVERSION_MULCT_CHANGE:
                Setup.setDivertMulct(eNewValue);
                break;
            case Setup.REVENUE_HAZARD_FEE_CHANGE:
                Setup.setHazardFee(eNewValue);
                break;
            case Setup.REVENUE_MAX_DISCOUNT_CHANGE:
                Setup.setMaxDiscount(eNewValue);
                break;
            case Setup.REVENUE_SWITCHING_EMPTY_CHANGE:
                Setup.setSwitchEmpty(eNewValue);
                break;
            case Setup.REVENUE_SWITCHING_LOADS_CHANGE:
                Setup.setSwitchLoads(eNewValue);
                break;
            case Setup.REVENUE_SWITCHING_AGGRS_CHANGE:
                Setup.setSwitchAggrs(eNewValue);
                break;
            case Setup.REVENUE_SWITCHING_GRAIN_CHANGE:
                Setup.setSwitchGrain(eNewValue);
                break;
            case Setup.REVENUE_SWITCHING_METAL_CHANGE:
                Setup.setSwitchMetal(eNewValue);
                break;
            case Setup.REVENUE_SWITCHING_TANKS_CHANGE:
                Setup.setSwitchTanks(eNewValue);
                break;
            case Setup.REVENUE_SWITCHING_WOODY_CHANGE:
                Setup.setSwitchWoody(eNewValue);
                break;
            }
        }
    }

    @Override
    public void savePreferences() {
        log.debug("savePreferences()");

        for (Map.Entry<String, JTextField> e : jTextFieldMap.entrySet()) {
            String name = e.getKey();
            String value = e.getValue().getText();
            if (isValidInput(name, value)) {
                setSetupValue(name, value);
            }
        }

        InstanceManager.getDefault(OperationsSetupXml.class).writeOperationsFile();
    }

    @Override
    public boolean isDirty() {
        for (Map.Entry<String, JTextField> e : jTextFieldMap.entrySet()) {
            String name = e.getKey();
            String value = e.getValue().getText();
            if (!getSetupValue(name).equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidInput(String name, String value) {
        if (value != null) {
            String trim = value.trim();
            try {
                if (!trim.isEmpty()) {
                    Integer.parseInt(trim);
                    return true;
                }
            } catch (NumberFormatException e) {
                log.error("{}: {} isn't a value number", name, value);
                JOptionPane.showMessageDialog(this,
                                              Bundle.getMessage(name),
                                              Bundle.getMessage("CanNotAcceptNumber"),
                                              JOptionPane.ERROR_MESSAGE
                );
                return false;
            }
        }
        return true;
    }

    private String getSetupValue(String name) {
        switch (name) {
        case "AddedHazardFee":
            return Setup.getHazardFee();
        case "CancelledMulct":
            return Setup.getCancelMulct();
        case "DemurrageRR":
            return Setup.getDemurrageRR();
        case "DemurrageXX":
            return Setup.getDemurrageXX();
        case "DemurrageCredits":
            return Setup.getDemurCredits();
        case "DiversionMulct":
            return Setup.getDivertMulct();
        case "MaximumDiscount":
            return Setup.getMaxDiscount();
        case "SwitchingEmpty":
            return Setup.getSwitchEmpty();
        case "SwitchingLoads":
            return Setup.getSwitchLoads();
        case "SwitchingAggrs":
            return Setup.getSwitchAggrs();
        case "SwitchingGrain":
            return Setup.getSwitchGrain();
        case "SwitchingMetal":
            return Setup.getSwitchMetal();
        case "SwitchingTanks":
            return Setup.getSwitchTanks();
        case "SwitchingWoody":
            return Setup.getSwitchWoody();
        default:
            return Bundle.getMessage(name);
        }
    }

    private void setSetupValue(String name, String value) {
        switch (name) {
        case "AddedHazardFee":
            Setup.setHazardFee(value);
            break;
        case "CancelledMulct":
            Setup.setCancelMulct(value);
            break;
        case "DemurrageRR":
            Setup.setDemurrageRR(value);
            break;
        case "DemurrageXX":
            Setup.setDemurrageXX(value);
            break;
        case "DemurrageCredits":
            Setup.setDemurCredits(value);
            break;
        case "DiversionMulct":
            Setup.setDivertMulct(value);
            break;
        case "MaximumDiscount":
            Setup.setMaxDiscount(value);
            break;
        case "SwitchingEmpty":
            Setup.setSwitchEmpty(value);
            break;
        case "SwitchingLoads":
            Setup.setSwitchLoads(value);
            break;
        case "SwitchingAggrs":
            Setup.setSwitchAggrs(value);
            break;
        case "SwitchingGrain":
            Setup.setSwitchGrain(value);
            break;
        case "SwitchingMetal":
            Setup.setSwitchMetal(value);
            break;
        case "SwitchingTanks":
            Setup.setSwitchTanks(value);
            break;
        case "SwitchingWoody":
            Setup.setSwitchWoody(value);
            break;
        }
    }

    private void loadPanel() {
        // general GUI config
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel revenueParamsPanel = new JPanel();
        revenueParamsPanel.setLayout(new GridBagLayout());
        revenueParamsPanel.setBorder(
                BorderFactory.createTitledBorder(
                        Bundle.getMessage("RevenueParameters")
                )
        );

        int fieldRow = 0;
        for (String fieldName : FIELD_NAME_LIST) {
            switch (fieldName) {
            case "MaximumDiscount":
                addItemLeft(revenueParamsPanel, new JLabel(SPACE), 0, fieldRow++);
                addItemLeft(revenueParamsPanel, new JLabel(Bundle.getMessage("DiscountTitle")), 0, fieldRow++);
                break;
            case "SwitchingEmpty":
                addItemLeft(revenueParamsPanel, new JLabel(SPACE), 0, fieldRow++);
                addItemLeft(revenueParamsPanel, new JLabel(Bundle.getMessage("SwitchingTitle")), 0, fieldRow++);
                break;
            case "CancelledMulct":
                addItemLeft(revenueParamsPanel, new JLabel(SPACE), 0, fieldRow++);
                addItemLeft(revenueParamsPanel, new JLabel(Bundle.getMessage("MulctTitle")), 0, fieldRow++);
                break;
            case "DemurrageRR":
                addItemLeft(revenueParamsPanel, new JLabel(SPACE), 0, fieldRow++);
                addItemLeft(revenueParamsPanel, new JLabel(Bundle.getMessage("DemurrageTitle")), 0, fieldRow++);
                break;
            case "AddedHazardFee":
                addItemLeft(revenueParamsPanel, new JLabel(SPACE), 0, fieldRow++);
                addItemLeft(revenueParamsPanel, new JLabel(Bundle.getMessage("HandlingTitle")), 0, fieldRow++);
                break;
            default:
                break;
            }

            addItemLeft(revenueParamsPanel, new JLabel(" - " + Bundle.getMessage(fieldName)), 0, fieldRow);

            JTextField jtf = new JTextField(4);
            jtf.setHorizontalAlignment(JTextField.LEFT);
            jtf.setText(getSetupValue(fieldName));
            String toolTipText = getSetupValue(fieldName + "ToolTip");
            jtf.setToolTipText(toolTipText);
            jTextFieldMap.put(fieldName, jtf);

            addItemLeft(revenueParamsPanel, new JLabel(" - " + Bundle.getMessage(fieldName)), 0, fieldRow);
            addItem(revenueParamsPanel, jtf, 1, fieldRow++);
        }

        JPanel pButtons = new JPanel();
        pButtons.setLayout(new GridBagLayout());
        pButtons.setBorder(BorderFactory.createEtchedBorder());

        saveButton.setToolTipText(Bundle.getMessage("SaveToolTip"));
        addItem(pButtons, saveButton, 0, 0);
        addButtonAction(saveButton);

        add(revenueParamsPanel);
        add(pButtons);
    }

    private static final Logger log = LoggerFactory.getLogger(TrainRevenueParametersPanel.class);
}
