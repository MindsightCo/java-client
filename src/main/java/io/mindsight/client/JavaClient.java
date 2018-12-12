package io.mindsight.client;

import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

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

    private List<String> modules;
    private Set<String> entryPoints = new HashSet<String>();
    private String project;
    private String agentRoot;
    private String agentURL;
    private int sendAfter = 100;
    private int numCached = 0;
    private Map<String, Integer> samples = new HashMap<String, Integer>();

    public JavaClient() {
        String modulesParam = System.getenv("MINDSIGHT_MODULES");
        String entryPointsParam = System.getenv("MINDSIGHT_ENTRY_POINTS");
        String sendAfterParam = System.getenv("MINDSIGHT_SEND_AFTER");
        this.project = System.getenv("MINDSIGHT_PROJECT");
        this.agentRoot = System.getenv("MINDSIGHT_AGENT");

        if (modulesParam == null) {
            throw new RuntimeException("Must set MINDSIGHT_MODULES environment variable!");
        } else if (this.project == null) {
            throw new RuntimeException("Must set MINDSIGHT_PROJECT environment variable!");
        } else if (this.agentRoot == null) {
            throw new RuntimeException("Must set MINDSIGHT_AGENT environment variable!");
        }

        if (sendAfterParam != null) {
            this.sendAfter = Integer.parseInt(sendAfterParam);
        }

        if (entryPointsParam != null) {
            for (String e: entryPointsParam.split(",")) {
                this.entryPoints.add(e);
            }
        }

        this.modules = Arrays.asList(modulesParam.split(","));
        this.agentURL = String.format("%s/samples/?project=%s", this.agentRoot, this.project);
    }

    private void sendIfFull() {
        if (this.numCached < this.sendAfter) {
            return;
        }

        String jsonText = "";

        try {
            jsonText = JsonUtils.serialize(this.samples);
        } catch (RuntimeException e) {
            System.out.println("Error serializing Mindsight stats to JSON: " + e);
            return;
        }

        RequestBody body = RequestBody.create(JSON, jsonText);
        Request request = new Request.Builder()
            .url(this.agentURL)
            .post(body)
            .build();
        try (Response response = this.client.newCall(request).execute()) {
            this.numCached = 0;
        } catch (IOException e) {
            System.out.println("Error sending stats to Mindsight agent: " + e);
        }
    }

    private boolean recordedSample(String fn) {
        if (this.entryPoints.contains(fn)) {
            return true; // for now just ignore counting the entry points
        }

        boolean match = false;

        for (String mod: this.modules) {
            if (fn.startsWith(mod)) {
                match = true;
                break;
            }
        }

        if (!match) {
            return false;
        }

        Integer tally = this.samples.get(fn);
        if (tally == null) {
            tally = 1;
        } else {
            tally += 1;
        }

        this.samples.put(fn, tally);
        this.numCached++;
        return true;
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

        for (String fn: trace) {
            if (this.recordedSample(fn)) {
                this.sendIfFull();
                break;
            }
        }
    }

    @Override
    public void close() {
        this.sendAfter = 1; // flush any stats we might have
        this.sendIfFull();
    }
}
