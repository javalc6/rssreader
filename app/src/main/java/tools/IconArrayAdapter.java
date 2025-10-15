package tools;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import livio.rssreader.R;

/******************************************************************************
 * IconArrayAdapter: support class for dialog for selection of an item from a list of IconItem
 ******************************************************************************/
final class IconArrayAdapter extends ArrayAdapter<IconItem> {
    private final LayoutInflater mInflater;
    private final Context context;
    private final int resourceid;

    private final List<IconItem> iconItems;

    public IconArrayAdapter(Context context, int textViewResourceId,
                            List<IconItem> objects) {
        super(context, textViewResourceId, objects);
        mInflater = LayoutInflater.from(context);
        this.context = context;
        this.iconItems = objects;
        this.resourceid = textViewResourceId;
    }

    public int getCount() {
        return iconItems.size();
    }

    public IconItem getItem(int index) {
        return iconItems.get(index);
    }

    /** Holds child views for one row. */
    static class ItemViewHolder {//viewholder pattern
        final TextView textView;
        final ImageView icon;
        ItemViewHolder(TextView textView, ImageView icon) {
            this.textView = textView;
            this.icon = icon;
        }
    }

    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        final ItemViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(resourceid, parent, false);
            TextView textView = convertView.findViewById(R.id.item_name);
            ImageView icon = convertView.findViewById(R.id.item_icon);
            holder = new ItemViewHolder(textView, icon);// set up the ViewHolder
            convertView.setTag(holder);// store the holder with the view
        } else {
            holder = (ItemViewHolder) convertView.getTag();
        }

        // Get item
        IconItem iconItem = getItem(position);
        holder.textView.setText(iconItem.name);
        try {
            Drawable d;
            if (iconItem.packagename == null) { // own package ?
                d = ContextCompat.getDrawable(context, iconItem.resourceId);
            } else {
                Resources res = context.getPackageManager().getResourcesForApplication(iconItem.packagename);
                d = ResourcesCompat.getDrawable(res, iconItem.resourceId, null);
            }
            if (d != null)
                holder.icon.setImageDrawable(d);

        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            e.printStackTrace();
        }
        return convertView;
    }

}
