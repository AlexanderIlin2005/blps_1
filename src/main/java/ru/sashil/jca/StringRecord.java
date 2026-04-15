package ru.sashil.jca;

import jakarta.resource.cci.Record;

public class StringRecord implements Record {
    private String recordName;
    private String recordShortDescription;
    private String payload;

    @Override
    public String getRecordName() {
        return recordName;
    }

    @Override
    public void setRecordName(String name) {
        this.recordName = name;
    }

    @Override
    public void setRecordShortDescription(String description) {
        this.recordShortDescription = description;
    }

    @Override
    public String getRecordShortDescription() {
        return recordShortDescription;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
