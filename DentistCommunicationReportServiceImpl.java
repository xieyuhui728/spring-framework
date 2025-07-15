package com.angelalign.gms.service.report.impl;

import com.angelalign.gms.service.report.DentistCommunicationReportService;
import com.angelalign.gms.web.rest.vm.report.DentistCommunicationDetailReportVM;
import com.angelalign.gms.web.rest.vm.report.DentistCommunicationReportDetailQueryVM;
import com.angelalign.gms.web.rest.vm.report.DentistCommunicationReportQueryVM;
import com.angelalign.gms.web.rest.vm.report.DentistCommunicationReportVM;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@Transactional
public class DentistCommunicationReportServiceImpl implements DentistCommunicationReportService {

    @PersistenceContext(unitName = "doris")
    private EntityManager entityManager;

    /**
     * 主查询SQL
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
                WHERE 1=1 
                    AND (gd.send_out >= :startTime)
                    AND (gd.send_out <= :endTime)
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
                GROUP BY gd.id, gd.name, gd.code, gocrd.case_code
            ),
            FirstDesigns AS (
                SELECT
                    dc.dentist_id,
                    dc.dentist_name,
                    dc.dentist_code,
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
                fd.dentist_code,
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
            GROUP BY fd.dentist_id, fd.dentist_name, fd.dentist_code
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
              COALESCE(MAX(gms_team.name), '未分组') AS team_name,
              MAX(gms_dentist.name) AS dentist_name,
              MAX(gms_dentist.code) AS dentist_code,
              COALESCE(MAX(dcm.all_case), 0) AS all_cases_num,
              COALESCE(MAX(dcm.first_tag_cases), 0) AS first_tag_cases_num,
              COALESCE(MAX(dom.pre_design_tag_cases), 0) AS pre_design_tag_cases_num,
              COALESCE(MAX(dom.pre_called_cases), 0) AS pre_called_cases_num,
              CASE WHEN COALESCE(MAX(dom.pre_design_tag_cases), 0) > 0 
                   THEN ROUND(COALESCE(MAX(dom.pre_called_cases), 0) * 100.0 / MAX(dom.pre_design_tag_cases), 2)
                   ELSE 0 END AS pre_called_cases_rate,
              COALESCE(MAX(dom.pre_connected_cases), 0) AS pre_connected_cases_num,
              CASE WHEN COALESCE(MAX(dom.pre_design_tag_cases), 0) > 0 
                   THEN ROUND(COALESCE(MAX(dom.pre_connected_cases), 0) * 100.0 / MAX(dom.pre_design_tag_cases), 2)
                   ELSE 0 END AS pre_connected_cases_rate,
              COALESCE(MAX(dom.pre_total_connected_calls), 0) AS pre_total_connected_call_num,
              CASE WHEN COALESCE(MAX(dom.pre_connected_cases), 0) > 0 
                   THEN ROUND(COALESCE(MAX(dom.pre_total_connected_calls), 0) * 1.0 / MAX(dom.pre_connected_cases), 2)
                   ELSE 0 END AS pre_average_connected_call_num,
              CONCAT(
                LPAD(FLOOR(COALESCE(MAX(dom.pre_total_duration_sec), 0) / 3600), 2, '0'), ':',
                LPAD(FLOOR((COALESCE(MAX(dom.pre_total_duration_sec), 0) % 3600) / 60), 2, '0'), ':',
                LPAD(COALESCE(MAX(dom.pre_total_duration_sec), 0) % 60, 2, '0')
              ) AS pre_total_duration_sec,
              CASE WHEN COALESCE(MAX(dom.pre_connected_cases), 0) > 0 
                   THEN CONCAT(
                          LPAD(FLOOR(COALESCE(MAX(dom.pre_total_duration_sec), 0) / MAX(dom.pre_connected_cases) / 3600), 2, '0'), ':',
                          LPAD(FLOOR((COALESCE(MAX(dom.pre_total_duration_sec), 0) / MAX(dom.pre_connected_cases) % 3600) / 60), 2, '0'), ':',
                          LPAD(FLOOR(COALESCE(MAX(dom.pre_total_duration_sec), 0) / MAX(dom.pre_connected_cases) % 60), 2, '0')
                        )
                   ELSE '00:00:00' END AS pre_average_duration_sec,
              COALESCE(MAX(dom.post_design_tag_cases), 0) AS post_design_tag_cases_num,
              COALESCE(MAX(dom.post_called_cases), 0) AS post_called_cases_num,
              CASE WHEN COALESCE(MAX(dom.post_design_tag_cases), 0) > 0 
                   THEN ROUND(COALESCE(MAX(dom.post_called_cases), 0) * 100.0 / MAX(dom.post_design_tag_cases), 2)
                   ELSE 0 END AS post_called_cases_rate,
              COALESCE(MAX(dom.post_connected_cases), 0) AS post_connected_cases_num,
              CASE WHEN COALESCE(MAX(dom.post_design_tag_cases), 0) > 0 
                   THEN ROUND(COALESCE(MAX(dom.post_connected_cases), 0) * 100.0 / MAX(dom.post_design_tag_cases), 2)
                   ELSE 0 END AS post_connected_cases_rate,
              COALESCE(MAX(dom.post_total_connected_calls), 0) AS post_total_connected_call_num,
              CASE WHEN COALESCE(MAX(dom.post_connected_cases), 0) > 0 
                   THEN ROUND(COALESCE(MAX(dom.post_total_connected_calls), 0) * 1.0 / MAX(dom.post_connected_cases), 2)
                   ELSE 0 END AS post_average_connected_call_num,
              CONCAT(
                LPAD(FLOOR(COALESCE(MAX(dom.post_total_duration_sec), 0) / 3600), 2, '0'), ':',
                LPAD(FLOOR((COALESCE(MAX(dom.post_total_duration_sec), 0) % 3600) / 60), 2, '0'), ':',
                LPAD(COALESCE(MAX(dom.post_total_duration_sec), 0) % 60, 2, '0')
              ) AS post_total_duration_sec,
              CASE WHEN COALESCE(MAX(dom.post_connected_cases), 0) > 0 
                   THEN CONCAT(
                          LPAD(FLOOR(COALESCE(MAX(dom.post_total_duration_sec), 0) / MAX(dom.post_connected_cases) / 3600), 2, '0'), ':',
                          LPAD(FLOOR((COALESCE(MAX(dom.post_total_duration_sec), 0) / MAX(dom.post_connected_cases) % 3600) / 60), 2, '0'), ':',
                          LPAD(FLOOR(COALESCE(MAX(dom.post_total_duration_sec), 0) / MAX(dom.post_connected_cases) % 60), 2, '0')
                        )
                   ELSE '00:00:00' END AS post_average_duration_sec
            FROM dentist_in_scope dis
            JOIN gms_dentist ON dis.dentist_code = gms_dentist.code
            LEFT JOIN gms_order_case_stakeholder_detail gocsd on dis.dentist_code = gocsd.dentist_code
            LEFT join gms_order on gocsd.order_id = gms_order.id
            LEFT join gms_team on gms_order.team_id = gms_team.id
            LEFT JOIN dentist_order_metrics dom ON gms_dentist.id = dom.dentist_id
            LEFT JOIN dentist_case_metrics dcm ON gms_dentist.id = dcm.dentist_id
            WHERE 1 = 1
            """;

    /**
     * 计数查询SQL
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
                WHERE 1=1
                    AND (gd.send_out >= :startTime)
                    AND (gd.send_out <= :endTime)
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
            WHERE 1 = 1
            """;

    /**
     * 明细报表主查询SQL
     */
    private static final String DETAIL_MAIN_QUERY_TEMPLATE = """
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
                WHERE 1=1 
                    AND (gd.send_out >= :startTime)
                    AND (gd.send_out <= :endTime)
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
            case_detail_base AS (
              SELECT 
                pdo.case_code,
                pdo.first_design_send_time,
                pdo.task_code,
                pdo.designer_id,
                pdo.dentist_code,
                gc.created_date as case_created_date,
                gc.patient_code,
                gc.agency_code,
                gd.name as designer_name,
                gms_dentist.name as dentist_name,
                gms_agency.name as agency_name,
                gms_patient.name as patient_name,
                COALESCE(MAX(gms_team.name), '未分组') AS team_name,
                -- 检查是否有设计前沟通标签
                MAX(CASE WHEN go_pre.tags LIKE '%ORDER_TAG-PRE_DESIGN_COMMUNICATION%' THEN 1 ELSE 0 END) AS has_pre_communication,
                -- 检查是否有设计后沟通标签
                MAX(CASE WHEN go_post.tags LIKE '%ORDER_TAG-POST_DESIGN_COMMUNICATION%' THEN 1 ELSE 0 END) AS has_post_communication
              FROM pre_design_orders pdo
              LEFT JOIN gms_case gc ON pdo.case_code = gc.code
              LEFT JOIN gms_designer gd ON pdo.designer_id = gd.id
              LEFT JOIN gms_dentist ON gc.dentist_code = gms_dentist.code
              LEFT JOIN gms_agency ON gc.agency_code = gms_agency.code
              LEFT JOIN gms_patient ON gc.patient_code = gms_patient.code
              LEFT JOIN gms_order_case_related_detail gocrd ON pdo.case_code = gocrd.case_code
              LEFT JOIN gms_order go_pre ON gocrd.order_id = go_pre.id AND go_pre.tags LIKE '%ORDER_TAG-PRE_DESIGN_COMMUNICATION%'
              LEFT JOIN gms_order go_post ON gocrd.order_id = go_post.id AND go_post.tags LIKE '%ORDER_TAG-POST_DESIGN_COMMUNICATION%'
              LEFT JOIN gms_order_case_stakeholder_detail gocsd ON gc.dentist_code = gocsd.dentist_code
              LEFT JOIN gms_order gms_order ON gocsd.order_id = gms_order.id
              LEFT JOIN gms_team ON gms_order.team_id = gms_team.id
              WHERE pdo.rn = 1
              GROUP BY pdo.case_code, pdo.first_design_send_time, pdo.task_code, pdo.designer_id, pdo.dentist_code, 
                       gc.created_date, gc.patient_code, gc.agency_code, gd.name, gms_dentist.name, gms_agency.name, gms_patient.name
            ),
            call_metrics AS (
              SELECT
                cdb.case_code,
                -- 设计前拨打电话数
                COUNT(CASE WHEN tocl_pre.id IS NOT NULL THEN 1 END) AS pre_call_count,
                -- 设计前接通电话数
                COUNT(CASE WHEN tocl_pre.status = '接通' THEN 1 END) AS pre_connected_count,
                -- 设计前接通电话时长
                SUM(
                  CASE
                    WHEN tocl_pre.status = '接通' AND tocl_pre.duration IS NOT NULL 
                    THEN 
                      (
                        COALESCE(SUBSTRING_INDEX(tocl_pre.duration, ':', 1), 0) * 3600 +
                        COALESCE(SUBSTRING_INDEX(SUBSTRING_INDEX(tocl_pre.duration, ':', 2), ':', -1), 0) * 60 +
                        COALESCE(SUBSTRING_INDEX(tocl_pre.duration, ':', -1), 0)
                      )
                    ELSE 0
                  END
                ) AS pre_duration_sec,
                -- 设计后拨打电话数
                COUNT(CASE WHEN tocl_post.id IS NOT NULL THEN 1 END) AS post_call_count,
                -- 设计后接通电话数
                COUNT(CASE WHEN tocl_post.status = '接通' THEN 1 END) AS post_connected_count,
                -- 设计后接通电话时长
                SUM(
                  CASE
                    WHEN tocl_post.status = '接通' AND tocl_post.duration IS NOT NULL 
                    THEN 
                      (
                        COALESCE(SUBSTRING_INDEX(tocl_post.duration, ':', 1), 0) * 3600 +
                        COALESCE(SUBSTRING_INDEX(SUBSTRING_INDEX(tocl_post.duration, ':', 2), ':', -1), 0) * 60 +
                        COALESCE(SUBSTRING_INDEX(tocl_post.duration, ':', -1), 0)
                      )
                    ELSE 0
                  END
                ) AS post_duration_sec
              FROM case_detail_base cdb
              LEFT JOIN tt_order_call_log tocl_pre 
                ON cdb.task_code = tocl_pre.order_no
                AND tocl_pre.begin_time < cdb.first_design_send_time
              LEFT JOIN tt_order_call_log tocl_post 
                ON cdb.task_code = tocl_post.order_no
                AND tocl_post.begin_time >= cdb.first_design_send_time
              GROUP BY cdb.case_code
            )
            SELECT
              cdb.case_code,
              cdb.team_name,
              cdb.designer_name,
              cdb.dentist_name,
              cdb.agency_name,
              cdb.patient_name,
              cdb.case_created_date,
              cdb.first_design_send_time,
              cdb.task_code,
              cdb.has_pre_communication,
              COALESCE(cm.pre_call_count, 0) AS pre_call_count,
              COALESCE(cm.pre_connected_count, 0) AS pre_connected_count,
              CONCAT(
                LPAD(FLOOR(COALESCE(cm.pre_duration_sec, 0) / 3600), 2, '0'), ':',
                LPAD(FLOOR((COALESCE(cm.pre_duration_sec, 0) % 3600) / 60), 2, '0'), ':',
                LPAD(COALESCE(cm.pre_duration_sec, 0) % 60, 2, '0')
              ) AS pre_duration,
              cdb.has_post_communication,
              COALESCE(cm.post_call_count, 0) AS post_call_count,
              COALESCE(cm.post_connected_count, 0) AS post_connected_count,
              CONCAT(
                LPAD(FLOOR(COALESCE(cm.post_duration_sec, 0) / 3600), 2, '0'), ':',
                LPAD(FLOOR((COALESCE(cm.post_duration_sec, 0) % 3600) / 60), 2, '0'), ':',
                LPAD(COALESCE(cm.post_duration_sec, 0) % 60, 2, '0')
              ) AS post_duration
            FROM case_detail_base cdb
            LEFT JOIN call_metrics cm ON cdb.case_code = cm.case_code
            WHERE 1 = 1
            """;

