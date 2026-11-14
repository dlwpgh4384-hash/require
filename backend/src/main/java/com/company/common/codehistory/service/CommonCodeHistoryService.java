package com.company.common.codehistory.service;

import com.company.common.codehistory.dto.CodeHistoryContracts.Result;
import com.company.common.codehistory.mapper.CodeHistoryRow;
import com.company.common.codehistory.mapper.CommonCodeHistoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonCodeHistoryService {
    private final CommonCodeHistoryMapper mapper;

    public CommonCodeHistoryService(CommonCodeHistoryMapper mapper) {
        this.mapper = mapper;
    }

    public Result getCommonCodeChangeHistory() {
        return new Result(mapper.selectChanges().stream().map(CodeHistoryRow::value).toList());
    }
}
