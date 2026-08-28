package com.kchat.controller;

import com.kchat.common.dto.call.CallDto;
import com.kchat.common.dto.call.CreateCallRequest;
import com.kchat.common.dto.call.IceAnswerRequest;
import com.kchat.common.dto.call.IceCandidateRequest;
import com.kchat.common.dto.call.IceOfferRequest;
import com.kchat.common.dto.call.IceServersResponse;
import com.kchat.security.SecurityUtils;
import com.kchat.service.CallService;
import com.kchat.service.IceConfigService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class CallController {

    private final CallService callService;
    private final IceConfigService iceConfigService;

    public CallController(CallService callService, IceConfigService iceConfigService) {
        this.callService = callService;
        this.iceConfigService = iceConfigService;
    }

    @PostMapping("/rooms/{roomId}/calls")
    @ResponseStatus(HttpStatus.CREATED)
    public CallDto initiate(
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateCallRequest request
    ) {
        return callService.initiate(SecurityUtils.requireUserId(), roomId, request.callType());
    }

    @GetMapping("/calls/ice-servers")
    public IceServersResponse iceServers() {
        return iceConfigService.iceServersForUser(SecurityUtils.requireUserId());
    }

    @PostMapping("/calls/{callId}/accept")
    public CallDto accept(@PathVariable UUID callId) {
        return callService.accept(SecurityUtils.requireUserId(), callId);
    }

    @PostMapping("/calls/{callId}/decline")
    public CallDto decline(@PathVariable UUID callId) {
        return callService.decline(SecurityUtils.requireUserId(), callId);
    }

    @PostMapping("/calls/{callId}/end")
    public CallDto end(@PathVariable UUID callId) {
        return callService.end(SecurityUtils.requireUserId(), callId);
    }

    @GetMapping("/calls/incoming")
    public List<CallDto> incoming() {
        return callService.listIncomingRinging(SecurityUtils.requireUserId());
    }

    /** REST fallback when WS ICE frames are dropped (latency-sensitive; idempotent on peer). */
    @PostMapping("/calls/{callId}/ice/offer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void relayIceOffer(
            @PathVariable UUID callId,
            @Valid @RequestBody IceOfferRequest request
    ) {
        callService.relayIceOffer(SecurityUtils.requireUserId(), callId, request.sdp());
    }

    @PostMapping("/calls/{callId}/ice/answer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void relayIceAnswer(
            @PathVariable UUID callId,
            @Valid @RequestBody IceAnswerRequest request
    ) {
        callService.relayIceAnswer(SecurityUtils.requireUserId(), callId, request.sdp());
    }

    @PostMapping("/calls/{callId}/ice/candidate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void relayIceCandidate(
            @PathVariable UUID callId,
            @Valid @RequestBody IceCandidateRequest request
    ) {
        callService.relayIceCandidate(
                SecurityUtils.requireUserId(),
                callId,
                request.candidate(),
                request.sdpMid(),
                request.sdpMLineIndex()
        );
    }
}
