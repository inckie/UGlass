package com.damn.uglass.model;

import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import com.uber.sdk.rides.client.model.UserProfile;

public class Profile {
    private UserProfile mProfile;
    private Bitmap mPicture;

    @Nullable
    public UserProfile getProfile() {
        return mProfile;
    }

    public void setProfile(UserProfile mProfile) {
        this.mProfile = mProfile;
    }

    @Nullable
    public Bitmap getPicture() {
        return mPicture;
    }

    public void setPicture(Bitmap bitmap) {
        this.mPicture = bitmap;
    }

    @Nullable
    public String getPictureUrl(){
        return null == mProfile ? null : mProfile.getPicture();
    }
}
