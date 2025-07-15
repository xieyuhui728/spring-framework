package com.example.vm;

import lombok.Data;

@Data
public class BasePageVM {

    private Integer pageNumber = 0;
    private Integer pageSize = 20;
}