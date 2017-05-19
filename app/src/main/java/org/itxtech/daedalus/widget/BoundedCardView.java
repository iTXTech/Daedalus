package org.itxtech.daedalus.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import org.itxtech.daedalus.R;

/**
 * Daedalus Project
 *
 * @author iTX Technologies & MrFuFuFu
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public class BoundedCardView extends CardView {
    public BoundedCardView(Context context) {
        super(context);
    }

    public BoundedCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(context, attrs);
    }

    public BoundedCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private int boundedWidth;
    private int boundedHeight;

    private void initAttrs(Context context, AttributeSet attrs) {
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.BoundedView);
        boundedWidth = arr.getDimensionPixelSize(R.styleable.BoundedView_bounded_width, 0);
        boundedHeight = arr.getDimensionPixelSize(R.styleable.BoundedView_bounded_height, 0);
        arr.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (boundedWidth > 0 && boundedWidth < MeasureSpec.getSize(widthMeasureSpec)) {
            MeasureSpec.makeMeasureSpec(boundedWidth, MeasureSpec.getMode(widthMeasureSpec));
        }
        if (boundedHeight > 0 && boundedHeight < MeasureSpec.getSize(heightMeasureSpec)) {
            MeasureSpec.makeMeasureSpec(boundedHeight, MeasureSpec.getMode(heightMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
