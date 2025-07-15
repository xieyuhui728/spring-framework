package com.example.service;

import com.example.vm.DentistCommunicationReportQueryVM;
import com.example.vm.DentistCommunicationReportVM;
import org.springframework.data.domain.PageImpl;

public interface ReportService {
    
    /**
     * 医生沟通执行报表
     * 
     * @param param 查询参数
     * @return 分页结果
     */
    PageImpl<DentistCommunicationReportVM> dentistCommunicationReport(DentistCommunicationReportQueryVM param);
}