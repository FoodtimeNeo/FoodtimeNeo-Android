package cn.edu.bjtu.foodtime.domain.repository;

import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecision;
import cn.edu.bjtu.foodtime.domain.model.Models.ModerationDecisionPage;
import cn.edu.bjtu.foodtime.domain.model.Models.ReportAction;
import java.util.List;

/** Recipient-owned decisions and appeals, independent of catalog and staff management. */
public interface AppealRepository {
  default ModerationDecision myModerationDecision(String id){throw new UnsupportedOperationException();}
  default ModerationDecisionPage myModerationDecisions(int page){throw new UnsupportedOperationException();}
  default List<ReportAction> decisionActions(String id){throw new UnsupportedOperationException();}
  default void submitAppeal(String decision,String content,String requestId){submitAppeal(decision,content,requestId,List.of());}
  default void submitAppeal(String decision,String content,String requestId,List<String> evidence){throw new UnsupportedOperationException();}
}
