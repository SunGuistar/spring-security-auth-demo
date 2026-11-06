package com.example.authdemo.dto;

public record ApiResponse(int status, String message) {

    public static ApiResponse ok(String message) {
        return new ApiResponse(200, message);
    }

    public static ApiResponse unauthorized(String message) {
        return new ApiResponse(401, message);
    }
}
