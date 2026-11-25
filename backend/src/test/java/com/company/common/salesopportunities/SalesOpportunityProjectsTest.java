package com.company.common.salesopportunities;

import com.company.common.projects.dto.ProjectContracts;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.CommonProjectService;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.Win;
import com.company.common.salesopportunities.mapper.SalesOpportunityRows.Header;
import com.company.common.salesopportunities.service.SalesOpportunityProjects;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesOpportunityProjectsTest {
    @Test void winUsesExistingProjectCreateContractAndCopiesSalesFields() {
        var mapper = mock(ProjectMapper.class);
        var projects = mock(CommonProjectService.class);
        var adapter = new SalesOpportunityProjects(mapper, projects);
        var header = new Header("opportunity", "기회명", "customer", "고객명", "sales-part", "PROP", BigDecimal.TEN,
            "N", new BigDecimal("60000000"), null, "202611", 6, "서울", "1114000000", "서울 중구", null, null, "기회 비고", "ING",
            new BigDecimal("14"), "WAIT", null, null, null, null, null, "admin", Instant.parse("2026-11-15T00:00:00Z"));
        var input = new Win(header.modifiedAt(), new BigDecimal("55000000"), "TK", LocalDate.of(2026, 11, 1), LocalDate.of(2027, 4, 30), "PRE");
        var project = new ProjectContracts.Project(); project.projectId = "P-0001";
        when(projects.create(any(), isNull(), eq(true))).thenReturn(project);
        assertThat(adapter.create(header, input)).isEqualTo("P-0001");
        var capture = ArgumentCaptor.forClass(ProjectContracts.Input.class);
        // 수주로 옮기는 근무지 지역은 사용 중인지 다시 막지 않는다(carriedArea=true).
        verify(projects).create(capture.capture(), isNull(), eq(true));
        var created = capture.getValue();
        assertThat(created.projectName()).isEqualTo("기회명");
        assertThat(created.customerId()).isEqualTo("customer");
        assertThat(created.salesPartId()).isEqualTo("sales-part");
        assertThat(created.workplace()).isEqualTo("서울");
        assertThat(created.workAreaCode()).isEqualTo("1114000000"); // 근무지 지역도 같이 넘김(2026-11-25)
        assertThat(created.contractAmount()).isEqualByComparingTo("55000000");
        assertThat(created.estimateAmount()).isEqualByComparingTo("60000000");
        assertThat(created.contractTypeCode()).isEqualTo("TK");
        assertThat(created.statusCode()).isEqualTo("PRE");
        assertThat(created.startDate()).isEqualTo(input.contractStartDate());
        assertThat(created.endDate()).isEqualTo(input.contractEndDate());
    }
}
