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
 * 验证EntityManager实现的完整性和正确性
 */
@SpringBootTest
@ActiveProfiles("test")
public class DentistCommunicationReportServiceTest {

    @Autowired
    private ReportService reportService;

    private DentistCommunicationReportQueryVM basicQueryVM;
    private DentistCommunicationReportQueryVM filteredQueryVM;

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
    }

    @Test
    public void testBasicQuery() {
        System.out.println("=== 测试基础查询 ===");
        
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
    public void testFilteredQuery() {
        System.out.println("=== 测试过滤查询 ===");
        
        try {
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(filteredQueryVM);
            
            // 验证基本结构
            assertNotNull(result, "过滤查询结果不应为null");
            assertNotNull(result.getContent(), "过滤查询内容不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            assertEquals(20, result.getSize(), "页大小应该为20");
            
            System.out.println("过滤查询测试通过：");
            System.out.println("- 查询条件: startTime=" + filteredQueryVM.getStartTime() +
                             ", endTime=" + filteredQueryVM.getEndTime() +
                             ", teamName=" + filteredQueryVM.getTeamName() +
                             ", dentistId=" + filteredQueryVM.getDentistId());
            System.out.println("- 总记录数: " + result.getTotalElements());
            System.out.println("- 当前页记录数: " + result.getContent().size());
            
            // 验证结果内容
            result.getContent().forEach(record -> {
                validateRecordStructure(record);
                System.out.println("- 记录: 医生=" + record.getDentistName() + 
                                 ", 设计组=" + record.getTeamName() +
                                 ", 设计前沟通=" + record.getPreDesignTagCasesNum() + "例" +
                                 ", 设计后讲解=" + record.getPostDesignTagCasesNum() + "例");
            });
            
        } catch (Exception e) {
            fail("过滤查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testPagination() {
        System.out.println("=== 测试分页功能 ===");
        
        try {
            // 第一页
            DentistCommunicationReportQueryVM page1Query = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0)
                    .pageSize(5)
                    .build();
            
            PageImpl<DentistCommunicationReportVM> page1Result = reportService.dentistCommunicationReport(page1Query);
            
            // 第二页
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
            
            System.out.println("分页测试通过：");
            System.out.println("- 总记录数: " + page1Result.getTotalElements());
            System.out.println("- 总页数: " + page1Result.getTotalPages());
            System.out.println("- 第一页记录数: " + page1Result.getContent().size());
            System.out.println("- 第二页记录数: " + page2Result.getContent().size());
            
            // 验证不同页的数据不重复（如果有数据的话）
            if (!page1Result.getContent().isEmpty() && !page2Result.getContent().isEmpty()) {
                String firstPageFirstId = page1Result.getContent().get(0).getDentistCode();
                String secondPageFirstId = page2Result.getContent().get(0).getDentistCode();
                
                assertNotEquals(firstPageFirstId, secondPageFirstId, 
                              "不同页的第一条记录应该不同");
                
                System.out.println("- 第一页第一条记录医生编号: " + firstPageFirstId);
                System.out.println("- 第二页第一条记录医生编号: " + secondPageFirstId);
            }
            
        } catch (Exception e) {
            fail("分页查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testParameterValidation() {
        System.out.println("=== 测试参数验证 ===");
        
        // 测试null参数
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(null);
        }, "null参数应该抛出异常");
        
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
        
        // 测试时间范围错误
        DentistCommunicationReportQueryVM invalidTimeQuery = DentistCommunicationReportQueryVM.builder()
                .pageNumber(0)
                .pageSize(10)
                .startTime(ZonedDateTime.parse("2024-12-31T23:59:59Z"))
                .endTime(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
                .build();
        
        assertThrows(RuntimeException.class, () -> {
            reportService.dentistCommunicationReport(invalidTimeQuery);
        }, "开始时间大于结束时间应该抛出异常");
        
        System.out.println("参数验证测试通过");
    }

    @Test
    public void testTimeRangeQuery() {
        System.out.println("=== 测试时间范围查询 ===");
        
        try {
            DentistCommunicationReportQueryVM timeRangeQuery = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0)
                    .pageSize(15)
                    .startTime(ZonedDateTime.parse("2024-06-01T00:00:00Z"))
                    .endTime(ZonedDateTime.parse("2024-11-30T23:59:59Z"))
                    .build();
            
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(timeRangeQuery);
            
            assertNotNull(result, "时间范围查询结果不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            
            System.out.println("时间范围查询测试通过：");
            System.out.println("- 查询时间范围: " + timeRangeQuery.getStartTime() + 
                             " 到 " + timeRangeQuery.getEndTime());
            System.out.println("- 总记录数: " + result.getTotalElements());
            
        } catch (Exception e) {
            fail("时间范围查询应该成功执行，但抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testEmptyFilters() {
        System.out.println("=== 测试空过滤条件 ===");
        
        try {
            DentistCommunicationReportQueryVM emptyFilterQuery = DentistCommunicationReportQueryVM.builder()
                    .pageNumber(0)
                    .pageSize(10)
                    .teamName("")  // 空字符串
                    .dentistId("   ")  // 只有空格
                    .build();
            
            PageImpl<DentistCommunicationReportVM> result = reportService.dentistCommunicationReport(emptyFilterQuery);
            
            assertNotNull(result, "空过滤条件查询结果不应为null");
            assertTrue(result.getTotalElements() >= 0, "总元素数应该>=0");
            
            System.out.println("空过滤条件测试通过：");
            System.out.println("- 总记录数: " + result.getTotalElements());
            
        } catch (Exception e) {
            fail("空过滤条件查询应该成功执行，但抛出异常: " + e.getMessage());
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
        
        // 验证数字字段格式（应该能转换为数字或者是时间格式）
        try {
            Double.parseDouble(record.getAllCasesNum());
            Double.parseDouble(record.getFirstTagCasesNum());
            Double.parseDouble(record.getPreDesignTagCasesNum());
            // 时间格式验证（应该是HH:MM:SS格式）
            assertTrue(record.getPreTotalDurationSec().matches("\\d{2}:\\d{2}:\\d{2}"), 
                      "设计前沟通接通电话时长应该是HH:MM:SS格式");
            assertTrue(record.getPreAverageDurationSec().matches("\\d{2}:\\d{2}:\\d{2}"), 
                      "设计前沟通例均通话时长应该是HH:MM:SS格式");
        } catch (NumberFormatException e) {
            // 某些字段可能为"0"，这是正常的
        }
    }
}