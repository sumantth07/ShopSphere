package com.shopsphere.chatbot;

import com.shopsphere.chatbot.ChatService.ChatMessage;
import com.shopsphere.chatbot.ChatService.ChatResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
public class ChatController {
    public record ChatRequest(@NotBlank @Size(max = 1000) String message, @Size(max = 20) List<ChatMessage> history, @Size(max = 4) List<Long> compareIds) {}
    private final ChatService chat;
    public ChatController(ChatService chat) { this.chat = chat; }

    /** Anonymous callers get catalog + help answers. A valid bearer token additionally unlocks that user's own orders. */
    @PostMapping("/message")
    public ChatResult message(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatRequest r) {
        return chat.reply(r.message(), r.history(), r.compareIds(), jwt == null ? null : Long.parseLong(jwt.getSubject()));
    }
}
