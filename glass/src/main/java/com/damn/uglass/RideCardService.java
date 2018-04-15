package com.damn.uglass;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.widget.RemoteViews;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;
import com.google.android.glass.widget.CardBuilder;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.uber.sdk.rides.client.error.ApiError;
import com.uber.sdk.rides.client.error.ErrorParser;
import com.uber.sdk.rides.client.model.Driver;
import com.uber.sdk.rides.client.model.Location;
import com.uber.sdk.rides.client.model.Ride;
import com.uber.sdk.rides.client.model.Vehicle;
import com.uber.sdk.rides.client.services.RidesService;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.damn.uglass.UberAPI.configureUber;

/**
 * A {@link Service} that publishes a {@link LiveCard} in the timeline.
 * [ ] use custom layouts and update images and text directly
 * [ ] handle no/expired token
 * [ ] handle no internet and so on
 */
public class RideCardService extends Service {

    private static final String LIVE_CARD_TAG = "RideCardService";

    private static final int sIMAGE_TIMEOUT = 5000;
    private static final int UPDATE_FREQ_MS = sIMAGE_TIMEOUT;

    private static final int no_current_trip = 404;

    private LiveCard mLiveCard;
    private RidesService mAPI;

    // runtime
    private Ride mRide;

    private static abstract class TimedTarget implements Target {
        public final long timeStamp = System.currentTimeMillis();
    }

    private Map<String, Bitmap> mImages = new HashMap<String, Bitmap>();
    // to keep strong refs to Target
    private Map<String, TimedTarget> mImagesLoading = new HashMap<String, TimedTarget>();

    private Bitmap mLastMap;
    private String mLastMapUrl = "";
    private TimedTarget mMapLoading;

