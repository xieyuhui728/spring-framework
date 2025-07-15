package com.example.controller;

import com.example.service.ReportService;
import com.example.vm.DentistCommunicationReportQueryVM;
import com.example.vm.DentistCommunicationReportVM;
import com.example.common.RespResult;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {
    
    private final ReportService reportService;

    @ApiOperation(value = "数字看板-新手首例沟通执行报表-医生维度", notes = "数字看板-新手首例沟通执行报表-医生维度")
    @PostMapping("/dentistCommunicationReport")
    public ResponseEntity<RespResult<PageImpl<DentistCommunicationReportVM>>> dentistCommunicationReport(
            @RequestBody @Valid DentistCommunicationReportQueryVM param) {
        PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(param);
        return ResponseEntity.ok(RespResult.success(result));
    }
}