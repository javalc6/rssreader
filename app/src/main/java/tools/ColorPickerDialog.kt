package tools

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import livio.rssreader.R

class ColorPickerDialog(
    context: Context,
    initialColor: Int,
    title: String?,
    colors: IntArray,
    mode: Int,
    listener: OnColorChangedListener
) : AppCompatDialog(context) {

    private val mListener: OnColorChangedListener = listener
    private val mInitialColor: Int = initialColor
    private val mMode: Int = mode
    private val mTitle: String? = title
    private val mColors: IntArray = colors
    private var mCurrentColor: Int = initialColor
    private val N: Int

    interface OnColorChangedListener {
        fun colorChanged(color: Int)
    }

    init {
        var n = 1
        while (n * n < colors.size) n++
        N = n
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_color_picker)
        window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val txtTitle = findViewById<TextView>(R.id.dialog_title)
        txtTitle?.text = mTitle

        val recycler = findViewById<RecyclerView>(R.id.swatch_recycler)
        if (recycler != null) {
            recycler.layoutManager = GridLayoutManager(context, N)
            val adapter = SwatchAdapter(mColors, mCurrentColor, mMode) { selectedColor ->
                mCurrentColor = selectedColor
            }
            recycler.adapter = adapter
        }

        val btnSelect = findViewById<MaterialButton>(R.id.btn_select)
        btnSelect?.setOnClickListener {
            mListener.colorChanged(mCurrentColor)
            dismiss()
        }

        val btnCancel = findViewById<MaterialButton>(R.id.btn_cancel)
        btnCancel?.setOnClickListener {
            dismiss()
        }
    }

    private class SwatchAdapter(
        private val colors: IntArray,
        initialSelectedColor: Int,
        private val mode: Int,
        private val onSwatchSelected: (Int) -> Unit
    ) : RecyclerView.Adapter<SwatchViewHolder>() {

        private var selectedColor: Int = initialSelectedColor

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwatchViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.color_swatch_item, parent, false)
            return SwatchViewHolder(view)
        }

        override fun onBindViewHolder(holder: SwatchViewHolder, position: Int) {
            val color = colors[position]
            val isSelected = (color == selectedColor)

            val context = holder.itemView.context
            val density = context.resources.displayMetrics.density
            val swatchSize = context.resources.getDimension(R.dimen.color_swatch_size)
            val cornerRadius = if (mode == CIRCLE_CIRCLE) swatchSize / 2f else 8f * density
            holder.swatchCard.radius = cornerRadius

            holder.swatchCard.setCardBackgroundColor(color)

            val primaryTypedValue = TypedValue()
            context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, primaryTypedValue, true)
            val primaryColor = primaryTypedValue.data

            val outlineTypedValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, outlineTypedValue, true)
            val outlineColor = if (outlineTypedValue.resourceId != 0) {
                context.getColor(outlineTypedValue.resourceId)
            } else {
                outlineTypedValue.data
            }

            if (isSelected) {
                holder.swatchCard.strokeColor = primaryColor
                holder.swatchCard.strokeWidth = (3 * density).toInt()
                holder.swatchCheck.visibility = View.VISIBLE
                val checkColor = if (ColorBase.isDarkColor(color)) Color.WHITE else Color.BLACK
                holder.swatchCheck.setColorFilter(checkColor)
            } else {
                holder.swatchCard.strokeColor = outlineColor
                holder.swatchCard.strokeWidth = (1.5f * density).toInt()
                holder.swatchCheck.visibility = View.GONE
            }

            holder.swatchCard.setOnClickListener {
                selectedColor = color
                notifyDataSetChanged()
                onSwatchSelected(color)
            }
        }

        override fun getItemCount(): Int = colors.size
    }

    private class SwatchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val swatchCard: MaterialCardView = view.findViewById(R.id.swatch_card)
        val swatchCheck: ImageView = view.findViewById(R.id.swatch_check)
    }

    companion object {
        const val SQUARE_SQUARE: Int = 0
        const val CIRCLE_CIRCLE: Int = 1
    }
}
