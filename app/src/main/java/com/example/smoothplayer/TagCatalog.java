package com.example.smoothplayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TagCatalog {
    private TagCatalog() {
    }

    static List<String> combineTags(List<String> selectedTags, String typedTags) {
        List<String> combined = new ArrayList<>();
        if (selectedTags != null) {
            combined.addAll(selectedTags);
        }
        if (typedTags != null) {
            String[] parts = typedTags.split("[,，\\n\\r]+");
            for (String part : parts) {
                combined.add(part);
            }
        }
        return normalizeTags(combined);
    }

    static List<String> normalizeTags(List<String> tags) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (tags == null) {
            return new ArrayList<>();
        }
        for (String tag : tags) {
            String normalized = tag == null ? "" : tag.trim();
            if (!normalized.isEmpty()) {
                unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        return new ArrayList<>(unique.values());
    }

    static List<TaggedVideo> upsert(List<TaggedVideo> records, TaggedVideo updated) {
        List<TaggedVideo> result = new ArrayList<>();
        if (updated != null && !updated.tags.isEmpty()) {
            result.add(updated);
        }
        if (records != null) {
            for (TaggedVideo record : records) {
                if (record != null && (updated == null || !record.key.equals(updated.key))) {
                    result.add(record);
                }
            }
        }
        return result;
    }

    static List<String> allTags(List<TaggedVideo> records) {
        List<String> tags = new ArrayList<>();
        if (records != null) {
            for (TaggedVideo record : records) {
                if (record != null) {
                    tags.addAll(record.tags);
                }
            }
        }
        return normalizeTags(tags);
    }

    static List<TaggedVideo> videosForTag(List<TaggedVideo> records, String tag) {
        List<TaggedVideo> result = new ArrayList<>();
        if (records == null || tag == null) {
            return result;
        }
        for (TaggedVideo record : records) {
            if (record != null && containsTag(record.tags, tag)) {
                result.add(record);
            }
        }
        return result;
    }

    static boolean containsTag(List<String> tags, String expected) {
        for (String tag : tags) {
            if (tag.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }
}
