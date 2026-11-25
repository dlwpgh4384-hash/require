package com.company.common.financeitems.mapper;

import com.company.common.financeitems.dto.FinanceItemContracts.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FinanceItemMapper {
    void lockSources();
    boolean isClosed(@Param("month") String month);
    List<String> closedMonths();
    Instant baseline(@Param("month") String month);
    List<Item> items(@Param("month") String month);
    List<Loan> loans(@Param("month") String month);
    List<Transfer> transfers(@Param("month") String month);
    List<Adjustment> adjustments(@Param("month") String month);
    List<Card> cards(@Param("month") String month);
    TaxEstimate estimate(@Param("year") String year);
    List<TaxPayment> payments(@Param("year") String year);
    List<TaxPayment> allPayments();
    TaxPayment payment(PaymentKey key);
    CardPayment cardPayment(@Param("month") String month);
    BigDecimal fixedTax(@Param("month") String month);
    String code(@Param("group") String group, @Param("value") String value);
    String codeName(@Param("group") String group, @Param("code") String code);
    boolean duplicateCard(Card row);
    int prepareYear(@Param("year") String year, @Param("actor") String actor);
    int preparePayments(@Param("year") String year, @Param("actor") String actor);
    int confirm(PaymentWrite write);
    int updateEstimate(Write<EstimateInput> write);
    int updatePayment(Write<PaymentInput> write);
    int insertItem(Write<Item> write);
    int updateItem(Write<Item> write);
    int insertLoan(Write<Loan> write);
    int updateLoan(Write<Loan> write);
    int insertTransfer(Write<Transfer> write);
    int updateTransfer(Write<Transfer> write);
    int insertAdjustment(Write<Adjustment> write);
    int updateAdjustment(Write<Adjustment> write);
    int insertCard(Write<Card> write);
    int updateCard(Write<Card> write);
    int updateCardMonth(Write<Card> write);
    int saveCardPayment(@Param("write") Write<CardPayment> write, @Param("total") BigDecimal total);
    int deleteItem(Version version);
    int deleteLoan(Version version);
    int deleteTransfer(Version version);
    int deleteAdjustment(Version version);
    int deleteUploaded(@Param("month") String month);
    long nextItem(@Param("month") String month);
    long nextLoan();
    long nextTransfer();
    long nextAdjustment();
    int history(History history);
    int updateProfit(@Param("row") Profit row, @Param("actor") String actor);
    List<CashContribution> cashContributions();
}
