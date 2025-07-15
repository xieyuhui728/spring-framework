package com.example.vm;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentistCommunicationReportVM {

    @ApiModelProperty(value = "设计组")
    private String teamName;

    @ApiModelProperty(value = "医生")
    private String dentistName;

    @ApiModelProperty(value = "医生编号")
    private String dentistCode;

    @ApiModelProperty(value = "总病例数")
    private String allCasesNum;

    @ApiModelProperty(value = "新手首例病例数")
    private String firstTagCasesNum;

    @ApiModelProperty(value = "设计前沟通病例数")
    private String preDesignTagCasesNum;

    @ApiModelProperty(value = "设计前沟通拨打电话病例数")
    private String preCalledCasesNum;

    @ApiModelProperty(value = "设计前沟通拨打率")
    private String preCalledCasesRate;

    @ApiModelProperty(value = "设计前沟通接通电话病例数")
    private String preConnectedCasesNum;

    @ApiModelProperty(value = "设计前沟通接通率")
    private String preConnectedCasesRate;

    @ApiModelProperty(value = "设计前沟通接通电话通话数")
    private String preTotalConnectedCallNum;

    @ApiModelProperty(value = "设计前沟通例均通话次数")
    private String preAverageConnectedCallNum;

    @ApiModelProperty(value = "设计前沟通接通电话时长")
    private String preTotalDurationSec;

    @ApiModelProperty(value = "设计前沟通例均通话时长")
    private String preAverageDurationSec;

    @ApiModelProperty(value = "设计后讲解病例数")
    private String postDesignTagCasesNum;

    @ApiModelProperty(value = "设计后讲解拨打电话病例数")
    private String postCalledCasesNum;

    @ApiModelProperty(value = "设计后讲解拨打率")
    private String postCalledCasesRate;

    @ApiModelProperty(value = "设计后讲解接通电话病例数")
    private String postConnectedCasesNum;

    @ApiModelProperty(value = "设计后讲解接通率")
    private String postConnectedCasesRate;

    @ApiModelProperty(value = "设计后讲解接通电话通话数")
    private String postTotalConnectedCallNum;

    @ApiModelProperty(value = "设计后讲解例均通话次数")
    private String postAverageConnectedCallNum;

    @ApiModelProperty(value = "设计后讲解接通电话时长")
    private String postTotalDurationSec;

    @ApiModelProperty(value = "设计后讲解例均通话时长")
    private String postAverageDurationSec;
}