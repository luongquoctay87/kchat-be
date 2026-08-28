package com.kchat.service;

import com.kchat.common.dto.chat.AddMembersRequest;
import com.kchat.common.dto.chat.ContactDto;
import com.kchat.common.dto.chat.CreateGroupRequest;
import com.kchat.common.dto.chat.DirectRoomDto;
import com.kchat.common.dto.chat.EditMessageRequest;
import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.dto.chat.MessageSearchResultDto;
import com.kchat.common.dto.chat.MuteRoomRequest;
import com.kchat.common.dto.chat.PinMessageRequest;
import com.kchat.common.dto.chat.PinnedMessageDto;
import com.kchat.common.dto.chat.ReactMessageRequest;
import com.kchat.common.dto.chat.ReadReceiptDto;
import com.kchat.common.dto.chat.RegisterDeviceRequest;
import com.kchat.common.dto.chat.RenameRoomRequest;
import com.kchat.common.dto.chat.UpdateRoomRequest;
import com.kchat.common.dto.chat.RoomDto;
import com.kchat.common.dto.chat.RoomMemberDto;
import com.kchat.common.dto.chat.SendMessageRequest;
import com.kchat.common.dto.user.WipeMessagesResultDto;
import com.kchat.entity.MessageAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ChatService {

    List<RoomDto> listRooms(UUID userId);

    List<MessageDto> listMessages(UUID userId, UUID roomId, int limit);

    List<MessageSearchResultDto> searchMessages(UUID userId, UUID roomId, String query, int limit);

    MessageDto sendMessage(UUID userId, UUID roomId, SendMessageRequest request);

    MessageDto editMessage(UUID userId, UUID roomId, UUID messageId, EditMessageRequest request);

    void deleteMessage(UUID userId, UUID roomId, UUID messageId);

    MessageDto toggleReaction(UUID userId, UUID roomId, UUID messageId, ReactMessageRequest request);

    MessageDto sendMediaMessage(UUID userId, UUID roomId, MultipartFile file, String caption);

    MessageAttachment requireAttachmentForUser(UUID userId, UUID attachmentId);

    void registerDevice(UUID userId, RegisterDeviceRequest request);

    List<ContactDto> listContacts(UUID userId);

    DirectRoomDto openOrCreateDirectRoom(UUID userId, UUID peerUserId);

    RoomDto createGroup(UUID userId, CreateGroupRequest request);

    List<RoomMemberDto> listMembers(UUID userId, UUID roomId);

    void addMembers(UUID userId, UUID roomId, AddMembersRequest request);

    void removeMember(UUID userId, UUID roomId, UUID targetUserId);

    void leaveRoom(UUID userId, UUID roomId);

    RoomDto renameRoom(UUID userId, UUID roomId, RenameRoomRequest request);

    RoomDto updateRoom(UUID userId, UUID roomId, UpdateRoomRequest request);

    RoomDto muteRoom(UUID userId, UUID roomId, MuteRoomRequest request);

    void markRoomRead(UUID userId, UUID roomId);

    List<ReadReceiptDto> listMessageReceipts(UUID userId, UUID roomId, UUID messageId);

    PinnedMessageDto getPinnedMessage(UUID userId, UUID roomId);

    PinnedMessageDto pinMessage(UUID userId, UUID roomId, PinMessageRequest request);

    void unpinMessage(UUID userId, UUID roomId);

    /** Soft-delete all messages sent by the user (emergency wipe from client PIN). */
    WipeMessagesResultDto wipeAllMyMessages(UUID userId);
}
