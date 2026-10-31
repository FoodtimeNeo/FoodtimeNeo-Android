package cn.edu.bjtu.foodtime.presentation;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

/** Native password rendering regression; forms are never submitted. */
public final class PasswordInputRunner extends Instrumentation {
  private int checks;

  private void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    checks++;
  }

  private EditText field(View view, String label) {
    if (view instanceof EditText input && label.contentEquals(input.getContentDescription()))
      return input;
    if (view instanceof ViewGroup group) {
      for (int i = 0; i < group.getChildCount(); i++) {
        EditText found = field(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  private void masked(LinearLayout page, String label) {
    EditText input = field(page, label);
    check(input != null, label + " exists");
    check(input.getTransformationMethod() instanceof PasswordTransformationMethod,
        label + " retains password masking after single-line setup");
    String value = "MaskCheck2026!";
    input.setText(value);
    check(!input.getTransformationMethod().getTransformation(input.getText(), input)
        .toString().contains(value), label + " is not displayed as plaintext");
    check(input.getText().toString().equals(value), label + " keeps original submission value");
    check(input.createAccessibilityNodeInfo().isPassword(), label + " is marked sensitive");
    check(!input.isSaveEnabled(), label + " is not saved in view state");
    check((input.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0
        && input.getMaxLines() == 1, label + " uses single-line password input");
  }

  @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

  @Override public void onStart() {
    Bundle result = new Bundle();
    MainActivity activity = null;
    try {
      if (!cn.edu.bjtu.foodtime.BuildConfig.DEBUG
          || !cn.edu.bjtu.foodtime.BuildConfig.APPLICATION_ID.endsWith(".maskcheck"))
        throw new IllegalStateException("Use isolated .maskcheck debug APK");
      activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      MainActivity target = activity;
      AtomicReference<Throwable> failure = new AtomicReference<>();
      runOnMainSync(() -> {
        try {
          for (boolean register : new boolean[]{false, true}) {
            LinearLayout page = Ui.col(target);
            AuthScreen.show(target, page, register);
            masked(page, "密码");
            check(!(field(page, "学号邮箱").getTransformationMethod()
                instanceof PasswordTransformationMethod), "Email remains visible");
          }
          LinearLayout page = Ui.col(target);
          PasswordScreen.show(target, page, new MainActivity.Route("password", ""));
          for (String label : new String[]{"旧密码", "新密码", "确认新密码"}) masked(page, label);
        } catch (Throwable error) { failure.set(error); }
      });
      if (failure.get() != null) throw new AssertionError(failure.get());
      result.putString("stream", "Password input PASS: " + checks + " checks\n");
      finish(-1, result);
    } catch (Throwable error) {
      StringWriter stack = new StringWriter();
      error.printStackTrace(new PrintWriter(stack));
      result.putString("stream", "Password input FAIL after " + checks + " checks\n" + stack);
      finish(1, result);
    } finally {
      if (activity != null) { MainActivity target = activity; runOnMainSync(target::finish); }
    }
  }
}
