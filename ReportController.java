package com.example.controller;

import com.example.service.ReportService;
import com.example.vm.DentistCommunicationReportQueryVM;
import com.example.vm.DentistCommunicationReportVM;
import com.example.common.RespResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Api(tags = "医生沟通报表接口")
@Slf4j
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {
    
    private final ReportService reportService;

    @ApiOperation(value = "数字看板-新手首例沟通执行报表-医生维度", notes = "数字看板-新手首例沟通执行报表-医生维度")
    @PostMapping("/dentistCommunicationReport")
    public ResponseEntity<RespResult<PageImpl<DentistCommunicationReportVM>>> dentistCommunicationReport(
            @RequestBody @Valid DentistCommunicationReportQueryVM param) {
        
        log.info("接收到医生沟通报表查询请求: {}", param);
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(param);
            
            log.info("医生沟通报表查询成功，返回{}条记录，总{}条", 
                    result.getContent().size(), result.getTotalElements());
            
            return ResponseEntity.ok(RespResult.success(result));
            
        } catch (IllegalArgumentException e) {
            log.warn("医生沟通报表查询参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest().body(RespResult.error("400", e.getMessage()));
            
        } catch (Exception e) {
            log.error("医生沟通报表查询失败", e);
            return ResponseEntity.internalServerError().body(RespResult.error("500", "查询失败: " + e.getMessage()));
        }
    }

    @ApiOperation(value = "健康检查", notes = "检查服务是否正常运行")
    @GetMapping("/health")
    public ResponseEntity<RespResult<String>> health() {
        return ResponseEntity.ok(RespResult.success("医生沟通报表服务运行正常"));
    }
}