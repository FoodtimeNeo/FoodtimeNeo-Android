package cn.edu.bjtu.foodtime.data.remote;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import cn.edu.bjtu.foodtime.domain.model.Models.DishSearchFilters;
import org.junit.Test;
import static org.junit.Assert.*;

public class DishSearchRepositoryTest {
  @Test public void sendsOneEncodedSearchAndParsesPageWithoutFetchingImages() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(5000);
      var executor = Executors.newSingleThreadExecutor();
      try {
        var request = executor.submit(() -> {
          try (var socket = server.accept()) {
            socket.setSoTimeout(5000);
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String first = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {}
            byte[] body = """
                {"code":"OK","data":{"items":[{"id":"dish","name":"鸡腿饭","price":12.30,
                "stallName":"二号档口","diningHallName":"学苑","averageScore":4.5,"reviewCount":8}],
                "page":2,"size":20,"total":41}}
                """.getBytes(StandardCharsets.UTF_8);
            var output = socket.getOutputStream();
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            return first;
          }
        });
        String base = "http://127.0.0.1:" + server.getLocalPort() + "/api/v1/";
        var repo = new RemoteRepository(base, new HttpTransport(base));
        var result = repo.searchDishes("鸡腿&%", 2);
        assertEquals("GET /api/v1/dishes/search?q=%E9%B8%A1%E8%85%BF%26%25&page=2&size=20&sort=relevance HTTP/1.1",
            request.get(5, TimeUnit.SECONDS));
        assertEquals(2, result.page());
        assertTrue(result.hasMore());
        var dish = result.items().get(0);
        assertEquals("学苑", dish.diningHallName());
        assertEquals("二号档口", dish.stallName());
        assertEquals(12.30, dish.price().doubleValue(), 0.001);
        assertEquals(4.5, dish.averageScore().doubleValue(), 0.001);
        assertEquals(8, dish.reviewCount());
      } finally {
        executor.shutdownNow();
      }
    }
  }

  @Test public void sendsFiltersAndSortInTheWhitelistedForm() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(5000);
      var executor = Executors.newSingleThreadExecutor();
      try {
        var request = executor.submit(() -> {
          try (var socket = server.accept()) {
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String first = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {}
            byte[] body = "{\"code\":\"OK\",\"data\":{\"items\":[],\"page\":1,\"size\":20,\"total\":0}}"
                .getBytes(StandardCharsets.UTF_8);
            var output = socket.getOutputStream();
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            return first;
          }
        });
        String base = "http://127.0.0.1:" + server.getLocalPort() + "/api/v1/";
        var repo = new RemoteRepository(base, new HttpTransport(base));
        repo.searchDishes("", 1, new DishSearchFilters(List.of("hall-1", "hall-2"),
            new BigDecimal("10"), new BigDecimal("30"), new BigDecimal("4.0"), "price_desc"));
        String line = request.get(5, TimeUnit.SECONDS);
        assertTrue(line.contains("diningHallIds=hall-1,hall-2"));
        assertTrue(line.contains("minPrice=10"));
        assertTrue(line.contains("maxPrice=30"));
        assertTrue(line.contains("minRating=4.0"));
        assertTrue(line.contains("sort=price_desc"));
      } finally {
        executor.shutdownNow();
      }
    }
  }
}
