package cn.edu.bjtu.foodtime.domain.validation;

import java.math.BigDecimal;
import java.util.Locale;

public final class Validation {
  public static String accountEmail(String s) {
    String value=s.trim().toLowerCase(Locale.ROOT);
    if(value.length()>254||!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("请输入账号绑定的完整邮箱");
    return value;
  }
  /** 与后端注册白名单一致；子域（如 vip.qq.com）同样允许。 */
  private static final java.util.Set<String> REGISTRATION_EMAIL_DOMAINS = java.util.Set.of(
      "qq.com", "foxmail.com", "163.com", "126.com", "yeah.net",
      "sina.com", "sina.cn", "sohu.com", "139.com", "189.cn", "wo.cn", "aliyun.com", "21cn.com",
      "gmail.com", "outlook.com", "hotmail.com", "live.com", "msn.com",
      "icloud.com", "me.com", "mac.com", "yahoo.com", "proton.me", "protonmail.com",
      "bjtu.edu.cn");
  public static String email(String s) {
    String value = s.trim().toLowerCase(Locale.ROOT);
    // 与服务端一致：只允许主流邮箱白名单；校园邮箱注册可直接发布内容。
    if (value.length() > 254 || !value.matches(
        "[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]{1,63}(\\.[A-Za-z0-9-]{1,63})+"))
      throw new IllegalArgumentException("请输入有效邮箱");
    String domain=value.substring(value.lastIndexOf('@')+1);
    boolean allowed=false;
    for(String candidate:REGISTRATION_EMAIL_DOMAINS) {
      if(domain.equals(candidate)||domain.endsWith("."+candidate)) {allowed=true;break;}
    }
    if(!allowed) throw new IllegalArgumentException("请使用主流邮箱注册，如 QQ、163、126、Gmail、Outlook 或学校邮箱");
    return value;
  }

  /** 绑定学校邮箱只接受北交数字邮箱。 */
  public static String schoolEmail(String s) {
    String value = s.trim().toLowerCase(Locale.ROOT);
    if (value.length() > 254 || !value.matches("[0-9]+@bjtu\\.edu\\.cn"))
      throw new IllegalArgumentException("请输入学号邮箱：数字@bjtu.edu.cn");
    return value;
  }

  public static void password(String s, boolean complex) {
    if (s.length() < 8 || s.length() > 128 || !s.matches("[!-~]+"))
      throw new IllegalArgumentException("密码须为8–128位英文、数字或半角符号，不能含空格");
    if (complex && (!s.matches(".*[A-Za-z].*") || !s.matches(".*[0-9].*")))
      throw new IllegalArgumentException("密码须同时包含字母和数字");
  }

  public static String text(String s, int max, String label) {
    String v = s.trim();
    if (v.isEmpty() || v.length() > max)
      throw new IllegalArgumentException(label + "须填写，最多" + max + "字");
    return v;
  }

  public static BigDecimal price(String s) {
    try {
      BigDecimal p = new BigDecimal(s.trim());
      if (p.signum() < 0 || p.scale() > 2 || p.compareTo(new BigDecimal("99999999.99")) > 0)
        throw new NumberFormatException();
      return p.setScale(2);
    } catch (Exception e) {
      throw new IllegalArgumentException("价格须为非负金额，最多两位小数");
    }
  }

  private Validation() {}
}
