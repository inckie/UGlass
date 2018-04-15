package com.damn.uglass.cardproviders;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.damn.uglass.ConnectActivity;
import com.damn.uglass.R;
import com.google.android.glass.widget.CardBuilder;

// Should be ErrorCardProvider
public class NoConnectionCardProvider
        extends BaseCardProvider {

    private final ConnectActivity mHost;
    private final BaseCardProvider mPrevious;
    private View mView;

    public NoConnectionCardProvider(ConnectActivity ctx/*should be IFace*/,
                                    BaseCardProvider previous,
                                    String msg){
        mHost = ctx;
        mPrevious = previous;
        mView = new CardBuilder(ctx, CardBuilder.Layout.ALERT)
                .setText(msg)
                .setFootnote(R.string.tap_to_retry)
                .getView();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mHost.onRetry(mPrevious);
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
        if (mView.equals(item)) {
            return 0;
        }
        return AdapterView.INVALID_POSITION;
    }

}
