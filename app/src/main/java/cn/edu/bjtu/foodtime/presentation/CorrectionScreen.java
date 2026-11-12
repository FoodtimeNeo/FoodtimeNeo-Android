package cn.edu.bjtu.foodtime.presentation;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.CatalogCorrection;
import cn.edu.bjtu.foodtime.domain.model.Models.CatalogCorrectionAdmin;
import cn.edu.bjtu.foodtime.domain.model.Models.CatalogCorrectionAdminPage;
import cn.edu.bjtu.foodtime.domain.model.Models.CatalogCorrectionPage;
import cn.edu.bjtu.foodtime.domain.model.Models.CorrectionProposal;
import cn.edu.bjtu.foodtime.domain.model.Models.Dish;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** User-facing catalog correction form and personal proposal list. */
final class CorrectionScreen {
  private CorrectionScreen() {}

  static void submit(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    Dish dish = activity.repo.dish(route.id());
    if (dish == null || !dish.active()) throw new IllegalArgumentException("菜品不存在或已下架");
    activity.heading(page, "01.4", "目录纠错", dish.name());
    if (dish.revision() <= 0) {
      page.addView(Ui.text(activity, "目录版本缺失，请返回详情刷新后重试。", 14, Design.RED));
      return;
    }
    TextView error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    page.addView(error);

    CheckBox nameCheck = check(activity, page, "更正名称");
    EditText name = Ui.field(page, "更正后的名称", false, false);
    name.setText(dish.name());

    CheckBox descriptionCheck = check(activity, page, "更正文字介绍");
    EditText description = Ui.field(page, "更正后的介绍，留空表示清空", false, true);
    description.setText(dish.description() == null ? "" : dish.description());

    CheckBox priceCheck = check(activity, page, "更正价格");
    EditText price = Ui.field(page, "以元为单位；留空表示价格未知", false, false);
    price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    price.setText(dish.price() == null ? "" : dish.price().stripTrailingZeros().toPlainString());

    EvidenceSlots.show(activity, page, "correction-evidence:" + dish.id());
    Ui.gap(page, 8);
    EditText reason = Ui.field(page, "更正理由", false, true);
    reason.setHint("例如：食堂菜单已经更新");
    Ui.gap(page, 12);

    String idempotencyKey = UUID.randomUUID().toString();
    TextView submit = Ui.button(activity, "提交纠错", true, () -> {});
    submit.setOnClickListener(view -> {
      List<String> fields = new ArrayList<>();
      if (nameCheck.isChecked()) fields.add("name");
      if (descriptionCheck.isChecked()) fields.add("description");
      if (priceCheck.isChecked()) fields.add("price");
      if (fields.isEmpty()) {
        error.setText("请至少勾选一个要更正的字段");
        return;
      }
      String reasonText = reason.getText().toString().strip();
      if (reasonText.isEmpty()) {
        error.setText("请填写更正理由");
        return;
      }
      BigDecimal proposedPrice = null;
      if (priceCheck.isChecked() && !price.getText().toString().strip().isEmpty()) {
        try {
          proposedPrice = new BigDecimal(price.getText().toString().strip());
          if (proposedPrice.signum() < 0) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
          error.setText("价格必须为非负数");
          return;
        }
      }
      final BigDecimal finalPrice = proposedPrice;
      activity.request(submit, error, () -> {
        var evidence = EvidenceSlots.upload(activity, "correction-evidence:" + dish.id());
        CorrectionProposal proposal = new CorrectionProposal("dish", dish.id(), dish.revision(),
            fields, name.getText().toString(), description.getText().toString(), finalPrice,
            reasonText, evidence, idempotencyKey);
        activity.repo.submitCorrection(proposal);
      }, () -> {
        activity.message("纠错提案已提交，等待管理员核对");
        activity.back();
      });
    });
    page.addView(submit);
    Ui.gap(page, 8);
    page.addView(Ui.text(activity, "每个认证用户每天最多提交 5 单；最多 3 张证据。", 12, Design.MUTED));
  }

  static void mine(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    activity.heading(page, "01.5", "我的纠错", "名称、价格与介绍的更正进度");
    LinearLayout rows = Ui.col(activity);
    TextView status = Ui.text(activity, "正在加载…", 14, Design.MUTED);
    TextView error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    page.addView(status);
    page.addView(error);
    page.addView(rows);
    load(activity, rows, status, error, 1);
  }

  private static void load(MainActivity activity, LinearLayout rows, TextView status, TextView error, int page) {
    AtomicReference<CatalogCorrectionPage> result = new AtomicReference<>();
    activity.read("catalog-corrections:" + page, null, error,
        () -> result.set(activity.repo.myCorrections(page)), () -> {
          rows.removeAllViews();
          CatalogCorrectionPage data = result.get();
          status.setText(data.items().isEmpty() ? "还没有纠错提案。" : "共 " + data.total() + " 条");
          for (CatalogCorrection item : data.items()) card(activity, rows, item);
          if (data.hasMore()) Ui.action(rows, "加载更多", false,
              () -> load(activity, rows, status, error, page + 1));
        }, () -> {});
  }

