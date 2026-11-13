package com.company.common.commonops.controller;

import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CodeExceptionHandler;
import com.company.common.controller.CommonCodeHistoryController;
import com.company.common.mapper.CodeHistoryRow;
import com.company.common.mapper.CommonCodeHistoryMapper;
import com.company.common.service.CommonCodeHistoryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonCodeHistoryController.class)
@ContextConfiguration(classes = {CommonCodeHistoryController.class, CommonCodeHistoryService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonCodeHistoryControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CommonCodeHistoryMapper mapper;

    private CodeHistoryRow row(String kind, String item) {
        var row = new CodeHistoryRow();
        row.changedAt = Instant.parse("2026-11-13T00:01:02Z");
        row.kind = kind;
        row.item = item;
        row.before = "이전 값";
        row.after = "새 값";
        row.processor = "admin";
        return row;
    }

    @Test void anonymousReadHasExactlyTheApprovedEnvelopeAndSixFields() throws Exception {
        when(mapper.selectChanges()).thenReturn(List.of(
            row("코드", "CODE_NM"), row("그룹", "CODE_GRP_NM"),
            row("운영 기준값", "STD_VAL"), row("표기 대응", "엑셀 표기 대응")));
        mvc.perform(get("/api/common/code-change-history"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items.length()").value(4))
            .andExpect(jsonPath("$.data.items[0].length()").value(6))
            .andExpect(jsonPath("$.data.items[0].changedAt").value("2026-11-13T00:01:02Z"))
            .andExpect(jsonPath("$.data.items[0].kind").value("코드"))
            .andExpect(jsonPath("$.data.items[0].item").value("CODE_NM"))
            .andExpect(jsonPath("$.data.items[0].before").value("이전 값"))
            .andExpect(jsonPath("$.data.items[0].after").value("새 값"))
            .andExpect(jsonPath("$.data.items[*].processor", everyItem(is("admin"))));
        verify(mapper).selectChanges();
        verifyNoMoreInteractions(mapper);
    }

    @Test void emptyDatabaseResultRemainsAnEmptyArray() throws Exception {
        when(mapper.selectChanges()).thenReturn(List.of());
        mvc.perform(get("/api/common/code-change-history"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test void databaseFailureIsNotAnEmptySuccessAndDoesNotLeakSql() throws Exception {
        when(mapper.selectChanges()).thenThrow(new IllegalStateException("private SQL detail"));
        mvc.perform(get("/api/common/code-change-history"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(content().string(not(containsString("private SQL detail"))));
    }

    @Test void noHistoryMutationEndpointIsIntroduced() throws Exception {
        mvc.perform(post("/api/common/code-change-history"))
            .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(mapper);
    }
}
