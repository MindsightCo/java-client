package io.mindsight.client;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.uber.profiling.Reporter;
import com.uber.profiling.util.JsonUtils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class JavaClient implements Reporter {
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-9");

    OkHttpClient client = new OkHttpClient();

    String post(String url, String json) throws IOException {
      RequestBody body = RequestBody.create(JSON, json);
      Request request = new Request.Builder()
          .url(url)
          .post(body)
          .build();
      try (Response response = client.newCall(request).execute()) {
        return response.body().string();
      }
    }

    @Override
    public void report(String profilerName, Map<String, Object> metrics) {
        if (profilerName != "Stacktrace") {
            return;
        }

        Object val = metrics.get("stacktrace");
        if (val == null) {
            return;
        }

        List<String> trace = (List<String>) val;

        System.out.println(String.format("stack depth: %d", trace.size()));
    }

    @Override
    public void close() {
    }

    String bowlingJson(String player1, String player2) {
        return "{'winCondition':'HIGH_SCORE',"
            + "'name':'Bowling',"
            + "'round':4,"
            + "'lastSaved':1367702411696,"
            + "'dateStarted':1367702378785,"
            + "'players':["
            + "{'name':'" + player1 + "','history':[10,8,6,7,8],'color':-13388315,'total':39},"
            + "{'name':'" + player2 + "','history':[6,10,5,10,10],'color':-48060,'total':41}"
            + "]}";
    }

    public static void main(String[] args) throws IOException {
        JavaClient example = new JavaClient();
        String json = example.bowlingJson("Jesse", "Jake");
        String response = example.post("http://www.roundsapp.com/post", json);
        System.out.println(response);
    }
}
