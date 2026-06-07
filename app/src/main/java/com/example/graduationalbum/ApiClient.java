package com.example.graduationalbum;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApiClient {
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Gson GSON = new Gson();

    public static List<Photo> fetchPhotos(Context context) throws IOException {
        String baseUrl = context.getString(R.string.api_base_url).trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String endpoint = baseUrl + "/api/photos";
        Request request = new Request.Builder().url(endpoint).get().build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("加载照片失败: " + (response == null ? "空返回" : response.code()));
            }
            String body = response.body().string();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("photos");
            List<Photo> photos = new ArrayList<>();
            for (JsonElement element : array) {
                Photo photo = GSON.fromJson(element, Photo.class);
                photo.setSrc(buildAbsoluteUrl(baseUrl, photo.getSrc()));
                if (photo.getThumb() != null && !photo.getThumb().isEmpty()) {
                    photo.setThumb(buildAbsoluteUrl(baseUrl, photo.getThumb()));
                } else {
                    photo.setThumb(photo.getSrc());
                }
                photos.add(photo);
            }
            return photos;
        }
    }

    private static String buildAbsoluteUrl(String baseUrl, String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return baseUrl + path;
    }
}
