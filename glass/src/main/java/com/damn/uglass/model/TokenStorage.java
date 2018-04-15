package com.damn.uglass.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import damn.com.shared.TokenMessage;

public class TokenStorage {

    private static final String TOKEN = "token";
    private static final String sStorage = "token_storage";

    @Nullable
    public static TokenMessage getToken(Context ctx){
        final SharedPreferences sharedPreferences = ctx.getSharedPreferences(sStorage, 0);
        final String token = sharedPreferences.getString(TOKEN, null);
        if(null == token)
            return null;
        return TokenMessage.fromJson(token);
    }

    public static void storeToken(Context ctx, @NonNull String tokenJson){
        ctx.getSharedPreferences(sStorage, 0)
                .edit()
                .putString(TOKEN, tokenJson)
                .apply();
    }

    // I could use  storeToken(, null) to erase the token, but its a bit obscure
    public static void removeToken(Context ctx){
        ctx.getSharedPreferences(sStorage, 0)
                .edit()
                .remove(TOKEN)
                .apply();
    }
}
