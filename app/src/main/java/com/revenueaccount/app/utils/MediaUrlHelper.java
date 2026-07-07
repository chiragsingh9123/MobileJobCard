package com.revenueaccount.app.utils;

import android.net.Uri;
import android.util.Log;
import com.revenueaccount.app.api.ApiClient;

/**
 * Builds a full, loadable URL from a relative media path returned by the API
 * (e.g. "/uploads/job_media/xyz.jpg").
 *
 * Fix: previously this simply string-concatenated ApiClient.BASE_URL with the
 * relative path. If BASE_URL ever includes a path segment (for example a
 * reverse-proxy setup like "https://example.com/api/"), that concatenation
 * produced a broken URL ("https://example.com/api/uploads/...") even though
 * the media route is served at the server root ("https://example.com/uploads/...").
 * This helper instead extracts only the scheme + host + port from BASE_URL and
 * appends the relative path to that, which is correct regardless of how
 * BASE_URL is configured.
 */
public class MediaUrlHelper {

    private static final String TAG = "MediaUrlHelper";

    public static String buildFullUrl(String relativeUrl) {
        if (relativeUrl == null || relativeUrl.isEmpty()) return null;
        try {
            Uri base = Uri.parse(ApiClient.BASE_URL);
            String scheme = base.getScheme();
            String authority = base.getAuthority();
            if (scheme == null || authority == null) {
                return fallback(relativeUrl);
            }
            String path = relativeUrl.startsWith("/") ? relativeUrl : "/" + relativeUrl;
            return scheme + "://" + authority + path;
        } catch (Exception e) {
            Log.e(TAG, "Failed to build media URL, using fallback", e);
            return fallback(relativeUrl);
        }
    }

    private static String fallback(String relativeUrl) {
        String base = ApiClient.BASE_URL.replaceAll("/$", "");
        String path = relativeUrl.startsWith("/") ? relativeUrl : "/" + relativeUrl;
        return base + path;
    }
}
