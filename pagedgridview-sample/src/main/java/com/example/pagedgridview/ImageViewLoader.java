package com.example.pagedgridview;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.LruCache;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/**
 * User: Andrew Matuk (Veon)
 * Date: 3/7/13
 * Time: 12:30 AM
 */
public class ImageViewLoader<T> extends AsyncTask<Object, Object, Bitmap> {
    private static final int FADE_IN_TIME = 200;
    protected static final int REQ_WIDTH = 300;
    protected static final int REQ_HEIGHT = 200;

    private final LruCache<T, Bitmap> mCache;
    private final WeakReference<ImageView> mView;
    private final ContentResolver mContentResolver;
    private final T mId;
    private boolean mPauseWork = false;
    private final Object mPauseWorkLock = new Object();
    private static final String TAG = "ImageViewLoader";
    private Resources mResources;

    public ImageViewLoader(ContentResolver contentResolver, Resources resources, LruCache<T, Bitmap> cache, ImageView view, T id) {
        this.mCache = cache;
        this.mView = new WeakReference<ImageView>(view);
        this.mContentResolver = contentResolver;
        this.mResources = resources;
        this.mId = id;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else {
                inSampleSize = Math.round((float) width / (float) reqWidth);
            }
        }
        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmap(String pathName, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(pathName, options);
    }

    @Override
    protected Bitmap doInBackground(Object... none) {
        Bitmap bmp = mCache.get(mId);
        if (bmp != null) return bmp;

        // Wait here if work is paused and the task is not cancelled
        synchronized (mPauseWorkLock) {
            while (mPauseWork && !isCancelled()) {
                try {
                    mPauseWorkLock.wait();
                } catch (InterruptedException ignore) {
                }
            }
        }

        if (isCancelled() || mView == null || mView.get() == null) return null;

        long mediaStoreId = 0; // check if we have the bitmap in OS MediaStore
        if (mId instanceof String) {
            bmp = decodeSampledBitmap((String) mId, REQ_WIDTH, REQ_HEIGHT);
        } else if (mId instanceof Uri) {
            Uri uri = (Uri) mId;
            String scheme = uri.getScheme();
            if ("content".equals(scheme))
                mediaStoreId = Long.parseLong(uri.getLastPathSegment());
            else if ("file".equals(scheme))
                bmp = decodeSampledBitmap(uri.getPath(), REQ_WIDTH, REQ_HEIGHT);
        } else if (mId instanceof Long) {
            mediaStoreId = (Long) mId;
        }

        if (!isCancelled() && mediaStoreId != 0)
            // load thumbnail from media store
            bmp = MediaStore.Images.Thumbnails.getThumbnail(mContentResolver, mediaStoreId, MediaStore.Images.Thumbnails.MINI_KIND, null);

        return bmp;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (!isCancelled() && mView != null && bitmap != null) {
            mCache.put(mId, bitmap);
            final ImageView imageView = mView.get();
            if (imageView != null) {
                setImageBitmap(imageView, bitmap);
            }
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        synchronized (mPauseWorkLock) {
            mPauseWorkLock.notifyAll();
        }
    }

    public void setPauseWork(boolean pauseWork) {
        synchronized (mPauseWorkLock) {
            mPauseWork = pauseWork;
            if (!mPauseWork) {
                mPauseWorkLock.notifyAll();
            }
        }
    }

    /**
     * Called when the processing is complete and the final bitmap should be set on the ImageView.
     *
     * @param imageView
     * @param bitmap
     */
    private void setImageBitmap(ImageView imageView, Bitmap bitmap) {
        boolean mFadeInBitmap = false;
        if (mFadeInBitmap) {
            final TransitionDrawable td = new TransitionDrawable(new Drawable[]{new ColorDrawable(android.R.color.transparent), new BitmapDrawable(mResources, bitmap)});
            // Set background to loading bitmap
            //imageView.setBackgroundDrawable(
            //        new BitmapDrawable(mResources, mLoadingBitmap));

            imageView.setImageDrawable(td);
            td.startTransition(FADE_IN_TIME);
        } else {
            imageView.setImageBitmap(bitmap);
        }
    }


    /**
     * A custom Drawable that will be attached to the imageView while the work is in progress.
     * Contains a reference to the actual worker task, so that it can be stopped if a new binding is
     * required, and makes sure that only the last started worker process can bind its result,
     * independently of the finish order.
     */
    public static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<ImageViewLoader> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, ImageViewLoader bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<ImageViewLoader>(bitmapWorkerTask);
        }

        public ImageViewLoader getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }


    /**
     * Returns true if the current work has been canceled or if there was no work in
     * progress on this image view.
     * Returns false if the work in progress deals with the same data. The work is not
     * stopped in that case.
     */
    public static boolean cancelPotentialWork(Object id, ImageView imageView) {
        final ImageViewLoader bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Object bitmapData = bitmapWorkerTask.mId;
            if (bitmapData != null && !bitmapData.equals(id)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * @param imageView Any imageView
     * @return Retrieve the currently active work task (if any) associated with this imageView.
     *         null if there is no such task.
     */
    private static ImageViewLoader getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }
}
