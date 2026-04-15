package ru.sashil.jca;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.*;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Set;
import javax.security.auth.Subject;

@ConnectionDefinition(
    connectionFactory = jakarta.resource.cci.ConnectionFactory.class,
    connectionFactoryImpl = ru.sashil.jca.AccountingConnectionFactoryImpl.class,
    connection = jakarta.resource.cci.Connection.class,
    connectionImpl = ru.sashil.jca.AccountingConnectionImpl.class
)
public class AccountingManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation {
    private String host = "localhost";
    private int port = 9090;
    private PrintWriter logWriter;
    private ResourceAdapter ra;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    @Override
    public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
        return new AccountingConnectionFactoryImpl(this, cxManager);
    }

    @Override
    public Object createConnectionFactory() throws ResourceException {
        return new AccountingConnectionFactoryImpl(this, null);
    }

    @Override
    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        return new AccountingManagedConnection(this);
    }

    @Override
    public ManagedConnection matchManagedConnections(Set connectionSet, Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        for (Object obj : connectionSet) {
            if (obj instanceof AccountingManagedConnection) {
                return (AccountingManagedConnection) obj;
            }
        }
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
    public ResourceAdapter getResourceAdapter() {
        return ra;
    }

    @Override
    public void setResourceAdapter(ResourceAdapter ra) throws ResourceException {
        this.ra = ra;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountingManagedConnectionFactory that = (AccountingManagedConnectionFactory) o;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
