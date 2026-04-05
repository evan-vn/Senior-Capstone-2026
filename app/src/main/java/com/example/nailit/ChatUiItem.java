package com.example.nailit;

import android.net.Uri;

import com.example.nailit.data.model.AiMatchedOption;

import java.util.ArrayList;
import java.util.List;

public class ChatUiItem {

    public static final int TYPE_TEXT = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int TYPE_OPTIONS = 3;

    public int type;
    public String text;
    public boolean isUser;
    public String imageUriString;
    public List<AiMatchedOption> options;

    public ChatUiItem(int type) {
        this.type = type;
    }

    public static ChatUiItem text(String text, boolean isUser) {
        ChatUiItem item = new ChatUiItem(TYPE_TEXT);
        item.text = text;
        item.isUser = isUser;
        return item;
    }

    public static ChatUiItem image(Uri uri) {
        ChatUiItem item = new ChatUiItem(TYPE_IMAGE);
        item.imageUriString = uri != null ? uri.toString() : null;
        return item;
    }

    public static ChatUiItem options(List<AiMatchedOption> options) {
        ChatUiItem item = new ChatUiItem(TYPE_OPTIONS);
        item.options = options != null ? options : new ArrayList<>();
        return item;
    }
}