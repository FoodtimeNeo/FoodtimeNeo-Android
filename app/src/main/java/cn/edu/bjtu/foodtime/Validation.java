package cn.edu.bjtu.foodtime;

import java.math.BigDecimal;
import java.util.Locale;

public final class Validation {
  public static String email(String s) {
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
