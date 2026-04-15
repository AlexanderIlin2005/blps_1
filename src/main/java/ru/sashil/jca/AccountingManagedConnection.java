package ru.sashil.jca;

import jakarta.resource.NotSupportedException;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountingManagedConnection implements ManagedConnection, jakarta.resource.spi.LocalTransaction {
    private final AccountingManagedConnectionFactory mcf;
    private final List<ConnectionEventListener> listeners = new ArrayList<>();
    private Socket socket;
    private PrintWriter logWriter;

    public AccountingManagedConnection(AccountingManagedConnectionFactory mcf) throws ResourceException {
        this.mcf = mcf;
        try {
            this.socket = new Socket(mcf.getHost(), mcf.getPort());
        } catch (IOException e) {
            throw new ResourceException("Could not connect to accounting system at " + mcf.getHost() + ":" + mcf.getPort(), e);
        }
    }

    @Override
    public Object getConnection(Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        return new AccountingConnectionImpl(this);
    }

    @Override
    public void destroy() throws ResourceException {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            throw new ResourceException(e);
        }
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

    public void send(String payload) throws IOException {
        socket.getOutputStream().write((payload + "\n").getBytes());
        socket.getOutputStream().flush();
    }

    public String receive() throws IOException {
        byte[] buffer = new byte[1024];
        int read = socket.getInputStream().read(buffer);
        if (read == -1) return null;
        return new String(buffer, 0, read).trim();
    }

    public AccountingManagedConnectionFactory getMcf() {
        return mcf;
    }
}
