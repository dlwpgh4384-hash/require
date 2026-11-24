package com.company.common.common;

import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salespipeline.dto.SalesPipelineContracts.Forecast;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.service.SalesPipelineCalculationRules;
import com.company.common.salespipeline.service.SalesPipelineForecastService;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import java.sql.Connection;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Grouped-read regression for the existing forecast consumed unchanged by UI-28. */
@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ManpowerBoardQueryCountTest {
    private ManpowerBoardFixture fixture;

    @BeforeEach
    void independentSchema() throws Exception {
        fixture = new ManpowerBoardFixture();
    }

    @AfterEach
    void removeOwnedSchema() throws Exception {
        if (fixture != null) fixture.close();
    }

    @Test
    void sixMonthForecastQueryCountDoesNotGrowWithPeopleAndUsesRealSeedDefinitions() throws Exception {
        fixture.forecastSources();
        fixture.person("기준 인력");
        String before = fixture.businessSnapshot();
        Forecast small = forecast();
        Map<String, Integer> smallQueries = fixture.counter().calls();
        assertThat(fixture.businessSnapshot()).isEqualTo(before);
        for (int i = 0; i < 100; i++) fixture.person("추가 인력 " + i);
        String expanded = fixture.businessSnapshot();
        Forecast large = forecast();
        assertThat(fixture.counter().calls()).isEqualTo(smallQueries);
        assertThat(fixture.businessSnapshot()).isEqualTo(expanded);
        String namespace = SalesPipelineMapper.class.getName() + ".";
        for (String method : new String[]{"jobs", "revenues", "supply", "extensions",
            "opportunityDemand", "projectDemand", "opportunityRevenues", "candidates"}) {
            assertThat(smallQueries).containsEntry(namespace + method, 1);
        }
        assertThat(small.months()).extracting(month -> month.month()).containsExactly(
            "2026-12", "2027-01", "2027-02", "2027-03", "2027-04", "2027-05");
        assertThat(large.months()).hasSize(6);
        for (int i = 0; i < 6; i++) {
            var original = small.months().get(i);
            var grown = large.months().get(i);
            assertThat(grown.month()).isEqualTo(original.month());
            assertThat(grown.supply().subtract(original.supply())).isEqualByComparingTo("100");
            assertThat(grown.demand()).isEqualByComparingTo(original.demand());
            assertThat(grown.projectRevenue()).isEqualByComparingTo(original.projectRevenue());
            assertThat(grown.weightedOpportunityRevenue()).isEqualByComparingTo(original.weightedOpportunityRevenue());
            assertThat(grown.allWonRevenue()).isEqualByComparingTo(original.allWonRevenue());
        }
    }

    @Test
    void boardDefinitionReadsAreFixedWhenAnnualRosterGrows() {
        fixture.boardSources();
        readBoardDefinitions();
        var small = fixture.counter().calls();
        for (int i = 0; i < 100; i++) fixture.person("추가 인력 " + i);
        readBoardDefinitions();
        assertThat(fixture.counter().calls()).isEqualTo(small);
        assertThat(small).containsExactlyEntriesOf(Map.of(
            "com.company.common.calculation.mapper.CalculationRuleMapper.selectDefinition", 8));
    }

    private void readBoardDefinitions() {
        fixture.counter().reset();
        try (var session = fixture.factory().openSession()) {
            assertThat(new ManpowerBoardDefinitions(fixture.metadata(session)).definitions()).hasSize(8);
        }
    }

    private Forecast forecast() throws Exception {
        fixture.counter().reset();
        try (var session = fixture.factory().openSession()) {
            session.getConnection().setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            session.getConnection().setReadOnly(true);
            var metadata = fixture.metadata(session);
            var calendar = new BusinessCalendar(ManpowerBoardFixture.CLOCK);
            var calculations = new SalesPipelineCalculationRules(new SalesPipelineDefinitions(metadata),
                mock(SalesPipelineApprovalStandards.class), metadata, mock(SalesOpportunityMapper.class),
                mock(SalesPipelineCodes.class), calendar);
            var service = new SalesPipelineForecastService(session.getMapper(SalesPipelineMapper.class),
                new WithdrawalCalculationRules(metadata), calculations, calendar);
            return service.getCommonSalesPipelineForecast();
        }
    }
}
