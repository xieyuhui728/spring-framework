package com.example.vm;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DentistCommunicationReportQueryVM extends BasePageVM {

    @ApiModelProperty(value = "方案发送日期-开始日期", example = "2023-01-01 00:00:00")
    private String startTime;

    @ApiModelProperty(value = "方案发送日期-结束日期", example = "2023-12-31 23:59:59")
    private String endTime;

    @ApiModelProperty(value = "设计组名称")
    private String teamName;

    @ApiModelProperty(value = "医生id")
    private String dentistId;
}