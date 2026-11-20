package com.company.common.partsettlements.controller;

import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

final class PartSettlementInput {
    private PartSettlementInput() {}
    static void fields(JsonNode body,String... names) {
        if (body==null || !body.isObject()) throw CodeFailure.invalid("body","입력 내용을 확인해 주세요.");
        var allowed=Set.of(names);
        body.fieldNames().forEachRemaining(name->{ if (!allowed.contains(name)) throw CodeFailure.invalid(name,"허용되지 않은 입력 항목입니다."); });
        for (String name:names) if (!body.has(name)) throw CodeFailure.invalid(name,"필수 입력 항목을 확인해 주세요.");
    }
    static Instant version(JsonNode body) {
        if (!body.has("modifiedAt")) throw CodeFailure.invalid("modifiedAt","수정 시각을 전달해 주세요.");
        if (body.get("modifiedAt").isNull()) return null;
        try {
            if (!body.get("modifiedAt").isTextual()) throw new IllegalArgumentException();
            return Instant.parse(body.get("modifiedAt").textValue());
        } catch (RuntimeException e) { throw CodeFailure.invalid("modifiedAt","수정 시각 형식을 확인해 주세요."); }
    }
    static CardInput card(JsonNode body) {
        fields(body,"corpCardAmount","modifiedAt");
        if (!body.get("corpCardAmount").isIntegralNumber()) throw CodeFailure.invalid("corpCardAmount","법인카드 금액을 정수로 입력해 주세요.");
        return new CardInput(body.get("corpCardAmount").decimalValue(),version(body));
    }
    static CancelInput cancel(JsonNode body) {
        fields(body,"cancelReason","modifiedAt");
        if (!body.get("cancelReason").isTextual()) throw CodeFailure.invalid("cancelReason","확정 취소 사유를 입력해 주세요.");
        return new CancelInput(body.get("cancelReason").textValue(),version(body));
    }
    static CloseInput close(JsonNode body) {
        fields(body,"yearMonth","items");
        if (!body.get("yearMonth").isTextual()) throw CodeFailure.invalid("yearMonth","정산 년월을 확인해 주세요.");
        com.company.common.partsettlements.service.PartSettlementRules.month(body.get("yearMonth").textValue());
        if (!body.get("items").isArray()) throw CodeFailure.invalid("items","정산 점검 결과를 입력해 주세요.");
        var items=new ArrayList<CloseItem>();
        for (JsonNode row:body.get("items")) {
            fields(row,"partId","confirmed","modifiedAt");
            if (!row.get("partId").isTextual()) throw CodeFailure.invalid("partId","파트를 확인해 주세요.");
            if (!row.get("confirmed").isBoolean()) throw CodeFailure.invalid("confirmed","정산 점검 상태를 확인해 주세요.");
            items.add(new CloseItem(row.get("partId").textValue(),row.get("confirmed").booleanValue(),version(row)));
        }
        return new CloseInput(body.get("yearMonth").textValue(),items);
    }

}
