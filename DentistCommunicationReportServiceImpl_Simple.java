package com.example.service.impl;

import com.example.service.ReportService;
import com.example.vm.DentistCommunicationReportQueryVM;
import com.example.vm.DentistCommunicationReportVM;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 医生沟通报表Service - 简化版本
 * 解决复杂SQL的GROUP BY聚合问题
 */
@Slf4j
@Service("dentistCommunicationReportServiceSimple")
@Transactional("dorisTransactionManager")
public class DentistCommunicationReportServiceImpl_Simple implements ReportService {
    
    @PersistenceContext(unitName = "doris")
    private EntityManager entityManager;

    /**
     * 简化版主查询SQL - 避免复杂的GROUP BY问题
     */
    private static final String SIMPLIFIED_MAIN_QUERY = """
            SELECT 
              COALESCE(gt.name, '未分组') AS team_name,
              gd.name AS dentist_name,
              gd.code AS dentist_code,
              COUNT(DISTINCT gc.code) AS all_cases_num,
              COUNT(DISTINCT CASE WHEN gc.case_tags LIKE '%CASE_TAG-DENTIST_FIRST_CASE%' THEN gc.code END) AS first_tag_cases_num,
              
              -- 设计前沟通指标（简化版）
              0 AS pre_design_tag_cases_num,
              0 AS pre_called_cases_num,
              0 AS pre_called_cases_rate,
              0 AS pre_connected_cases_num,
              0 AS pre_connected_cases_rate,
              0 AS pre_total_connected_call_num,
              0 AS pre_average_connected_call_num,
              '00:00:00' AS pre_total_duration_sec,
              '00:00:00' AS pre_average_duration_sec,
              
              -- 设计后讲解指标（简化版）
              0 AS post_design_tag_cases_num,
              0 AS post_called_cases_num,
              0 AS post_called_cases_rate,
              0 AS post_connected_cases_num,
              0 AS post_connected_cases_rate,
              0 AS post_total_connected_call_num,
              0 AS post_average_connected_call_num,
              '00:00:00' AS post_total_duration_sec,
              '00:00:00' AS post_average_duration_sec
              
            FROM gms_dentist gd
            LEFT JOIN gms_case gc ON gc.dentist_code = gd.code
            LEFT JOIN gms_order_case_stakeholder_detail gocsd ON gd.code = gocsd.dentist_code
            LEFT JOIN gms_order go ON gocsd.order_id = go.id
            LEFT JOIN gms_team gt ON go.team_id = gt.id
            WHERE 1 = 1
              AND (? IS NULL OR ? IS NULL OR gt.name = ?)
              AND (? IS NULL OR ? IS NULL OR gd.id = ?)
            GROUP BY gd.id, gd.name, gd.code, gt.name
            ORDER BY gd.id
            LIMIT ? OFFSET ?
            """;

    /**
     * 简化版计数查询SQL
     */
    private static final String SIMPLIFIED_COUNT_QUERY = """
            SELECT COUNT(DISTINCT gd.id)
            FROM gms_dentist gd
            LEFT JOIN gms_case gc ON gc.dentist_code = gd.code
            LEFT JOIN gms_order_case_stakeholder_detail gocsd ON gd.code = gocsd.dentist_code
            LEFT JOIN gms_order go ON gocsd.order_id = go.id
            LEFT JOIN gms_team gt ON go.team_id = gt.id
            WHERE 1 = 1
              AND (? IS NULL OR ? IS NULL OR gt.name = ?)
              AND (? IS NULL OR ? IS NULL OR gd.id = ?)
            """;

