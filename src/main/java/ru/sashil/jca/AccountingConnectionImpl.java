package ru.sashil.jca;

import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionMetaData;
import jakarta.resource.cci.Interaction;
import jakarta.resource.cci.ResultSetInfo;

public class AccountingConnectionImpl implements Connection {
    private AccountingManagedConnection managedConnection;

    public AccountingConnectionImpl(AccountingManagedConnection managedConnection) {
        this.managedConnection = managedConnection;
    }

    @Override
    public Interaction createInteraction() throws ResourceException {
        return new AccountingInteractionImpl(this);
    }

    @Override
    public jakarta.resource.cci.LocalTransaction getLocalTransaction() throws ResourceException {
        return new jakarta.resource.cci.LocalTransaction() {
            @Override
            public void begin() throws ResourceException {
                managedConnection.begin();
            }

            @Override
            public void commit() throws ResourceException {
                managedConnection.commit();
            }

            @Override
            public void rollback() throws ResourceException {
                managedConnection.rollback();
            }
        };
    }

    @Override
    public ConnectionMetaData getMetaData() throws ResourceException {
        return null;
    }

    @Override
    public ResultSetInfo getResultSetInfo() throws ResourceException {
        return null;
    }

    @Override
    public void close() throws ResourceException {
        managedConnection = null;
    }

    public AccountingManagedConnection getManagedConnection() {
        return managedConnection;
    }

    public void setManagedConnection(AccountingManagedConnection managedConnection) {
        this.managedConnection = managedConnection;
    }
}
