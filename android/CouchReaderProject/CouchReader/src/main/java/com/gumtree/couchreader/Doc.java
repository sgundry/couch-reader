package com.gumtree.couchreader;

import org.codehaus.jackson.JsonNode;

public class Doc {

    public static String getValue(JsonNode json, String key) {
        return getStringValue(json, key);
    }

    public static String getValue(JsonNode json, String key, String defaultValue) {
        return getStringValue(json, key, defaultValue);
    }

    public static String getStringValue(JsonNode json, String key) {
        return getStringValue(json, key, "");
    }

    public static String getStringValue(JsonNode json, String key, String defaultValue) {
        JsonNode item = json.get(key);
        if(item != null) {
            return item.getTextValue();
        }
        else {
            return defaultValue;
        }
    }

    public static int getIntValue(JsonNode json, String key){
        return getIntValue(json, key, 0);
    }

    public static int getIntValue(JsonNode json, String key, int defaultValue) {
        JsonNode item = json.get(key);
        if(item != null) {
            return item.getIntValue();
        }
        else {
            return defaultValue;
        }
    }

    public static boolean getBooleanValue(JsonNode json, String key) {
        return getBooleanValue(json, key, false);
    }

    public static boolean getBooleanValue(JsonNode json, String key, boolean defaultValue) {
        JsonNode item = json.get(key);
        if(item != null) {
            return item.getBooleanValue();
        }
        else {
            return defaultValue;
        }
    }
}
