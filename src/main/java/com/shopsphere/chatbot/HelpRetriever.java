package com.shopsphere.chatbot;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** Simple keyword retrieval over the markdown help documents in resources/help (split by "## " sections). No vector DB needed at this scale. */
@Component
public class HelpRetriever {
    public record Chunk(String source, String title, String text) {}
    private final List<Chunk> chunks = new ArrayList<>();
    private static final Set<String> STOP = Set.of("the", "a", "an", "is", "are", "to", "of", "in", "on", "for", "and", "or", "how", "do", "i", "can", "my", "me", "what", "does", "it", "with", "this", "that", "be", "you", "your", "from", "at", "by");

    @PostConstruct
    void load() throws IOException {
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:help/*.md")) {
            String md = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String name = Objects.requireNonNull(r.getFilename()).replace(".md", "");
            for (String sec : md.split("(?m)^## ")) {
                if (sec.isBlank()) continue;
                int nl = sec.indexOf('\n');
                chunks.add(new Chunk(name, (nl > 0 ? sec.substring(0, nl) : sec).replace("#", "").trim(), nl > 0 ? sec.substring(nl + 1).trim() : ""));
            }
        }
    }

    static List<String> tokens(String s) {
        return Arrays.stream(s.toLowerCase().split("[^a-z0-9]+")).filter(t -> t.length() > 2 && !STOP.contains(t)).distinct().toList();
    }

    public List<Chunk> search(String question, int limit) {
        List<String> q = tokens(question);
        if (q.isEmpty()) return List.of();
        record Scored(Chunk c, int score) {}
        return chunks.stream().map(c -> {
            String hay = (c.title() + " " + c.title() + " " + c.text()).toLowerCase();
            int score = 0;
            for (String t : q) if (hay.contains(t)) score += c.title().toLowerCase().contains(t) ? 3 : 1;
            return new Scored(c, score);
        }).filter(s -> s.score() > 0).sorted(Comparator.comparingInt(Scored::score).reversed()).limit(limit).map(Scored::c).collect(Collectors.toList());
    }
}
