package com.kchat.controller;

import com.kchat.common.dto.chat.ContactDto;
import com.kchat.security.SecurityUtils;
import com.kchat.service.ChatService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contacts")
public class ContactsController {

    private final ChatService chatService;

    public ContactsController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ContactDto> listContacts() {
        return chatService.listContacts(SecurityUtils.requireUserId());
    }

    @GetMapping("/search")
    public List<ContactDto> searchUsers(@RequestParam("q") String query) {
        return chatService.searchUsers(SecurityUtils.requireUserId(), query);
    }

    @PostMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addContact(@PathVariable("userId") UUID userId) {
        chatService.addContact(SecurityUtils.requireUserId(), userId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeContact(@PathVariable("userId") UUID userId) {
        chatService.removeContact(SecurityUtils.requireUserId(), userId);
    }
}
