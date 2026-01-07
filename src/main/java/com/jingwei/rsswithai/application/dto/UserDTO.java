package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.User;

public record UserDTO(
    Long id,
    String username,
    String avatarUrl
) {
    public static UserDTO from(User user) {
        return new UserDTO(
            user.getId(),
            user.getUsername(),
            user.getAvatarUrl()
        );
    }
}