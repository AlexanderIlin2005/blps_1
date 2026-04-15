package ru.sashil.jca;

import jakarta.resource.NotSupportedException;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountingManagedConnection implements ManagedConnection, jakarta.resource.spi.LocalTransaction {
    private final AccountingManagedConnectionFactory mcf;
    private final List<ConnectionEventListener> listeners = new ArrayList<>();
    private PrintWriter logWriter;

    public AccountingManagedConnection(AccountingManagedConnectionFactory mcf) throws ResourceException {
        this.mcf = mcf;
    }

    @Override
    public Object getConnection(Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        return new AccountingConnectionImpl(this);
    }

    @Override
    public void destroy() throws ResourceException {
    }

    @Override
    public void cleanup() throws ResourceException {
    }

    @Override
    public void associateConnection(Object connection) throws ResourceException {
        if (!(connection instanceof AccountingConnectionImpl)) {
            throw new ResourceException("Invalid connection object");
        }
        ((AccountingConnectionImpl) connection).setManagedConnection(this);
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        throw new NotSupportedException("XA not supported");
    }

    @Override
    public jakarta.resource.spi.LocalTransaction getLocalTransaction() throws ResourceException {
        return this;
    }

    @Override
    public ManagedConnectionMetaData getMetaData() throws ResourceException {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws ResourceException {
        this.logWriter = out;
    }

    @Override
    public PrintWriter getLogWriter() throws ResourceException {
        return logWriter;
    }

    @Override
    public void begin() throws ResourceException {
        log.info("Beginning local JCA transaction");
    }

    @Override
    public void commit() throws ResourceException {
        log.info("Committing local JCA transaction");
        notifyListeners(ConnectionEvent.LOCAL_TRANSACTION_COMMITTED);
    }

    @Override
    public void rollback() throws ResourceException {
        log.info("Rolling back local JCA transaction");
        notifyListeners(ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK);
    }

    private void notifyListeners(int eventType) {
        ConnectionEvent event = new ConnectionEvent(this, eventType);
        for (ConnectionEventListener listener : listeners) {
            switch (eventType) {
                case ConnectionEvent.LOCAL_TRANSACTION_COMMITTED -> listener.localTransactionCommitted(event);
                case ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK -> listener.localTransactionRolledback(event);
            }
        }
    }

    public void send(String payload) {
    }

    public String receive() {
        return null;
    }

    public AccountingManagedConnectionFactory getMcf() {
        return mcf;
    }
}
