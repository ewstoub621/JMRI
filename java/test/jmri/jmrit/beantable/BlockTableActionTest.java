package jmri.jmrit.beantable;

import jmri.util.gui.GuiLafPreferencesManager;

import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import jmri.*;
import jmri.jmrit.display.layoutEditor.LayoutBlock;
import jmri.jmrit.display.layoutEditor.LayoutBlockManager;
import jmri.util.JUnitAppender;
import jmri.util.JUnitUtil;
import jmri.util.ThreadingUtil;
import jmri.util.swing.JemmyUtil;

import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import org.netbeans.jemmy.operators.*;

/**
 * Tests for the jmri.jmrit.beantable.BlockTableAction class
 *
 * @author Bob Jacobsen Copyright 2004, 2007, 2008
 */
public class BlockTableActionTest extends AbstractTableActionBase<Block> {

    @Test
    public void testCreate() {
        Assert.assertNotNull(a);
        Assert.assertNull(a.f); // frame should be null until action invoked
    }

    @Override
    public String getTableFrameName() {
        return Bundle.getMessage("TitleBlockTable");
    }

    @Override
    @Test
    public void testGetClassDescription() {
        Assert.assertEquals("Block Table Action class description", "Block Table", a.getClassDescription());
    }

    /**
     * Check the return value of includeAddButton. The table generated by this
     * action includes an Add Button.
     */
    @Override
    @Test
    public void testIncludeAddButton() {
        Assert.assertTrue("Default include add button", a.includeAddButton());
    }

    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    @Test
    public void testInvoke() {

        // create a couple of blocks, and see if they show
        InstanceManager.getDefault(BlockManager.class).createNewBlock("IB1", "block 1");
        Block b2 = InstanceManager.getDefault(BlockManager.class).createNewBlock("IB2", "block 2");
        Assert.assertNotNull(b2);
        b2.setDirection(Path.EAST);

        // set graphic state column display preference to false, read by createModel()
        InstanceManager.getDefault(GuiLafPreferencesManager.class).setGraphicTableState(false);
        BlockTableAction _bTable = new BlockTableAction();
        Assert.assertNotNull("found BlockTable frame", _bTable);

        // assert blocks show in table
        //Assert.assertEquals("Block1 getValue","(no name)",_bTable.getValue(null)); // taken out for now, returns null on CI?
        //Assert.assertEquals("Block1 getValue","(no Block)",_bTable.getValue("nonsenseBlock"));
        //Assert.assertEquals("Block1 getValue","IB1",_bTable.getValue("block 1"));
        _bTable.dispose();

        // set to true, use icons
        InstanceManager.getDefault(GuiLafPreferencesManager.class).setGraphicTableState(true);
        BlockTableAction _b1Table = new BlockTableAction();
        Assert.assertNotNull("found BlockTable1 frame", _b1Table);

        ThreadingUtil.runOnGUI( ()-> {
            _b1Table.addPressed(null);
        });
        JFrameOperator af = new JFrameOperator(Bundle.getMessage("TitleAddBlock"));
        Assert.assertNotNull("found Add frame", af);

        // Cancel & close AddPane
        ThreadingUtil.runOnGUI( ()-> {
            _b1Table.cancelPressed(null);
        });

        // clean up
        af.requestClose();
        af.waitClosed();

        _b1Table.dispose();

    }

    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    @Test
    public void testAddBlock() {

        ThreadingUtil.runOnGUI( ()-> {
            a.actionPerformed(null); // show table
        });

        JFrameOperator f = new JFrameOperator(Bundle.getMessage("TitleBlockTable"));
        Assert.assertNotNull(f);

        ThreadingUtil.runOnGUI( ()-> {
            a.addPressed(null);
        });
        JFrameOperator addFrame = new JFrameOperator(Bundle.getMessage("TitleAddBlock"));  // NOI18N
        Assert.assertNotNull("Found Add Block Frame", addFrame);  // NOI18N

        new JTextFieldOperator(addFrame, 0).setText("105");  // NOI18N
        new JTextFieldOperator(addFrame, 2).setText("Block 105");  // NOI18N
        new JButtonOperator(addFrame, Bundle.getMessage("ButtonCreate")).push();  // NOI18N
        addFrame.getQueueTool().waitEmpty();
        new JButtonOperator(addFrame, Bundle.getMessage("ButtonCancel")).push();  // NOI18N

        Block chk105 = InstanceManager.getDefault(BlockManager.class).getBlock("Block 105");  // NOI18N
        Assert.assertNotNull("Verify IB105 Added", chk105);  // NOI18N
        Assert.assertEquals("Verify system name prefix", "IB105", chk105.getSystemName());  // NOI18N

        f.requestClose();
        f.waitClosed();

    }

    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    @Test
    public void testRenameBlock() {

        // Create a Layout Block which will create the Block entry
        LayoutBlockManager lbm = InstanceManager.getDefault(LayoutBlockManager.class);
        LayoutBlock layoutBlock = lbm.createNewLayoutBlock("ILB999", "Block Name");  // NOI18N
        if (layoutBlock == null) {
            Assertions.fail("No Layout Block ILB999");
            return;
        }
        layoutBlock.initializeLayoutBlock();
        Assert.assertNotNull(layoutBlock);
        Assert.assertEquals("Block Name", layoutBlock.getUserName());  // NOI18N

        // Get the referenced block
        Block block = InstanceManager.getDefault(BlockManager.class).getByUserName("Block Name");  // NOI18N
        Assert.assertNotNull(block);

        // Open the block table
        ThreadingUtil.runOnGUI( ()-> {
            a.actionPerformed(null); // show table
        });
        JFrameOperator jfo = new JFrameOperator(Bundle.getMessage("TitleBlockTable"));  // NOI18N
        Assert.assertNotNull(jfo);

        JTableOperator tbo = new JTableOperator(jfo);
        Assert.assertNotNull(tbo);

        // Click on the edit button, set the user name to empty for remove
        tbo.clickOnCell(0, 5);
        JFrameOperator jfoEdit = new JFrameOperator(Bundle.getMessage("TitleEditBlock"));  // NOI18N
        JTextFieldOperator jtxt = new JTextFieldOperator(jfoEdit, 0);
        jtxt.clickMouse();
        jtxt.setText("");

        // Prepare the dialog thread and click on OK
        Thread remove = JemmyUtil.createModalDialogOperatorThread(Bundle.getMessage("WarningTitle"), Bundle.getMessage("ButtonOK"));  // NOI18N
        new JButtonOperator(jfoEdit, "OK").doClick();  // NOI18N
        JUnitUtil.waitFor(() -> {
            return !(remove.isAlive());
        }, "remove finished");  // NOI18N
        tbo.clickOnCell(0, 0);  // deselect the edit button

        // Click on the edit button, set the user name to a new value
        tbo.clickOnCell(0, 5);
        jfoEdit = new JFrameOperator(Bundle.getMessage("TitleEditBlock"));  // NOI18N
        jtxt = new JTextFieldOperator(jfoEdit, 0);
        jtxt.clickMouse();
        jtxt.setText("New Block Name");  // NOI18N

        // Prepare the dialog thread and click on OK
        Thread rename = JemmyUtil.createModalDialogOperatorThread(Bundle.getMessage("QuestionTitle"), Bundle.getMessage("ButtonYes"));  // NOI18N
        new JButtonOperator(jfoEdit, "OK").doClick();  // NOI18N
        JUnitUtil.waitFor(() -> {
            return !(rename.isAlive());
        }, "rename finished");  // NOI18N
        tbo.clickOnCell(0, 0);  // deselect the edit button

        // Confirm the layout block user name change
        Assert.assertEquals("New Block Name", layoutBlock.getUserName());

        JUnitAppender.assertWarnMessage("Cannot remove user name for block Block Name");  // NOI18N
        jfo.requestClose();
        jfo.waitClosed();
    }

