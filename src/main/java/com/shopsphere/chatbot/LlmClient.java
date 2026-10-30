package com.shopsphere.chatbot;

import java.util.List;

/** Provider abstraction. Swap the implementation to change AI vendor without touching ChatService. */
public interface LlmClient {
    record Message(String role, String content) {}
    class Unavailable extends RuntimeException { public Unavailable(String m) { super(m); } }
    boolean configured();
    String complete(String system, List<Message> messages);
}
