package cn.edu.bjtu.foodtime.presentation;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ScrollView;

/** ScrollView that reports raw gestures without consuming them, so pages can add refresh gestures. */
final class RefreshScrollView extends ScrollView {
  interface GestureListener {
    void onGesture(int action, float x, float y, int pointers);
  }

  private final GestureListener listener;

  RefreshScrollView(Context context, GestureListener listener) {
    super(context);
    this.listener = listener;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (listener != null) {
      listener.onGesture(event.getActionMasked(), event.getX(), event.getY(), event.getPointerCount());
    }
    return super.dispatchTouchEvent(event);
  }
}
