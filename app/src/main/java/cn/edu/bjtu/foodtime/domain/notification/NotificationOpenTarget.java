package cn.edu.bjtu.foodtime.domain.notification;

import java.util.Set;

/** Safe navigation data only; owner identity is checked after login, never inferred from notification text. */
public record NotificationOpenTarget(String actor,String category,String refType,String refId) {
  public record Destination(String kind,String id,String inboxTab){}
  public static NotificationOpenTarget parse(String actor,String category,String type,String id){
    if(!uuid(actor)||category==null||!Set.of("message","interaction","notice","feedback","correction","report","appeal").contains(category))return null;
    return new NotificationOpenTarget(actor,category,type==null?"":type,uuid(id)?id:"");
  }
  public Destination destination(){
    return switch(category){
      case "message" -> new Destination("inbox","","chat");
      case "notice" -> new Destination("inbox","","notice");
      case "feedback" -> refType.equals("feedback")&&!refId.isEmpty()?new Destination("feedback-detail",refId,""):inbox();
      case "correction" -> new Destination("my-corrections","","");
      case "report" -> refType.equals("report")&&!refId.isEmpty()?new Destination("report-followups",refId,"")
          :refType.equals("moderation-decision")?new Destination("report-receipts","decisions",""):inbox();
      default -> inbox();
    };
  }
  private static Destination inbox(){return new Destination("inbox","","message");}
  private static boolean uuid(String id){return id!=null&&id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");}
}
