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
 * 演示如何在Service中使用EntityManager执行复杂SQL查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DentistCommunicationUsageExample {

    private final ReportService reportService;

    /**
     * 基础查询示例
     */
    public void basicQueryExample() {
        log.info("=== 基础查询示例 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("查询成功：总记录数={}, 当前页记录数={}", 
                    result.getTotalElements(), result.getContent().size());
            
            // 输出第一条记录示例
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
     * 带时间范围的查询示例
     */
    public void timeRangeQueryExample() {
        log.info("=== 时间范围查询示例 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(20)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("时间范围查询成功：总记录数={}", result.getTotalElements());
            
            // 统计沟通指标
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
     * 带过滤条件的查询示例
     */
    public void filteredQueryExample() {
        log.info("=== 过滤条件查询示例 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(15)
                .teamName("设计组A")
                .dentistId("123")
                .startTime(ZonedDateTime.parse("2024-06-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            log.info("过滤查询成功：总记录数={}", result.getTotalElements());
            
            result.getContent().forEach(record -> {
                log.info("过滤结果 - 医生: {}, 设计组: {}, 新手首例: {}, 设计前沟通: {}例, 设计后讲解: {}例",
                        record.getDentistName(),
                        record.getTeamName(),
                        record.getFirstTagCasesNum(),
                        record.getPreDesignTagCasesNum(),
                        record.getPostDesignTagCasesNum());
            });
            
        } catch (Exception e) {
            log.error("过滤查询失败", e);
        }
    }

    /**
     * 分页查询示例
     */
    public void paginationExample() {
        log.info("=== 分页查询示例 ===");
        
        int pageSize = 5;
        int maxPages = 3;
        
        for (int page = 0; page < maxPages; page++) {
            DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(page)
                    .pageSize(pageSize)
                    .build();

            try {
                PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
                
                log.info("第{}页查询成功：当前页记录数={}, 总页数={}", 
                        page + 1, result.getContent().size(), result.getTotalPages());
                
                if (result.getContent().isEmpty()) {
                    log.info("第{}页没有数据，停止分页查询", page + 1);
                    break;
                }
                
                // 如果已经是最后一页，也停止查询
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
     * 性能指标分析示例
     */
    public void performanceAnalysisExample() {
        log.info("=== 性能指标分析示例 ===");
        
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(50)
                .build();

        try {
            long startTime = System.currentTimeMillis();
            
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            log.info("查询性能分析：");
            log.info("- 执行时间: {}ms", executionTime);
            log.info("- 总记录数: {}", result.getTotalElements());
            log.info("- 查询记录数: {}", result.getContent().size());
            log.info("- 平均每条记录耗时: {}ms", 
                    result.getContent().isEmpty() ? 0 : executionTime / result.getContent().size());
            
            // 分析数据质量
            long validRecords = result.getContent().stream()
                    .filter(record -> !record.getDentistName().isEmpty() 
                            && !record.getDentistCode().isEmpty())
                    .count();
                    
            log.info("- 有效记录数: {}", validRecords);
            log.info("- 数据完整率: {}%", 
                    result.getContent().isEmpty() ? 0 : 
                    (validRecords * 100.0 / result.getContent().size()));
            
        } catch (Exception e) {
            log.error("性能分析查询失败", e);
        }
    }

    /**
     * 运行所有示例
     */
    public void runAllExamples() {
        log.info("开始运行所有医生沟通报表查询示例...");
        
        basicQueryExample();
        timeRangeQueryExample();
        filteredQueryExample();
        paginationExample();
        performanceAnalysisExample();
        
        log.info("所有示例运行完成！");
    }
}