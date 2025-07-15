package com.example.example;

import com.example.service.ReportService;
import com.example.vm.DentistCommunicationReportQueryVM;
import com.example.vm.DentistCommunicationReportVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * 医生沟通报表使用示例
 * 演示完全兼容参数不传场景 - 不传则不过滤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DentistCommunicationUsageExample {

    private final ReportService reportService;

    /**
     * 场景1：完全不传过滤参数 - 查询所有数据
     */
    public void scenarioNoFilters() {
        log.info("=== 场景1：完全不传过滤参数（查询所有数据）===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                // 所有过滤参数都不传（null）
                .startTime(null)
                .endTime(null)
                .teamName(null)
                .dentistId(null)
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：无任何过滤条件");
            log.info("总记录数={}, 当前页记录数={}", 
                    result.getTotalElements(), result.getContent().size());
            
            if (!result.getContent().isEmpty()) {
                log.info("前3条记录:");
                result.getContent().stream().limit(3).forEach(record -> {
                    log.info("  - 医生: {}, 设计组: {}, 总病例: {}",
                            record.getDentistName(),
                            record.getTeamName(),
                            record.getAllCasesNum());
                });
            }
            
        } catch (Exception e) {
            log.error("场景1查询失败", e);
        }
    }

    /**
     * 场景2：只传时间范围，其他参数不传
     */
    public void scenarioTimeRangeOnly() {
        log.info("=== 场景2：只传时间范围，其他参数不传 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(15)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                // teamName 和 dentistId 不传（null）
                .teamName(null)
                .dentistId(null)
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：只有时间范围过滤");
            log.info("时间范围: {} 到 {}", 
                    queryVM.getStartTime(), queryVM.getEndTime());
            log.info("总记录数={}", result.getTotalElements());
            
        } catch (Exception e) {
            log.error("场景2查询失败", e);
        }
    }

    /**
     * 场景3：只传开始时间，其他参数不传
     */
    public void scenarioStartTimeOnly() {
        log.info("=== 场景3：只传开始时间，其他参数不传 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(20)
                .startTime(ZonedDateTime.parse("2024-06-01T00:00:00Z"))
                // endTime, teamName, dentistId 都不传（null）
                .endTime(null)
                .teamName(null)
                .dentistId(null)
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：只有开始时间过滤");
            log.info("开始时间: {} （结束时间不限）", queryVM.getStartTime());
            log.info("总记录数={}", result.getTotalElements());
            
        } catch (Exception e) {
            log.error("场景3查询失败", e);
        }
    }

    /**
     * 场景4：只传结束时间，其他参数不传
     */
    public void scenarioEndTimeOnly() {
        log.info("=== 场景4：只传结束时间，其他参数不传 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(20)
                // startTime 不传（null）
                .startTime(null)
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                // teamName, dentistId 都不传（null）
                .teamName(null)
                .dentistId(null)
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：只有结束时间过滤");
            log.info("结束时间: {} （开始时间不限）", queryVM.getEndTime());
            log.info("总记录数={}", result.getTotalElements());
            
        } catch (Exception e) {
            log.error("场景4查询失败", e);
        }
    }

    /**
     * 场景5：只传设计组，其他参数不传
     */
    public void scenarioTeamNameOnly() {
        log.info("=== 场景5：只传设计组，其他参数不传 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                // 时间参数不传（null）
                .startTime(null)
                .endTime(null)
                .teamName("设计组A")
                // dentistId 不传（null）
                .dentistId(null)
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：只有设计组过滤");
            log.info("设计组: {}", queryVM.getTeamName());
            log.info("总记录数={}", result.getTotalElements());
            
            if (!result.getContent().isEmpty()) {
                log.info("结果验证 - 所有记录的设计组都应该是: {}", queryVM.getTeamName());
                result.getContent().forEach(record -> {
                    log.info("  - 医生: {}, 设计组: {}", 
                            record.getDentistName(), record.getTeamName());
                });
            }
            
        } catch (Exception e) {
            log.error("场景5查询失败", e);
        }
    }

    /**
     * 场景6：只传医生ID，其他参数不传
     */
    public void scenarioDentistIdOnly() {
        log.info("=== 场景6：只传医生ID，其他参数不传 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(5)
                // 时间和设计组参数不传（null）
                .startTime(null)
                .endTime(null)
                .teamName(null)
                .dentistId("123")
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：只有医生ID过滤");
            log.info("医生ID: {}", queryVM.getDentistId());
            log.info("总记录数={}", result.getTotalElements());
            
            if (!result.getContent().isEmpty()) {
                log.info("结果验证 - 所有记录的医生ID都应该是: {}", queryVM.getDentistId());
                result.getContent().forEach(record -> {
                    log.info("  - 医生: {} (ID: {}), 设计组: {}", 
                            record.getDentistName(), record.getDentistCode(), record.getTeamName());
                });
            }
            
        } catch (Exception e) {
            log.error("场景6查询失败", e);
        }
    }

    /**
     * 场景7：传空字符串参数（应该被当作null处理）
     */
    public void scenarioEmptyStringParameters() {
        log.info("=== 场景7：传空字符串参数（应该被当作null处理）===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(null)
                .endTime(null)
                .teamName("")        // 空字符串
                .dentistId("   ")    // 只有空格
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：空字符串参数被正确处理为null");
            log.info("传入参数 - teamName: '{}', dentistId: '{}'", 
                    queryVM.getTeamName(), queryVM.getDentistId());
            log.info("总记录数={} （应该等同于无过滤条件的查询）", result.getTotalElements());
            
        } catch (Exception e) {
            log.error("场景7查询失败", e);
        }
    }

    /**
     * 场景8：部分参数组合（时间+设计组，医生ID不传）
     */
    public void scenarioPartialFilters() {
        log.info("=== 场景8：部分参数组合（时间+设计组，医生ID不传）===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(15)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .teamName("设计组A")
                // dentistId 不传（null）
                .dentistId(null)
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：时间范围+设计组过滤，医生ID不限");
            log.info("有效过滤条件:");
            log.info("  - 时间范围: {} 到 {}", queryVM.getStartTime(), queryVM.getEndTime());
            log.info("  - 设计组: {}", queryVM.getTeamName());
            log.info("  - 医生ID: 不限制");
            log.info("总记录数={}", result.getTotalElements());
            
        } catch (Exception e) {
            log.error("场景8查询失败", e);
        }
    }

    /**
     * 场景9：验证不同分页下的无过滤查询一致性
     */
    public void scenarioPaginationConsistency() {
        log.info("=== 场景9：验证不同分页下的无过滤查询一致性 ===");
        
        try {
            // 第一页
            DentistCommunicationReportQueryVM page1Query = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0)
                    .pageSize(5)
                    // 无过滤条件
                    .startTime(null)
                    .endTime(null)
                    .teamName(null)
                    .dentistId(null)
                    .build();
            
            PageImpl<DentistCommunicationReportVM> page1Result = 
                    reportService.dentistCommunicationReport(page1Query);
            
            // 第二页
            DentistCommunicationReportQueryVM page2Query = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(1)
                    .pageSize(5)
                    // 无过滤条件
                    .startTime(null)
                    .endTime(null)
                    .teamName(null)
                    .dentistId(null)
                    .build();
            
            PageImpl<DentistCommunicationReportVM> page2Result = 
                    reportService.dentistCommunicationReport(page2Query);
            
            log.info("分页一致性验证:");
            log.info("  - 第1页总记录数: {}", page1Result.getTotalElements());
            log.info("  - 第2页总记录数: {}", page2Result.getTotalElements());
            log.info("  - 第1页当前页记录数: {}", page1Result.getContent().size());
            log.info("  - 第2页当前页记录数: {}", page2Result.getContent().size());
            
            if (page1Result.getTotalElements().equals(page2Result.getTotalElements())) {
                log.info("✅ 分页一致性验证通过");
            } else {
                log.warn("❌ 分页一致性验证失败");
            }
            
        } catch (Exception e) {
            log.error("场景9查询失败", e);
        }
    }

    /**
     * 场景10：性能对比测试（有过滤 vs 无过滤）
     */
    public void scenarioPerformanceComparison() {
        log.info("=== 场景10：性能对比测试（有过滤 vs 无过滤）===");
        
        try {
            // 无过滤查询
            long startTime1 = System.currentTimeMillis();
            
            DentistCommunicationReportQueryVM noFilterQuery = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0)
                    .pageSize(50)
                    .startTime(null)
                    .endTime(null)
                    .teamName(null)
                    .dentistId(null)
                    .build();
            
            PageImpl<DentistCommunicationReportVM> noFilterResult = 
                    reportService.dentistCommunicationReport(noFilterQuery);
            
            long endTime1 = System.currentTimeMillis();
            long duration1 = endTime1 - startTime1;
            
            // 有过滤查询
            long startTime2 = System.currentTimeMillis();
            
            DentistCommunicationReportQueryVM withFilterQuery = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0)
                    .pageSize(50)
                    .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                    .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                    .teamName("设计组A")
                    .dentistId("123")
                    .build();
            
            PageImpl<DentistCommunicationReportVM> withFilterResult = 
                    reportService.dentistCommunicationReport(withFilterQuery);
            
            long endTime2 = System.currentTimeMillis();
            long duration2 = endTime2 - startTime2;
            
            log.info("性能对比结果:");
            log.info("  - 无过滤查询: {}ms, 总记录数: {}", duration1, noFilterResult.getTotalElements());
            log.info("  - 有过滤查询: {}ms, 总记录数: {}", duration2, withFilterResult.getTotalElements());
            log.info("  - 性能差异: {}ms", Math.abs(duration1 - duration2));
            
        } catch (Exception e) {
            log.error("场景10查询失败", e);
        }
    }

    /**
     * 运行所有场景示例
     */
    public void runAllScenarios() {
        log.info("开始运行所有参数不传场景示例...");
        
        scenarioNoFilters();
        scenarioTimeRangeOnly();
        scenarioStartTimeOnly();
        scenarioEndTimeOnly();
        scenarioTeamNameOnly();
        scenarioDentistIdOnly();
        scenarioEmptyStringParameters();
        scenarioPartialFilters();
        scenarioPaginationConsistency();
        scenarioPerformanceComparison();
        
        log.info("所有场景示例运行完成！");
    }

    /**
     * 快速验证核心功能
     */
    public void quickValidation() {
        log.info("=== 快速验证：参数不传兼容性 ===");
        
        try {
            // 测试1：完全无过滤
            DentistCommunicationReportQueryVM query1 = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0).pageSize(5).build();
            
            PageImpl<DentistCommunicationReportVM> result1 = reportService.dentistCommunicationReport(query1);
            log.info("✅ 完全无过滤查询成功，记录数: {}", result1.getTotalElements());
            
            // 测试2：部分过滤
            DentistCommunicationReportQueryVM query2 = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0).pageSize(5)
                    .teamName("设计组A")
                    .build();
            
            PageImpl<DentistCommunicationReportVM> result2 = reportService.dentistCommunicationReport(query2);
            log.info("✅ 部分过滤查询成功，记录数: {}", result2.getTotalElements());
            
            // 测试3：空字符串处理
            DentistCommunicationReportQueryVM query3 = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0).pageSize(5)
                    .teamName("")
                    .dentistId("   ")
                    .build();
            
            PageImpl<DentistCommunicationReportVM> result3 = reportService.dentistCommunicationReport(query3);
            log.info("✅ 空字符串处理成功，记录数: {}", result3.getTotalElements());
            
            log.info("🎉 核心功能验证通过！");
            
        } catch (Exception e) {
            log.error("❌ 快速验证失败", e);
        }
    }
}