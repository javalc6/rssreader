package livio.rssreader.backend;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import livio.rssreader.ListFeeds;
import livio.rssreader.R;

/** Holds child views for one row. */
class ItemViewHolder {
    final CheckBox checkBox;
    final TextView textView;
    public ItemViewHolder(TextView textView, CheckBox checkBox) {
        this.checkBox = checkBox;
        this.textView = textView;
    }
}

/** Custom adapter for displaying an array of Item objects. */
public final class ItemArrayAdapter extends ArrayAdapter<Item> {

    private final LayoutInflater inflater;
    private final String feed_id;
    private final ListFeeds.FeedsFragment ff;
    private final boolean first_element_immutable;
    private int paintFlags;

    public ItemArrayAdapter(Context context, List<Item> itemList, ListFeeds.FeedsFragment ff, String feed_id, boolean first_element_immutable) {
        super(context, R.layout.checkboxrow, R.id.rowtext, itemList);
        // Cache the LayoutInflate to avoid asking for a new one each time.
        inflater = LayoutInflater.from(context);
        this.feed_id = feed_id;
        this.ff = ff;
        this.first_element_immutable = first_element_immutable;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        // Item to display
        Item item = this.getItem(position);

        // The child views in each row.
        CheckBox checkBox;
        TextView textView;

        if (convertView == null) {// Create a new row view
            int mResource = R.layout.checkboxrow;
            convertView = inflater.inflate(mResource, null);

            // Find the child views.
            textView = convertView.findViewById(R.id.rowtext);
            paintFlags = textView.getPaintFlags();//get default paintflags
            checkBox = convertView.findViewById(R.id.checkbox);

            // Optimization: Tag the row with it's child views, so we don't have to
            // call findViewById() later when we reuse the row.
            convertView.setTag(new ItemViewHolder(textView,checkBox));

            // If CheckBox is toggled, update the item it is tagged with.
            checkBox.setOnClickListener(v -> {
                CheckBox cb = (CheckBox) v;
                Item item1 = (Item) cb.getTag();
                item1.setChecked(cb.isChecked());
            });
        } else {// Reuse existing row view
            // Because we use a ViewHolder, we avoid having to call findViewById().
            ItemViewHolder viewHolder = (ItemViewHolder) convertView.getTag();
            checkBox = viewHolder.checkBox;
            textView = viewHolder.textView;
        }

        // Tag the CheckBox with the Item it is displaying, so that we can
        // access the item in onClick() when the CheckBox is toggled.
        checkBox.setTag(item);
        if (first_element_immutable && position == 0)
            checkBox.setVisibility(View.GONE);
        else checkBox.setVisibility(View.VISIBLE);

        // Display item data
        checkBox.setChecked(item.isChecked());
        textView.setText(item.name);
        if (feed_id.equals(ff.get_feedid(position))) {//highlight a feed already selected
            textView.setPaintFlags(paintFlags | Paint.FAKE_BOLD_TEXT_FLAG);
        } else textView.setPaintFlags(paintFlags);

        return convertView;
    }

}
