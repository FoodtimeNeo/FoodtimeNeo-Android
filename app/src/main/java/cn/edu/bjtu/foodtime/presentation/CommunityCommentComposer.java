package cn.edu.bjtu.foodtime.presentation;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityComment;
import cn.edu.bjtu.foodtime.domain.model.Models.CommunityPost;
import cn.edu.bjtu.foodtime.domain.validation.Validation;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Drafts are scoped to the post or exact reply target, independent of the sheet lifecycle. */
final class CommunityCommentComposer {
  private static final int LIMIT = 500;
  private static final String SENDING_KEY = "commentSubmittingDraft";
  private final MainActivity activity;
  private final View page;
  private final CommunityPost post;
  private final Runnable onChanged;
  private final BiConsumer<String, String> onSubmitted;
  private Dialog current;

  CommunityCommentComposer(MainActivity activity, View page, CommunityPost post, Runnable onChanged,
      BiConsumer<String, String> onSubmitted) {
    this.activity = activity;
    this.page = page;
    this.post = post;
    this.onChanged = onChanged;
    this.onSubmitted = onSubmitted;
    page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override public void onViewAttachedToWindow(View view) {}
      @Override public void onViewDetachedFromWindow(View view) {
        if (current != null) current.dismiss();
      }
    });
  }

  void open(CommunityComment root, CommunityComment target) {
    if (current != null || !page.isAttachedToWindow() || activity.operationInFlight()) return;
    boolean reply = target != null;
    String targetId = reply ? target.id() : "";
    String rootId = reply ? root.id() : "";
    String draft = activity.draftKey(reply ? "reply-content:" + targetId : "评论内容");
    String submission = activity.draftKey(SENDING_KEY);
    String name = reply && !target.userDisplayName().isBlank() ? target.userDisplayName() : "同学";
    String title = reply ? "回复 @" + name : "评论帖子";
    String hint = reply ? "回复 @" + name + "…" : "分享你的美食体验…";

    Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar);
    current = dialog;
    FrameLayout overlay = new FrameLayout(activity);
    overlay.setOnClickListener(v -> { if (!activity.operationInFlight()) dialog.dismiss(); });
    overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout sheet = Ui.col(activity);
    sheet.setTag("community-comment-sheet");
    sheet.setBackground(Ui.shape(activity, Design.PAPER, Color.TRANSPARENT, 18));
    sheet.setPadding(dp(16), dp(6), dp(16), dp(8));
    sheet.setClickable(true);
    overlay.addView(sheet, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

    LinearLayout heading = Ui.row(activity);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    TextView label = Ui.text(activity, title, 16, reply ? Design.RED : Design.INK);
    label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    label.setMaxLines(2);
    label.setEllipsize(TextUtils.TruncateAt.END);
    heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    TextView close = CommunityDiscussion.action(activity, "×", () -> {
      if (!activity.operationInFlight()) dialog.dismiss();
    });
    close.setTextSize(22);
    close.setContentDescription("收起输入面板");
    heading.addView(close);
    sheet.addView(heading);

    EditText input = new EditText(activity);
    input.setTag("community-composer-input");
    input.setContentDescription(reply ? title : "评论内容");
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setGravity(Gravity.TOP | Gravity.START);
    input.setTextColor(Design.INK);
    input.setHintTextColor(Design.MUTED);
    input.setTextSize(16);
    input.setMinLines(2);
    input.setMaxLines(4);
    input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(LIMIT)});
    input.setPadding(dp(13), dp(10), dp(13), dp(10));
    input.setBackground(Ui.shape(activity, Design.ALT, Color.TRANSPARENT, 14));
    input.setHint(hint);
    input.setText(activity.drafts.getString(draft, ""));
    input.setSelection(input.length());
    sheet.addView(input);

    TextView error = Ui.text(activity, "", 13, Design.RED);
    Ui.observeError(error);
    sheet.addView(error);
    LinearLayout footer = Ui.row(activity);
    footer.setGravity(Gravity.CENTER_VERTICAL);
    footer.setPadding(0, dp(6), 0, 0);
    TextView counter = Ui.text(activity, "", 12, Design.MUTED);
    footer.addView(counter, new LinearLayout.LayoutParams(0, -2, 1));
    TextView send = Ui.button(activity, "发送", true, () -> {});
    send.setTag("community-composer-send");
    send.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
        new int[]{0xffa07a6a, Design.RED}));
    send.setMinWidth(dp(72));
    footer.addView(send);
    sheet.addView(footer);

    Runnable paint = () -> {
      counter.setText(input.length() + " / " + LIMIT);
      send.setEnabled(input.isEnabled() && !input.getText().toString().isBlank());
    };
    observe(input, value -> {
      activity.drafts.putString(draft, value.toString());
      error.setText("");
      paint.run();
    });
    observe(error, value -> {
      if (value.length() == 0) return;
      dialog.setCancelable(true);
      input.setEnabled(true);
      paint.run();
    });
    paint.run();
    send.setOnClickListener(v -> {
      if (activity.operationInFlight()) return;
      String body;
      try { body = Validation.text(input.getText().toString(), LIMIT, reply ? "回复" : "评论"); }
      catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); return; }
      String request = requestId(draft, rootId, body);
      activity.drafts.putString(submission, draft);
      input.setEnabled(false);
      dialog.setCancelable(false);
      activity.requestCommunityComment(send, error, reply,
          () -> activity.repo.submitCommunityComment(post.id(), body, rootId, targetId, request), created -> {
            completeSubmission(activity.drafts, activity.draftKey(""));
            dialog.dismiss();
            onSubmitted.accept(rootId, body);
            activity.message("已提交审核，通过前仅自己可见");
            if (created != null && !created.isBlank()) watchApproval(rootId, created, 0);
          });
    });

    dialog.setOnDismissListener(unused -> current = null);
    dialog.setContentView(overlay);
    var window = dialog.getWindow();
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    window.setDimAmount(.28f);
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    if (Build.VERSION.SDK_INT >= 30) {
      window.setDecorFitsSystemWindows(false);
      overlay.setOnApplyWindowInsetsListener((view, insets) -> {
        var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
        view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
        return insets;
      });
    }
    input.requestFocus();
    overlay.getViewTreeObserver().addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener() {
      @Override public void onWindowFocusChanged(boolean focused) {
        if (!focused || !dialog.isShowing()) return;
        overlay.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
        if (Build.VERSION.SDK_INT >= 30 && window.getInsetsController() != null) {
          window.getInsetsController().show(WindowInsets.Type.ime());
        } else {
          ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
              .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
      }
    });
    dialog.show();
    window.setLayout(-1, -1);
    overlay.requestApplyInsets();
  }

  private String requestId(String draft, String rootId, String body) {
    String fingerprint = rootId + "|" + body;
    String id = activity.drafts.getString(draft + ":request", "");
    if (id.isBlank() || !fingerprint.equals(activity.drafts.getString(draft + ":payload", ""))) {
      id = UUID.randomUUID().toString();
      activity.drafts.putString(draft + ":request", id);
      activity.drafts.putString(draft + ":payload", fingerprint);
    }
    return id;
  }

  static void completeSubmission(Bundle drafts, String prefix) {
    String key = drafts.getString(prefix + SENDING_KEY, "");
    if (key.startsWith(prefix) && key.length() > prefix.length()) {
      drafts.remove(key);
      drafts.remove(key + ":payload");
      drafts.remove(key + ":request");
    }
    drafts.remove(prefix + SENDING_KEY);
  }

  private void watchApproval(String rootId, String commentId, int attempt) {
    if (attempt >= 12 || !page.isAttachedToWindow()) return;
    String[] status = {null};
    activity.read("comment-status:" + commentId, null, null,
        () -> status[0] = activity.repo.communityCommentStatus(commentId), () -> {
          if (status[0] == null) return;
          if (status[0].equals("pending")) {
            page.postDelayed(() -> watchApproval(rootId, commentId, attempt + 1), 2000);
            return;
          }
          activity.read("comment-reload:" + post.id() + ":" + rootId, null, null, () -> {
            if (rootId.isBlank()) activity.repo.reloadCommunityComments(post.id());
            else activity.repo.reloadCommunityReplies(post.id(), rootId);
          }, onChanged, () -> {});
        }, () -> {});
  }

  private static void observe(TextView view, Consumer<CharSequence> change) {
    view.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
      @Override public void onTextChanged(CharSequence value, int start, int before, int count) { change.accept(value); }
      @Override public void afterTextChanged(Editable value) {}
    });
  }

  private int dp(int value) { return Ui.dp(activity, value); }
}
