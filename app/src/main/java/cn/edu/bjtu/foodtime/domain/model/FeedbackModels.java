package cn.edu.bjtu.foodtime.domain.model;

import java.util.List;

public final class FeedbackModels {
  public record Feedback(String id,String type,String content,String status,long createdAt,long updatedAt,
                         String userDisplayName,String latestReply) {}
  public record Reply(String id,String content,long createdAt) {}
  public record Page(List<Feedback> items,long total,int page,int size) {
    public Page {items=List.copyOf(items);}
    public boolean hasMore(){return (long)page*size<total;}
  }
  public record ReplyPage(List<Reply> items,long total,int page,int size) {
    public ReplyPage {items=List.copyOf(items);}
    public boolean hasMore(){return (long)page*size<total;}
  }
  public record Export(String filename,String csv,int count) {}
  public record ImportIssue(int row,String message) {}
  public record ImportResult(int updated,int skipped,List<ImportIssue> issues) {
    public ImportResult {issues=List.copyOf(issues);}
  }
  public static final List<String> TYPES=List.of("function","content","usage","suggestion","other");
  public static String typeLabel(String value){return switch(value){case "function"->"功能反馈";case "content"->"内容反馈";case "usage"->"使用反馈";case "suggestion"->"改进建议";default->"其他";};}
  public static String statusLabel(String value){return switch(value){case "processing"->"处理中";case "completed"->"已完成";default->"待处理";};}
  private FeedbackModels(){}
}
