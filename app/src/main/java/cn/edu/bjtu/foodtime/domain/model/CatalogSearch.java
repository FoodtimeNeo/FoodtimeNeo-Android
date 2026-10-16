package cn.edu.bjtu.foodtime.domain.model;

import java.text.Normalizer;
import java.util.Locale;

/** Local name matching only; does not imply a server-side or cross-canteen search API. */
public final class CatalogSearch {
  private CatalogSearch() {}

  public static boolean matches(String name, String query) {
    String normalizedName = normalize(name);
    String normalizedQuery = normalize(query).trim();
    if (normalizedQuery.isEmpty()) return true;
    for (String term : normalizedQuery.split("\\s+"))
      if (!normalizedName.contains(term)) return false;
    return true;
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT);
  }
}
