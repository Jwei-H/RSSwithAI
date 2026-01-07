package com.jingwei.rsswithai.application.dto;

public record UpdatePasswordRequest(
    String oldPassword,
    String newPassword
) {}
