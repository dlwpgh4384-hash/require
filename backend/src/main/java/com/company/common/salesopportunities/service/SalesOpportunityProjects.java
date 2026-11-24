package com.company.common.salesopportunities.service;

import com.company.common.projects.dto.ProjectContracts;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.CommonProjectService;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.Win;
import com.company.common.salesopportunities.mapper.SalesOpportunityRows.Header;
import org.springframework.stereotype.Component;

@Component
public class SalesOpportunityProjects {
    private final ProjectMapper mapper;
    private final CommonProjectService projects;

    public SalesOpportunityProjects(ProjectMapper mapper, CommonProjectService projects) {
        this.mapper = mapper;
        this.projects = projects;
    }
    public void lockSources() { mapper.lockSources(); }
    public String create(Header opportunity, Win input) {
        var project = projects.create(new ProjectContracts.Input(opportunity.opportunityName(),
            opportunity.customerId(), input.contractTypeCode(), input.contractStartDate(), input.contractEndDate(),
            opportunity.salesPartId(), opportunity.workplace(), input.projectStatusCode(), opportunity.expectedAmount(),
            input.contractAmount(), null, null, null, opportunity.memo()), null);
        return project.projectId;
    }
}
