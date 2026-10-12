package cn.edu.bjtu.foodtime;

import static org.junit.Assert.*;

import org.junit.Test;

public class ValidationTest {
  @Test
  public void emailNormalizesAndKeepsLeadingZeros() {
    assertEquals("00123@bjtu.edu.cn", Validation.email(" 00123@BJTU.EDU.CN "));
  }

  @Test
  public void emailRejectsLookalikeDomains() {
    for (String s :
        new String[] {
          "abc@bjtu.edu.cn", "123@bjtu.edu.cn.evil.com", "123@example.com", "１２３@bjtu.edu.cn"
        }) {
      try {
        Validation.email(s);
        fail(s);
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void passwordRulesPreserveOriginalInput() {
    Validation.password("Foodtime2026!", true);
    for (String s : new String[] {"abcdefgh", "12345678", "Abcdef12 ", "密码123456", "Ab1"}) {
      try {
        Validation.password(s, true);
        fail(s);
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void priceUsesExactDecimals() {
    assertEquals("0.00", Validation.price("0").toPlainString());
    assertEquals("12.30", Validation.price("12.30").toPlainString());
    for (String s : new String[] {"-1", "1.234", "NaN", "999999999"}) {
      try {
        Validation.price(s);
        fail(s);
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void saltedPasswordsAndWrongPassword() {
    String h = Passwords.hash("Foodtime2026!");
    assertTrue(Passwords.matches("Foodtime2026!", h));
    assertFalse(Passwords.matches("foodtime2026!", h));
    assertNotEquals(h, Passwords.hash("Foodtime2026!"));
    assertFalse(Passwords.matches("a", "corrupt"));
  }
}
