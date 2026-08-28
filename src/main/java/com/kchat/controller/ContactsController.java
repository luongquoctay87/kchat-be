package com.kchat.controller;

import com.kchat.common.dto.chat.ContactDto;
import com.kchat.security.SecurityUtils;
import com.kchat.service.ChatService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
