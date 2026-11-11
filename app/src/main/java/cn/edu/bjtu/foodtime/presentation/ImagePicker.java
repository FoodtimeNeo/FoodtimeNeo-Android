package cn.edu.bjtu.foodtime.presentation;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

/**
 * Album-first picker adapter. Uses the system photo picker when the device actually resolves it,
 * otherwise falls back to the existing document picker. No storage-wide permission is requested.
 */
final class ImagePicker {
  private ImagePicker() {}

  static Intent intent(Context context, boolean multiple, int max, String[] mimeTypes) {
    if (Build.VERSION.SDK_INT >= 30) {
      Intent picker = new Intent(MediaStore.ACTION_PICK_IMAGES);
      picker.setType("image/*");
      if (mimeTypes != null && mimeTypes.length > 0)
        picker.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
      if (multiple) {
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        picker.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, Math.max(1, max));
      }
      if (picker.resolveActivity(context.getPackageManager()) != null) return picker;
    }
    return fallback(multiple, mimeTypes);
  }

  static Intent fallback(boolean multiple, String[] mimeTypes) {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.setType("image/*");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    if (mimeTypes != null && mimeTypes.length > 0)
      intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
    if (multiple) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    return intent;
  }

  /** Start the primary picker; on a missing/failed activity retry the file picker once. */
  static boolean start(Activity activity, Intent primary, Intent fallback, int requestCode) {
    try {
      activity.startActivityForResult(primary, requestCode);
      return true;
    } catch (ActivityNotFoundException | SecurityException primaryFailure) {
      try {
        activity.startActivityForResult(fallback, requestCode);
        return true;
      } catch (ActivityNotFoundException | SecurityException fallbackFailure) {
        return false;
      }
    }
  }

  /** Photo picker and document picker may return one URI in either data or ClipData. */
  static Uri firstUri(Intent data) {
    if (data == null) return null;
    if (data.getData() != null) return data.getData();
    if (data.getClipData() != null && data.getClipData().getItemCount() > 0)
      return data.getClipData().getItemAt(0).getUri();
    return null;
  }
}
