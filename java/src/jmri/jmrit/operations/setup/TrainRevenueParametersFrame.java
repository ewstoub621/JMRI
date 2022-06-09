package jmri.jmrit.operations.setup;

import jmri.jmrit.operations.OperationsFrame;

/**
 * TrainRevenueParametersFrame for revenue parameter preference values
 *
 * @author Everett Stoub Copyright (C) 2021
 */
public class TrainRevenueParametersFrame extends OperationsFrame {
    public TrainRevenueParametersFrame() {
        super(Bundle.getMessage("EditRevenueParameters"), new TrainRevenueParametersPanel());
    }

    @Override
    public void initComponents() {
        super.initComponents();

        // build menu
        addHelpMenu("package.jmri.jmrit.operations.Operations_EditRevenueParameters", true); // NOI18N

        initMinimumSize();
    }

    @Override
    public void buttonActionPerformed(java.awt.event.ActionEvent ae) {
        if (Setup.isCloseWindowOnSaveEnabled()) {
            super.dispose();
        }
    }

}
