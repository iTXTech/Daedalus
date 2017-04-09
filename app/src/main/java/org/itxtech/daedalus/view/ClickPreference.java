package org.itxtech.daedalus.view;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

/**
 * @author PeratX
 */
public class ClickPreference extends ListPreference {

    public ClickPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ClickPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClickPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
    }
}
