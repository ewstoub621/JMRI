package jmri.jmrit.logixng;

import java.util.List;

import jmri.NamedBean;
import jmri.jmrit.logixng.SymbolTable.InitialValueType;
import jmri.jmrit.logixng.SymbolTable.VariableData;

/**
 * A LogixNG male socket.
 * 
 * @author Daniel Bergqvist Copyright 2018
 */
public interface MaleSocket extends Debugable {

    public enum ErrorHandlingType {
        
        SHOW_DIALOG_BOX,
        LOG_ERROR,
        LOG_ERROR_ONCE,
        THROW,
        ABORT_EXECUTION,
    }
    
    /**
     * Set whenether this male socket is enabled or disabled.
     * <P>
     * This method must call registerListeners() / unregisterListeners().
     * 
     * @param enable true if this male socket should be enabled, false otherwise
     */
    public void setEnabled(boolean enable);
    
    /**
     * Determines whether this male socket is enabled.
     * 
     * @return true if the male socket is enabled, false otherwise
     */
    @Override
    public boolean isEnabled();
    
    public void addLocalVariable(
            String name,
            InitialValueType initialValueType,
            String initialValueData);
    
    public void addLocalVariable(VariableData variableData);
    
    public void clearLocalVariables();
    
    public List<VariableData> getLocalVariables();
    
    /**
     * Get the error handling type for this socket.
     * @return the error handling type
     */
    public ErrorHandlingType getErrorHandlingType();
    
    /**
     * Set the error handling type for this socket.
     * @param errorHandlingType the error handling type
     */
    public void setErrorHandlingType(ErrorHandlingType errorHandlingType);
    
    /**
     * Get the object that this male socket holds.
     * This method is used when the object is going to be configured.
     * 
     * @return the object this male socket holds
     */
    public Base getObject();

    /**
     * Get the manager that stores this socket.
     * This method is used when the object is going to be configured.
     * 
     * @return the manager
     */
    public BaseManager<? extends NamedBean> getManager();

    /** {@inheritDoc} */
    @Override
    default public void setup() {
        getObject().setup();
    }

}
