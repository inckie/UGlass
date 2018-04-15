package com.damn.uglass.cardproviders;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.damn.uglass.ConnectActivity;
import com.damn.uglass.R;
import com.damn.uglass.model.Profile;
import com.damn.uglass.model.TokenStorage;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.Slider;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.uber.sdk.rides.client.error.ApiError;
import com.uber.sdk.rides.client.error.ErrorParser;
import com.uber.sdk.rides.client.model.UserProfile;
import com.uber.sdk.rides.client.services.RidesService;

import java.util.ArrayList;
import java.util.List;

import damn.com.shared.TokenMessage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileCardProvider
        extends BaseCardProvider
        implements Target {

    private final ConnectActivity mHost;
    private final RidesService mAPI;
    private List<View> mCards = new ArrayList<View>();

    private Profile mProfile = new Profile();
    private boolean mLoaded;
    private Slider.Indeterminate mIndeterminate;

    public ProfileCardProvider(/*should be IFace*/ConnectActivity ctx, RidesService api){
        mAPI = api;
        mHost = ctx;
        mCards.add(new CardBuilder(ctx, CardBuilder.Layout.MENU)
                .setText(R.string.title_connecting)
                .getView());
        loadProfile();
    }

    @Override
    public BaseCardProvider retry() {
        loadProfile();
        return this;
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        if(null != mIndeterminate)
            mIndeterminate.hide();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // disallow clicks since we use is as a check for tokens
        if(!mLoaded)
            return;
        if(0 == position)
            mHost.onProfileClicked();
        else
            mHost.logout();
    }

    @Override
    public int getCount() {
        return mCards.size();
    }

    @Override
    public Object getItem(int position) {
        return mCards.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return mCards.get(position);
    }

    @Override
    public int getPosition(Object item) {
        //noinspection SuspiciousMethodCalls
        return mCards.indexOf(item);
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        if(mHost.isFinishing())
            return;
        mProfile.setPicture(bitmap);
        displayProfile();
    }

    @Override
    public void onBitmapFailed(Exception e, Drawable errorDrawable) {

    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {

    }

    private void loadProfile() {
        mIndeterminate = mHost.getSlider().startIndeterminate();
        mAPI.getUserProfile()
                .enqueue(new Callback<UserProfile>() {
                    private void fetchImage() {
                        Picasso.get().load(mProfile.getPictureUrl()).into(ProfileCardProvider.this);
                    }

                    @Override
                    public void onResponse(Call<UserProfile> call, Response<UserProfile> response) {
                        if(mHost.isFinishing())
                            return;
                        mIndeterminate.hide();
                        if (response.isSuccessful()) {
                            mProfile.setProfile(response.body());
                            displayProfile();
                            if(null == mProfile.getPicture() && null != mProfile.getPictureUrl())
                                fetchImage();
                            mLoaded = true;
                            mHost.onProfileLoaded();
                        } else {
                            ApiError error = ErrorParser.parseError(response);
                            mHost.onAPIError(error);
                        }
                    }

                    @Override
                    public void onFailure(Call<UserProfile> call, Throwable t) {
                        mHost.onFailure(t);
                    }
                });
    }

    private void displayProfile() {
        if(null == mProfile)
            return;
        UserProfile profile = mProfile.getProfile();
        if(null == profile)
            return;

        mCards.clear();

        // Profile
        CardBuilder card = new CardBuilder(mHost, CardBuilder.Layout.COLUMNS);
        card.setText(profile.getFirstName() + " " +
                     profile.getLastName() + "\n" +
                     profile.getEmail());

        if(null != mProfile.getPicture())
            card.addImage(mProfile.getPicture());
        final TokenMessage token = TokenStorage.getToken(mHost);
        if(null != token && !token.sandBox)
            card.setFootnote(R.string.footer_tap_to_ride);
        else
            card.setFootnote(R.string.footer_tap_to_ride_sandbox);
        mCards.add(card.getView());

        // Logout
        // note: I do not use usual menu activity since it is excessive,
        // even though more GlassWare way; all the actions are available on swipe
        View logoutView = new CardBuilder(mHost, CardBuilder.Layout.MENU)
                .setText(R.string.action_logout)
                .setIcon(R.drawable.ic_stop)
                .getView();
        mCards.add(logoutView);

        notifyDataSetChanged();
    }
}
