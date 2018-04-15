package com.damn.uglass;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.damn.uglass.cardproviders.BaseCardProvider;
import com.damn.uglass.cardproviders.NoConnectionCardProvider;
import com.damn.uglass.cardproviders.ObtainTokenCardProvider;
import com.damn.uglass.cardproviders.ProfileCardProvider;
import com.damn.uglass.model.TokenStorage;
import com.google.android.glass.widget.CardScrollView;
import com.google.android.glass.widget.Slider;
import com.uber.sdk.rides.client.error.ApiError;
import com.uber.sdk.rides.client.error.ClientError;
import com.uber.sdk.rides.client.model.Ride;
import com.uber.sdk.rides.client.services.RidesService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * {@link ConnectActivity}
 * todo:
 * [ ] 'find a ride' flow
 * [ ] handle no internet and so on (not checked yet)
 */
public class ConnectActivity extends Activity {

    /**
     * {@link CardScrollView} to use as the main content view.
     */
    private CardScrollView mCardScroller;

    private RidesService mAPI;
    private BaseCardProvider mCurrent;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mCardScroller = new CardScrollView(this);
        tryLogin();
        setContentView(mCardScroller);
    }

    public void tryLogin() {
        mAPI = UberAPI.configureUber(this);
        if(null != mAPI)
            setProvider(new ProfileCardProvider(this, mAPI));
        else
            setProvider(new ObtainTokenCardProvider(this));
    }

    public void obtainToken() {
       setProvider(new ObtainTokenCardProvider(this));
    }

    private void setProvider(BaseCardProvider provider) {
        if(null != mCurrent && mCurrent != provider)
            mCurrent.onRemoved();
        mCurrent = provider;
        mCardScroller.setAdapter(provider);
        mCardScroller.setOnItemClickListener(provider);
    }

    public void onProfileLoaded() {
        checkForRide();
    }

    private void checkForRide() {
        if(null == mAPI)
            return;
        mAPI.getCurrentRide().enqueue(new Callback<Ride>() {
            @Override
            public void onResponse(@NonNull Call<Ride> call, @NonNull Response<Ride> response) {
                if(isFinishing())
                    return;
                if(!response.isSuccessful())
                    return;
                Intent intent = new Intent(ConnectActivity.this, RideCardService.class);
                startService(intent);
                finish();
            }
            @Override
            public void onFailure(@NonNull Call<Ride> call, @NonNull Throwable t) {

            }
        });
    }

    public void onProfileClicked() {
        //todo: show ride request provider
        checkForRide();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCardScroller.activate();
    }

    @Override
    protected void onPause() {
        mCardScroller.deactivate();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(null != mCurrent)
            mCurrent.onRemoved();
    }

    public void onRetry(BaseCardProvider previous) {
        setProvider(previous.retry());
    }

    public void onAPIError(ApiError error) {
        ClientError clientError = error.getClientErrors().get(0);
        if (400 < clientError.getStatus())
            setProvider(new ObtainTokenCardProvider(this));
        else
            setProvider(new NoConnectionCardProvider(this, mCurrent,
                    getString(R.string.title_api_error_s, clientError.getTitle())));
    }

    public void onFailure(Throwable t) {
        setProvider(new NoConnectionCardProvider(this, mCurrent, getString(R.string.title_network_error)));
    }

    public Slider getSlider() {
        return Slider.from(mCardScroller);
    }

    public void logout() {
        TokenStorage.removeToken(this);
        mAPI = null;
        setProvider(new ObtainTokenCardProvider(this));
    }
}
