package com.example.service.impl;

import com.example.repository.DentistCommunicationReportRepository;
import com.example.service.ReportService;
import com.example.vm.DentistCommunicationReportQueryVM;
import com.example.vm.DentistCommunicationReportVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DentistCommunicationReportServiceImpl implements ReportService {
    
    private final DentistCommunicationReportRepository dentistCommunicationReportRepository;

    @Override
    public PageImpl<DentistCommunicationReportVM> dentistCommunicationReport(
            DentistCommunicationReportQueryVM param) {
        
        try {
            // 创建分页对象
            Pageable pageable = PageRequest.of(
                param.getPageNumber(), 
                param.getPageSize()
            );
            
            // 执行查询
            Page<Object[]> resultPage = dentistCommunicationReportRepository.findDentistCommunicationReport(
                param.getStartTime(),
                param.getEndTime(),
                param.getTeamName(),
                param.getDentistId(),
                pageable
            );
            
            // 映射结果到ViewModel
            List<DentistCommunicationReportVM> content = resultPage.getContent()
                .stream()
                .map(this::mapToViewModel)
                .collect(Collectors.toList());
            
            return new PageImpl<>(content, pageable, resultPage.getTotalElements());
            
        } catch (Exception e) {
            log.error("Error executing dentist communication report query", e);
            throw new RuntimeException("Failed to execute dentist communication report", e);
        }
    }
    
    /**
     * 将Object[]映射到DentistCommunicationReportVM
     */
    private DentistCommunicationReportVM mapToViewModel(Object[] row) {
        return DentistCommunicationReportVM.builder()
            .teamName(getString(row[0]))
            .dentistName(getString(row[1]))
            .dentistCode(getString(row[2]))
            .allCasesNum(getString(row[3]))
            .firstTagCasesNum(getString(row[4]))
            .preDesignTagCasesNum(getString(row[5]))
            .preCalledCasesNum(getString(row[6]))
            .preCalledCasesRate(getString(row[7]))  // 修正：这应该是拨打率，不是post_design_tag_cases_rate
            .preConnectedCasesNum(getString(row[8]))
            .preConnectedCasesRate(getString(row[9]))
            .preTotalConnectedCallNum(getString(row[10]))
            .preAverageConnectedCallNum(getString(row[11]))
            .preTotalDurationSec(getString(row[12]))
            .preAverageDurationSec(getString(row[13]))
            .postDesignTagCasesNum(getString(row[14]))
            .postCalledCasesNum(getString(row[15]))
            .postCalledCasesRate(getString(row[16]))
            .postConnectedCasesNum(getString(row[17]))
            .postConnectedCasesRate(getString(row[18]))
            .postTotalConnectedCallNum(getString(row[19]))
            .postAverageConnectedCallNum(getString(row[20]))
            .postTotalDurationSec(getString(row[21]))
            .postAverageDurationSec(getString(row[22]))
            .build();
    }
    
    /**
     * 安全地将Object转换为String
     */
    private String getString(Object obj) {
        return obj != null ? obj.toString() : "0";
    }
}