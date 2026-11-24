package cn.edu.bjtu.foodtime.presentation;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.edu.bjtu.foodtime.domain.model.Models.DishSearchRow;

/** Small row renderer retained by favorites and review target selection. */
final class DishSearchRows {
  private DishSearchRows() {}
  static void appendRow(MainActivity activity, LinearLayout results, DishSearchRow dish, Runnable action) {
    String price = dish.price() == null ? "价格待补充"
        : "¥" + dish.price().stripTrailingZeros().toPlainString();
    String rating = dish.averageScore() == null ? "暂无评分"
        : dish.averageScore().stripTrailingZeros().toPlainString() + "分 · " + dish.reviewCount() + "条";
    TextView row = Ui.button(activity, dish.name() + "  " + price + "\n"
        + dish.diningHallName() + " · " + dish.stallName() + " · " + rating, false, action);
    results.addView(row, new LinearLayout.LayoutParams(-1, -2));
    Ui.gap(results, 8);
  }

}
