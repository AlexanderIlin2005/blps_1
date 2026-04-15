package ru.sashil.jca;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.Connector;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAResource;

@Connector(
    displayName = "Accounting Resource Adapter",
    vendorName = "Sashil",
    eisType = "Accounting",
    version = "1.0"
)
public class AccountingResourceAdapter implements ResourceAdapter {

    @Override
    public void start(BootstrapContext ctx) throws ResourceAdapterInternalException {
    }

    @Override
    public void stop() {
    }

    @Override
    public void endpointActivation(MessageEndpointFactory endpointFactory, jakarta.resource.spi.ActivationSpec spec) throws ResourceException {
    }

    @Override
    public void endpointDeactivation(MessageEndpointFactory endpointFactory, jakarta.resource.spi.ActivationSpec spec) {
    }

    @Override
    public XAResource[] getXAResources(jakarta.resource.spi.ActivationSpec[] specs) throws ResourceException {
        return new XAResource[0];
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        return getClass() == obj.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
