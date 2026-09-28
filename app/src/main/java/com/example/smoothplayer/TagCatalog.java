package com.example.smoothplayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
        if (updated != null && (!updated.tags.isEmpty() || updated.rating > 0)) {
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

    static int normalizeRating(int rating) {
        return rating >= 1 && rating <= 10 ? rating : 0;
    }

    static int numericRating(String tag) {
        if (tag == null) {
            return 0;
        }
        String value = tag.trim();
        if (!value.matches("(?:10|[1-9])")) {
            return 0;
        }
        try {
            return normalizeRating(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static List<TaggedVideo> migrateNumericTags(List<TaggedVideo> records) {
        List<TaggedVideo> result = new ArrayList<>();
        if (records == null) {
            return result;
        }
        for (TaggedVideo record : records) {
            if (record == null) {
                continue;
            }
            List<String> textTags = new ArrayList<>();
            int migratedRating = record.rating;
            for (String tag : record.tags) {
                int numeric = numericRating(tag);
                if (numeric > migratedRating) {
                    migratedRating = numeric;
                }
                if (numeric == 0) {
                    textTags.add(tag);
                }
            }
            result.add(new TaggedVideo(record.key, record.title, record.path, record.shizuku,
                    record.contentUri, textTags, migratedRating));
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

    static List<TaggedVideo> videosForRating(List<TaggedVideo> records, int rating) {
        List<TaggedVideo> result = new ArrayList<>();
        int expected = normalizeRating(rating);
        if (records == null || expected == 0) {
            return result;
        }
        for (TaggedVideo record : records) {
            if (record != null && record.rating == expected) {
                result.add(record);
            }
        }
        return result;
    }

    static List<TaggedVideo> videosForRatingAndTag(List<TaggedVideo> records, int rating,
            String tag) {
        List<TaggedVideo> result = new ArrayList<>();
        for (TaggedVideo record : videosForRating(records, rating)) {
            if (containsTag(record.tags, tag)) {
                result.add(record);
            }
        }
        return result;
    }

    static List<Integer> allRatings(List<TaggedVideo> records) {
        Set<Integer> ratings = new TreeSet<>((left, right) -> right - left);
        if (records != null) {
            for (TaggedVideo record : records) {
                if (record != null && normalizeRating(record.rating) > 0) {
                    ratings.add(record.rating);
                }
            }
        }
        return new ArrayList<>(ratings);
    }

    static List<String> tagsForRating(List<TaggedVideo> records, int rating) {
        List<String> tags = new ArrayList<>();
        for (TaggedVideo record : videosForRating(records, rating)) {
            tags.addAll(record.tags);
        }
        return normalizeTags(tags);
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
