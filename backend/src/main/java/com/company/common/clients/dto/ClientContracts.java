package com.company.common.clients.dto;

import java.time.Instant;
import java.util.List;

public final class ClientContracts {
    private ClientContracts() {}

    public record Item(String clientId, String clientTypeCode, String clientName, String businessNumber,
        String representativeName, String industryCode, String contactName, String contactPhone,
        String taxEmail, String bankCode, String useYn, String modifiedAt,
        String paymentAccountSummary, long projectCount, long personCount) {}
    public record Items<T>(List<T> items) {}
    public record Account(String bankCode, String accountNumber, String accountHolder) {}
    public record Save(Item client, List<String> warnings) {}
    public record Input(String clientTypeCode, String clientName, String businessNumber,
        String representativeName, String industryCode, String contactName, String contactPhone,
        String taxEmail, String bankCode, String accountNumber, String accountHolder) {}
    public record Update(Input input, Instant modifiedAt) {}
    public record Filter(String clientTypeCode, String useYn, String keyword) {}
    public record Version(String clientId, Instant modifiedAt) {}
    public record StateInput(List<Version> items, String useYn) {}
    public record Rejected(String clientId, String reason) {}
    public record StateResult(List<String> changed, List<Rejected> failed) {}
    public record Write(String clientId, Input input, String encryptedAccount, String actor,
        Instant modifiedAt) {}
    public record StateWrite(Version version, String useYn, String actor) {}
}
