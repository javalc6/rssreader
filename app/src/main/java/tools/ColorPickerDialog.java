package tools;
/*
Copyright 2018 javalc6

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

IMPORTANT NOTICE, please read:

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import livio.rssreader.R;

import static tools.ColorBase.half_color;

public class ColorPickerDialog extends AppCompatDialog {
//values for mode:
    private final static int SQUARE_SQUARE = 0;
    public final static int CIRCLE_CIRCLE = 1;

    private final static boolean debug = true;
    private final static String tag = "ColorPickerDialog";

    private final OnColorChangedListener mListener;
    private final int mInitialColor;
    private final int mMode;
    private final String mTitle;
    private final int[] mColors;
    private final int N;

    public interface OnColorChangedListener {
        void colorChanged(int color);
    }

    public ColorPickerDialog(Context context, int initialColor, String title, int[] colors, int mode, OnColorChangedListener listener) {
        super(context);
        if (debug)
            Log.d(tag, "ColorPickerDialog constructor");
        mListener = listener;
        mInitialColor = initialColor;
        mMode = mode;
        mTitle = title;
        mColors = colors;
        int n = 1;
        while (n * n < colors.length) n++;
        N = n;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (debug)
            Log.d(tag, "onCreate");
        setContentView(new ColorPickerView(getContext(), mListener, mInitialColor, mMode));
        setTitle(mTitle);
    }

    private class ColorPickerView extends View {
        private final Paint mPaint;
        private final Paint mPaintStroke;
        private int mCurrentColor;
        private final int mMode;
        private final OnColorChangedListener mListener;

        ColorPickerView(Context ctx, OnColorChangedListener listener, int color, int mode) {
            super(ctx);
            if (debug)
                Log.d(tag, "ColorPickerView constructor");
            mListener = listener;
            mCurrentColor = color;
            mMode = mode;
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaintStroke.setStyle(Paint.Style.STROKE);
        }

        int mMarginSize;
        int offsetY;
        int offsetX;
        int gridL = 48;
        final int mSize = gridL * 5;

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (debug)
                Log.d(tag, "onDraw");
            mPaintStroke.setStrokeWidth(mMarginSize + 1);
            mPaintStroke.setColor(half_color(mCurrentColor));
            for (int i = 0; i < N; i++)
                for (int j = 0; j < N; j++) {
                    int index = i + j * N;
                    if (index < mColors.length) {//sub-optimal solution
                        int x = i * gridL + offsetX;
                        int y = j * gridL + offsetY;

                        mPaint.setColor(mColors[i + j * N]);

                        switch (mMode) {
                            case CIRCLE_CIRCLE:
                                int radius = gridL / 2;
                                canvas.drawCircle(x + radius, y + radius, radius  - mMarginSize, mPaint);
                                if (mColors[index] == mCurrentColor) {
                                    canvas.drawCircle(x + radius, y + radius, radius - mMarginSize, mPaintStroke);
                                }
                                break;
                            case SQUARE_SQUARE:
                            default://same as mode == SQUARE_SQUARE
                                canvas.drawRect(x + mMarginSize, y + mMarginSize,
                                        x + gridL - mMarginSize, y + gridL - mMarginSize, mPaint);
                                if (mColors[index] == mCurrentColor) {
                                    canvas.drawRect(x + mMarginSize, y + mMarginSize,
                                            x + gridL - mMarginSize, y + gridL - mMarginSize, mPaintStroke);
                                }
                        }
                    }
                }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            int w = MeasureSpec.getSize(widthMeasureSpec); //getMeasuredWidth();
            int h = MeasureSpec.getSize(heightMeasureSpec); //getMeasuredHeight();
            int mSwatchLength;
            Resources res = getResources();
            if (res.getBoolean(R.bool.is_tablet)) {
                mSwatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_large);
                mMarginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_large);
            } else {
                mSwatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_small);
                mMarginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_small);
            }
            if (debug)
                Log.d(tag, "onMeasure, w:"+w+", h:"+h);
            int extraheight = 2 * mMarginSize;//aggiungiamo 2 mMarginSize in verticale (sopra e sotto la palette)
            if (w > h - extraheight) {
                gridL = (h - extraheight - 1) / N;
                if (gridL > mSwatchLength) {
                    gridL = mSwatchLength;
                    w = gridL * N + 1;
                    h = w + extraheight;
                }
                offsetX = (w - h) / 2;
                offsetY = extraheight / 2;
            } else {
                gridL = (w - 1) / N;
                if (gridL > mSwatchLength) {
                    gridL = mSwatchLength;
                    w = gridL * N + 1;
                    h = w + extraheight;
                }
                offsetX = 0;
                offsetY = (h - w) / 2;
            }
            if (debug)
                Log.d(tag, "onMeasure, mSize:"+mSize+", offsetX:"+offsetX + ", gridL:"+gridL);

            setMeasuredDimension(w, h);

        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                if (debug)
                    Log.d(tag, "onTouchEvent, x:" + x + ", y:" + y);
                if ((x >= offsetX) && (y >= offsetY)) {
                    int transX = ((int) x - offsetX) / gridL;
                    int transY = ((int) y - offsetY) / gridL;
                    if (transX < N && transY < N) {
                        int index = N * transY + transX;
                        if (index < mColors.length) {
                            mCurrentColor = mColors[index];
                            mListener.colorChanged(mCurrentColor);
                            dismiss();
                        }
                    }
                }
            }
            return true;
        }
    }

}