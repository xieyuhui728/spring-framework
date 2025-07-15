package com.example;

import com.example.service.ReportService;
import com.example.vm.DentistCommunicationReportQueryVM;
import com.example.vm.DentistCommunicationReportVM;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZonedDateTime;

/**
 * 医生沟通报表测试类
 * 演示如何使用EntityManager进行复杂查询
 */
@SpringBootTest
@ActiveProfiles("test") // 使用测试环境配置
public class DentistCommunicationReportTest {

    @Autowired
    private ReportService reportService;

    @Test
    public void testDentistCommunicationReport() {
        // 构建查询参数
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .teamName("设计组A")
                .dentistId("123")
                .build();

        try {
            // 执行查询
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            
            // 输出结果
            System.out.println("=== 医生沟通执行报表查询结果 ===");
            System.out.println("总记录数: " + result.getTotalElements());
            System.out.println("总页数: " + result.getTotalPages());
            System.out.println("当前页: " + result.getNumber());
            System.out.println("每页大小: " + result.getSize());
            
            result.getContent().forEach(report -> {
                System.out.println("------------------");
                System.out.println("设计组: " + report.getTeamName());
                System.out.println("医生: " + report.getDentistName());
                System.out.println("医生编号: " + report.getDentistCode());
                System.out.println("总病例数: " + report.getAllCasesNum());
                System.out.println("新手首例病例数: " + report.getFirstTagCasesNum());
                System.out.println("设计前沟通病例数: " + report.getPreDesignTagCasesNum());
                System.out.println("设计前沟通拨打率: " + report.getPreCalledCasesRate() + "%");
                System.out.println("设计后讲解病例数: " + report.getPostDesignTagCasesNum());
                System.out.println("设计后讲解拨打率: " + report.getPostCalledCasesRate() + "%");
            });
            
        } catch (Exception e) {
            System.err.println("查询失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void testWithMinimalParameters() {
        // 测试最小参数查询
        DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(5)
                .build();

        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
            System.out.println("=== 最小参数查询结果 ===");
            System.out.println("总记录数: " + result.getTotalElements());
            System.out.println("返回记录数: " + result.getContent().size());
            
        } catch (Exception e) {
            System.err.println("查询失败: " + e.getMessage());
        }
    }

    @Test
    public void testPagination() {
        // 测试分页功能
        System.out.println("=== 分页测试 ===");
        
        for (int page = 0; page < 3; page++) {
            DentistCommunicationReportQueryVM queryVM = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(page)
                    .pageSize(2)
                    .build();

            try {
                PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(queryVM);
                System.out.println("第" + (page + 1) + "页，共" + result.getTotalPages() + "页");
                System.out.println("当前页记录数: " + result.getContent().size());
                
            } catch (Exception e) {
                System.err.println("第" + (page + 1) + "页查询失败: " + e.getMessage());
                break;
            }
        }
    }
}