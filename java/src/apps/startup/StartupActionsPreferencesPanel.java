package apps.startup;

import apps.StartupActionsManager;
import apps.StartupModel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ResourceBundle;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import jmri.InstanceManager;
import jmri.profile.ProfileManager;
import jmri.swing.PreferencesPanel;

/**
 *
 * @author Randall Wood (C) 2016
 */
public class StartupActionsPreferencesPanel extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Creates new form StartupActionsPreferencesPanel
     */
    public StartupActionsPreferencesPanel() {
        initComponents();
        this.actionsTbl.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            int row = this.actionsTbl.getSelectedRow();
            this.upBtn.setEnabled(row != 0);
            this.downBtn.setEnabled(row != this.actionsTbl.getRowCount() - 1);
        });
        InstanceManager.getDefault(StartupActionsManager.class).getFactories().values().stream().forEach((factory) -> {
            JMenuItem item = new JMenuItem(factory.getDescription());
            item.addActionListener((ActionEvent e) -> {
                StartupModel model = factory.newModel();
                factory.editModel(model);
                if (model.getName() != null && !model.getName().isEmpty()) {
                    InstanceManager.getDefault(StartupActionsManager.class).addAction(model);
                }
            });
            this.actionsMenu.add(item);
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        actionsMenu = new JPopupMenu();
        jScrollPane1 = new JScrollPane();
        actionsTbl = new JTable();
        addBtn = new JButton();
        removeBtn = new JButton();
        startupLbl = new JLabel();
        upBtn = new JButton();
        downBtn = new JButton();
        jLabel1 = new JLabel();

        actionsTbl.setModel(new TableModel(InstanceManager.getDefault(StartupActionsManager.class)));
        actionsTbl.setCellSelectionEnabled(false);
        actionsTbl.setRowSelectionAllowed(true);
        actionsTbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionsTbl.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(actionsTbl);
        actionsTbl.getColumnModel().getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        ResourceBundle bundle = ResourceBundle.getBundle("apps/startup/Bundle"); // NOI18N
        addBtn.setText(bundle.getString("StartupActionsPreferencesPanel.addBtn.text")); // NOI18N
        addBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                addBtnActionPerformed(evt);
            }
        });

        removeBtn.setText(bundle.getString("StartupActionsPreferencesPanel.removeBtn.text")); // NOI18N
        removeBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                removeBtnActionPerformed(evt);
            }
        });

        startupLbl.setText(bundle.getString("StartupActionsPreferencesPanel.startupLbl.text")); // NOI18N

        upBtn.setText(bundle.getString("StartupActionsPreferencesPanel.upBtn.text")); // NOI18N
        upBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                upBtnActionPerformed(evt);
            }
        });

        downBtn.setText(bundle.getString("StartupActionsPreferencesPanel.downBtn.text")); // NOI18N
        downBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                downBtnActionPerformed(evt);
            }
        });

        jLabel1.setText(bundle.getString("StartupActionsPreferencesPanel.jLabel1.text")); // NOI18N

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 487, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addBtn)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel1)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upBtn)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(downBtn)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(removeBtn))
                    .addComponent(startupLbl))
                .addContainerGap())
        );
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(startupLbl, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 222, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(addBtn)
                    .addComponent(removeBtn)
                    .addComponent(upBtn)
                    .addComponent(downBtn)
                    .addComponent(jLabel1))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addBtnActionPerformed(ActionEvent evt) {//GEN-FIRST:event_addBtnActionPerformed
        this.actionsMenu.show((Component) evt.getSource(), 0, 0);
    }//GEN-LAST:event_addBtnActionPerformed

    private void removeBtnActionPerformed(ActionEvent evt) {//GEN-FIRST:event_removeBtnActionPerformed
        int row = this.actionsTbl.getSelectedRow();
        if (row != -1) {
            StartupModel model = InstanceManager.getDefault(StartupActionsManager.class).getActions(row);
            InstanceManager.getDefault(StartupActionsManager.class).removeAction(model);
        }
    }//GEN-LAST:event_removeBtnActionPerformed

    private void upBtnActionPerformed(ActionEvent evt) {//GEN-FIRST:event_upBtnActionPerformed
        int row = this.actionsTbl.getSelectedRow();
        if (row != 0) {
            InstanceManager.getDefault(StartupActionsManager.class).moveAction(row, row - 1);
            this.actionsTbl.setRowSelectionInterval(row - 1, row - 1);
        }
    }//GEN-LAST:event_upBtnActionPerformed

    private void downBtnActionPerformed(ActionEvent evt) {//GEN-FIRST:event_downBtnActionPerformed
        int row = this.actionsTbl.getSelectedRow();
        if (row != this.actionsTbl.getRowCount() - 1) {
            InstanceManager.getDefault(StartupActionsManager.class).moveAction(row, row + 1);
            this.actionsTbl.setRowSelectionInterval(row + 1, row + 1);
        }
    }//GEN-LAST:event_downBtnActionPerformed

    @Override
    public String getPreferencesItem() {
        return "STARTUP"; // NOI18N
    }

    @Override
    public String getPreferencesItemText() {
        return Bundle.getMessage("MenuStartUp"); // NOI18N
    }

    @Override
    public String getTabbedPreferencesTitle() {
        return null;
    }

    @Override
    public String getLabelKey() {
        return null;
    }

    @Override
    public JComponent getPreferencesComponent() {
        return this;
    }

    @Override
    public boolean isPersistant() {
        return false;
    }

    @Override
    public String getPreferencesTooltip() {
        return null;
    }

    @Override
    public void savePreferences() {
        InstanceManager.getDefault(StartupActionsManager.class).savePreferences(ProfileManager.getDefault().getActiveProfile());
    }

    @Override
    public boolean isDirty() {
        // TODO: be real
        return true;
    }

    @Override
    public boolean isRestartRequired() {
        return this.isDirty();
    }

    @Override
    public boolean isPreferencesValid() {
        // TODO: be real
        return true;
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    JPopupMenu actionsMenu;
    JTable actionsTbl;
    JButton addBtn;
    JButton downBtn;
    JLabel jLabel1;
    JScrollPane jScrollPane1;
    JButton removeBtn;
    JLabel startupLbl;
    JButton upBtn;
    // End of variables declaration//GEN-END:variables

    class TableModel extends AbstractTableModel implements PropertyChangeListener {

        private static final long serialVersionUID = 1L;
        private final StartupActionsManager manager;

        @SuppressWarnings("LeakingThisInConstructor")
        public TableModel(StartupActionsManager manager) {
            this.manager = manager;
            this.manager.addPropertyChangeListener(this);
        }

        @Override
        public int getRowCount() {
            return this.manager.getActions().length;
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            StartupModel model = this.manager.getActions(rowIndex);
            switch (columnIndex) {
                case -1: // tooltip
                    return model.getName();
                case 0:
                    return model.getName();
                case 1:
                    return model.getClass().getSimpleName();
                default:
                    return new TableActionsPanel(model);
            }

        }

        @Override
        public String getColumnName(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return Bundle.getMessage("StartupActionsTableModel.name"); // NOI18N
                case 1:
                    return Bundle.getMessage("StartupActionsTableModel.type"); // NOI18N
                default:
                    return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
//
//        @Override
//        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
//            switch (columnIndex) {
//                case 1:
//                    try {
//                        ProfileManager.defaultManager().setDefaultSearchPath((File) this.getValueAt(rowIndex, 0));
//                    } catch (IOException ex) {
//                        log.warn("Unable to write profiles while setting default search path", ex);
//                    }
//                    break;
//                default:
//            }
//        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            this.fireTableDataChanged();
        }
    }

    class TableActionsPanel extends JPanel {

        public TableActionsPanel(StartupModel model) {

        }
    }
}
