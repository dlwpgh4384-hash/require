package com.company.common.financeitems.service;

import com.company.common.financeitems.dto.FinanceItemContracts.UploadResult;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.support.service.CodeFailure;
import com.company.common.uploads.dto.UploadContracts.Apply;
import com.company.common.uploads.service.CommonUploadService;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class FinanceItemUploadService {
    private final CommonUploadService uploads;
    private final CommonFinanceItemService months;
    private final FinanceItemMapper mapper;
    public FinanceItemUploadService(CommonUploadService uploads,CommonFinanceItemService months,FinanceItemMapper mapper) {
        this.uploads=uploads; this.months=months; this.mapper=mapper;
    }
    public UploadResult postCommonFinanceItemsUpload(String month,Instant version,String fileName,byte[] bytes) {
        var target=FinanceItemRules.month(month);
        var checked=uploads.postCommonUpload("B2",target.toString(),fileName,bytes);
        if (checked.errorCount()>0) {
            var issue=checked.issues().stream().filter(i->"오류".equals(i.severity())).findFirst().orElseThrow();
            throw CodeFailure.invalid("file.rows["+issue.row()+"]."+issue.column(),issue.reason());
        }
        int[] counts=new int[2];
        var applied=uploads.postCommonUploadApply(checked.uploadId(),new Apply(checked.modifiedAt()==null ? null : Instant.parse(checked.modifiedAt())),()->{
            months.lockAndCheck(month,version);
            var rows=mapper.items(month);
            counts[0]=(int)rows.stream().filter(i->i.uploadId()!=null).count();
            counts[1]=rows.size()-counts[0];
        });
        if (applied.errorCount()>0) throw CodeFailure.invalid("file","파일을 다시 검사해 주세요.");
        return new UploadResult(applied.uploadId(),mapper.items(month),counts[0],counts[1]);
    }
}