    /**
     * 明细报表计数查询SQL
     */
    private static final String DETAIL_COUNT_QUERY = """
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
                WHERE 1=1 
                    AND (gd.send_out >= :startTime)
                    AND (gd.send_out <= :endTime)
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
            )
            SELECT COUNT(DISTINCT pdo.case_code)
            FROM pre_design_orders pdo
            LEFT JOIN gms_case gc ON pdo.case_code = gc.code
            WHERE pdo.rn = 1
            """;

    /**
     *  新手首例沟通执行报表-医生维度
     * 筛选条件
     * 第一个方案发送日期：开始日期-结束日期（日期范围）。第一个方案：病例第一个"已发送"/"已确认"/"有修改意见"/"不修改"的方案
     *
     * 设计组
     *
     * 医生
     *
     * 列。以下统计均包含条件：所有病例的第一个"已发送"/"已确认"/"有修改意见"/"不修改"方案的发送时间在选择的时间范围内
     * 设计组
     * 医生
     * 总病例数
     * 新手首例病例数
     * 病例标签包含"新手首例"
     * 设计前沟通病例数
     * 订单标签包含"设计前沟通"
     * 设计前沟通拨打电话病例数：
     * 订单标签包含"设计前沟通"
     * 第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单关联的且呼叫时间在工单完成时间前的外呼记录>=1
     * 设计前沟通拨打率=设计前沟通拨打电话病例数/设计前沟通病例数
     * 设计前沟通接通电话病例数
     * 订单标签包含"设计前沟通"
     * 第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单有关联且呼叫时间在工单完成前且被接通的外呼记录数>=1
     * 设计前沟通接通率=设计前沟通接通电话病例数/设计前沟通病例数
     * 设计前沟通接通电话通话数
     * 订单标签包含"设计前沟通"
     * 第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单有关联且呼叫时间在工单完成前且被接通的外呼记录数
     * 设计前沟通例均通话次数=设计前沟通接通电话通话数/设计前沟通接通电话病例数
     * 设计前沟通接通电话时长
     * 订单标签包含"设计前沟通"
     *  第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单有关联且呼叫时间在工单完成前且接通的呼叫记录的总通话时长（1个工单可能有多个呼叫记录）
     * 设计前沟通例均通话时长=设计前沟通接通电话时长/设计前沟通接通电话病例数
     * 设计后讲解病例数：病例标签包含"设计后讲解"
     * 设计后讲解拨打电话病例数：
     * 订单标签包含"设计后讲解"
     * 第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单有关联且呼叫时间在第一个方案发送后的外呼记录>=1
     * 设计后讲解拨打率=设计后讲解拨打电话病例数/设计后讲解病例数
     * 设计后讲解接通电话病例数
     * 订单标签包含"设计后讲解"
     * 第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单有关联且呼叫时间在第一个方案发送后且被接通的外呼记录数>=1
     * 设计后讲解接通率=设计后讲解接通电话病例数/设计后讲解病例数
     * 设计后讲解接通电话通话数
     * 订单标签包含"设计后讲解"
     * 第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单有关联且呼叫时间在第一个方案发送后且被接通的外呼记录数
     * 设计后讲解例均通话次数=设计后讲解接通电话通话数/设计后讲解接通电话病例数
     * 设计后讲解接通电话时长
     * 订单标签包含"设计后讲解"
     *  第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单关联的且呼叫时间在第一个方案发送后且接通的呼叫记录总通话时长（1个工单可能有多个呼叫记录）
     * 设计后讲解例均通话时长=设计后讲解接通电话时长/设计后讲解接通电话病例数
     * @param param
     * @return
     */
    @Override
    public PageImpl<DentistCommunicationReportVM> dentistCommunicationReport(
            DentistCommunicationReportQueryVM param) {

        log.info("开始执行医生沟通报表查询，参数: {}", param);

        try {
            validateQueryParams(param);

            List<Object[]> resultList = executeMainQuery(param);

            Long totalElements = executeCountQuery(param);

            List<DentistCommunicationReportVM> content = resultList
                    .stream()
                    .map(this::mapToViewModel)
                    .collect(Collectors.toList());

            Pageable pageable = PageRequest.of(param.getPageNumber(), param.getPageSize());

            return new PageImpl<>(content, pageable, totalElements);

        } catch (Exception e) {
            log.warn("执行医生沟通报表查询失败，参数: {}", param, e);
            throw new RuntimeException("执行医生沟通报表查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 新手医生沟通明细报表
     *
     * 筛选条件
     * 病例编号
     * 第一个方案发送日期：开始日期-结束日期（日期范围）。第一个方案：病例第一个"已发送"/"已确认"的方案
     * 设计id
     * 设计id
     * 医生code
     * 机构code
     * 患者code
     * 
     * 列
     * 病例编号
     * 设计组
     * 设计师
     * 医生
     * 机构
     * 患者
     * 病例提交日期
     * 第一个方案发送日期
     * 工单编号：第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单编号
     * 是否"设计前沟通"：第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单所属的订单的订单标签包含"设计前沟通"。以下几项设计前呼叫统计，标签包含"设计前沟通"或不包含"设计前沟通"的都统计。
     * 设计前拨打电话数：第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单关联的且呼叫时间在工单完成前的外呼记录数
     * 设计前接通电话数：第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单关联的且呼叫时间在工单完成前且接通的外呼记录数
     * 设计前接通电话时长：第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单关联的且呼叫时间在工单完成前且接通的外呼记录的总通话时长
     * 是否"设计后沟通"：第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单所属的订单的订单标签包含"设计后沟通"。以下几项设计后呼叫统计，标签包含"设计前沟通"或不包含"设计前沟通"的都统计。
     * 设计后拨打电话数：第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单关联的且呼叫时间在第一个方案发送后的外呼记录数
     * 设计后接通电话数：第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单关联的且呼叫时间在第一个方案发送后且接通的外呼记录数
     * 设计后接通电话时长：第一个方案发送前最后一张"已完成"的方案筛选/目标位/方案转换的工单关联的且呼叫时间在第一个方案发送后且接通的外呼记录的总通话时长
     * @param param
     * @return
     */
    @Override
    public PageImpl<DentistCommunicationDetailReportVM> dentistCommunicationDetailReport(DentistCommunicationReportDetailQueryVM param) {
        
        log.info("开始执行医生沟通明细报表查询，参数: {}", param);

        try {
            validateDetailQueryParams(param);

            List<Object[]> resultList = executeDetailMainQuery(param);

            Long totalElements = executeDetailCountQuery(param);

            List<DentistCommunicationDetailReportVM> content = resultList
                    .stream()
                    .map(this::mapToDetailViewModel)
                    .collect(Collectors.toList());

            Pageable pageable = PageRequest.of(param.getPageNumber(), param.getPageSize());

            return new PageImpl<>(content, pageable, totalElements);

        } catch (Exception e) {
            log.warn("执行医生沟通明细报表查询失败，参数: {}", param, e);
            throw new RuntimeException("执行医生沟通明细报表查询失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> executeMainQuery(DentistCommunicationReportQueryVM param) {
        int page = param.getPageNumber() != null ? param.getPageNumber() : 0;
        int size = param.getPageSize() != null ? param.getPageSize() : 20;
        StringBuilder sqlBuilder = new StringBuilder(MAIN_QUERY_TEMPLATE);
        if (StringUtils.isNotBlank(param.getTeamId())) {
            sqlBuilder.append(" AND gms_team.id = :teamId");
        }
        if (StringUtils.isNotEmpty(param.getDentistId())) {
            sqlBuilder.append(" AND gms_dentist.id = :dentistId");
        }
        sqlBuilder.append(" GROUP BY gms_dentist.id");
        sqlBuilder.append(" LIMIT :limit OFFSET :offset");

        Query query = entityManager.createNativeQuery(sqlBuilder.toString());

        query.setParameter("startTime", param.getStartTime());
        query.setParameter("endTime", param.getEndTime());

        if (StringUtils.isNotBlank(param.getTeamId())) {
            query.setParameter("teamId", param.getTeamId());
        }
        if (StringUtils.isNotBlank(param.getDentistId())) {
            query.setParameter("dentistId", param.getDentistId());
        }
        query.setParameter("limit", size);
        query.setParameter("offset", page * size);

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> executeDetailMainQuery(DentistCommunicationReportDetailQueryVM param) {
        int page = param.getPageNumber() != null ? param.getPageNumber() : 0;
        int size = param.getPageSize() != null ? param.getPageSize() : 20;
        StringBuilder sqlBuilder = new StringBuilder(DETAIL_MAIN_QUERY_TEMPLATE);
        
        // 添加筛选条件
        if (StringUtils.isNotBlank(param.getCaseCode())) {
            sqlBuilder.append(" AND cdb.case_code = :caseCode");
        }
        if (StringUtils.isNotBlank(param.getDesignerId())) {
            sqlBuilder.append(" AND cdb.designer_id = :designerId");
        }
        if (StringUtils.isNotBlank(param.getDentistCode())) {
            sqlBuilder.append(" AND cdb.dentist_code = :dentistCode");
        }
        if (StringUtils.isNotBlank(param.getAgencyCode())) {
            sqlBuilder.append(" AND cdb.agency_code = :agencyCode");
        }
        if (StringUtils.isNotBlank(param.getPatientCode())) {
            sqlBuilder.append(" AND cdb.patient_code = :patientCode");
        }
        
        sqlBuilder.append(" ORDER BY cdb.case_code");
        sqlBuilder.append(" LIMIT :limit OFFSET :offset");

        Query query = entityManager.createNativeQuery(sqlBuilder.toString());

        // 设置基础参数
        query.setParameter("startTime", param.getStartTime());
        query.setParameter("endTime", param.getEndTime());

        // 设置筛选参数
        if (StringUtils.isNotBlank(param.getCaseCode())) {
            query.setParameter("caseCode", param.getCaseCode());
        }
        if (StringUtils.isNotBlank(param.getDesignerId())) {
            query.setParameter("designerId", param.getDesignerId());
        }
        if (StringUtils.isNotBlank(param.getDentistCode())) {
            query.setParameter("dentistCode", param.getDentistCode());
        }
        if (StringUtils.isNotBlank(param.getAgencyCode())) {
            query.setParameter("agencyCode", param.getAgencyCode());
        }
        if (StringUtils.isNotBlank(param.getPatientCode())) {
            query.setParameter("patientCode", param.getPatientCode());
        }
        
        query.setParameter("limit", size);
        query.setParameter("offset", page * size);

        return query.getResultList();
    }

    private Long executeCountQuery(DentistCommunicationReportQueryVM param) {
        StringBuilder sqlBuilder = new StringBuilder(COUNT_QUERY);
        if (StringUtils.isNotBlank(param.getTeamId())) {
            sqlBuilder.append(" AND gms_team.id = :teamId");
        }
        if (StringUtils.isNotEmpty(param.getDentistId())) {
            sqlBuilder.append(" AND gms_dentist.id = :dentistId");
        }
        Query query = entityManager.createNativeQuery(sqlBuilder.toString());
        query.setParameter("startTime", param.getStartTime());
        query.setParameter("endTime", param.getEndTime());

        if (StringUtils.isNotBlank(param.getTeamId())) {
            query.setParameter("teamId", param.getTeamId());
        }
        if (StringUtils.isNotBlank(param.getDentistId())) {
            query.setParameter("dentistId", param.getDentistId());
        }
        Object result = query.getSingleResult();
        return result != null ? ((Number) result).longValue() : 0L;
    }

    private Long executeDetailCountQuery(DentistCommunicationReportDetailQueryVM param) {
        StringBuilder sqlBuilder = new StringBuilder(DETAIL_COUNT_QUERY);
        
        // 添加筛选条件
        if (StringUtils.isNotBlank(param.getCaseCode())) {
            sqlBuilder.append(" AND pdo.case_code = :caseCode");
        }
        if (StringUtils.isNotBlank(param.getDesignerId())) {
            sqlBuilder.append(" AND pdo.designer_id = :designerId");
        }
        if (StringUtils.isNotBlank(param.getDentistCode())) {
            sqlBuilder.append(" AND pdo.dentist_code = :dentistCode");
        }
        if (StringUtils.isNotBlank(param.getAgencyCode())) {
            sqlBuilder.append(" AND gc.agency_code = :agencyCode");
        }
        if (StringUtils.isNotBlank(param.getPatientCode())) {
            sqlBuilder.append(" AND gc.patient_code = :patientCode");
        }
        
        Query query = entityManager.createNativeQuery(sqlBuilder.toString());
        
        // 设置基础参数
        query.setParameter("startTime", param.getStartTime());
        query.setParameter("endTime", param.getEndTime());

        // 设置筛选参数
        if (StringUtils.isNotBlank(param.getCaseCode())) {
            query.setParameter("caseCode", param.getCaseCode());
        }
        if (StringUtils.isNotBlank(param.getDesignerId())) {
            query.setParameter("designerId", param.getDesignerId());
        }
        if (StringUtils.isNotBlank(param.getDentistCode())) {
            query.setParameter("dentistCode", param.getDentistCode());
        }
        if (StringUtils.isNotBlank(param.getAgencyCode())) {
            query.setParameter("agencyCode", param.getAgencyCode());
        }
        if (StringUtils.isNotBlank(param.getPatientCode())) {
            query.setParameter("patientCode", param.getPatientCode());
        }
        
        Object result = query.getSingleResult();
        return result != null ? ((Number) result).longValue() : 0L;
    }

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

    private DentistCommunicationDetailReportVM mapToDetailViewModel(Object[] row) {
        if (row == null || row.length < 17) {
            log.warn("明细查询结果行数据不完整，期望17个字段，实际: {}", row != null ? row.length : 0);
            return createEmptyDetailViewModel();
        }

        return DentistCommunicationDetailReportVM.builder()
                .caseCode(safeToString(row[0]))                         // case_code
                .teamName(safeToString(row[1]))                         // team_name
                .designerName(safeToString(row[2]))                     // designer_name
                .dentistName(safeToString(row[3]))                      // dentist_name
                .agencyName(safeToString(row[4]))                       // agency_name
                .patientName(safeToString(row[5]))                      // patient_name
                .caseCreatedDate(safeToString(row[6]))                  // case_created_date
                .firstDesignSendTime(safeToString(row[7]))              // first_design_send_time
                .taskCode(safeToString(row[8]))                         // task_code
                .hasPreCommunication(safeToString(row[9]).equals("1") ? "是" : "否")  // has_pre_communication
                .preCallCount(safeToString(row[10]))                    // pre_call_count
                .preConnectedCount(safeToString(row[11]))               // pre_connected_count
                .preDuration(safeToString(row[12]))                     // pre_duration
                .hasPostCommunication(safeToString(row[13]).equals("1") ? "是" : "否") // has_post_communication
                .postCallCount(safeToString(row[14]))                   // post_call_count
                .postConnectedCount(safeToString(row[15]))              // post_connected_count
                .postDuration(safeToString(row[16]))                    // post_duration
                .build();
    }

    private void validateQueryParams(DentistCommunicationReportQueryVM param) {
        if (param == null) {
            throw new IllegalArgumentException("查询参数不能为空");
        }
        if (StringUtils.isBlank(param.getStartTime())) {
            throw new IllegalArgumentException("方案发送日期-开始日期不能为空");
        }
        if (StringUtils.isBlank(param.getEndTime())) {
            throw new IllegalArgumentException("方案发送日期-结束日期不能为空");
        }
        if (param.getPageNumber() == null || param.getPageNumber() < 0) {
            throw new IllegalArgumentException("页码不能为空且不能小于0");
        }
        if (param.getPageSize() == null || param.getPageSize() <= 0 || param.getPageSize() > 1000) {
            throw new IllegalArgumentException("页大小不能为空且必须在1-1000之间");
        }
    }

    private void validateDetailQueryParams(DentistCommunicationReportDetailQueryVM param) {
        if (param == null) {
            throw new IllegalArgumentException("查询参数不能为空");
        }
        if (StringUtils.isBlank(param.getStartTime())) {
            throw new IllegalArgumentException("方案发送日期-开始日期不能为空");
        }
        if (StringUtils.isBlank(param.getEndTime())) {
            throw new IllegalArgumentException("方案发送日期-结束日期不能为空");
        }
        if (param.getPageNumber() == null || param.getPageNumber() < 0) {
            throw new IllegalArgumentException("页码不能为空且不能小于0");
        }
        if (param.getPageSize() == null || param.getPageSize() <= 0 || param.getPageSize() > 1000) {
            throw new IllegalArgumentException("页大小不能为空且必须在1-1000之间");
        }
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

    /**
     * 创建空的明细ViewModel对象
     */
    private DentistCommunicationDetailReportVM createEmptyDetailViewModel() {
        return DentistCommunicationDetailReportVM.builder()
                .caseCode("未知")
                .teamName("未分组")
                .designerName("未知")
                .dentistName("未知")
                .agencyName("未知")
                .patientName("未知")
                .caseCreatedDate("未知")
                .firstDesignSendTime("未知")
                .taskCode("未知")
                .hasPreCommunication("否")
                .preCallCount("0")
                .preConnectedCount("0")
                .preDuration("00:00:00")
                .hasPostCommunication("否")
                .postCallCount("0")
                .postConnectedCount("0")
                .postDuration("00:00:00")
                .build();
    }

}