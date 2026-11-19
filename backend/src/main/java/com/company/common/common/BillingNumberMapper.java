package com.company.common.common;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BillingNumberMapper {
    void lockBills();
    int nextMonthNumber(String month);
    int nextProjectOrder(String projectId);
}
