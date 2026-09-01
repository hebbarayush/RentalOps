package com.rentalops.maintenance;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Classifies a maintenance request into a trade category, recommends a priority, estimates a
 * cost band, and drafts a first reply to the tenant. Uses Claude when an Anthropic API key is
 * configured ({@code app.ai.anthropic-api-key}); otherwise a deterministic keyword classifier.
 */
@Service
public class MaintenanceTriageService {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceTriageService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private volatile AnthropicClient client;

    public MaintenanceTriageService(
            @Value("${app.ai.anthropic-api-key:}") String apiKey,
            @Value("${app.ai.model:claude-opus-5}") String model
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    public record Result(
            String source,
            String category,
            MaintenancePriority priority,
            String summary,
            String costBand,
            String draftReply
    ) {
    }

    public Result triage(String title, String description) {
        if (!apiKey.isEmpty()) {
            try {
                Future<Result> f = executor.submit(() -> callClaude(title, description));
                return f.get(20, TimeUnit.SECONDS);
            } catch (Exception ex) {
                log.warn("Claude triage failed ({}); falling back to rules", ex.toString());
            }
        }
        return rulesTriage(title, description);
    }

    // ---------------------------------------------------------------- Claude

    private Result callClaude(String title, String description) throws Exception {
        String system = """
                You triage rental-property maintenance requests for a property manager.
                Respond with ONLY a compact JSON object, no prose, using exactly these keys:
                  "category": one of PLUMBING, ELECTRICAL, HVAC, APPLIANCE, STRUCTURAL, PEST, LOCKS_SECURITY, GENERAL
                  "priority": one of LOW, MEDIUM, HIGH, URGENT (URGENT = safety risk or property damage in progress)
                  "summary": one sentence a manager can scan
                  "costBand": a rough INR range like "Rs 500 - 2,000"
                  "draftReply": 2-3 sentence reply to the tenant acknowledging the issue and next steps
                """;
        String user = "Title: " + title + "\nDescription: " + description;

        Message response = anthropic().messages().create(MessageCreateParams.builder()
                .model(model)
                .maxTokens(700L)
                .system(system)
                .addUserMessage(user)
                .build());

        String text = response.content().stream()
                .flatMap(b -> b.text().stream())
                .map(t -> t.text())
                .reduce("", String::concat);

        JsonNode json = JSON.readTree(extractJson(text));
        MaintenancePriority priority = parsePriority(json.path("priority").asText(""), MaintenancePriority.MEDIUM);
        return new Result(
                "CLAUDE",
                json.path("category").asText("GENERAL"),
                priority,
                trim(json.path("summary").asText(""), 480),
                trim(json.path("costBand").asText(""), 120),
                trim(json.path("draftReply").asText(""), 1900));
    }

    private AnthropicClient anthropic() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder()
                            .apiKey(apiKey)
                            .timeout(Duration.ofSeconds(18))
                            .build();
                }
                c = client;
            }
        }
        return c;
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        throw new IllegalStateException("No JSON object in model response");
    }

    // ---------------------------------------------------------------- Rules fallback

    private Result rulesTriage(String title, String description) {
        String text = (title + " " + description).toLowerCase(Locale.ROOT);
        String category = classify(text);
        MaintenancePriority priority = priorityFor(text, category);
        String costBand = COST_BANDS.getOrDefault(category, "Rs 500 - 3,000");
        String summary = "Likely %s issue (%s priority) reported by the tenant."
                .formatted(category.toLowerCase(Locale.ROOT).replace('_', ' '), priority.name().toLowerCase(Locale.ROOT));
        String draftReply = ("Thanks for reporting this. We've logged it as a %s priority %s issue and a "
                + "technician will be in touch to schedule a visit. Please let us know if it gets worse in the meantime.")
                .formatted(priority.name().toLowerCase(Locale.ROOT), category.toLowerCase(Locale.ROOT).replace('_', ' '));
        return new Result("RULES", category, priority, summary, costBand, draftReply);
    }

    private static final java.util.Map<String, String[]> KEYWORDS = java.util.Map.of(
            "PLUMBING", new String[]{"leak", "tap", "faucet", "pipe", "drain", "toilet", "water", "sink", "flush", "geyser"},
            "ELECTRICAL", new String[]{"electric", "power", "socket", "switch", "wiring", "short circuit", "spark", "breaker", "light not"},
            "HVAC", new String[]{"ac ", "a/c", "air condition", "heater", "heating", "cooling", "fan not", "thermostat"},
            "APPLIANCE", new String[]{"fridge", "refrigerator", "washing machine", "dishwasher", "oven", "microwave", "stove", "appliance"},
            "STRUCTURAL", new String[]{"crack", "ceiling", "wall", "roof", "damp", "mould", "mold", "floor", "door frame", "window broken"},
            "PEST", new String[]{"pest", "cockroach", "rodent", "rat", "mice", "termite", "ants", "bed bug", "infestation"},
            "LOCKS_SECURITY", new String[]{"lock", "key", "door won", "latch", "burglar", "security", "gate", "intercom"});

    private static final java.util.Map<String, String> COST_BANDS = java.util.Map.of(
            "PLUMBING", "Rs 500 - 3,000",
            "ELECTRICAL", "Rs 800 - 4,000",
            "HVAC", "Rs 1,500 - 8,000",
            "APPLIANCE", "Rs 1,000 - 6,000",
            "STRUCTURAL", "Rs 3,000 - 25,000",
            "PEST", "Rs 800 - 3,500",
            "LOCKS_SECURITY", "Rs 500 - 2,500",
            "GENERAL", "Rs 500 - 3,000");

    private String classify(String text) {
        for (var entry : KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (text.contains(kw)) {
                    return entry.getKey();
                }
            }
        }
        return "GENERAL";
    }

    private MaintenancePriority priorityFor(String text, String category) {
        if (containsAny(text, "gas", "fire", "smoke", "electric shock", "spark", "burst", "flooding", "no water", "no power", "break-in", "burglar", "cannot lock", "won't lock")) {
            return MaintenancePriority.URGENT;
        }
        if (containsAny(text, "leak", "not working", "broken", "no ac", "no heat", "overflow", "won't turn")) {
            return MaintenancePriority.HIGH;
        }
        if (category.equals("STRUCTURAL") || category.equals("PEST")) {
            return MaintenancePriority.MEDIUM;
        }
        return MaintenancePriority.MEDIUM;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static MaintenancePriority parsePriority(String raw, MaintenancePriority fallback) {
        try {
            return MaintenancePriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
