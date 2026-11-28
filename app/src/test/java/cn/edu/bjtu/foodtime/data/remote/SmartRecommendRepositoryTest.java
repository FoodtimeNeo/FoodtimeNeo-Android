package cn.edu.bjtu.foodtime.data.remote;

import cn.edu.bjtu.foodtime.domain.model.DishTagModels;
import cn.edu.bjtu.foodtime.domain.model.DishTagModels.SmartRecommendQuery;
import cn.edu.bjtu.foodtime.domain.model.SearchModels.Query;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class SmartRecommendRepositoryTest {
  private static String line(InputStream input) throws IOException {
    var bytes = new ByteArrayOutputStream();
    int value;
    while ((value = input.read()) != -1) {
      if (value == 10) break;
      if (value != 13) bytes.write(value);
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  public void loadsDishTagsAndQueriesSmartRecommendationsWithBudgetAndExclusions() throws Exception {
    var recBody = new AtomicReference<String>();
    var searchUri = new AtomicReference<String>();
    try (var server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(10000);
      var executor = Executors.newSingleThreadExecutor();
      try {
        var served = executor.submit(() -> {
          for (int i = 0; i < 4; i++) {
            try (var socket = server.accept()) {
              socket.setSoTimeout(10000);
              var input = socket.getInputStream();
              String path = line(input);
              int length = 0;
              for (String header; (header = line(input)) != null && !header.isEmpty(); ) {
                if (header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                  length = Integer.parseInt(header.substring(15).strip());
                }
              }
              String payload;
              if (path.contains("dishes/tags")) {
                payload = """
                    [{"code":"rice","label":"米饭/盖饭","dimension":"category","dimensionLabel":"主食/品类","sortOrder":10},
                     {"code":"healthy","label":"健康清淡","dimension":"style","dimensionLabel":"饮食偏向","sortOrder":310}]
                    """;
              } else if (path.contains("auth/csrf")) {
                payload = "{\"token\":\"csrf-ok\"}";
              } else if (path.contains("recommendations/dishes/query")) {
                recBody.set(new String(input.readNBytes(length), StandardCharsets.UTF_8));
                payload = """
                    {"exactMode":false,"exactTotal":0,"returnedCount":1,
                     "relaxedHints":["预算上限适当放宽了约 30%","仍严格遵守你的忌口排除条件"],
                     "picked":{"id":"d1","name":"清蒸鸡胸肉饭","price":18,"priceUnit":"serving","imageUrl":"/uploads/a.webp",
                               "stallId":"s1","stallName":"轻食窗","diningHallId":"h1","diningHallName":"留园",
                               "averageScore":4.7,"reviewCount":12,"distanceMeters":null,
                               "tags":["rice","poultry","steamed","healthy"],"matchedTags":["rice"],
                               "missedRequirements":["超出预算 ¥3"],"reasons":["¥18/份"],"matchScore":0.84},
                     "items":[{"id":"d1","name":"清蒸鸡胸肉饭","price":18,"priceUnit":"serving","imageUrl":"/uploads/a.webp",
                               "stallId":"s1","stallName":"轻食窗","diningHallId":"h1","diningHallName":"留园",
                               "averageScore":4.7,"reviewCount":12,"distanceMeters":null,
                               "tags":["rice","poultry","steamed","healthy"],"matchedTags":["rice"],
                               "missedRequirements":["超出预算 ¥3"],"reasons":["¥18/份"],"matchScore":0.84}]}
                    """;
              } else {
                searchUri.set(path);
                payload = """
                    {"items":[{"id":"s1","name":"轻食窗","diningHallId":"h1","diningHallName":"留园","distanceMeters":null,"matchCount":1,
                               "dishes":[{"id":"d1","name":"清蒸鸡胸肉饭","price":14,"priceUnit":"serving","imageUrl":"","averageScore":4.7,"reviewCount":12,"tags":["rice","healthy"]}]}],
                     "total":1,"page":1,"size":10}
                    """;
              }
              byte[] body = ("{\"code\":\"OK\",\"data\":" + payload + "}").getBytes(StandardCharsets.UTF_8);
              var output = socket.getOutputStream();
              output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                  + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
              output.write(body);
            }
          }
          return true;
        });

        String base = "http://127.0.0.1:" + server.getLocalPort() + "/api/v1/";
        var repo = new RemoteRepository(base, new HttpTransport(base));

        List<DishTagModels.DishTag> tags = repo.loadDishTags();
        assertEquals(2, tags.size());
        assertEquals("rice", tags.get(0).code());
        assertEquals("米饭/盖饭", tags.get(0).label());

        var result = repo.recommendDishes(new SmartRecommendQuery(
            List.of("h1"), null, new BigDecimal("15"), true, new BigDecimal("4.0"),
            List.of("rice", "healthy"), List.of("spicy"), List.of("d0"),
            "match", null, null, 12, 5L));
        assertFalse(result.exactMode());
        assertEquals(1, result.items().size());
        assertNotNull(result.picked());
        assertEquals("清蒸鸡胸肉饭", result.picked().name());
        assertEquals(List.of("超出预算 ¥3"), result.picked().missedRequirements());
        assertEquals(2, result.relaxedHints().size());

        var search = repo.searchStalls(new Query(
            "", List.of("h1"), null, new BigDecimal("15"), null,
            "relevance", "all", null, null, 4,
            true, List.of("rice"), List.of("spicy")), 1);
        assertEquals(1, search.items().size());
        assertEquals(List.of("rice", "healthy"), search.items().get(0).dishes().get(0).tags());

        assertTrue(served.get(10, TimeUnit.SECONDS));
        JSONObject sent = new JSONObject(recBody.get());
        assertTrue(sent.getBoolean("portionOnly"));
        assertEquals(5L, sent.getLong("seed"));
        assertEquals("spicy", sent.getJSONArray("excludeTags").getString(0));
        assertEquals("d0", sent.getJSONArray("excludeDishIds").getString(0));
        assertTrue(searchUri.get().contains("portionOnly=true"));
        assertTrue(searchUri.get().contains("tags=rice"));
        assertTrue(searchUri.get().contains("excludeTags=spicy"));
      } finally {
        executor.shutdownNow();
      }
    }
  }
}
