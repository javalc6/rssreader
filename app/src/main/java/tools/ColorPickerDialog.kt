package tools

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatDialog
import livio.rssreader.R

/*
Version 1.2, 16-06-2026, Code converted to Kotlin language

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
class ColorPickerDialog(
    context: Context,
    initialColor: Int,
    title: String?,
    colors: IntArray,
    mode: Int,
    listener: OnColorChangedListener
) : AppCompatDialog(context) {
    private val mListener: OnColorChangedListener
    private val mInitialColor: Int
    private val mMode: Int
    private val mTitle: String?
    private val mColors: IntArray
    private val N: Int

    fun interface OnColorChangedListener {
        fun colorChanged(color: Int)
    }

    init {
        if (debug) Log.d(tag, "ColorPickerDialog constructor")
        mListener = listener
        mInitialColor = initialColor
        mMode = mode
        mTitle = title
        mColors = colors
        var n = 1
        while (n * n < colors.size) n++
        N = n
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (debug) Log.d(tag, "onCreate")
        setContentView(ColorPickerView(context, mListener, mInitialColor, mMode))
        setTitle(mTitle)
    }

    private inner class ColorPickerView(
        ctx: Context?,
        listener: OnColorChangedListener,
        color: Int,
        mode: Int
    ) : View(ctx) {
        private val mPaint: Paint
        private val mPaintStroke: Paint
        private var mCurrentColor: Int
        private val mMode: Int
        private val mListener: OnColorChangedListener

        var mMarginSize: Int = 0
        var offsetY: Int = 0
        var offsetX: Int = 0
        var gridL: Int = 48
        val mSize: Int = gridL * 5

        init {
            if (debug) Log.d(Companion.tag, "ColorPickerView constructor")
            mListener = listener
            mCurrentColor = color
            mMode = mode
            mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            mPaintStroke = Paint(Paint.ANTI_ALIAS_FLAG)
            mPaintStroke.setStyle(Paint.Style.STROKE)
        }

        override fun onDraw(canvas: Canvas) {
            if (debug) Log.d(Companion.tag, "onDraw")
            mPaintStroke.strokeWidth = (mMarginSize + 1).toFloat()
            mPaintStroke.setColor(ColorBase.half_color(mCurrentColor))
            for (i in 0..<N) for (j in 0..<N) {
                val index = i + j * N
                if (index < mColors.size) { //sub-optimal solution
                    val x = i * gridL + offsetX
                    val y = j * gridL + offsetY

                    mPaint.setColor(mColors[i + j * N])

                    when (mMode) {
                        CIRCLE_CIRCLE -> {
                            val radius = gridL / 2
                            canvas.drawCircle(
                                (x + radius).toFloat(),
                                (y + radius).toFloat(),
                                (radius - mMarginSize).toFloat(),
                                mPaint
                            )
                            if (mColors[index] == mCurrentColor) {
                                canvas.drawCircle(
                                    (x + radius).toFloat(),
                                    (y + radius).toFloat(),
                                    (radius - mMarginSize).toFloat(),
                                    mPaintStroke
                                )
                            }
                        }

                        SQUARE_SQUARE -> {
                            canvas.drawRect(
                                (x + mMarginSize).toFloat(),
                                (y + mMarginSize).toFloat(),
                                (x + gridL - mMarginSize).toFloat(),
                                (y + gridL - mMarginSize).toFloat(),
                                mPaint
                            )
                            if (mColors[index] == mCurrentColor) {
                                canvas.drawRect(
                                    (x + mMarginSize).toFloat(),
                                    (y + mMarginSize).toFloat(),
                                    (x + gridL - mMarginSize).toFloat(),
                                    (y + gridL - mMarginSize).toFloat(),
                                    mPaintStroke
                                )
                            }
                        }

                        else -> {
                            canvas.drawRect(
                                (x + mMarginSize).toFloat(),
                                (y + mMarginSize).toFloat(),
                                (x + gridL - mMarginSize).toFloat(),
                                (y + gridL - mMarginSize).toFloat(),
                                mPaint
                            )
                            if (mColors[index] == mCurrentColor) {
                                canvas.drawRect(
                                    (x + mMarginSize).toFloat(),
                                    (y + mMarginSize).toFloat(),
                                    (x + gridL - mMarginSize).toFloat(),
                                    (y + gridL - mMarginSize).toFloat(),
                                    mPaintStroke
                                )
                            }
                        }
                    }
                }
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)

            var w = MeasureSpec.getSize(widthMeasureSpec) //getMeasuredWidth();
            var h = MeasureSpec.getSize(heightMeasureSpec) //getMeasuredHeight();
            val mSwatchLength: Int
            val res = resources
            if (res.getBoolean(R.bool.is_tablet)) {
                mSwatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_large)
                mMarginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_large)
            } else {
                mSwatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_small)
                mMarginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_small)
            }
            if (debug) Log.d(Companion.tag, "onMeasure, w:" + w + ", h:" + h)
            val extraheight =
                2 * mMarginSize //aggiungiamo 2 mMarginSize in verticale (sopra e sotto la palette)
            if (w > h - extraheight) {
                gridL = (h - extraheight - 1) / N
                if (gridL > mSwatchLength) {
                    gridL = mSwatchLength
                    w = gridL * N + 1
                    h = w + extraheight
                }
                offsetX = (w - h) / 2
                offsetY = extraheight / 2
            } else {
                gridL = (w - 1) / N
                if (gridL > mSwatchLength) {
                    gridL = mSwatchLength
                    w = gridL * N + 1
                    h = w + extraheight
                }
                offsetX = 0
                offsetY = (h - w) / 2
            }
            if (debug) Log.d(
                Companion.tag,
                "onMeasure, mSize:$mSize, offsetX:$offsetX, gridL:$gridL"
            )

            setMeasuredDimension(w, h)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y
                if (debug) Log.d(Companion.tag, "onTouchEvent, x:" + x + ", y:" + y)
                if ((x >= offsetX) && (y >= offsetY)) {
                    val transX = (x.toInt() - offsetX) / gridL
                    val transY = (y.toInt() - offsetY) / gridL
                    if (transX < N && transY < N) {
                        val index = N * transY + transX
                        if (index < mColors.size) {
                            mCurrentColor = mColors[index]
                            mListener.colorChanged(mCurrentColor)
                            dismiss()
                        }
                    }
                }
            }
            return true
        }
    }

    companion object {
        //values for mode:
        private const val SQUARE_SQUARE = 0
        const val CIRCLE_CIRCLE: Int = 1

        private const val debug = true
        private const val tag = "ColorPickerDialog"
    }
}