package com.damn.uglass.cardproviders;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import com.damn.uglass.ConnectActivity;
import com.damn.uglass.R;
import com.damn.uglass.bluetooth.BluetoothHost;
import com.damn.uglass.model.TokenStorage;
import com.google.android.glass.widget.Slider;

import damn.com.shared.TokenMessage;

public class ObtainTokenCardProvider
        extends BaseCardProvider {

    private final ConnectActivity mHost;
    private final BluetoothHost mBt;
    private View mView;
    private Slider.Indeterminate mIndeterminate;

    public ObtainTokenCardProvider(/*should be IFace*/ConnectActivity ctx){
        mHost = ctx;
        // we are in activity, so can use normal view for cheaper updates
        final LayoutInflater layoutInflater = ctx.getLayoutInflater();
        mView = layoutInflater.inflate(R.layout.card_acquiring_token, null);
        final TextView label = (TextView) mView.findViewById(R.id.lbl_status);
        final Resources res = mView.getResources();
        mBt = new BluetoothHost(ctx) {

            @Override
            public void onWaiting() {
                label.setText(R.string.msg_waiting_for_connection);
            }

            @Override
            public void onDataReceived(String data) {
                final TokenMessage tokenMessage = TokenMessage.fromJson(data);
                if (null == tokenMessage) {
                    label.setText(R.string.msg_token_format_error);
                }else if (!tokenMessage.isValid()) {
                    label.setText(R.string.msg_token_expired);
                } else {
                    label.setText(R.string.msg_token_received);
                    stop();
                    // if parsing was a success, store string form
                    TokenStorage.storeToken(mHost, data);
                    mHost.tryLogin();
                }
            }

            @Override
            public void onConnectionStarted(String device) {
                label.setText(res.getString(R.string.msg_connected_to_s, device));
            }

            @Override
            public void onConnectionLost(String error) {
                if(null != mIndeterminate)
                    mIndeterminate.hide();

                if(null != error)
                    label.setText(res.getString(R.string.msg_connection_error_s, error));
                // otherwise keep error message from onDataReceived
            }
        };
    }

    @Override
    public BaseCardProvider retry() {
        return this;
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        if(null != mIndeterminate)
            mIndeterminate.hide();
        mBt.stop();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if(mBt.isStarted())
            return;
        mIndeterminate = mHost.getSlider().startIndeterminate();
        mBt.start();
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public Object getItem(int position) {
        return mView;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return mView;
    }

    @Override
    public int getPosition(Object item) {
        return mView.equals(item) ? 0 : AdapterView.INVALID_POSITION;
    }

}
