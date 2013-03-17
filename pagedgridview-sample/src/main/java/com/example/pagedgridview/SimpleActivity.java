package com.example.pagedgridview;

import android.R;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;

/**
 * User: Andrew Matuk (Veon)
 * Date: 3/7/13
 * Time: 12:30 AM
 */

public class SimpleActivity extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    SampleCursorAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Utils.enableStrictMode();

        ViewPager viewPager = new ViewPager(this);
        viewPager.setId(R.id.primary);

        setContentView(viewPager);
        adapter = new SampleCursorAdapter(this, null);
        viewPager.setAdapter(adapter);
        getSupportLoaderManager().initLoader(0, null, this);
    }


    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        final String[] PROJECTION = new String[]{MediaStore.Images.Media._ID};
        return new CursorLoader(this, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, PROJECTION, null, null, null);
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> objectLoader, Cursor o) {
        adapter.changeCursor(o);
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> objectLoader) {
        adapter.changeCursor(null);
    }

}
