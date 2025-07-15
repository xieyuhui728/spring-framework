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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional("dorisTransactionManager")
public class DentistCommunicationReportServiceImpl implements ReportService {
    
    @PersistenceContext(unitName = "doris")
    private EntityManager entityManager;

    /**
     * 主查询SQL - 使用位置参数解决元数据问题
     */
    private static final String MAIN_QUERY_TEMPLATE = """
            WITH ExpandedOrders AS (
              SELECT 
                id AS order_id,
                'pre' AS design_type
              FROM gms_order 
              WHERE tags LIKE '%ORDER_TAG-PRE_DESIGN_COMMUNICATION%'
              
              UNION ALL 
              
              SELECT 
                id AS order_id,
                'post' AS design_type
              FROM gms_order 
              WHERE tags LIKE '%ORDER_TAG-POST_DESIGN_COMMUNICATION%'
            ),
            first_designs AS (
                select 
                case_first_design.case_code,
                case_first_design.design_type,
                case_first_design.first_design_id,
                gd.send_out as first_design_send_time 
                from 
                ( SELECT
                  gc.code AS case_code,
                  eo.design_type,
                  MIN(gd.id) AS first_design_id,
                  MAX(gd.send_out) AS first_design_send_time
                FROM ExpandedOrders eo
                LEFT JOIN gms_order_case_related_detail gocrd 
                  ON eo.order_id = gocrd.order_id
                LEFT JOIN gms_case gc 
                  ON gc.code = gocrd.case_code
                LEFT JOIN gms_design gd 
                  ON gc.code = gd.case_code
                WHERE (? IS NULL OR gd.send_out >= ?)
                  AND (? IS NULL OR gd.send_out <= ?)
                GROUP BY gc.code, eo.design_type
                ) as case_first_design
                left join gms_design gd on case_first_design.first_design_id = gd.id
                where gd.send_out IS NOT null
                  AND gd.status IN ('SENT','CONFIRMED','MODIFICATION','NOT_MODIFICATION')
            ),
            pre_design_orders AS (
              SELECT 
                fd.case_code,
                fd.design_type,
                fd.first_design_send_time,
                gt.code AS task_code,
                gt.assignee_id as designer_id,
                gc.dentist_code as dentist_code,
                ROW_NUMBER() OVER (
                    PARTITION BY fd.case_code, fd.design_type 
                    ORDER BY go2.finished DESC
                ) AS rn
              FROM first_designs fd
              JOIN gms_order_case_related_detail gocrd 
                ON fd.case_code = gocrd.case_code
              JOIN gms_order go2 
                ON go2.id = gocrd.order_id
              JOIN gms_task gt 
                ON gt.process_instance_id = go2.active_process_instance_id 
              JOIN gms_task_type gtt 
                ON gt.type_id = gtt.id
              JOIN gms_case gc
                ON gocrd.case_code = gc.code
              WHERE gt.status = 'COMPLETED'
                AND gtt.id IN (29, 31, 35)
                AND gt.finished < fd.first_design_send_time
            ),
            dentist_in_scope AS (
              SELECT DISTINCT dentist_code
              FROM pre_design_orders
              WHERE dentist_code IS NOT NULL
            ),
            pre_call_metrics AS (
              SELECT
                pdo.case_code,
                MAX(CASE WHEN tocl.id IS NOT NULL THEN 1 ELSE 0 END) AS has_call,
                SUM(CASE WHEN tocl.status = '接通' THEN 1 ELSE 0 END) AS connected_calls,
                SUM(
                  CASE
                    WHEN tocl.duration IS NOT NULL 
                    THEN 
                      (
                        COALESCE(SUBSTRING_INDEX(tocl.duration, ':', 1), 0) * 3600 +
                        COALESCE(SUBSTRING_INDEX(SUBSTRING_INDEX(tocl.duration, ':', 2), ':', -1), 0) * 60 +
                        COALESCE(SUBSTRING_INDEX(tocl.duration, ':', -1), 0)
                      )
                    ELSE 0
                  END
                ) AS total_duration_sec
              FROM pre_design_orders pdo
              LEFT JOIN tt_order_call_log tocl 
                ON pdo.task_code = tocl.order_no
               AND tocl.begin_time < pdo.first_design_send_time
              WHERE pdo.rn = 1
              and pdo.design_type = 'pre'
              GROUP BY pdo.case_code
            ),
            post_call_metrics AS (
              SELECT
                pdo.case_code,
                MAX(CASE WHEN tocl.id IS NOT NULL THEN 1 ELSE 0 END) AS has_call,
                SUM(CASE WHEN tocl.status = '接通' THEN 1 ELSE 0 END) AS connected_calls,
                SUM(
                  CASE
                    WHEN tocl.duration IS NOT NULL 
                    THEN
                      (
                        COALESCE(SUBSTRING_INDEX(tocl.duration, ':', 1), 0) * 3600 +
                        COALESCE(SUBSTRING_INDEX(SUBSTRING_INDEX(tocl.duration, ':', 2), ':', -1), 0) * 60 +
                        COALESCE(SUBSTRING_INDEX(tocl.duration, ':', -1), 0)
                      )
                    ELSE 0
                  END
                ) AS total_duration_sec
              FROM pre_design_orders pdo
              LEFT JOIN tt_order_call_log tocl 
                ON pdo.task_code = tocl.order_no
               AND tocl.begin_time >= pdo.first_design_send_time
              WHERE pdo.rn = 1
              and pdo.design_type = 'post'
              GROUP BY pdo.case_code
            ),
            DentistCases AS (
                SELECT
                    gd.id AS dentist_id,
                    gd.name AS dentist_name,
                    gd.code AS dentist_code,
                    gocrd.case_code AS case_code,
                    MAX(gc.case_tags) AS case_tags
                FROM gms_dentist gd
                LEFT JOIN gms_case gc ON gc.dentist_code = gd.code
                LEFT JOIN gms_order_case_related_detail gocrd ON gc.code = gocrd.case_code
                where gc.dentist_code IS NOT NULL
                GROUP BY gd.id, gd.name, gocrd.case_code
            ),
            FirstDesigns AS (
                SELECT
                    dc.dentist_id,
                    dc.dentist_name,
                    dc.case_code,
                    dc.case_tags,
                    fd.design_type
                FROM DentistCases dc
                LEFT JOIN first_designs fd ON dc.case_code = fd.case_code
            ),
            dentist_order_metrics AS (
              SELECT
                fd.dentist_id,
                fd.dentist_name,
                COUNT(DISTINCT CASE WHEN fd.design_type = 'pre' THEN fd.case_code END) AS pre_design_tag_cases,
                COUNT(DISTINCT CASE WHEN fd.design_type = 'pre' AND pcm.has_call = 1 THEN fd.case_code END) AS pre_called_cases,
                COUNT(DISTINCT CASE WHEN fd.design_type = 'pre' AND pcm.connected_calls > 0 THEN fd.case_code END) AS pre_connected_cases,
                COALESCE(SUM(CASE WHEN fd.design_type = 'pre' THEN pcm.connected_calls END), 0) AS pre_total_connected_calls,
                COALESCE(SUM(CASE WHEN fd.design_type = 'pre' THEN pcm.total_duration_sec END), 0) AS pre_total_duration_sec,
                COUNT(DISTINCT CASE WHEN fd.design_type = 'post' THEN fd.case_code END) AS post_design_tag_cases,
                COUNT(DISTINCT CASE WHEN fd.design_type = 'post' AND ppcm.has_call = 1 THEN fd.case_code END) AS post_called_cases,
                COUNT(DISTINCT CASE WHEN fd.design_type = 'post' AND ppcm.connected_calls > 0 THEN fd.case_code END) AS post_connected_cases,
                COALESCE(SUM(CASE WHEN fd.design_type = 'post' THEN ppcm.connected_calls END), 0) AS post_total_connected_calls,
                COALESCE(SUM(CASE WHEN fd.design_type = 'post' THEN ppcm.total_duration_sec END), 0) AS post_total_duration_sec
            FROM FirstDesigns fd
            LEFT JOIN pre_call_metrics pcm 
                ON fd.case_code = pcm.case_code 
                AND fd.design_type = 'pre'
            LEFT JOIN post_call_metrics ppcm 
                ON fd.case_code = ppcm.case_code 
                AND fd.design_type = 'post'
            GROUP BY fd.dentist_id, fd.dentist_name
            ),
            dentist_case_metrics AS (
              SELECT 
                gd.id AS dentist_id,
                COUNT(DISTINCT gc.code) AS all_case,
                COUNT(DISTINCT CASE WHEN gc.case_tags LIKE '%CASE_TAG-DENTIST_FIRST_CASE%' THEN gc.code END) AS first_tag_cases
              FROM gms_dentist gd 
              LEFT JOIN gms_case gc ON gc.dentist_code = gd.code
              where 1=1
              GROUP BY gd.id
            )
            select
              COALESCE(gms_team.name, '未分组') AS team_name,
              gms_dentist.name AS dentist_name,
              gms_dentist.code AS dentist_code,
              COALESCE(dcm.all_case, 0) AS all_cases_num,
              COALESCE(dcm.first_tag_cases, 0) AS first_tag_cases_num,
              COALESCE(dom.pre_design_tag_cases, 0) AS pre_design_tag_cases_num,
              COALESCE(dom.pre_called_cases, 0) AS pre_called_cases_num,
              CASE WHEN COALESCE(dom.pre_design_tag_cases, 0) > 0 
                   THEN ROUND(COALESCE(dom.pre_called_cases, 0) * 100.0 / dom.pre_design_tag_cases, 2)
                   ELSE 0 END AS pre_called_cases_rate,
              COALESCE(dom.pre_connected_cases, 0) AS pre_connected_cases_num,
              CASE WHEN COALESCE(dom.pre_design_tag_cases, 0) > 0 
                   THEN ROUND(COALESCE(dom.pre_connected_cases, 0) * 100.0 / dom.pre_design_tag_cases, 2)
                   ELSE 0 END AS pre_connected_cases_rate,
              COALESCE(dom.pre_total_connected_calls, 0) AS pre_total_connected_call_num,
              CASE WHEN COALESCE(dom.pre_connected_cases, 0) > 0 
                   THEN ROUND(COALESCE(dom.pre_total_connected_calls, 0) * 1.0 / dom.pre_connected_cases, 2)
                   ELSE 0 END AS pre_average_connected_call_num,
              CONCAT(
                LPAD(FLOOR(COALESCE(dom.pre_total_duration_sec, 0) / 3600), 2, '0'), ':',
                LPAD(FLOOR((COALESCE(dom.pre_total_duration_sec, 0) % 3600) / 60), 2, '0'), ':',
                LPAD(COALESCE(dom.pre_total_duration_sec, 0) % 60, 2, '0')
              ) AS pre_total_duration_sec,
              CASE WHEN COALESCE(dom.pre_connected_cases, 0) > 0 
                   THEN CONCAT(
                          LPAD(FLOOR(COALESCE(dom.pre_total_duration_sec, 0) / dom.pre_connected_cases / 3600), 2, '0'), ':',
                          LPAD(FLOOR((COALESCE(dom.pre_total_duration_sec, 0) / dom.pre_connected_cases % 3600) / 60), 2, '0'), ':',
                          LPAD(FLOOR(COALESCE(dom.pre_total_duration_sec, 0) / dom.pre_connected_cases % 60), 2, '0')
                        )
                   ELSE '00:00:00' END AS pre_average_duration_sec,
              COALESCE(dom.post_design_tag_cases, 0) AS post_design_tag_cases_num,
              COALESCE(dom.post_called_cases, 0) AS post_called_cases_num,
              CASE WHEN COALESCE(dom.post_design_tag_cases, 0) > 0 
                   THEN ROUND(COALESCE(dom.post_called_cases, 0) * 100.0 / dom.post_design_tag_cases, 2)
                   ELSE 0 END AS post_called_cases_rate,
              COALESCE(dom.post_connected_cases, 0) AS post_connected_cases_num,
              CASE WHEN COALESCE(dom.post_design_tag_cases, 0) > 0 
                   THEN ROUND(COALESCE(dom.post_connected_cases, 0) * 100.0 / dom.post_design_tag_cases, 2)
                   ELSE 0 END AS post_connected_cases_rate,
              COALESCE(dom.post_total_connected_calls, 0) AS post_total_connected_call_num,
              CASE WHEN COALESCE(dom.post_connected_cases, 0) > 0 
                   THEN ROUND(COALESCE(dom.post_total_connected_calls, 0) * 1.0 / dom.post_connected_cases, 2)
                   ELSE 0 END AS post_average_connected_call_num,
              CONCAT(
                LPAD(FLOOR(COALESCE(dom.post_total_duration_sec, 0) / 3600), 2, '0'), ':',
                LPAD(FLOOR((COALESCE(dom.post_total_duration_sec, 0) % 3600) / 60), 2, '0'), ':',
                LPAD(COALESCE(dom.post_total_duration_sec, 0) % 60, 2, '0')
              ) AS post_total_duration_sec,
              CASE WHEN COALESCE(dom.post_connected_cases, 0) > 0 
                   THEN CONCAT(
                          LPAD(FLOOR(COALESCE(dom.post_total_duration_sec, 0) / dom.post_connected_cases / 3600), 2, '0'), ':',
                          LPAD(FLOOR((COALESCE(dom.post_total_duration_sec, 0) / dom.post_connected_cases % 3600) / 60), 2, '0'), ':',
                          LPAD(FLOOR(COALESCE(dom.post_total_duration_sec, 0) / dom.post_connected_cases % 60), 2, '0')
                        )
                   ELSE '00:00:00' END AS post_average_duration_sec
            FROM dentist_in_scope dis
            JOIN gms_dentist ON dis.dentist_code = gms_dentist.code
            LEFT JOIN gms_order_case_stakeholder_detail gocsd on dis.dentist_code = gocsd.dentist_code
            LEFT join gms_order on gocsd.order_id = gms_order.id
            LEFT join gms_team on gms_order.team_id = gms_team.id
            LEFT JOIN dentist_order_metrics dom ON gms_dentist.id = dom.dentist_id
            LEFT JOIN dentist_case_metrics dcm ON gms_dentist.id = dcm.dentist_id
            where 1 = 1
              AND (? IS NULL OR gms_team.name = ?)
              AND (? IS NULL OR gms_dentist.id = ?)
            GROUP BY gms_dentist.id
            ORDER BY gms_dentist.id
            LIMIT ? OFFSET ?
            """;

