package cn.edu.bjtu.foodtime.presentation;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only content:// bridge for the downloaded APK; the system installer cannot open file://. */
public final class ApkProvider extends ContentProvider {
  static Uri uriFor(Context context, File apk) {
    return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".apk").appendPath(apk.getName()).build();
  }

  @Override public boolean onCreate() { return true; }

  @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (mode != null && mode.contains("w")) throw new FileNotFoundException("read only");
    Context context = getContext();
    if (context == null) throw new FileNotFoundException();
    File directory = new File(context.getCacheDir(), "updates");
    File target = new File(directory, String.valueOf(uri.getLastPathSegment()));
    try {
      String root = directory.getCanonicalPath() + File.separator;
      if (!target.getCanonicalPath().startsWith(root)) throw new FileNotFoundException();
    } catch (IOException invalid) {
      throw new FileNotFoundException();
    }
    if (!target.isFile()) throw new FileNotFoundException();
    return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

  @Override public Cursor query(Uri uri, String[] projection, String selection,
                                String[] selectionArgs, String sortOrder) { return null; }

  @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }

  @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

  @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }
}
