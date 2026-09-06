package com.qqversionfix;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Exposes the module's saved config to the QQ process (which runs under QQ's
 * uid and cannot read our SharedPreferences directly).
 */
public class ConfigProvider extends ContentProvider {

    static final String AUTHORITY = "com.qqversionfix.provider";
    static final Uri CONFIG_URI = Uri.parse("content://" + AUTHORITY + "/config");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        if (ctx == null) {
            return null;
        }
        SharedPreferences sp = ctx.getSharedPreferences(MainActivity.PREF, Context.MODE_PRIVATE);
        String version = sp.getString(MainActivity.KEY_VERSION, "");
        String build = sp.getString(MainActivity.KEY_BUILD, "");
        String date = sp.getString(MainActivity.KEY_DATE, "");
        String sig = sp.getString(MainActivity.KEY_SIG, "");
        String code = sp.getString(MainActivity.KEY_CODE, "");
        String extra = sp.getString(MainActivity.KEY_EXTRA, "");

        MatrixCursor cursor = new MatrixCursor(new String[]{
                MainActivity.KEY_VERSION,
                MainActivity.KEY_BUILD,
                MainActivity.KEY_DATE,
                MainActivity.KEY_SIG,
                MainActivity.KEY_CODE,
                MainActivity.KEY_EXTRA
        });
        cursor.addRow(new Object[]{version, build, date, sig, code, extra});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.qqversionfix.config";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
