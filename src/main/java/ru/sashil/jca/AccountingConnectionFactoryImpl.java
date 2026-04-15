package ru.sashil.jca;

import jakarta.resource.Referenceable;
import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionFactory;
import jakarta.resource.cci.ConnectionSpec;
import jakarta.resource.cci.RecordFactory;
import jakarta.resource.cci.ResourceAdapterMetaData;
import jakarta.resource.spi.ConnectionManager;

import javax.naming.NamingException;
import javax.naming.Reference;

public class AccountingConnectionFactoryImpl implements ConnectionFactory, Referenceable {
    private final AccountingManagedConnectionFactory mcf;
    private final ConnectionManager cxManager;
    private Reference reference;

    public AccountingConnectionFactoryImpl(AccountingManagedConnectionFactory mcf, ConnectionManager cxManager) {
        this.mcf = mcf;
        this.cxManager = cxManager;
    }

    @Override
    public Connection getConnection() throws ResourceException {
        if (cxManager == null) {
            return (Connection) mcf.createManagedConnection(null, null).getConnection(null, null);
        }
        return (Connection) cxManager.allocateConnection(mcf, null);
    }

    @Override
    public Connection getConnection(ConnectionSpec properties) throws ResourceException {
        return getConnection();
    }

    @Override
    public RecordFactory getRecordFactory() throws ResourceException {
        return null;
    }

    @Override
    public ResourceAdapterMetaData getMetaData() throws ResourceException {
        return null;
    }

    @Override
    public void setReference(Reference reference) {
        this.reference = reference;
    }

    @Override
    public Reference getReference() throws NamingException {
        return reference;
    }

    public AccountingManagedConnectionFactory getMcf() {
        return mcf;
    }

    public ConnectionManager getCxManager() {
        return cxManager;
    }
}
