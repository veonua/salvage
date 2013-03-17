package com.example.pagedgridview;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

/**
 * User: Andrew Matuk (Veon)
 * Date: 3/7/13
 * Time: 12:30 AM
 */
public class BitmapLruCache<T> extends LruCache<T, Bitmap> {
    private static final float DEFAULT_MEM_CACHE_PERCENT = 0.25f;

    public BitmapLruCache(Context context) {
        super(Utils.getMemCacheSize(context, DEFAULT_MEM_CACHE_PERCENT));
    }

    @Override
    protected int sizeOf(T key, Bitmap bitmap) {
        return Utils.getBitmapSize(bitmap);
    }
}
