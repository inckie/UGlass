/*
 * Copyright (c) 2016 Uber Technologies, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.damn.uglass;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.uber.sdk.android.core.auth.AccessTokenManager;
import com.uber.sdk.android.core.auth.AuthenticationError;
import com.uber.sdk.android.core.auth.LoginCallback;
import com.uber.sdk.android.core.auth.LoginManager;
import com.uber.sdk.core.auth.AccessToken;
import com.uber.sdk.core.auth.Scope;
import com.uber.sdk.rides.client.Session;
import com.uber.sdk.rides.client.SessionConfiguration;
import com.uber.sdk.rides.client.UberRidesApi;
import com.uber.sdk.rides.client.error.ApiError;
import com.uber.sdk.rides.client.error.ErrorParser;
import com.uber.sdk.rides.client.model.UserProfile;
import com.uber.sdk.rides.client.services.RidesService;

import java.util.Arrays;

import damn.com.shared.TokenMessage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.uber.sdk.android.core.utils.Preconditions.checkNotNull;
import static com.uber.sdk.android.core.utils.Preconditions.checkState;


/**
 * This code is very, very dirty
 */
public class LoginActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

    public static final String CLIENT_ID = BuildConfig.CLIENT_ID;
    public static final String REDIRECT_URI = BuildConfig.REDIRECT_URI;

    private static final String LOG_TAG = "LoginActivity";

    private static final int CUSTOM_BUTTON_REQUEST_CODE = 1113;

    public static final String PREFS_NAME = "prefs";
    public static final String KEY_TIME = "time";
    public static final String KEY_SANDBOX = "sandbox";

    private AccessTokenManager accessTokenStorage;
    private LoginManager loginManager;
    private boolean mSandbox;
    private View mBtnSend;
    private SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        recreateAPI();

        mPreferences = getSharedPreferences(PREFS_NAME, 0);
        CheckBox cb = findViewById(R.id.cb_sandbox);
        // not 100% reliable, but ok for now
        mSandbox = mPreferences.getBoolean(KEY_SANDBOX, true);
        cb.setChecked(mSandbox);
        cb.setOnCheckedChangeListener(this);

        findViewById(R.id.btn_uber_sign_in).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginManager.login(LoginActivity.this);
            }
        });
        mBtnSend = findViewById(R.id.btn_send);
        mBtnSend.setEnabled(false);
        mBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendData();
            }
        });
    }

    private void recreateAPI() {
        SessionConfiguration configuration = new SessionConfiguration.Builder()
                .setClientId(CLIENT_ID)
                .setRedirectUri(REDIRECT_URI)
                .setEnvironment(mSandbox
                        ? SessionConfiguration.Environment.SANDBOX
                        : SessionConfiguration.Environment.PRODUCTION)
                .setScopes(Arrays.asList(
                        Scope.PROFILE,
                        Scope.RIDE_WIDGETS,
                        Scope.PLACES,
                        Scope.REQUEST,
                        Scope.ALL_TRIPS))
                .build();

        validateConfiguration(configuration);

        accessTokenStorage = new AccessTokenManager(this);
        //Use a custom button with an onClickListener to call the LoginManager directly
        loginManager = new LoginManager(accessTokenStorage,
                new SampleLoginCallback(),
                configuration,
                CUSTOM_BUTTON_REQUEST_CODE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loginManager.isAuthenticated()) {
            loadProfileInfo();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(LOG_TAG, String.format("onActivityResult requestCode:[%s] resultCode [%s]", requestCode, resultCode));
        loginManager.onActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mSandbox = isChecked;
        mPreferences.edit().putBoolean(KEY_SANDBOX, isChecked).apply();
        clearToken();
    }

    private class SampleLoginCallback implements LoginCallback {

        @Override
        public void onLoginCancel() {
            Toast.makeText(LoginActivity.this, R.string.user_cancels_message, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onLoginError(@NonNull AuthenticationError error) {
            Toast.makeText(LoginActivity.this,
                    getString(R.string.login_error_message, error.name()), Toast.LENGTH_LONG)
                    .show();
        }

        @Override
        public void onLoginSuccess(@NonNull AccessToken accessToken) {
            loadProfileInfo();
            // hack for ballpark keys life time tracking
            mPreferences.edit().putLong(KEY_TIME, System.currentTimeMillis()).apply();
        }

        @Override
        public void onAuthorizationCodeReceived(@NonNull String authorizationCode) {
            Toast.makeText(LoginActivity.this,
                    getString(R.string.authorization_code_message, authorizationCode),
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void sendData() {
        final AccessToken accessToken = loginManager.getAccessTokenManager().getAccessToken();
        if (null == accessToken)
            return;

        // I can actually use JSONObject there, or even StringBuilder, but just in case we will add more data
        TokenMessage token = new TokenMessage();
        token.token = accessToken.getToken();
        // this line is plain wrong (getExpiresIn was related to some moment in the past),
        // but Uber SDK does not keep the reference date. Usual life time id 30 days
        final long time = mPreferences.getLong(KEY_TIME, System.currentTimeMillis());
        token.expiresAt = time + accessToken.getExpiresIn() * 1000;
        token.sandBox = mSandbox;
        SendTask.send(this, token);
    }

    private void loadProfileInfo() {
        Session session = loginManager.getSession();
        RidesService service = UberRidesApi.with(session).build().createService();

        service.getUserProfile()
                .enqueue(new Callback<UserProfile>() {
                    @Override
                    public void onResponse(Call<UserProfile> call, Response<UserProfile> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(LoginActivity.this,
                                    getString(R.string.greeting, response.body().getFirstName()),
                                    Toast.LENGTH_LONG).show();
                            mBtnSend.setEnabled(true);
                        } else {
                            ApiError error = ErrorParser.parseError(response);
                            Toast.makeText(LoginActivity.this,
                                    error.getClientErrors().get(0).getTitle(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<UserProfile> call, Throwable t) {

                    }
                });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        accessTokenStorage = new AccessTokenManager(this);

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_clear) {
            clearToken();
            return true;
        } else if (id == R.id.action_copy) {
            AccessToken accessToken = accessTokenStorage.getAccessToken();
            if (accessToken != null) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("UberSampleAccessToken", accessToken.getToken());
                clipboard.setPrimaryClip(clip);
            }

            String message = accessToken == null ? "No AccessToken stored" : "AccessToken copied to clipboard";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }

        return super.onOptionsItemSelected(item);
    }

    private void clearToken() {
        accessTokenStorage.removeAccessToken();
        mBtnSend.setEnabled(false);
        mPreferences.edit().remove(KEY_TIME).apply();
        recreateAPI();
        Toast.makeText(this, "AccessToken cleared", Toast.LENGTH_SHORT).show();
    }

    /**
     * Validates the local variables needed by the Uber SDK used in the sample project
     *
     * @param configuration
     */
    private void validateConfiguration(SessionConfiguration configuration) {
        String nullError = "%s must not be null";
        String sampleError = "Please update your %s in the gradle.properties of the project before " +
                "using the Uber SDK Sample app. For a more secure storage location, " +
                "please investigate storing in your user home gradle.properties ";

        checkNotNull(configuration, String.format(nullError, "SessionConfiguration"));
        checkNotNull(configuration.getClientId(), String.format(nullError, "Client ID"));
        checkNotNull(configuration.getRedirectUri(), String.format(nullError, "Redirect URI"));
        checkState(!configuration.getClientId().equals("insert_your_client_id_here"),
                String.format(sampleError, "Client ID"));
        checkState(!configuration.getRedirectUri().equals("insert_your_redirect_uri_here"),
                String.format(sampleError, "Redirect URI"));
    }
}