  static void admin(MainActivity activity, LinearLayout page, MainActivity.Route route) {
    activity.heading(page, "04.1", "纠错审核", "目录名称、介绍与价格的整单核对");
    LinearLayout rows = Ui.col(activity);
    TextView status = Ui.text(activity, "正在加载…", 14, Design.MUTED);
    TextView error = Ui.text(activity, "", 14, Design.RED);
    Ui.observeError(error);
    page.addView(status);
    page.addView(error);
    page.addView(rows);
    loadAdmin(activity, rows, status, error, 1);
  }

  private static void loadAdmin(MainActivity activity, LinearLayout rows, TextView status, TextView error, int page) {
    AtomicReference<CatalogCorrectionAdminPage> result = new AtomicReference<>();
    activity.read("admin-catalog-corrections:" + page, null, error,
        () -> result.set(activity.repo.adminCorrections("pending", page)), () -> {
          rows.removeAllViews();
          CatalogCorrectionAdminPage data = result.get();
          status.setText(data.items().isEmpty() ? "没有待核对纠错。" : "待核对 " + data.total() + " 条");
          for (CatalogCorrectionAdmin item : data.items()) adminCard(activity, rows, status, error, item);
          if (data.hasMore()) Ui.action(rows, "加载更多", false,
              () -> loadAdmin(activity, rows, status, error, page + 1));
        }, () -> {});
  }

  private static void adminCard(MainActivity activity, LinearLayout rows, TextView status, TextView error,
      CatalogCorrectionAdmin admin) {
    CatalogCorrection item = admin.correction();
    LinearLayout card = Ui.card(rows);
    card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
    card.addView(Ui.title(activity, item.targetSummary(), 17));
    card.addView(Ui.text(activity, admin.submitterName() + " · " + SubmissionScreen.date(item.createdAt()),
        12, Design.MUTED));
    List<String> changes = new ArrayList<>();
    for (String field : item.changedFields()) changes.add(changeText(field, item));
    card.addView(Ui.text(activity, String.join("\n", changes), 14, Design.INK));
    card.addView(Ui.text(activity, "理由：" + item.reason(), 13, Design.MUTED));
    card.setOnClickListener(view -> decisionDialog(activity, status, error, admin));
  }

  private static void decisionDialog(MainActivity activity, TextView status, TextView error,
      CatalogCorrectionAdmin admin) {
    CatalogCorrection item = admin.correction();
    EditText note = new EditText(activity);
    note.setHint("审核说明（可选）");
    note.setSingleLine(false);
    LinearLayout content = Ui.col(activity);
    content.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), 0);
    for (String field : item.changedFields())
      content.addView(Ui.text(activity, changeText(field, item), 14, Design.INK));
    content.addView(Ui.text(activity, "理由：" + item.reason(), 13, Design.MUTED));
    content.addView(note);
    new AlertDialog.Builder(activity).setTitle("审核纠错").setView(content)
        .setNegativeButton("取消", null)
        .setNeutralButton("拒绝", (dialog, which) -> decide(activity, status, error, item, "rejected", note))
        .setPositiveButton("通过并应用", (dialog, which) -> decide(activity, status, error, item, "approved", note))
        .show();
  }

  private static void decide(MainActivity activity, TextView status, TextView error, CatalogCorrection item,
      String decision, EditText note) {
    activity.request(null, error, () -> activity.repo.decideCorrection(item.id(), decision,
            note.getText().toString().strip(), item.revision()),
        () -> {
          activity.message("approved".equals(decision) ? "已通过并应用" : "已拒绝");
          activity.render();
        });
  }

  private static void card(MainActivity activity, LinearLayout rows, CatalogCorrection item) {
    LinearLayout card = Ui.card(rows);
    card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
    card.addView(Ui.title(activity, item.targetSummary(), 17));
    card.addView(Ui.text(activity, statusLabel(item.status()) + " · " + SubmissionScreen.date(item.createdAt()),
        12, Design.MUTED));
    Ui.gap(card, 6);
    for (String field : item.changedFields()) card.addView(Ui.text(activity, changeText(field, item), 14, Design.INK));
    card.addView(Ui.text(activity, "理由：" + item.reason(), 13, Design.MUTED));
    if (item.reviewNote() != null && !item.reviewNote().isBlank())
      card.addView(Ui.text(activity, "审核说明：" + item.reviewNote(), 13, Design.MUTED));
  }

  private static String statusLabel(String status) {
    return switch (status) {
      case "approved" -> "已通过";
      case "rejected" -> "未通过";
      default -> "待核对";
    };
  }

  private static String changeText(String field, CatalogCorrection item) {
    return switch (field) {
      case "name" -> "名称：" + item.originalName() + " → " + item.proposedName();
      case "description" -> "介绍：" + (item.originalDescription() == null ? "（空）" : item.originalDescription())
          + " → " + (item.proposedDescription() == null ? "（空）" : item.proposedDescription());
      case "price" -> "价格：" + price(item.originalPrice()) + " → " + price(item.proposedPrice());
      default -> field;
    };
  }

  private static String price(BigDecimal value) {
    return value == null ? "未知" : "¥" + value.stripTrailingZeros().toPlainString();
  }

  private static CheckBox check(MainActivity activity, LinearLayout page, String label) {
    CheckBox box = new CheckBox(activity);
    box.setText(label);
    box.setTextColor(Design.INK);
    box.setTextSize(15);
    page.addView(box);
    return box;
  }
}