    // updating
    private Handler mHandler;
    private final Runnable mUpdater = new Runnable() {
        @Override
        public void run() {
            updateRideStatus();
        }
    };


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mLiveCard == null) {
            mAPI = configureUber(this);
            // no tokens
            if(null == mAPI){
                stopSelf();
                return START_NOT_STICKY;
            }
            mLiveCard = new LiveCard(this, LIVE_CARD_TAG);

            RemoteViews remoteView = new CardBuilder(getApplicationContext(), CardBuilder.Layout.MENU)
                    .setText(R.string.title_updating)
                    .getRemoteViews();
            mLiveCard.setViews(remoteView);

            // Display the options menu when the live card is tapped.
            Intent menuIntent = new Intent(this, LiveCardMenuActivity.class);
            mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));
            mLiveCard.publish(PublishMode.REVEAL);

            mHandler = new Handler(Looper.getMainLooper());
            updateRideStatus();
        } else {
            if (null != mRide)
                showRide();
            mLiveCard.navigate();
        }
        return START_STICKY;
    }

    private void updateRideStatus() {
        mHandler.removeCallbacks(mUpdater);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if(null != pm && !pm.isScreenOn()){
            // do not stop updating, but do not call API
            mHandler.postDelayed(mUpdater, UPDATE_FREQ_MS);
            return;
        }

        mAPI.getCurrentRide().enqueue(new Callback<Ride>() {
            @Override
            public void onResponse(@NonNull Call<Ride> call, @NonNull Response<Ride> response) {
                if (response.isSuccessful()) {
                    mRide = response.body();
                    if (null == mRide) {
                        stopSelf();
                    } else {
                        checkImages();
                        if (!showRide())
                            mHandler.postDelayed(mUpdater, UPDATE_FREQ_MS);
                    }
                } else {
                    ApiError error = ErrorParser.parseError(response);
                    //noinspection ConstantConditions
                    if (no_current_trip == error.getClientErrors().get(0).getStatus())
                        stopSelf();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Ride> call, @NonNull Throwable t) {

            }
        });
    }

    private void checkImages() {

        Driver driver = mRide.getDriver();
        if (null != driver) {
            checkImage(driver.getPictureUrl());
        }

        Vehicle vehicle = mRide.getVehicle();
        if (null != vehicle) {
            checkImage(vehicle.getPictureUrl());
        }

        checkMap();
    }

    private void checkMap() {
        if(null != mMapLoading && mMapLoading.timeStamp + sIMAGE_TIMEOUT > System.currentTimeMillis())
            return;

        final String mapUrl = getMapUrl();
        if (mLastMapUrl.equals(mapUrl))
            return;

        mMapLoading = new TimedTarget() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                mLastMapUrl = mapUrl;
                mLastMap = bitmap;
                mMapLoading = null;
                onImagesUpdated();
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                mMapLoading = null;
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
            }
        };
        Picasso.get().load(mapUrl).memoryPolicy(MemoryPolicy.NO_STORE).into(mMapLoading);
    }

    private void checkImage(final String pictureUrl) {
        if (null == pictureUrl)
            return;
        if (mImages.containsKey(pictureUrl))
            return;

        // sometimes this hangs
        TimedTarget timedTarget = mImagesLoading.get(pictureUrl);
        if (null != timedTarget) {
            if (timedTarget.timeStamp + sIMAGE_TIMEOUT > System.currentTimeMillis())
                return;
            else
                mImagesLoading.remove(pictureUrl);
        }

        timedTarget = new TimedTarget() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                mImagesLoading.remove(pictureUrl);
                mImages.put(pictureUrl, bitmap);
                onImagesUpdated();
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                mImagesLoading.remove(pictureUrl);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
           }
        };
        mImagesLoading.put(pictureUrl, timedTarget);
        Picasso.get().load(pictureUrl).into(timedTarget);
    }

    private Bitmap getImage(String url) {
        return null == url ? null : mImages.get(url);
    }

    private void onImagesUpdated() {
        showRide();
    }

    /**
     * Parse and display current ride
     *
     * @return true if the ride is complete
     */
    private boolean showRide() {
        if (mLiveCard == null || !mLiveCard.isPublished())
            return false;

        if (null == mRide)
            return false;

        Ride.Status status = mRide.getStatus();
        if (Ride.Status.PROCESSING == status) {
            mLiveCard.setViews(getStatusCard(status, CardBuilder.Layout.TEXT));
        } else if (Ride.Status.NO_DRIVERS_AVAILABLE == status) {
            // too bad I can't add card to the timeline anymore
            mLiveCard.setViews(getStatusCard(status, CardBuilder.Layout.TEXT));
            return true;
        } else if (Ride.Status.ACCEPTED == status) {
            mLiveCard.setViews(fillAccepted());
        } else if (Ride.Status.ARRIVING == status) {
            mLiveCard.setViews(fillArriving());
        } else if (Ride.Status.IN_PROGRESS == status) {
            mLiveCard.setViews(showProgress());
        } else if (Ride.Status.DRIVER_CANCELED == status ||
                   Ride.Status.RIDER_CANCELED == status ||
                   Ride.Status.COMPLETED == status) {
            mLiveCard.setViews(getStatusCard(status, CardBuilder.Layout.MENU));
            return true;
        }
        // continue updating
        return false;
    }

    private RemoteViews getStatusCard(Ride.Status status, CardBuilder.Layout layout) {
        return new CardBuilder(getApplicationContext(), layout)
                        .setText(status.toString())
                        .getRemoteViews();
    }

    private RemoteViews showProgress() {
        String msg = "";
        Driver driver = mRide.getDriver();
        if (null != driver) {
            String name = driver.getName();
            if (null != name)
                msg += getString(R.string.msg_drive_with_s, name);
        }
        Location destination = mRide.getDestination();
        msg += null != destination ? "\nETA: " + destination.getEta() : "\nNo ETA";
        return new CardBuilder(getApplicationContext(), CardBuilder.Layout.TEXT)
                .setText(msg)
                .getRemoteViews();
    }

    private RemoteViews fillArriving() {
        CardBuilder cardBuilder = new CardBuilder(getApplicationContext(), CardBuilder.Layout.COLUMNS);
        StringBuilder b = new StringBuilder();
        Driver driver = mRide.getDriver();
        if (null != driver) {
            // image
            Bitmap bitmap = getImage(driver.getPictureUrl());
            if (null != bitmap)
                cardBuilder.addImage(bitmap);
            // text
            if (null != driver.getName())
                b.append(driver.getName()).append("\n");
        }
        Vehicle vehicle = mRide.getVehicle();
        if (null != vehicle) {
            // image
            Bitmap bitmap = getImage(vehicle.getPictureUrl());
            if (null != bitmap)
                cardBuilder.addImage(bitmap);
            // text
            if (null != vehicle.getMake())
                b.append(vehicle.getMake());
            if (null != vehicle.getModel())
                b.append(" ").append(vehicle.getModel());
            if (null != vehicle.getLicensePlate())
                b.append(" ").append(vehicle.getLicensePlate());
        }
        Location pickup = mRide.getPickup();
        if (null != pickup)
            b.append("\nETA: ").append(null == pickup.getEta() ? "?" : pickup.getEta());
        cardBuilder.setText(b.toString());
        return cardBuilder.getRemoteViews();
    }

    private RemoteViews fillAccepted() {
        CardBuilder cardBuilder = new CardBuilder(getApplicationContext(), CardBuilder.Layout.TEXT);
        if(null != mLastMap)
            cardBuilder.addImage(mLastMap);
        StringBuilder b = new StringBuilder();
        Driver driver = mRide.getDriver();
        if (null != driver) {
            // text
            if (null != driver.getName())
                b.append(driver.getName()).append("\n");
        }
        Vehicle vehicle = mRide.getVehicle();
        if (null != vehicle) {
            // text, a bit copy-paste here
            if (null != vehicle.getMake())
                b.append(vehicle.getMake());
            if (null != vehicle.getModel())
                b.append(" ").append(vehicle.getModel());
            if (null != vehicle.getLicensePlate())
                b.append(" ").append(vehicle.getLicensePlate());
        }
        Location pickup = mRide.getPickup();
        if (null != pickup)
            b.append("\nETA: ").append(null == pickup.getEta() ? "?" : pickup.getEta());
        cardBuilder.setText(b.toString());
        return cardBuilder.getRemoteViews();
    }

    private String getMapUrl() {
        if(null == mRide)
            return "";
        Location driver = mRide.getLocation();
        Location pickup = mRide.getPickup();
        if(null == driver || null == pickup)
            return "";
        return String.format(
                Locale.getDefault(),
                "https://static-maps.yandex.ru/1.x/?lang=en_US" +
                        "&size=640,360&l=map&pt=" +
                        "%f,%f" + // driver
                        ",pm2rdl~" +
                        "%f,%f" + // pickup
                        ",comma",
                driver.getLongitude(), driver.getLatitude(),
                pickup.getLongitude(), pickup.getLatitude());
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mUpdater);
        if (mLiveCard != null && mLiveCard.isPublished()) {
            mLiveCard.unpublish();
            mLiveCard = null;
        }
        super.onDestroy();
    }
}
