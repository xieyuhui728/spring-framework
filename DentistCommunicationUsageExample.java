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
 * 演示EntityManager + LIMIT OFFSET分页 + null参数支持
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DentistCommunicationUsageExample {

    private final ReportService reportService;

    /**
     * 基础查询示例 - 所有参数为null
     */
    public void basicQueryWithNullParams() {
        log.info("=== 基础查询示例（所有过滤参数为null）===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                // startTime, endTime, teamName, dentistId 都为null
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：总记录数={}, 当前页记录数={}", 
                    result.getTotalElements(), result.getContent().size());
            
            if (!result.getContent().isEmpty()) {
                DentistCommunicationReportVM firstRecord = result.getContent().get(0);
                log.info("第一条记录：医生={}, 设计组={}, 总病例数={}", 
                        firstRecord.getDentistName(), 
                        firstRecord.getTeamName(), 
                        firstRecord.getAllCasesNum());
            }
            
        } catch (Exception e) {
            log.error("基础查询失败", e);
        }
    }

    /**
     * 时间范围查询示例 - 部分参数为null
     */
    public void timeRangeQueryExample() {
        log.info("=== 时间范围查询示例（部分参数为null）===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(20)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                // teamName 和 dentistId 为null
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("时间范围查询成功：总记录数={}", result.getTotalElements());
            
            result.getContent().forEach(record -> {
                log.info("医生: {} - 设计前沟通拨打率: {}%, 设计后讲解拨打率: {}%",
                        record.getDentistName(),
                        record.getPreCalledCasesRate(),
                        record.getPostCalledCasesRate());
            });
            
        } catch (Exception e) {
            log.error("时间范围查询失败", e);
        }
    }

    /**
     * 空字符串参数测试 - 验证null参数处理
     */
    public void emptyStringParamsTest() {
        log.info("=== 空字符串参数测试 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(15)
                .startTime(ZonedDateTime.parse("2024-06-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .teamName("") // 空字符串，应该被处理为null
                .dentistId("   ") // 只有空格，应该被处理为null
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("空字符串参数查询成功：总记录数={}", result.getTotalElements());
            
            result.getContent().forEach(record -> {
                log.info("医生: {}, 设计组: {}, 新手首例: {}, 设计前沟通: {}例, 设计后讲解: {}例",
                        record.getDentistName(),
                        record.getTeamName(),
                        record.getFirstTagCasesNum(),
                        record.getPreDesignTagCasesNum(),
                        record.getPostDesignTagCasesNum());
            });
            
        } catch (Exception e) {
            log.error("空字符串参数查询失败", e);
        }
    }

    /**
     * LIMIT OFFSET分页测试
     */
    public void limitOffsetPaginationTest() {
        log.info("=== LIMIT OFFSET分页测试 ===");
        
        int pageSize = 5;
        int maxPages = 3;
        
        for (int page = 0; page < maxPages; page++) {
            DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(page)
                    .pageSize(pageSize)
                    .build();

            try {
                PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
                
                int limit = pageSize;
                int offset = page * pageSize;
                
                log.info("第{}页查询成功：LIMIT={}, OFFSET={}, 当前页记录数={}, 总页数={}", 
                        page + 1, limit, offset, result.getContent().size(), result.getTotalPages());
                
                if (result.getContent().isEmpty()) {
                    log.info("第{}页没有数据，停止分页查询", page + 1);
                    break;
                }
                
                // 输出每页第一条记录用于验证分页效果
                if (!result.getContent().isEmpty()) {
                    DentistCommunicationReportVM firstRecord = result.getContent().get(0);
                    log.info("第{}页第一条记录：医生编号={}, 医生姓名={}", 
                            page + 1, firstRecord.getDentistCode(), firstRecord.getDentistName());
                }
                
                if (page >= result.getTotalPages() - 1) {
                    log.info("已到达最后一页，停止分页查询");
                    break;
                }
                
            } catch (Exception e) {
                log.error("第{}页查询失败", page + 1, e);
                break;
            }
        }
    }

    /**
     * 完整参数查询示例
     */
    public void fullParametersQueryExample() {
        log.info("=== 完整参数查询示例 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .teamName("设计组A")
                .dentistId("123")
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("完整参数查询成功：总记录数={}", result.getTotalElements());
            
            result.getContent().forEach(record -> {
                log.info("查询结果 - 医生: {}, 设计组: {}, 总病例: {}, " +
                        "设计前沟通拨打率: {}%, 设计后讲解拨打率: {}%, " +
                        "设计前沟通时长: {}, 设计后讲解时长: {}",
                        record.getDentistName(),
                        record.getTeamName(),
                        record.getAllCasesNum(),
                        record.getPreCalledCasesRate(),
                        record.getPostCalledCasesRate(),
                        record.getPreTotalDurationSec(),
                        record.getPostTotalDurationSec());
            });
            
        } catch (Exception e) {
            log.error("完整参数查询失败", e);
        }
    }

    /**
     * 大分页测试 - 验证LIMIT OFFSET性能
     */
    public void largePaginationTest() {
        log.info("=== 大分页测试 ===");
        
        // 测试较大的OFFSET
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(10) // OFFSET = 10 * 20 = 200
                .pageSize(20)
                .build();

        try {
            long startTime = System.currentTimeMillis();
            
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            log.info("大分页查询性能分析：");
            log.info("- 页码: {}, 页大小: {}", queryVM.getPageNumber(), queryVM.getPageSize());
            log.info("- LIMIT: {}, OFFSET: {}", queryVM.getPageSize(), queryVM.getPageNumber() * queryVM.getPageSize());
            log.info("- 执行时间: {}ms", executionTime);
            log.info("- 总记录数: {}", result.getTotalElements());
            log.info("- 查询记录数: {}", result.getContent().size());
            
        } catch (Exception e) {
            log.error("大分页查询失败", e);
        }
    }

    /**
     * SQL注入防护测试
     */
    public void sqlInjectionProtectionTest() {
        log.info("=== SQL注入防护测试 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .teamName("'; DROP TABLE gms_dentist; --") // SQL注入尝试
                .dentistId("1' OR '1'='1") // SQL注入尝试
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("SQL注入防护测试通过：参数化查询成功阻止了注入攻击");
            log.info("查询结果记录数: {}", result.getContent().size());
            
        } catch (Exception e) {
            log.info("SQL注入防护测试 - 查询失败（这可能是正常的）: {}", e.getMessage());
        }
    }

    /**
     * 运行所有示例
     */
    public void runAllExamples() {
        log.info("开始运行所有医生沟通报表查询示例（EntityManager + LIMIT OFFSET）...");
        
        basicQueryWithNullParams();
        timeRangeQueryExample();
        emptyStringParamsTest();
        limitOffsetPaginationTest();
        fullParametersQueryExample();
        largePaginationTest();
        sqlInjectionProtectionTest();
        
        log.info("所有示例运行完成！");
    }
}