    @Override
    public String getAddFrameName() {
        return Bundle.getMessage("TitleAddBlock");
    }

    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    @Test
    @Override
    public void testAddThroughDialog() {

        Assert.assertTrue(a.includeAddButton());
        ThreadingUtil.runOnGUI( ()-> {
            a.actionPerformed(null); // show table
        });
        JFrameOperator f = new JFrameOperator(getTableFrameName());

        // find the "Add... " button and press it.
        JemmyUtil.pressButton(f, Bundle.getMessage("ButtonAdd"));
        new org.netbeans.jemmy.QueueTool().waitEmpty();
        JFrameOperator jf = new JFrameOperator(getAddFrameName());
        //Enter 1 in the text field labeled "System Name:"
        JLabelOperator jlo = new JLabelOperator(jf, Bundle.getMessage("LabelSystemName"));
        ((JTextField) jlo.getLabelFor()).setText("1");
        //and press create
        JemmyUtil.pressButton(jf, Bundle.getMessage("ButtonCreate"));
        jf.getQueueTool().waitEmpty();
        JemmyUtil.pressButton(jf, Bundle.getMessage("ButtonCancel"));  // NOI18N
        jf.waitClosed();

        f.requestClose();
        f.waitClosed();
    }

    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    @Test
    public void testSetDefaultSpeed() {

        Assert.assertTrue(a.includeAddButton());
        ThreadingUtil.runOnGUI( ()-> {
            a.actionPerformed(null); // show table
        });
        JFrameOperator main = new JFrameOperator(getTableFrameName());

        // find the "Add... " button and press it.
        ThreadingUtil.runOnGUI( ()-> {
            JemmyUtil.pressButton(main, Bundle.getMessage("ButtonAdd"));
        });
        JFrameOperator jf = new JFrameOperator(getAddFrameName());

        //Enter 1 in the text field labeled "System Name:"
        JLabelOperator jlo = new JLabelOperator(jf, Bundle.getMessage("LabelSystemName"));
        ((JTextField) jlo.getLabelFor()).setText("1");

        //and press create
        JemmyUtil.pressButton(jf, Bundle.getMessage("ButtonCreate"));
        jf.getQueueTool().waitEmpty();
        jf.requestClose();
        jf.waitClosed();

        // Open Speed pane to test Speed menu, which displays a JOptionPane
        
        // Use GUI menu to open Speeds pane:
        //This is a modal JOptionPane, so create a thread to dismiss it.
        Thread t = new Thread(() -> {
            try {
                JemmyUtil.confirmJOptionPane(main, Bundle.getMessage("BlockSpeedLabel"), "", "OK");
            }
            catch (org.netbeans.jemmy.TimeoutExpiredException tee) {
                // we're waiting for this thread to finish in the main method,
                // so any exception here means we failed.
                Assertions.fail("BlockTableActionTest caught timeout exception while waiting for modal dialog " + tee.getMessage());
            }
        });
        t.setName("Default Speeds Dialog Close Thread");
        t.start();
        // pushMenuNoBlock is used, because dialog is modal
        JMenuBarOperator mainbar = new JMenuBarOperator(main);
        mainbar.pushMenu(Bundle.getMessage("SpeedsMenu")); // stops at top level
        JMenuOperator jmo = new JMenuOperator(mainbar, Bundle.getMessage("SpeedsMenu"));
        JPopupMenu jpm = jmo.getPopupMenu();
        JMenuItemOperator jmio = new JMenuItemOperator(new JPopupMenuOperator(jpm), Bundle.getMessage("SpeedsMenuItemDefaults"));
        jmio.pushNoBlock();

        // wait for the dismiss thread to finish
        JUnitUtil.waitFor(() -> {
            return !t.isAlive();
        }, "Dismiss Default Speeds Thread finished");

        // clean up
        main.requestClose();
        main.waitClosed();

    }

    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    @Test
    @Override
    public void testEditButton() {

        Assert.assertTrue(a.includeAddButton());
        ThreadingUtil.runOnGUI( ()-> {
            a.actionPerformed(null);
        });

        JFrameOperator jfo = new JFrameOperator(getTableFrameName());

        // find the "Add... " button and press it.
        JemmyUtil.pressButton(jfo, Bundle.getMessage("ButtonAdd"));
        JFrameOperator jf = new JFrameOperator(getAddFrameName());
        //Enter 1 in the text field labeled "System Name:"

        JLabelOperator jlo = new JLabelOperator(jf, Bundle.getMessage("LabelSystemName"));
        ((JTextField) jlo.getLabelFor()).setText("1");
        Assertions.assertEquals(0, InstanceManager.getDefault(BlockManager.class).getObjectCount(),"no blocks in manager");
        
        //and press create
        JemmyUtil.pressButton(jf, Bundle.getMessage("ButtonCreate"));
        JUnitUtil.waitFor(() -> { return InstanceManager.getDefault(BlockManager.class).getObjectCount()>0;  });
        
        JemmyUtil.pressButton( jf, Bundle.getMessage("ButtonCancel"));
        jf.waitClosed();

        JTableOperator tbl = new JTableOperator(jfo, 0);
        // find the "Edit" button and press it.  This is in the table body.
        int column = tbl.findColumn(Bundle.getMessage("ButtonEdit"));
        tbl.clickOnCell(0, column);
        
        JFrameOperator f2 = new JFrameOperator(getEditFrameName());
        JemmyUtil.pressButton( f2, Bundle.getMessage("ButtonCancel"));
        f2.waitClosed();

        jf.requestClose();
        jf.waitClosed();
        
        jfo.requestClose();
        jfo.waitClosed();
    }

    @Override
    public String getEditFrameName() {
        return Bundle.getMessage("TitleEditBlock") + " IB1";
    }

    @BeforeEach
    @Override
    public void setUp() {
        JUnitUtil.setUp();
        JUnitUtil.resetInstanceManager();
        JUnitUtil.resetProfileManager();
        JUnitUtil.initDefaultUserMessagePreferences();
        JUnitUtil.initInternalTurnoutManager();
        JUnitUtil.initInternalLightManager();
        JUnitUtil.initInternalSensorManager();
        JUnitUtil.initInternalSignalHeadManager();
        InstanceManager.setDefault(BlockManager.class, new BlockManager());
        helpTarget = "package.jmri.jmrit.beantable.BlockTable";
        a = new BlockTableAction();
    }

    @AfterEach
    @Override
    public void tearDown() {
        if ( a != null ){
            a.dispose();
            a = null;
        }
        JUnitUtil.deregisterBlockManagerShutdownTask();
        JUnitUtil.deregisterEditorManagerShutdownTask();
        JUnitUtil.tearDown();
    }

}
