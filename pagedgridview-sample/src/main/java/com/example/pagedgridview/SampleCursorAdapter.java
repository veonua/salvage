package com.example.pagedgridview;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.os.Handler;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.jakewharton.salvage.RecyclingGridPagerAdapter;

/**
 * User: Andrew Matuk (Veon)
 * Date: 3/7/13
 * Time: 12:30 AM
 */

public class SampleCursorAdapter extends RecyclingGridPagerAdapter {
    private final LayoutInflater inflater;
    private Cursor cursor;
    private BitmapLruCache<Long> cache;
    private Resources resources;
    private ContentResolver cr;
    private boolean autoRequery;
    private boolean dataValid;
    private int rowIDColumn;

    public SampleCursorAdapter(Context context, Cursor cursor) {
        this(context, cursor, true);
    }

    public SampleCursorAdapter(Context context, Cursor cursor, boolean autoRequery) {
        super(context, R.layout.page, 0);

        inflater = LayoutInflater.from(context);
        this.cursor = cursor;
        this.autoRequery = autoRequery;
        cache = new BitmapLruCache<Long>(context);
        resources = context.getResources();
        cr = context.getContentResolver();

        dataValid = (cursor != null && !cursor.isClosed());
        rowIDColumn = dataValid ? cursor.getColumnIndexOrThrow(BaseColumns._ID) : -1;
        if (dataValid) {
            cursor.registerContentObserver(changeObserver);
            cursor.registerDataSetObserver(dataSetObserver);
        }
    }

    public void changeCursor(Cursor value) {
        if (value == this.cursor) {
            return;
        }
        if (this.cursor != null) {
            this.cursor.unregisterContentObserver(changeObserver);
            this.cursor.unregisterDataSetObserver(dataSetObserver);
            this.cursor.close();
        }
        this.cursor = value;
        if (value != null) {
            cursor.registerContentObserver(changeObserver);
            cursor.registerDataSetObserver(dataSetObserver);
            rowIDColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            dataValid = true;
            // notify the observers about the new cursor
            notifyDataSetChanged();
        } else {
            rowIDColumn = -1;
            dataValid = false;
            // notify the observers about the lack of a data set
            //notifyDataSetInvalidated();
        }
    }

    @Override
    public View getView(int position, View view, ViewGroup container) {
        String recycled = "No";
        ViewHolder holder;
        if (view != null) {
            holder = (ViewHolder) view.getTag();
            recycled = "Yes";
        } else {
            view = inflater.inflate(R.layout.item, container, false);
            holder = new ViewHolder(view);
            view.setTag(holder);
        }

        cursor.moveToPosition(position);
        Long cover = cursor.getLong(rowIDColumn);
        final Bitmap bitmap = cache.get(cover);

        boolean cancel = ImageViewLoader.cancelPotentialWork(cover, holder.image);
        if (bitmap != null) {
            holder.image.setImageBitmap(bitmap);
        } else if (cancel) {
            ImageViewLoader loader = new ImageViewLoader<Long>(cr, resources, cache, holder.image, cover);
            ImageViewLoader.AsyncDrawable ad = new ImageViewLoader.AsyncDrawable(resources, null, loader);
            holder.image.setImageDrawable(ad);
            loader.execute();
        }

        holder.position.setText(String.valueOf(position));
        holder.recycled.setText(recycled);

        return view;
    }


    @Override
    protected int getItemCount() {
        if (dataValid && cursor != null) {
            return cursor.getCount();
        } else {
            return 0;
        }
    }

    private static class ViewHolder {
        final ImageView image;
        final TextView position;
        final TextView recycled;

        public ViewHolder(View view) {
            image = (ImageView) view.findViewById(R.id.image);
            position = (TextView) view.findViewById(R.id.position);
            recycled = (TextView) view.findViewById(R.id.recycled);
        }
    }

    protected void onContentChanged() {
        if (autoRequery && cursor != null && !cursor.isClosed()) {
            if (BuildConfig.DEBUG) Log.v("Cursor", "Auto requerying " + cursor + " due to update");
            dataValid = cursor.requery();
        }
    }

    ContentObserver changeObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onContentChanged();
        }
    };

    DataSetObserver dataSetObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            dataValid = true;
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            dataValid = false;
            //notifyDataSetInvalidated();
        }
    };
}