    /**
     * 计数查询SQL - 使用位置参数
     */
    private static final String COUNT_QUERY = """
            WITH ExpandedOrders AS (
              SELECT 
                id AS order_id,
                'pre' AS design_type
              FROM gms_order 
              WHERE tags LIKE '%ORDER_TAG-PRE_DESIGN_COMMUNICATION%'
              
              UNION ALL 
              
              SELECT 
                id AS order_id,
                'post' AS design_type
              FROM gms_order 
              WHERE tags LIKE '%ORDER_TAG-POST_DESIGN_COMMUNICATION%'
            ),
            first_designs AS (
                select 
                case_first_design.case_code,
                case_first_design.design_type,
                case_first_design.first_design_id,
                gd.send_out as first_design_send_time 
                from 
                ( SELECT
                  gc.code AS case_code,
                  eo.design_type,
                  MIN(gd.id) AS first_design_id,
                  MAX(gd.send_out) AS first_design_send_time
                FROM ExpandedOrders eo
                LEFT JOIN gms_order_case_related_detail gocrd 
                  ON eo.order_id = gocrd.order_id
                LEFT JOIN gms_case gc 
                  ON gc.code = gocrd.case_code
                LEFT JOIN gms_design gd 
                  ON gc.code = gd.case_code
                WHERE (? IS NULL OR gd.send_out >= ?)
                  AND (? IS NULL OR gd.send_out <= ?)
                GROUP BY gc.code, eo.design_type
                ) as case_first_design
                left join gms_design gd on case_first_design.first_design_id = gd.id
                where gd.send_out IS NOT null
                  AND gd.status IN ('SENT','CONFIRMED','MODIFICATION','NOT_MODIFICATION')
            ),
            pre_design_orders AS (
              SELECT 
                fd.case_code,
                fd.design_type,
                fd.first_design_send_time,
                gt.code AS task_code,
                gt.assignee_id as designer_id,
                gc.dentist_code as dentist_code,
                ROW_NUMBER() OVER (
                    PARTITION BY fd.case_code, fd.design_type 
                    ORDER BY go2.finished DESC
                ) AS rn
              FROM first_designs fd
              JOIN gms_order_case_related_detail gocrd 
                ON fd.case_code = gocrd.case_code
              JOIN gms_order go2 
                ON go2.id = gocrd.order_id
              JOIN gms_task gt 
                ON gt.process_instance_id = go2.active_process_instance_id 
              JOIN gms_task_type gtt 
                ON gt.type_id = gtt.id
              JOIN gms_case gc
                ON gocrd.case_code = gc.code
              WHERE gt.status = 'COMPLETED'
                AND gtt.id IN (29, 31, 35)
                AND gt.finished < fd.first_design_send_time
            ),
            dentist_in_scope AS (
              SELECT DISTINCT dentist_code
              FROM pre_design_orders
              WHERE dentist_code IS NOT NULL
            )
            SELECT COUNT(DISTINCT gms_dentist.id)
            FROM dentist_in_scope dis
            JOIN gms_dentist ON dis.dentist_code = gms_dentist.code
            LEFT JOIN gms_order_case_stakeholder_detail gocsd on dis.dentist_code = gocsd.dentist_code
            LEFT join gms_order on gocsd.order_id = gms_order.id
            LEFT join gms_team on gms_order.team_id = gms_team.id
            where 1 = 1
              AND (? IS NULL OR gms_team.name = ?)
              AND (? IS NULL OR gms_dentist.id = ?)
            """;

