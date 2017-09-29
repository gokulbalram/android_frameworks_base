/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.internal.widget;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.RemotableViewMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import com.android.internal.R;

/**
 * A custom-built layout for the Notification.MessagingStyle.
 *
 * Evicts children until they all fit.
 */
@RemoteViews.RemoteView
public class MessagingLinearLayout extends ViewGroup {

    private static final int NOT_MEASURED_BEFORE = -1;
    /**
     * Spacing to be applied between views.
     */
    private int mSpacing;

    private int mMaxDisplayedLines = Integer.MAX_VALUE;

    /**
     * Id of the child that's also visible in the contracted layout.
     */
    private int mContractedChildId;
    /**
     * The last measured with in a layout pass if it was measured before or
     * {@link #NOT_MEASURED_BEFORE} if this is the first layout pass.
     */
    private int mLastMeasuredWidth = NOT_MEASURED_BEFORE;

    public MessagingLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.MessagingLinearLayout, 0,
                0);

        final int N = a.getIndexCount();
        for (int i = 0; i < N; i++) {
            int attr = a.getIndex(i);
            switch (attr) {
                case R.styleable.MessagingLinearLayout_spacing:
                    mSpacing = a.getDimensionPixelSize(i, 0);
                    break;
            }
        }

        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // This is essentially a bottom-up linear layout that only adds children that fit entirely
        // up to a maximum height.
        int targetHeight = MeasureSpec.getSize(heightMeasureSpec);
        switch (MeasureSpec.getMode(heightMeasureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                targetHeight = Integer.MAX_VALUE;
                break;
        }
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        boolean recalculateVisibility = mLastMeasuredWidth == NOT_MEASURED_BEFORE
                || getMeasuredHeight() != targetHeight
                || mLastMeasuredWidth != widthSize;

        // Now that we know which views to take, fix up the indents and see what width we get.
        int measuredWidth = mPaddingLeft + mPaddingRight;
        final int count = getChildCount();
        int totalHeight = getMeasuredHeight();
        if (recalculateVisibility) {
            // We only need to recalculate the view visibilities if the view wasn't measured already
            // in this pass, otherwise we may drop messages here already since we are measured
            // exactly with what we returned before, which was optimized already with the
            // line-indents.
            for (int i = 0; i < count; ++i) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                lp.hide = true;
            }

            totalHeight = mPaddingTop + mPaddingBottom;
            boolean first = true;
            int linesRemaining = mMaxDisplayedLines;

            // Starting from the bottom: we measure every view as if it were the only one. If it still

            // fits, we take it, otherwise we stop there.
            for (int i = count - 1; i >= 0 && totalHeight < targetHeight; i--) {
                if (getChildAt(i).getVisibility() == GONE) {
                    continue;
                }
                final View child = getChildAt(i);
                LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
                MessagingChild messagingChild = null;
                if (child instanceof MessagingChild) {
                    messagingChild = (MessagingChild) child;
                    messagingChild.setMaxDisplayedLines(linesRemaining);
                }
                int spacing = first ? 0 : mSpacing;
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, totalHeight
                        - mPaddingTop - mPaddingBottom + spacing);

                final int childHeight = child.getMeasuredHeight();
                int newHeight = Math.max(totalHeight, totalHeight + childHeight + lp.topMargin +
                        lp.bottomMargin + spacing);
                first = false;
                int measureType = MessagingChild.MEASURED_NORMAL;
                if (messagingChild != null) {
                    measureType = messagingChild.getMeasuredType();
                    linesRemaining -= messagingChild.getConsumedLines();
                }
                boolean isShortened = measureType == MessagingChild.MEASURED_SHORTENED;
                boolean isTooSmall = measureType == MessagingChild.MEASURED_TOO_SMALL;
                if (newHeight <= targetHeight && !isTooSmall) {
                    totalHeight = newHeight;
                    measuredWidth = Math.max(measuredWidth,
                            child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin
                                    + mPaddingLeft + mPaddingRight);
                    lp.hide = false;
                    if (isShortened || linesRemaining <= 0) {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        setMeasuredDimension(
                resolveSize(Math.max(getSuggestedMinimumWidth(), measuredWidth),
                        widthMeasureSpec),
                resolveSize(Math.max(getSuggestedMinimumHeight(), totalHeight),
                        heightMeasureSpec));
        mLastMeasuredWidth = widthSize;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int paddingLeft = mPaddingLeft;

        int childTop;

        // Where right end of child should go
        final int width = right - left;
        final int childRight = width - mPaddingRight;

        final int layoutDirection = getLayoutDirection();
        final int count = getChildCount();

        childTop = mPaddingTop;

        boolean first = true;

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (child.getVisibility() == GONE || lp.hide) {
                continue;
            }

            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            int childLeft;
            if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                childLeft = childRight - childWidth - lp.rightMargin;
            } else {
                childLeft = paddingLeft + lp.leftMargin;
            }

            if (!first) {
                childTop += mSpacing;
            }

            childTop += lp.topMargin;
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

            childTop += childHeight + lp.bottomMargin;

            first = false;
        }
        mLastMeasuredWidth = NOT_MEASURED_BEFORE;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.hide) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(mContext, attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        LayoutParams copy = new LayoutParams(lp.width, lp.height);
        if (lp instanceof MarginLayoutParams) {
            copy.copyMarginsFrom((MarginLayoutParams) lp);
        }
        return copy;
    }

    /**
     * Sets how many lines should be displayed at most
     */
    @RemotableViewMethod
    public void setMaxDisplayedLines(int numberLines) {
        mMaxDisplayedLines = numberLines;
    }

    public interface MessagingChild {
        int MEASURED_NORMAL = 0;
        int MEASURED_SHORTENED = 1;
        int MEASURED_TOO_SMALL = 2;

        int getMeasuredType();
        int getConsumedLines();
        void setMaxDisplayedLines(int lines);
    }

    public static class LayoutParams extends MarginLayoutParams {

        boolean hide = false;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }
    }
}
