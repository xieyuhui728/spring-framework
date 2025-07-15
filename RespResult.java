package com.example.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RespResult<T> {
    
    private boolean success;
    private String message;
    private T data;
    private String code;
    
    public static <T> RespResult<T> success(T data) {
        return new RespResult<>(true, "Success", data, "200");
    }
    
    public static <T> RespResult<T> error(String message) {
        return new RespResult<>(false, message, null, "500");
    }
    
    public static <T> RespResult<T> error(String code, String message) {
        return new RespResult<>(false, message, null, code);
    }
}