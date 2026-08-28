package com.kchat.controller;

import com.kchat.common.dto.chat.RegisterDeviceRequest;
import com.kchat.common.dto.user.DeviceDto;
import com.kchat.security.SecurityUtils;
import com.kchat.service.ChatService;
import com.kchat.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final ChatService chatService;
    private final UserService userService;

    public DeviceController(ChatService chatService, UserService userService) {
        this.chatService = chatService;
        this.userService = userService;
    }

    @GetMapping
    public List<DeviceDto> listDevices(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        return userService.listDevices(SecurityUtils.requireUserId(), deviceToken);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        chatService.registerDevice(SecurityUtils.requireUserId(), request);
    }

    @DeleteMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeDevice(
            @PathVariable UUID deviceId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        userService.revokeDevice(SecurityUtils.requireUserId(), deviceId, deviceToken);
    }
}
