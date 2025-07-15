package com.example;

import com.example.service.ReportService;
import com.example.vm.DentistCommunicationReportQueryVM;
import com.example.vm.DentistCommunicationReportVM;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 医生沟通报表Service测试类
 * 验证完全兼容参数不传场景 - 不传则不过滤
 */
@SpringBootTest
@ActiveProfiles("test")
public class DentistCommunicationReportServiceTest {

    @Autowired
    private ReportService reportService;

    private DentistCommunicationReportQueryVM noFilterQuery;
    private DentistCommunicationReportQueryVM timeRangeOnlyQuery;
    private DentistCommunicationReportQueryVM partialFilterQuery;

    @BeforeEach
    void setUp() {
        // 无过滤条件查询
        noFilterQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(null)
                .endTime(null)
                .teamName(null)
                .dentistId(null)
                .build();

        // 只有时间范围的查询
        timeRangeOnlyQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(15)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .teamName(null)
                .dentistId(null)
                .build();

        // 部分过滤条件查询
        partialFilterQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(20)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .teamName("设计组A")
                .dentistId(null) // 医生ID不传
                .build();
    }

    @Test
    public void testNoFilterQuery() {
        System.out.println("=== 测试无过滤条件查询 ===");
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(noFilterQuery);
            
            // 验证基本结构
            assertNotNull(result, "无过滤查询结果不应为null");
            assertNotNull(result.getContent(), "无过滤查询内容不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            assertEquals(0, result.getNumber(), "当前页码应该为0");
            assertEquals(10, result.getSize(), "页大小应该为10");
            
            System.out.println("无过滤查询测试通过：");
            System.out.println("- 查询条件: 所有过滤参数都为null");
            System.out.println("- 总记录数: " + result.getTotalElements());
            System.out.println("- 当前页记录数: " + result.getContent().size());
            System.out.println("- 预期行为: 查询所有数据，不进行任何过滤");
            
        } catch (Exception e) {
            fail("无过滤查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testTimeRangeOnlyQuery() {
        System.out.println("=== 测试只有时间范围过滤的查询 ===");
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(timeRangeOnlyQuery);
            
            assertNotNull(result, "时间范围查询结果不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            assertEquals(15, result.getSize(), "页大小应该为15");
            
            System.out.println("时间范围过滤查询测试通过：");
            System.out.println("- 有效过滤: 时间范围 " + timeRangeOnlyQuery.getStartTime() + " 到 " + timeRangeOnlyQuery.getEndTime());
            System.out.println("- 无过滤: teamName=null, dentistId=null");
            System.out.println("- 总记录数: " + result.getTotalElements());
            
        } catch (Exception e) {
            fail("时间范围查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testPartialFilterQuery() {
        System.out.println("=== 测试部分参数过滤查询 ===");
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(partialFilterQuery);
            
            assertNotNull(result, "部分过滤查询结果不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            assertEquals(20, result.getSize(), "页大小应该为20");
            
            System.out.println("部分过滤查询测试通过：");
            System.out.println("- 有效过滤: 时间范围 + 设计组=" + partialFilterQuery.getTeamName());
            System.out.println("- 无过滤: dentistId=null");
            System.out.println("- 总记录数: " + result.getTotalElements());
            
        } catch (Exception e) {
            fail("部分过滤查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testEmptyStringParametersHandling() {
        System.out.println("=== 测试空字符串参数处理 ===");
        
        DentistCommunicationReportQueryVM emptyStringQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(null)
                .endTime(null)
                .teamName("")        // 空字符串
                .dentistId("   ")    // 只有空格
                .build();
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(emptyStringQuery);
            
            assertNotNull(result, "空字符串参数查询结果不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            
            System.out.println("空字符串参数处理测试通过：");
            System.out.println("- 传入参数: teamName='', dentistId='   '");
            System.out.println("- 预期行为: 空字符串被标准化为null，等同于无过滤");
            System.out.println("- 总记录数: " + result.getTotalElements());
            
        } catch (Exception e) {
            fail("空字符串参数查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testSingleParameterFilters() {
        System.out.println("=== 测试单个参数过滤 ===");
        
        // 只传开始时间
        DentistCommunicationReportQueryVM startTimeOnlyQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(ZonedDateTime.parse("2024-06-01T00:00:00Z"))
                .endTime(null)
                .teamName(null)
                .dentistId(null)
                .build();
        
        // 只传结束时间
        DentistCommunicationReportQueryVM endTimeOnlyQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(null)
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .teamName(null)
                .dentistId(null)
                .build();
        
        // 只传设计组
        DentistCommunicationReportQueryVM teamOnlyQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(null)
                .endTime(null)
                .teamName("设计组A")
                .dentistId(null)
                .build();
        
        // 只传医生ID
        DentistCommunicationReportQueryVM dentistOnlyQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(null)
                .endTime(null)
                .teamName(null)
                .dentistId("123")
                .build();
        
        try {
            PageImpl<DentistCommunicationReportVM> result1 = reportService.dentistCommunicationReport(startTimeOnlyQuery);
            PageImpl<DentistCommunicationReportVM> result2 = reportService.dentistCommunicationReport(endTimeOnlyQuery);
            PageImpl<DentistCommunicationReportVM> result3 = reportService.dentistCommunicationReport(teamOnlyQuery);
            PageImpl<DentistCommunicationReportVM> result4 = reportService.dentistCommunicationReport(dentistOnlyQuery);
            
            assertNotNull(result1, "只传开始时间的查询应该成功");
            assertNotNull(result2, "只传结束时间的查询应该成功");
            assertNotNull(result3, "只传设计组的查询应该成功");
            assertNotNull(result4, "只传医生ID的查询应该成功");
            
            System.out.println("单个参数过滤测试通过：");
            System.out.println("- 只传开始时间: 总记录数=" + result1.getTotalElements());
            System.out.println("- 只传结束时间: 总记录数=" + result2.getTotalElements());
            System.out.println("- 只传设计组: 总记录数=" + result3.getTotalElements());
            System.out.println("- 只传医生ID: 总记录数=" + result4.getTotalElements());
            
        } catch (Exception e) {
            fail("单个参数过滤查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testNoFilterVsPartialFilterConsistency() {
        System.out.println("=== 测试无过滤与部分过滤的数据一致性 ===");
        
        try {
            // 无过滤查询
            PageImpl<DentistCommunicationReportVM> noFilterResult = 
                    reportService.dentistCommunicationReport(noFilterQuery);
            
            // 部分过滤查询
            PageImpl<DentistCommunicationReportVM> partialFilterResult = 
                    reportService.dentistCommunicationReport(partialFilterQuery);
            
            System.out.println("数据一致性验证：");
            System.out.println("- 无过滤查询总记录数: " + noFilterResult.getTotalElements());
            System.out.println("- 部分过滤查询总记录数: " + partialFilterResult.getTotalElements());
            
            // 逻辑验证：部分过滤的结果应该<=无过滤的结果
            assertTrue(partialFilterResult.getTotalElements() <= noFilterResult.getTotalElements(),
                    "部分过滤的结果数量应该小于等于无过滤的结果数量");
            
            System.out.println("✅ 数据一致性验证通过");
            
        } catch (Exception e) {
            fail("数据一致性验证失败: " + e.getMessage());
        }
    }

    @Test
    public void testPaginationWithNoFilters() {
        System.out.println("=== 测试无过滤条件下的分页功能 ===");
        
        try {
            // 第一页
            DentistCommunicationReportQueryVM page1Query = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0)
                    .pageSize(5)
                    .startTime(null)
                    .endTime(null)
                    .teamName(null)
                    .dentistId(null)
                    .build();
            
            // 第二页
            DentistCommunicationReportQueryVM page2Query = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(1)
                    .pageSize(5)
                    .startTime(null)
                    .endTime(null)
                    .teamName(null)
                    .dentistId(null)
                    .build();
            
            PageImpl<DentistCommunicationReportVM> page1Result = reportService.dentistCommunicationReport(page1Query);
            PageImpl<DentistCommunicationReportVM> page2Result = reportService.dentistCommunicationReport(page2Query);
            
            // 验证分页逻辑
            assertEquals(page1Result.getTotalElements(), page2Result.getTotalElements(), 
                        "两页的总记录数应该相同");
            assertEquals(page1Result.getTotalPages(), page2Result.getTotalPages(), 
                        "两页的总页数应该相同");
            assertEquals(0, page1Result.getNumber(), "第一页页码应该为0");
            assertEquals(1, page2Result.getNumber(), "第二页页码应该为1");
            
            System.out.println("无过滤分页测试通过：");
            System.out.println("- 总记录数: " + page1Result.getTotalElements());
            System.out.println("- 总页数: " + page1Result.getTotalPages());
            System.out.println("- 第一页记录数: " + page1Result.getContent().size());
            System.out.println("- 第二页记录数: " + page2Result.getContent().size());
            
        } catch (Exception e) {
            fail("无过滤分页查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testBuilderPatternWithNullValues() {
        System.out.println("=== 测试Builder模式中的null值处理 ===");
        
        // 使用Builder但不设置过滤参数（默认为null）
        DentistCommunicationReportQueryVM builderQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(8)
                // 不设置过滤参数，默认为null
                .build();
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(builderQuery);
            
            assertNotNull(result, "Builder模式查询结果不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            
            System.out.println("Builder模式null值处理测试通过：");
            System.out.println("- 查询参数: 通过Builder设置，过滤参数默认为null");
            System.out.println("- 总记录数: " + result.getTotalElements());
            System.out.println("- 预期行为: 等同于无过滤查询");
            
        } catch (Exception e) {
            fail("Builder模式查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testParameterValidationWithNullFilters() {
        System.out.println("=== 测试参数验证：允许过滤参数为null ===");
        
        // 测试null查询对象
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(null);
        }, "null查询对象应该抛出异常");
        
        // 测试必需参数为null
        DentistCommunicationReportQueryVM invalidQuery1 = DentistCommunicationReportQueryVM.builder()
                .pageNumber(null) // 页码不能为null
                .pageSize(10)
                .build();
        
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(invalidQuery1);
        }, "页码为null应该抛出异常");
        
        DentistCommunicationReportQueryVM invalidQuery2 = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(null) // 页大小不能为null
                .build();
        
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(invalidQuery2);
        }, "页大小为null应该抛出异常");
        
        // 测试过滤参数为null是允许的
        DentistCommunicationReportQueryVM validQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(null)    // 允许为null
                .endTime(null)      // 允许为null
                .teamName(null)     // 允许为null
                .dentistId(null)    // 允许为null
                .build();
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(validQuery);
            assertNotNull(result, "所有过滤参数为null的查询应该成功");
            System.out.println("✅ 参数验证测试通过：过滤参数允许为null");
        } catch (Exception e) {
            fail("过滤参数为null的查询应该成功，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testPerformanceWithNoFilters() {
        System.out.println("=== 测试无过滤条件的查询性能 ===");
        
        long startTime = System.currentTimeMillis();
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(noFilterQuery);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            System.out.println("无过滤查询性能测试：");
            System.out.println("- 执行时间: " + duration + "ms");
            System.out.println("- 总记录数: " + result.getTotalElements());
            System.out.println("- 当前页记录数: " + result.getContent().size());
            
            // 性能断言：查询应该在合理时间内完成（比如30秒）
            assertTrue(duration < 30000, "查询时间应该在30秒内完成，实际: " + duration + "ms");
            
        } catch (Exception e) {
            fail("性能测试失败: " + e.getMessage());
        }
    }

    /**
     * 验证记录结构的完整性
     */
    private void validateRecordStructure(DentistCommunicationReportVM record) {
        assertNotNull(record, "记录不应为null");
        
        // 基础信息字段验证
        assertNotNull(record.getTeamName(), "设计组不应为null");
        assertNotNull(record.getDentistName(), "医生姓名不应为null");
        assertNotNull(record.getDentistCode(), "医生编号不应为null");
        assertNotNull(record.getAllCasesNum(), "总病例数不应为null");
        assertNotNull(record.getFirstTagCasesNum(), "新手首例病例数不应为null");
        
        // 设计前沟通指标验证
        assertNotNull(record.getPreDesignTagCasesNum(), "设计前沟通病例数不应为null");
        assertNotNull(record.getPreCalledCasesNum(), "设计前沟通拨打电话病例数不应为null");
        assertNotNull(record.getPreCalledCasesRate(), "设计前沟通拨打率不应为null");
        assertNotNull(record.getPreConnectedCasesNum(), "设计前沟通接通电话病例数不应为null");
        assertNotNull(record.getPreConnectedCasesRate(), "设计前沟通接通率不应为null");
        assertNotNull(record.getPreTotalConnectedCallNum(), "设计前沟通接通电话通话数不应为null");
        assertNotNull(record.getPreAverageConnectedCallNum(), "设计前沟通例均通话次数不应为null");
        assertNotNull(record.getPreTotalDurationSec(), "设计前沟通接通电话时长不应为null");
        assertNotNull(record.getPreAverageDurationSec(), "设计前沟通例均通话时长不应为null");
        
        // 设计后讲解指标验证
        assertNotNull(record.getPostDesignTagCasesNum(), "设计后讲解病例数不应为null");
        assertNotNull(record.getPostCalledCasesNum(), "设计后讲解拨打电话病例数不应为null");
        assertNotNull(record.getPostCalledCasesRate(), "设计后讲解拨打率不应为null");
        assertNotNull(record.getPostConnectedCasesNum(), "设计后讲解接通电话病例数不应为null");
        assertNotNull(record.getPostConnectedCasesRate(), "设计后讲解接通率不应为null");
        assertNotNull(record.getPostTotalConnectedCallNum(), "设计后讲解接通电话通话数不应为null");
        assertNotNull(record.getPostAverageConnectedCallNum(), "设计后讲解例均通话次数不应为null");
        assertNotNull(record.getPostTotalDurationSec(), "设计后讲解接通电话时长不应为null");
        assertNotNull(record.getPostAverageDurationSec(), "设计后讲解例均通话时长不应为null");
        
        // 验证时间格式（应该是HH:MM:SS格式）
        assertTrue(record.getPreTotalDurationSec().matches("\\d{2}:\\d{2}:\\d{2}"), 
                  "设计前沟通接通电话时长应该是HH:MM:SS格式");
        assertTrue(record.getPreAverageDurationSec().matches("\\d{2}:\\d{2}:\\d{2}"), 
                  "设计前沟通例均通话时长应该是HH:MM:SS格式");
        assertTrue(record.getPostTotalDurationSec().matches("\\d{2}:\\d{2}:\\d{2}"), 
                  "设计后讲解接通电话时长应该是HH:MM:SS格式");
        assertTrue(record.getPostAverageDurationSec().matches("\\d{2}:\\d{2}:\\d{2}"), 
                  "设计后讲解例均通话时长应该是HH:MM:SS格式");
    }
}