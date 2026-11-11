package cn.edu.bjtu.foodtime.domain.model;

/** Absolute ISO instants; an optional expiresOn is interpreted by the server as an inclusive UTC+8 day. */
public record NoticeSchedule(String publishedAt,String displayStartsAt,String displayEndsAt,String expiresOn,
                             boolean isUpdate,boolean published,boolean enabled) {
  public NoticeSchedule {
    publishedAt=publishedAt==null?"":publishedAt;displayStartsAt=displayStartsAt==null?"":displayStartsAt;
    displayEndsAt=displayEndsAt==null?"":displayEndsAt;expiresOn=expiresOn==null?"":expiresOn;
  }
}
