package com.margomarket.mapper;

import com.margomarket.dto.UserResponse;
import com.margomarket.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().getName(),
                user.getCreatedAt()
        );
    }
}
