package com.company.common.preferences.mapper;

import java.time.Instant;

public class UserPreferenceRow {
    private String prefVal;
    private Instant modDttm;

    public String getPrefVal() { return prefVal; }
    public void setPrefVal(String value) { prefVal = value; }
    public Instant getModDttm() { return modDttm; }
    public void setModDttm(Instant value) { modDttm = value; }
}
