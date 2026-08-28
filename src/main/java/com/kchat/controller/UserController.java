package com.kchat.controller;

import com.kchat.common.dto.user.UpdateProfileRequest;
import com.kchat.common.dto.user.UpdateUserSettingsRequest;
import com.kchat.common.dto.user.UserProfileDto;
import com.kchat.common.dto.user.UserSettingsDto;
import com.kchat.common.dto.user.WipeMessagesResultDto;
import com.kchat.common.exception.ApiException;
import com.kchat.media.MediaHttp;
import com.kchat.media.MediaStorage;
import com.kchat.security.SecurityUtils;
import com.kchat.service.ChatService;
import com.kchat.service.UserService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final ChatService chatService;
    private final MediaStorage mediaStorage;

    public UserController(UserService userService, ChatService chatService, MediaStorage mediaStorage) {
        this.userService = userService;
        this.chatService = chatService;
        this.mediaStorage = mediaStorage;
    }

    @GetMapping("/me")
    public UserProfileDto getProfile() {
        return userService.getProfile(SecurityUtils.requireUserId());
    }

    @PatchMapping("/me")
    public UserProfileDto updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(SecurityUtils.requireUserId(), request);
    }

    @PostMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileDto updateAvatar(@RequestPart("file") MultipartFile file) {
        return userService.updateAvatar(SecurityUtils.requireUserId(), file);
    }

    @GetMapping("/{userId}/avatar")
    public ResponseEntity<Resource> getAvatar(@PathVariable UUID userId) {
        SecurityUtils.requireUserId();
        try {
            return MediaHttp.inline(
                    mediaStorage.open(userService.requireAvatarKey(userId)),
                    "private, max-age=86400");
        } catch (IllegalArgumentException | IOException ex) {
            throw ApiException.notFound("Avatar file missing");
        }
    }

    @GetMapping("/me/settings")
    public UserSettingsDto getSettings() {
        return userService.getSettings(SecurityUtils.requireUserId());
    }

    @PatchMapping("/me/settings")
    public UserSettingsDto updateSettings(@Valid @RequestBody UpdateUserSettingsRequest request) {
        return userService.updateSettings(SecurityUtils.requireUserId(), request);
    }

    @DeleteMapping("/me/messages")
    public WipeMessagesResultDto wipeMyMessages() {
        return chatService.wipeAllMyMessages(SecurityUtils.requireUserId());
    }
}
