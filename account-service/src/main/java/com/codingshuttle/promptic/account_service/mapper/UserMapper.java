package com.codingshuttle.promptic.account_service.mapper;

import com.codingshuttle.promptic.account_service.dto.auth.SignupRequest;
import com.codingshuttle.promptic.account_service.dto.auth.UserProfileResponse;
import com.codingshuttle.promptic.account_service.entity.User;
import com.codingshuttle.promptic.common_lib.dto.UserDto;
import com.codingshuttle.promptic.common_lib.security.JwtUserPrincipal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(SignupRequest signupRequest);

    @Mapping(source = "userId", target = "id")
    UserProfileResponse toUserProfileResponse(JwtUserPrincipal user);

    UserDto toUserDto(User user);

}

