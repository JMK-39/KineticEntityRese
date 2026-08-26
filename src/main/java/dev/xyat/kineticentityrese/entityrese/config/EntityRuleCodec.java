package dev.xyat.kineticentityrese.entityrese.config;

public final class EntityRuleCodec {
    private EntityRuleCodec() {
    }

    public record ParsedRule(
            String entityId,
            int threshold,
            boolean countRealDeath,
            boolean countPreventedDeath,
            boolean countCancelledDeath
    ) {
    }

    public static ParsedRule parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(";", -1);
        if (parts.length != 2 && parts.length != 3 && parts.length != 5) return null;

        String entityId = parts[0].trim();
        if (entityId.isEmpty()) return null;

        int threshold;
        try {
            threshold = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (threshold < 1) return null;

        if (parts.length == 2) {
            return new ParsedRule(entityId, threshold, true, false, false);
        }

        Boolean first = parseBoolean(parts[2]);
        if (first == null) return null;

        if (parts.length == 3) {
            return new ParsedRule(entityId, threshold, true, first, first);
        }

        Boolean prevented = parseBoolean(parts[3]);
        Boolean cancelled = parseBoolean(parts[4]);
        if (prevented == null || cancelled == null) return null;
        return new ParsedRule(entityId, threshold, first, prevented, cancelled);
    }

    public static String serialize(ParsedRule rule) {
        if (rule == null) throw new IllegalArgumentException("rule is null");
        if (rule.entityId() == null || rule.entityId().isBlank()) {
            throw new IllegalArgumentException("entity id is blank");
        }
        if (rule.threshold() < 1) {
            throw new IllegalArgumentException("threshold must be at least 1");
        }
        return rule.entityId().trim()
                + ";" + rule.threshold()
                + ";" + rule.countRealDeath()
                + ";" + rule.countPreventedDeath()
                + ";" + rule.countCancelledDeath();
    }

    private static Boolean parseBoolean(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
        return null;
    }
}
