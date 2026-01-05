package com.example.kefu.common;

import lombok.Data;

@Data
public class ApiResp<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> ApiResp<T> ok(T data) {
        ApiResp<T> r = new ApiResp<>();
        r.code = 0; r.msg = "ok"; r.data = data;
        return r;
    }
    public static <T> ApiResp<T> fail(String msg) {
        ApiResp<T> r = new ApiResp<>();
        r.code = -1; r.msg = msg;
        return r;
    }
}
