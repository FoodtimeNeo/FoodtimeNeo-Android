package cn.edu.bjtu.foodtime.presentation;

import android.widget.LinearLayout;
import cn.edu.bjtu.foodtime.domain.model.Models.AiAssessment;
import java.math.RoundingMode;

/** Read-only server assessment. The moderator keeps the original approve/reject controls. */
final class AiAssessmentView {
  static void show(MainActivity a,LinearLayout parent,AiAssessment assessment) {
    Ui.gap(parent,12);
    if(assessment==null||assessment.riskLevel().isBlank()) {
      parent.addView(Ui.text(a,"AI 分诊：暂无结论",14,Design.MUTED));return;
    }
    LinearLayout box=Ui.col(a);box.setPadding(Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12));
    box.setBackground(Ui.shape(a,Design.ALT,Design.BORDER,16));parent.addView(box);
    String level=switch(assessment.riskLevel()) {case "LOW" -> "低风险";case "MEDIUM" -> "需复核";case "HIGH" -> "高风险";default -> "未知风险";};
    String confidence=assessment.confidence()==null?"":" · 置信度 "+assessment.confidence().movePointRight(2).setScale(0,RoundingMode.HALF_UP).toPlainString()+"%";
    int color=switch(assessment.riskLevel()) {case "LOW" -> Design.GREEN;case "HIGH" -> Design.RED;default -> Design.BLUE;};
    box.addView(Ui.text(a,"AI 分诊："+level+confidence,14,color));
    if(!assessment.labels().isEmpty()) box.addView(Ui.text(a,"标签："+String.join("、",assessment.labels()),14,Design.INK));
    if(!assessment.reason().isBlank()) box.addView(Ui.text(a,"说明："+assessment.reason(),14,Design.INK));
    if(assessment.checkedAt()>0) box.addView(Ui.text(a,"诊断时间："+SubmissionScreen.date(assessment.checkedAt()),14,Design.MUTED));
    box.addView(Ui.text(a,"仅供参考，请结合原文审核。",14,Design.MUTED));
  }
}
