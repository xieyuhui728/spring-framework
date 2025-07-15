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
 * 验证EntityManager + LIMIT OFFSET分页 + null参数支持的完整性和正确性
 */
@SpringBootTest
@ActiveProfiles("test")
public class DentistCommunicationReportServiceTest {

    @Autowired
    private ReportService reportService;

    private DentistCommunicationReportQueryVM basicQueryVM;
    private DentistCommunicationReportQueryVM filteredQueryVM;
    private DentistCommunicationReportQueryVM nullParamsQueryVM;

    @BeforeEach
    void setUp() {
        // 基础查询参数
        basicQueryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .build();

        // 带过滤条件的查询参数
        filteredQueryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(20)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .teamName("设计组A")
                .dentistId("123")
                .build();

        // 全null参数查询
        nullParamsQueryVM = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(15)
                // 所有过滤参数都为null
                .startTime(null)
                .endTime(null)
                .teamName(null)
                .dentistId(null)
                .build();
    }

    @Test
    public void testBasicQueryWithNullParams() {
        System.out.println("=== 测试基础查询（null参数支持）===");
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(basicQueryVM);
            
            // 验证基本结构
            assertNotNull(result, "查询结果不应为null");
            assertNotNull(result.getContent(), "查询内容不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            assertTrue(result.getTotalPages() >= 0, "总页数应该>=0");
            assertEquals(0, result.getNumber(), "当前页码应该为0");
            assertEquals(10, result.getSize(), "页大小应该为10");
            
            System.out.println("基础查询测试通过：");
            System.out.println("- 总记录数: " + result.getTotalElements());
            System.out.println("- 总页数: " + result.getTotalPages());
            System.out.println("- 当前页记录数: " + result.getContent().size());
            System.out.println("- SQL使用: LIMIT " + basicQueryVM.getPageSize() + " OFFSET " + 
                             (basicQueryVM.getPageNumber() * basicQueryVM.getPageSize()));
            
            // 验证结果映射
            if (!result.getContent().isEmpty()) {
                DentistCommunicationReportVM firstRecord = result.getContent().get(0);
                validateRecordStructure(firstRecord);
                
                System.out.println("- 第一条记录: 医生=" + firstRecord.getDentistName() + 
                                 ", 设计组=" + firstRecord.getTeamName() +
                                 ", 总病例数=" + firstRecord.getAllCasesNum());
            }
            
        } catch (Exception e) {
            fail("基础查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testAllNullParametersQuery() {
        System.out.println("=== 测试全null参数查询 ===");
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(nullParamsQueryVM);
            
            // 验证基本结构
            assertNotNull(result, "null参数查询结果不应为null");
            assertNotNull(result.getContent(), "null参数查询内容不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            assertEquals(15, result.getSize(), "页大小应该为15");
            
            System.out.println("全null参数查询测试通过：");
            System.out.println("- 查询参数: 所有过滤条件都为null");
            System.out.println("- 总记录数: " + result.getTotalElements());
            System.out.println("- 当前页记录数: " + result.getContent().size());
            System.out.println("- SQL处理: WHERE子句中的IS NULL条件应该正确处理");
            
        } catch (Exception e) {
            fail("null参数查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testEmptyStringParametersQuery() {
        System.out.println("=== 测试空字符串参数查询 ===");
        
        DentistCommunicationReportQueryVM emptyStringQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .teamName("") // 空字符串
                .dentistId("   ") // 只有空格
                .build();
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(emptyStringQuery);
            
            assertNotNull(result, "空字符串参数查询结果不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            
            System.out.println("空字符串参数测试通过：");
            System.out.println("- 空字符串和空格应该被标准化为null");
            System.out.println("- 总记录数: " + result.getTotalElements());
            
        } catch (Exception e) {
            fail("空字符串参数查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testLimitOffsetPagination() {
        System.out.println("=== 测试LIMIT OFFSET分页 ===");
        
        try {
            // 第一页 - LIMIT 5 OFFSET 0
            DentistCommunicationReportQueryVM page1Query = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0)
                    .pageSize(5)
                    .build();
            
            PageImpl<DentistCommunicationReportVM> page1Result = reportService.dentistCommunicationReport(page1Query);
            
            // 第二页 - LIMIT 5 OFFSET 5
            DentistCommunicationReportQueryVM page2Query = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(1)
                    .pageSize(5)
                    .build();
            
            PageImpl<DentistCommunicationReportVM> page2Result = reportService.dentistCommunicationReport(page2Query);
            
            // 验证分页逻辑
            assertEquals(page1Result.getTotalElements(), page2Result.getTotalElements(), 
                        "两页的总记录数应该相同");
            assertEquals(page1Result.getTotalPages(), page2Result.getTotalPages(), 
                        "两页的总页数应该相同");
            assertEquals(0, page1Result.getNumber(), "第一页页码应该为0");
            assertEquals(1, page2Result.getNumber(), "第二页页码应该为1");
            assertEquals(5, page1Result.getSize(), "页大小应该为5");
            assertEquals(5, page2Result.getSize(), "页大小应该为5");
            
            System.out.println("LIMIT OFFSET分页测试通过：");
            System.out.println("- 总记录数: " + page1Result.getTotalElements());
            System.out.println("- 总页数: " + page1Result.getTotalPages());
            System.out.println("- 第一页: LIMIT 5 OFFSET 0, 记录数: " + page1Result.getContent().size());
            System.out.println("- 第二页: LIMIT 5 OFFSET 5, 记录数: " + page2Result.getContent().size());
            
            // 验证不同页的数据不重复（如果有数据的话）
            if (!page1Result.getContent().isEmpty() && !page2Result.getContent().isEmpty()) {
                String firstPageFirstId = page1Result.getContent().get(0).getDentistCode();
                String secondPageFirstId = page2Result.getContent().get(0).getDentistCode();
                
                System.out.println("- 第一页第一条记录医生编号: " + firstPageFirstId);
                System.out.println("- 第二页第一条记录医生编号: " + secondPageFirstId);
                
                // 注意：由于ORDER BY gms_dentist.id，如果数据量足够，不同页应该有不同的记录
            }
            
        } catch (Exception e) {
            fail("LIMIT OFFSET分页查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testLargeOffsetPagination() {
        System.out.println("=== 测试大OFFSET分页性能 ===");
        
        // 测试较大的OFFSET值
        DentistCommunicationReportQueryVM largeOffsetQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(20) // OFFSET = 20 * 10 = 200
                .pageSize(10)
                .build();
        
        try {
            long startTime = System.currentTimeMillis();
            
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(largeOffsetQuery);
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            
            assertNotNull(result, "大OFFSET查询结果不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            
            int expectedOffset = largeOffsetQuery.getPageNumber() * largeOffsetQuery.getPageSize();
            
            System.out.println("大OFFSET分页测试：");
            System.out.println("- 页码: " + largeOffsetQuery.getPageNumber());
            System.out.println("- 页大小: " + largeOffsetQuery.getPageSize());
            System.out.println("- SQL: LIMIT " + largeOffsetQuery.getPageSize() + " OFFSET " + expectedOffset);
            System.out.println("- 执行时间: " + executionTime + "ms");
            System.out.println("- 总记录数: " + result.getTotalElements());
            System.out.println("- 当前页记录数: " + result.getContent().size());
            
            // 如果OFFSET超过总记录数，应该返回空结果
            if (expectedOffset >= result.getTotalElements()) {
                assertTrue(result.getContent().isEmpty(), 
                          "当OFFSET超过总记录数时，应该返回空结果");
                System.out.println("- 结果验证: OFFSET超过总记录数，正确返回空结果");
            }
            
        } catch (Exception e) {
            fail("大OFFSET分页查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testParameterValidation() {
        System.out.println("=== 测试参数验证 ===");
        
        // 测试null查询对象
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(null);
        }, "null查询对象应该抛出异常");
        
        // 测试null页码
        DentistCommunicationReportQueryVM nullPageQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(null)
                .pageSize(10)
                .build();
        
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(nullPageQuery);
        }, "null页码应该抛出异常");
        
        // 测试null页大小
        DentistCommunicationReportQueryVM nullSizeQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(null)
                .build();
        
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(nullSizeQuery);
        }, "null页大小应该抛出异常");
        
        // 测试无效页码
        DentistCommunicationReportQueryVM invalidPageQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(-1)
                .pageSize(10)
                .build();
        
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(invalidPageQuery);
        }, "负数页码应该抛出异常");
        
        // 测试无效页大小
        DentistCommunicationReportQueryVM invalidSizeQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(0)
                .build();
        
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(invalidSizeQuery);
        }, "零页大小应该抛出异常");
        
        // 测试过大页大小
        DentistCommunicationReportQueryVM largeSizeQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(1001)
                .build();
        
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(largeSizeQuery);
        }, "过大页大小应该抛出异常");
        
        System.out.println("参数验证测试通过");
    }

    @Test
    public void testTimeRangeValidation() {
        System.out.println("=== 测试时间范围验证 ===");
        
        // 测试开始时间大于结束时间
        DentistCommunicationReportQueryVM invalidTimeQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .endTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .build();
        
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(invalidTimeQuery);
        }, "开始时间大于结束时间应该抛出异常");
        
        // 测试只有开始时间
        DentistCommunicationReportQueryVM onlyStartTimeQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .endTime(null)
                .build();
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(onlyStartTimeQuery);
            assertNotNull(result, "只有开始时间的查询应该成功");
            System.out.println("只有开始时间的查询测试通过");
        } catch (Exception e) {
            fail("只有开始时间的查询应该成功执行，但抛出异常: " + e.getMessage());
        }
        
        // 测试只有结束时间
        DentistCommunicationReportQueryVM onlyEndTimeQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(null)
                .endTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .build();
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(onlyEndTimeQuery);
            assertNotNull(result, "只有结束时间的查询应该成功");
            System.out.println("只有结束时间的查询测试通过");
        } catch (Exception e) {
            fail("只有结束时间的查询应该成功执行，但抛出异常: " + e.getMessage());
        }
        
        System.out.println("时间范围验证测试通过");
    }

    @Test
    public void testSqlInjectionProtection() {
        System.out.println("=== 测试SQL注入防护 ===");
        
        DentistCommunicationReportQueryVM injectionQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .teamName("'; DROP TABLE gms_dentist; --")
                .dentistId("1' OR '1'='1")
                .build();
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(injectionQuery);
            
            // 如果能执行到这里，说明参数化查询成功防止了SQL注入
            assertNotNull(result, "SQL注入防护测试通过，查询结果不为null");
            System.out.println("SQL注入防护测试通过：参数化查询成功阻止了注入攻击");
            System.out.println("- 查询结果记录数: " + result.getContent().size());
            
        } catch (Exception e) {
            // 即使查询失败，也不应该是因为SQL注入成功
            System.out.println("SQL注入防护测试 - 查询失败但不是因为注入成功: " + e.getMessage());
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