package com.example.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
@Transactional("dorisTransactionManager")
public class DentistCommunicationReportRepository {

    @PersistenceContext(unitName = "doris")
    private EntityManager entityManager;

    private static final String MAIN_QUERY = """
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
                WHERE (:startTime IS NULL OR gd.send_out >= :startTime)
                  AND (:endTime IS NULL OR gd.send_out <= :endTime)
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
              and pdo.design_type = "pre"
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
              and pdo.design_type = "post"
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
              AND (:teamName IS NULL OR gms_team.name = :teamName)
              AND (:dentistId IS NULL OR gms_dentist.id = :dentistId)
            GROUP BY gms_dentist.id
            ORDER BY gms_dentist.id
            """;

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
                WHERE (:startTime IS NULL OR gd.send_out >= :startTime)
                  AND (:endTime IS NULL OR gd.send_out <= :endTime)
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
              AND (:teamName IS NULL OR gms_team.name = :teamName)
              AND (:dentistId IS NULL OR gms_dentist.id = :dentistId)
            """;

    /**
     * 查询医生沟通报表数据
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findDentistCommunicationReport(ZonedDateTime startTime,
                                                         ZonedDateTime endTime,
                                                         String teamName,
                                                         String dentistId,
                                                         int pageNumber,
                                                         int pageSize) {
        Query query = entityManager.createNativeQuery(MAIN_QUERY);
        setParameters(query, startTime, endTime, teamName, dentistId);
        
        // 设置分页
        query.setFirstResult(pageNumber * pageSize);
        query.setMaxResults(pageSize);
        
        return query.getResultList();
    }

    /**
     * 查询总记录数
     */
    public Long countDentistCommunicationReport(ZonedDateTime startTime,
                                               ZonedDateTime endTime,
                                               String teamName,
                                               String dentistId) {
        Query query = entityManager.createNativeQuery(COUNT_QUERY);
        setParameters(query, startTime, endTime, teamName, dentistId);
        
        Object result = query.getSingleResult();
        return result != null ? ((Number) result).longValue() : 0L;
    }

    /**
     * 设置查询参数
     */
    private void setParameters(Query query, ZonedDateTime startTime, ZonedDateTime endTime, 
                              String teamName, String dentistId) {
        query.setParameter("startTime", startTime);
        query.setParameter("endTime", endTime);
        query.setParameter("teamName", teamName);
        query.setParameter("dentistId", dentistId);
    }
}