    @Override
    public PageImpl<DentistCommunicationReportVM> dentistCommunicationReport(
            DentistCommunicationReportQueryVM param) {
        
        log.info("开始执行医生沟通报表查询，参数: {}", param);
        
        try {
            // 参数验证
            validateQueryParams(param);
            
            // 执行数据查询
            List<Object[]> resultList = executeMainQuery(param);
            
            // 执行总数查询
            Long totalElements = executeCountQuery(param);
            
            log.info("查询完成，总记录数: {}, 当前页记录数: {}", totalElements, resultList.size());
            
            // 映射结果到ViewModel
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
     * 执行主查询获取数据列表 - 使用位置参数避免元数据问题
     */
    @SuppressWarnings("unchecked")
    private List<Object[]> executeMainQuery(DentistCommunicationReportQueryVM param) {
        log.debug("执行主查询 - 页码: {}, 页大小: {}", param.getPageNumber(), param.getPageSize());
        
        // 计算LIMIT和OFFSET
        int limit = param.getPageSize();
        int offset = param.getPageNumber() * param.getPageSize();
        
        Query query = entityManager.createNativeQuery(MAIN_QUERY_TEMPLATE);
        
        // 设置位置参数 - 按照SQL中的顺序
        setPositionalParameters(query, param, limit, offset);
        
        log.debug("分页参数 - LIMIT: {}, OFFSET: {}", limit, offset);
        
        List<Object[]> results = query.getResultList();
        log.debug("主查询执行完成，返回 {} 条记录", results.size());
        
        return results;
    }

    /**
     * 执行计数查询获取总记录数 - 使用位置参数
     */
    private Long executeCountQuery(DentistCommunicationReportQueryVM param) {
        log.debug("执行计数查询");
        
        Query query = entityManager.createNativeQuery(COUNT_QUERY);
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
        ZonedDateTime startTime = param.getStartTime();
        ZonedDateTime endTime = param.getEndTime();
        String teamName = normalizeStringParam(param.getTeamName());
        String dentistId = normalizeStringParam(param.getDentistId());
        
        // 按照SQL中出现的顺序设置参数
        int paramIndex = 1;
        
        // first_designs 子查询中的参数 (4个)
        query.setParameter(paramIndex++, startTime);    // 1: startTime check 1
        query.setParameter(paramIndex++, startTime);    // 2: startTime value
        query.setParameter(paramIndex++, endTime);      // 3: endTime check
        query.setParameter(paramIndex++, endTime);      // 4: endTime value
        
        // 主查询where子句中的参数 (4个)
        query.setParameter(paramIndex++, teamName);     // 5: teamName check
        query.setParameter(paramIndex++, teamName);     // 6: teamName value
        query.setParameter(paramIndex++, dentistId);    // 7: dentistId check
        query.setParameter(paramIndex++, dentistId);    // 8: dentistId value
        
        // 分页参数 (2个)
        query.setParameter(paramIndex++, limit);        // 9: LIMIT
        query.setParameter(paramIndex, offset);         // 10: OFFSET
        
        log.debug("主查询参数设置完成 - startTime: {}, endTime: {}, teamName: {}, dentistId: {}, limit: {}, offset: {}", 
                 startTime, endTime, teamName, dentistId, limit, offset);
    }

    /**
     * 设置计数查询的位置参数
     */
    private void setPositionalParametersForCount(Query query, DentistCommunicationReportQueryVM param) {
        ZonedDateTime startTime = param.getStartTime();
        ZonedDateTime endTime = param.getEndTime();
        String teamName = normalizeStringParam(param.getTeamName());
        String dentistId = normalizeStringParam(param.getDentistId());
        
        // 按照SQL中出现的顺序设置参数
        int paramIndex = 1;
        
        // first_designs 子查询中的参数 (4个)
        query.setParameter(paramIndex++, startTime);    // 1: startTime check 1
        query.setParameter(paramIndex++, startTime);    // 2: startTime value
        query.setParameter(paramIndex++, endTime);      // 3: endTime check
        query.setParameter(paramIndex++, endTime);      // 4: endTime value
        
        // 主查询where子句中的参数 (4个)
        query.setParameter(paramIndex++, teamName);     // 5: teamName check
        query.setParameter(paramIndex++, teamName);     // 6: teamName value
        query.setParameter(paramIndex++, dentistId);    // 7: dentistId check
        query.setParameter(paramIndex, dentistId);      // 8: dentistId value
        
        log.debug("计数查询参数设置完成 - startTime: {}, endTime: {}, teamName: {}, dentistId: {}", 
                 startTime, endTime, teamName, dentistId);
    }

    /**
     * 将Object[]映射到DentistCommunicationReportVM
     * 按照SQL查询结果的字段顺序进行精确映射
     */
    private DentistCommunicationReportVM mapToViewModel(Object[] row) {
        if (row == null || row.length < 23) {
            log.warn("查询结果行数据不完整，期望23个字段，实际: {}", row != null ? row.length : 0);
            return createEmptyViewModel();
        }
        
        return DentistCommunicationReportVM.builder()
            // 基础信息字段 (0-4)
            .teamName(safeToString(row[0]))                         // team_name
            .dentistName(safeToString(row[1]))                      // dentist_name  
            .dentistCode(safeToString(row[2]))                      // dentist_code
            .allCasesNum(safeToString(row[3]))                      // all_cases_num
            .firstTagCasesNum(safeToString(row[4]))                 // first_tag_cases_num
            
            // 设计前沟通指标字段 (5-13)
            .preDesignTagCasesNum(safeToString(row[5]))             // pre_design_tag_cases_num
            .preCalledCasesNum(safeToString(row[6]))                // pre_called_cases_num
            .preCalledCasesRate(safeToString(row[7]))               // pre_called_cases_rate
            .preConnectedCasesNum(safeToString(row[8]))             // pre_connected_cases_num
            .preConnectedCasesRate(safeToString(row[9]))            // pre_connected_cases_rate
            .preTotalConnectedCallNum(safeToString(row[10]))        // pre_total_connected_call_num
            .preAverageConnectedCallNum(safeToString(row[11]))      // pre_average_connected_call_num
            .preTotalDurationSec(safeToString(row[12]))             // pre_total_duration_sec
            .preAverageDurationSec(safeToString(row[13]))           // pre_average_duration_sec
            
            // 设计后讲解指标字段 (14-22)
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
        
        // 时间范围验证 - 允许null值
        if (param.getStartTime() != null && param.getEndTime() != null) {
            if (param.getStartTime().isAfter(param.getEndTime())) {
                throw new IllegalArgumentException("开始时间不能大于结束时间");
            }
        }
        
        log.debug("参数验证通过");
    }

    /**
     * 标准化字符串参数 - 处理null和空字符串
     */
    private String normalizeStringParam(String param) {
        if (param == null) {
            return null;
        }
        
        String trimmed = param.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 安全地将Object转换为String，处理null值
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