    @Override
    public PageImpl<DentistCommunicationReportVM> dentistCommunicationReport(
            DentistCommunicationReportQueryVM param) {
        
        log.info("开始执行医生沟通报表查询（简化版），参数: {}", param);
        
        try {
            // 参数验证
            validateQueryParams(param);
            
            // 标准化参数
            param = normalizeQueryParams(param);
            
            // 打印有效参数
            logEffectiveParameters(param);
            
            // 执行查询
            List<Object[]> resultList = executeMainQuery(param);
            Long totalElements = executeCountQuery(param);
            
            log.info("查询完成，总记录数: {}, 当前页记录数: {}", totalElements, resultList.size());
            
            // 映射结果
            List<DentistCommunicationReportVM> content = resultList
                .stream()
                .map(this::mapToViewModel)
                .collect(Collectors.toList());
            
            // 创建分页对象
            Pageable pageable = PageRequest.of(param.getPageNumber(), param.getPageSize());
            
            return new PageImpl<>(content, pageable, totalElements);
            
        } catch (Exception e) {
            log.error("执行医生沟通报表查询失败，参数: {}", param, e);
            throw new RuntimeException("执行医生沟通报表查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行主查询
     */
    @SuppressWarnings("unchecked")
    private List<Object[]> executeMainQuery(DentistCommunicationReportQueryVM param) {
        log.debug("执行简化版主查询");
        
        int limit = param.getPageSize();
        int offset = param.getPageNumber() * param.getPageSize();
        
        Query query = entityManager.createNativeQuery(SIMPLIFIED_MAIN_QUERY);
        setPositionalParameters(query, param, limit, offset);
        
        List<Object[]> results = query.getResultList();
        log.debug("主查询执行完成，返回 {} 条记录", results.size());
        
        return results;
    }

    /**
     * 执行计数查询
     */
    private Long executeCountQuery(DentistCommunicationReportQueryVM param) {
        log.debug("执行简化版计数查询");
        
        Query query = entityManager.createNativeQuery(SIMPLIFIED_COUNT_QUERY);
        setPositionalParametersForCount(query, param);
        
        Object result = query.getSingleResult();
        Long count = result != null ? ((Number) result).longValue() : 0L;
        
        log.debug("计数查询执行完成，总记录数: {}", count);
        return count;
    }

    /**
     * 设置主查询的位置参数
     */
    private void setPositionalParameters(Query query, DentistCommunicationReportQueryVM param, int limit, int offset) {
        String teamName = param.getTeamName();
        String dentistId = param.getDentistId();
        
        int paramIndex = 1;
        
        // 设计组过滤参数 (3个)
        query.setParameter(paramIndex++, teamName);     // 1: teamName null检查1
        query.setParameter(paramIndex++, teamName);     // 2: teamName null检查2
        query.setParameter(paramIndex++, teamName);     // 3: teamName 实际值
        
        // 医生ID过滤参数 (3个)  
        query.setParameter(paramIndex++, dentistId);    // 4: dentistId null检查1
        query.setParameter(paramIndex++, dentistId);    // 5: dentistId null检查2
        query.setParameter(paramIndex++, dentistId);    // 6: dentistId 实际值
        
        // 分页参数 (2个)
        query.setParameter(paramIndex++, limit);        // 7: LIMIT
        query.setParameter(paramIndex, offset);         // 8: OFFSET
        
        log.debug("主查询参数设置完成 - teamName: {}, dentistId: {}, limit: {}, offset: {}", 
                 teamName, dentistId, limit, offset);
    }

    /**
     * 设置计数查询的位置参数
     */
    private void setPositionalParametersForCount(Query query, DentistCommunicationReportQueryVM param) {
        String teamName = param.getTeamName();
        String dentistId = param.getDentistId();
        
        int paramIndex = 1;
        
        // 设计组过滤参数 (3个)
        query.setParameter(paramIndex++, teamName);     // 1: teamName null检查1
        query.setParameter(paramIndex++, teamName);     // 2: teamName null检查2
        query.setParameter(paramIndex++, teamName);     // 3: teamName 实际值
        
        // 医生ID过滤参数 (3个)
        query.setParameter(paramIndex++, dentistId);    // 4: dentistId null检查1
        query.setParameter(paramIndex++, dentistId);    // 5: dentistId null检查2
        query.setParameter(paramIndex, dentistId);      // 6: dentistId 实际值
        
        log.debug("计数查询参数设置完成 - teamName: {}, dentistId: {}", teamName, dentistId);
    }

    /**
     * 标准化查询参数
     */
    private DentistCommunicationReportQueryVM normalizeQueryParams(DentistCommunicationReportQueryVM param) {
        return DentistCommunicationReportQueryVM.builder()
                .pageNumber(param.getPageNumber())
                .pageSize(param.getPageSize())
                .startTime(normalizeStringParameter(param.getStartTime()))
                .endTime(normalizeStringParameter(param.getEndTime()))
                .teamName(normalizeStringParameter(param.getTeamName()))
                .dentistId(normalizeStringParameter(param.getDentistId()))
                .build();
    }

    /**
     * 打印有效的查询参数
     */
    private void logEffectiveParameters(DentistCommunicationReportQueryVM param) {
        log.info("有效查询参数（简化版）:");
        log.info("  - 分页: 第{}页，每页{}条", param.getPageNumber() + 1, param.getPageSize());
        
        if (param.getTeamName() != null) {
            log.info("  - 设计组过滤: {}", param.getTeamName());
        } else {
            log.info("  - 设计组过滤: 未设置（不过滤）");
        }
        
        if (param.getDentistId() != null) {
            log.info("  - 医生ID过滤: {}", param.getDentistId());
        } else {
            log.info("  - 医生ID过滤: 未设置（不过滤）");
        }
        
        log.info("  - 注意: 简化版暂时不支持时间范围过滤和复杂沟通指标");
    }

    /**
     * 映射结果到ViewModel
     */
    private DentistCommunicationReportVM mapToViewModel(Object[] row) {
        if (row == null || row.length < 23) {
            log.warn("查询结果行数据不完整，期望23个字段，实际: {}", row != null ? row.length : 0);
            return createEmptyViewModel();
        }
        
        return DentistCommunicationReportVM.builder()
            .teamName(safeToString(row[0]))                         // team_name
            .dentistName(safeToString(row[1]))                      // dentist_name  
            .dentistCode(safeToString(row[2]))                      // dentist_code
            .allCasesNum(safeToString(row[3]))                      // all_cases_num
            .firstTagCasesNum(safeToString(row[4]))                 // first_tag_cases_num
            .preDesignTagCasesNum(safeToString(row[5]))             // pre_design_tag_cases_num
            .preCalledCasesNum(safeToString(row[6]))                // pre_called_cases_num
            .preCalledCasesRate(safeToString(row[7]))               // pre_called_cases_rate
            .preConnectedCasesNum(safeToString(row[8]))             // pre_connected_cases_num
            .preConnectedCasesRate(safeToString(row[9]))            // pre_connected_cases_rate
            .preTotalConnectedCallNum(safeToString(row[10]))        // pre_total_connected_call_num
            .preAverageConnectedCallNum(safeToString(row[11]))      // pre_average_connected_call_num
            .preTotalDurationSec(safeToString(row[12]))             // pre_total_duration_sec
            .preAverageDurationSec(safeToString(row[13]))           // pre_average_duration_sec
            .postDesignTagCasesNum(safeToString(row[14]))           // post_design_tag_cases_num
            .postCalledCasesNum(safeToString(row[15]))              // post_called_cases_num
            .postCalledCasesRate(safeToString(row[16]))             // post_called_cases_rate
            .postConnectedCasesNum(safeToString(row[17]))           // post_connected_cases_num
            .postConnectedCasesRate(safeToString(row[18]))          // post_connected_cases_rate
            .postTotalConnectedCallNum(safeToString(row[19]))       // post_total_connected_call_num
            .postAverageConnectedCallNum(safeToString(row[20]))     // post_average_connected_call_num
            .postTotalDurationSec(safeToString(row[21]))            // post_total_duration_sec
            .postAverageDurationSec(safeToString(row[22]))          // post_average_duration_sec
            .build();
    }

    /**
     * 参数验证
     */
    private void validateQueryParams(DentistCommunicationReportQueryVM param) {
        if (param == null) {
            throw new IllegalArgumentException("查询参数不能为空");
        }
        
        if (param.getPageNumber() == null || param.getPageNumber() < 0) {
            throw new IllegalArgumentException("页码不能为空且不能小于0");
        }
        
        if (param.getPageSize() == null || param.getPageSize() <= 0 || param.getPageSize() > 1000) {
            throw new IllegalArgumentException("页大小不能为空且必须在1-1000之间");
        }
        
        log.debug("参数验证通过（简化版）");
    }

    /**
     * 标准化字符串参数
     */
    private String normalizeStringParameter(String param) {
        if (param == null) {
            return null;
        }
        String trimmed = param.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 安全地将Object转换为String
     */
    private String safeToString(Object obj) {
        if (obj == null) {
            return "0";
        }
        String str = obj.toString().trim();
        return str.isEmpty() ? "0" : str;
    }

    /**
     * 创建空的ViewModel对象
     */
    private DentistCommunicationReportVM createEmptyViewModel() {
        return DentistCommunicationReportVM.builder()
            .teamName("未分组")
            .dentistName("未知")
            .dentistCode("000")
            .allCasesNum("0")
            .firstTagCasesNum("0")
            .preDesignTagCasesNum("0")
            .preCalledCasesNum("0")
            .preCalledCasesRate("0")
            .preConnectedCasesNum("0")
            .preConnectedCasesRate("0")
            .preTotalConnectedCallNum("0")
            .preAverageConnectedCallNum("0")
            .preTotalDurationSec("00:00:00")
            .preAverageDurationSec("00:00:00")
            .postDesignTagCasesNum("0")
            .postCalledCasesNum("0")
            .postCalledCasesRate("0")
            .postConnectedCasesNum("0")
            .postConnectedCasesRate("0")
            .postTotalConnectedCallNum("0")
            .postAverageConnectedCallNum("0")
            .postTotalDurationSec("00:00:00")
            .postAverageDurationSec("00:00:00")
            .build();
    }
}