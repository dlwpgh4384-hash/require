package com.company.common.mapper;

import java.time.LocalDateTime;

public class UserPreferenceRow {
    private String prefVal;
    private LocalDateTime modDttm;

    public String getPrefVal() { return prefVal; }
    public void setPrefVal(String value) { prefVal = value; }
    public LocalDateTime getModDttm() { return modDttm; }
    public void setModDttm(LocalDateTime value) { modDttm = value; }
}
