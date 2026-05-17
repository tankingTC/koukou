package com.example.koukou.network;

import android.net.Uri;

import com.example.koukou.BuildConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ServerEndpointPolicy {
    private ServerEndpointPolicy() {
    }

    public static List<String> apiBaseUrls() {
        return distinctNormalizedUrls(BuildConfig.API_BASE_URL, BuildConfig.API_BASE_URL_BACKUP);
    }

    public static List<String> webSocketUrls(String token) {
        List<String> bases = distinctNormalizedUrls(BuildConfig.WS_URL, BuildConfig.WS_URL_BACKUP);
        List<String> result = new ArrayList<>(bases.size());
        String trimmedToken = token == null ? "" : token.trim();
        for (String base : bases) {
            if (trimmedToken.isEmpty()) {
                result.add(base);
            } else {
                String separator = base.contains("?") ? "&" : "?";
                result.add(base + separator + "token=" + Uri.encode(trimmedToken));
            }
        }
        return result;
    }

    public static boolean requiresUnsafeTls(String url) {
        String host = extractHost(url);
        if (host.isEmpty()) {
            return false;
        }
        for (String backupUrl : distinctNormalizedUrls(BuildConfig.API_BASE_URL_BACKUP, BuildConfig.WS_URL_BACKUP)) {
            if (host.equalsIgnoreCase(extractHost(backupUrl))) {
                return true;
            }
        }
        return false;
    }

    public static String appendPath(String baseUrl, String path) {
        return normalizeUrl(baseUrl) + path;
    }

    private static List<String> distinctNormalizedUrls(String... urls) {
        Set<String> unique = new LinkedHashSet<>();
        if (urls != null) {
            for (String url : urls) {
                String normalized = normalizeUrl(url);
                if (!normalized.isEmpty()) {
                    unique.add(normalized);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String extractHost(String url) {
        try {
            Uri uri = Uri.parse(url == null ? "" : url.trim());
            String host = uri.getHost();
            return host == null ? "" : host.trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
