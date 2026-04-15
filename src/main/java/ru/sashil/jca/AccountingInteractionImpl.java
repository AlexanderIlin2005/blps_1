package ru.sashil.jca;

import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.Interaction;
import jakarta.resource.cci.InteractionSpec;
import jakarta.resource.cci.Record;
import jakarta.resource.cci.ResourceWarning;
import ru.sashil.config.ApplicationContextProvider;
import ru.sashil.service.AccountingExternalSystem;

public class AccountingInteractionImpl implements Interaction {
    private final AccountingConnectionImpl connection;

    public AccountingInteractionImpl(AccountingConnectionImpl connection) {
        this.connection = connection;
    }

    @Override
    public boolean execute(InteractionSpec ispec, Record input, Record output) throws ResourceException {
        if (!(input instanceof StringRecord)) {
            throw new ResourceException("Input must be a StringRecord");
        }
        StringRecord strInput = (StringRecord) input;
        String payload = strInput.getPayload();

        try {
            AccountingExternalSystem accountingService =
                ApplicationContextProvider.getApplicationContext().getBean(AccountingExternalSystem.class);
            
            String response = accountingService.processPayload(payload);
            
            if (output instanceof StringRecord) {
                ((StringRecord) output).setPayload(response);
            }
            return true;
        } catch (Exception e) {
            throw new ResourceException("Error during interaction", e);
        }
    }

    @Override
    public Record execute(InteractionSpec ispec, Record input) throws ResourceException {
        StringRecord output = new StringRecord();
        execute(ispec, input, output);
        return output;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws ResourceException {
    }

    @Override
    public ResourceWarning getWarnings() throws ResourceException {
        return null;
    }

    @Override
    public void clearWarnings() throws ResourceException {
    }
}
