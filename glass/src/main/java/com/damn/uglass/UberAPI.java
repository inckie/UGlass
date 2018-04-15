package com.damn.uglass;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.damn.uglass.model.TokenStorage;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequest;
import com.uber.sdk.core.auth.Scope;
import com.uber.sdk.core.client.CredentialsSession;
import com.uber.sdk.core.client.SessionConfiguration;
import com.uber.sdk.rides.client.UberRidesApi;
import com.uber.sdk.rides.client.services.RidesService;

import java.util.Arrays;
import java.util.List;

import damn.com.shared.TokenMessage;
import okhttp3.logging.HttpLoggingInterceptor;

public class UberAPI {

    private static final String CLIENT_ID = BuildConfig.CLIENT_ID;
    private static final String REDIRECT_URI = BuildConfig.REDIRECT_URI;

    @NonNull
    private static SessionConfiguration getConfiguration(boolean sandBox) {
        return new SessionConfiguration.Builder()
                .setClientId(CLIENT_ID)
                .setRedirectUri(REDIRECT_URI)
                .setEnvironment(sandBox
                        ? SessionConfiguration.Environment.SANDBOX
                        : SessionConfiguration.Environment.PRODUCTION)
                .setScopes(Arrays.asList(
                        Scope.PROFILE,
                        Scope.RIDE_WIDGETS,
                        Scope.PLACES,
                        Scope.REQUEST,
                        Scope.ALL_TRIPS))
                .build();
    }

    private static final HttpLoggingInterceptor.Logger logger = new HttpLoggingInterceptor.Logger() {
        @Override
        public void log(@NonNull String message) {
            Log.d("UGlass", message);
        }
    };

    // returns null if no tokens stored
    @Nullable
    public static RidesService configureUber(Context context) {
        final TokenMessage token = TokenStorage.getToken(context);
        if(null == token)
            return null;
        if(System.currentTimeMillis() > token.expiresAt) {
            TokenStorage.removeToken(context);
            return null;
        }
        SessionConfiguration configuration = getConfiguration(token.sandBox);
        CredentialsSession session = new CredentialsSession(configuration, getCredential(token));
        return UberRidesApi.with(session)
                .setLogger(logger)
                .build()
                .createService();
    }

    // huge hack to get around auth on the Glass: we login on the phone and send data over there
    @NonNull
    private static Credential getCredential(TokenMessage token) {
        final Credential credential = new Credential(new Credential.AccessMethod() {
            private static final String HEADER_PREFIX = "Bearer ";

            @Override
            public void intercept(HttpRequest request, String accessToken) {
                request.getHeaders().setAuthorization(HEADER_PREFIX + accessToken);
            }

            @Override
            public String getAccessTokenFromRequest(HttpRequest request) {
                List<String> authorizationAsList = request.getHeaders().getAuthorizationAsList();
                if (authorizationAsList != null) {
                    for (String header : authorizationAsList) {
                        if (header.startsWith(HEADER_PREFIX)) {
                            return header.substring(HEADER_PREFIX.length());
                        }
                    }
                }
                return null;
            }
        });
        credential.setAccessToken(token.token);
        credential.setExpirationTimeMilliseconds(token.expiresAt);
        return credential;
    }
}
