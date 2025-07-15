package com.example.vm;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DentistCommunicationReportQueryVM extends BasePageVM {

    @ApiModelProperty(value = "方案发送日期-开始日期")
    private ZonedDateTime startTime;

    @ApiModelProperty(value = "方案发送日期-结束日期")
    private ZonedDateTime endTime;

    @ApiModelProperty(value = "设计组名称")
    private String teamName;

    @ApiModelProperty(value = "医生id")
    private String dentistId;
}