package com.damn.uglass.cardproviders;

import android.widget.AdapterView;

import com.google.android.glass.widget.CardScrollAdapter;

public abstract class BaseCardProvider
        extends CardScrollAdapter
        implements AdapterView.OnItemClickListener {
    /**
     * Called when provider  shown again after error
     * Can create new adapter, if state is not recoverable
     */
    public BaseCardProvider retry() {
        return this;
    }

    public void onRemoved() {

    }
}
