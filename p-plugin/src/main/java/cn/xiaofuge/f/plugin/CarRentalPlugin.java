package cn.xiaofuge.f.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 租车管家插件：把 car-rental REST API 注册为 DSH Agent 工具 */
public class CarRentalPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "rental-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public CarRentalPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new CarListTool(),
                new StoreListTool(),
                new RentTool(),
                new OrderInfoTool(),
                new PriceTool(),
                new MaintenanceTool(),
                new StatsTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("rental-capabilities", 20, """
                ## AI 租车管家（连锁租车门店运营 · 2026-09-25）
                - 查车辆 → car_list（可按门店/状态过滤：可租/已租出/维保中；报车型/车牌/日租价/押金/门店）
                - 查门店 → store_list（三家门店地址与营业时间、各店可租数）
                - 下单租车 → rent（customer/phone/carId/days 必填；仅"可租"状态可下单；
                  报订单号/租金/押金/取还车时间；下单前必须复述车辆、租期、租金与押金请客户确认）
                - 订单查询 → order_info（orderId：车辆/取还车时间/天数/金额/状态）
                - 价目 → price（车型：经济轿车/舒适SUV/新能源/商务MPV/豪华轿车；报日租价、押金与说明）
                - 维保 → maintenance（维保中车辆/项目/预计恢复时间）
                - 问运营 → stats（可租数/出租率/在租订单/分车型余量/调度建议）
                - 回答要求：
                  1) 下单前必须复述要素（车辆/租期/租金/押金/门店）请客户确认
                  2) 下单结果必报订单号、租金、押金与取车门店
                  3) 已租出/维保中给替代建议（同车型其他车或其他门店）
                  4) 押金在还车验车后退还；保险与事故处理只转述条款说明，不自行承诺；数据来自工具返回，禁止编造
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: rental tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("RENTAL_APP_BASE_URL", "http://127.0.0.1:18104")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class CarListTool extends AbstractTool {
        @Override public String name() { return "car_list"; }
        @Override public String description() {
            return "车辆列表：车型/车牌/所属门店/日租价/押金/挡位/座位数/状态（可租、已租出、维保中）。"
                    + "可按 store（机场店/市中心店/高铁站店）与 status 过滤。推荐车辆、下单前必查。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("store", stringSchema("门店名：机场店 / 市中心店 / 高铁站店，可空"))
                    .prop("status", stringSchema("状态过滤：可租 / 已租出 / 维保中，可空"))
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String q = "";
            if (!str(args, "store").isBlank()) q += "store=" + java.net.URLEncoder.encode(str(args, "store"), StandardCharsets.UTF_8) + "&";
            if (!str(args, "status").isBlank()) q += "status=" + java.net.URLEncoder.encode(str(args, "status"), StandardCharsets.UTF_8) + "&";
            return ok(get("/api/cars" + (q.isEmpty() ? "" : "?" + q), args));
        }
    }

    private class StoreListTool extends AbstractTool {
        @Override public String name() { return "store_list"; }
        @Override public String description() {
            return "门店列表：门店名/地址/营业时间/当前可租车辆数。客户问在哪取车、选门店时调用。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stores", args));
        }
    }

    private class RentTool extends AbstractTool {
        @Override public String name() { return "rent"; }
        @Override public String description() {
            return "下单租车：customer（租车人）/phone（手机号）/carId（C01-C06）/days（天数）必填。"
                    + "仅'可租'状态可下单；已租出/维保中会被拦截。必须先复述车辆、租期、租金与押金经客户确认后才能调用。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("customer", stringSchema("租车人姓名"))
                    .prop("phone", stringSchema("租车人手机号"))
                    .prop("carId", stringSchema("车辆编号 C01-C06"))
                    .prop("days", stringSchema("租期天数，正整数"))
                    .required("customer", "phone", "carId", "days")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String body = "{\"customer\":\"" + json(str(args, "customer"))
                    + "\",\"phone\":\"" + json(str(args, "phone"))
                    + "\",\"carId\":\"" + json(str(args, "carId"))
                    + "\",\"days\":\"" + json(str(args, "days")) + "\"}";
            return ok(post("/api/rent", body, args));
        }
    }

    private class OrderInfoTool extends AbstractTool {
        @Override public String name() { return "order_info"; }
        @Override public String description() {
            return "订单查询：orderId 必填（O9001 格式）。返回车辆/取还车时间/天数/金额/状态。"
                    + "何时必须调用：客户问订单、问取车安排。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("orderId", stringSchema("订单号，如 O9001"))
                    .required("orderId")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/order?orderId=" + java.net.URLEncoder.encode(str(args, "orderId"), StandardCharsets.UTF_8), args));
        }
    }

    private class PriceTool extends AbstractTool {
        @Override public String name() { return "price"; }
        @Override public String description() {
            return "车型价目：model 必填。价目：经济轿车 168/舒适SUV 298/新能源 258/商务MPV 598/豪华轿车 888（元/天）。"
                    + "返回日租价、押金与说明。何时必须调用：问价格、下单前报价。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("model", stringSchema("车型：经济轿车 / 舒适SUV / 新能源 / 商务MPV / 豪华轿车"))
                    .required("model")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/price?model=" + java.net.URLEncoder.encode(str(args, "model"), StandardCharsets.UTF_8), args));
        }
    }

    private class MaintenanceTool extends AbstractTool {
        @Override public String name() { return "maintenance"; }
        @Override public String description() {
            return "维保查询：维保中车辆/车牌/项目/开始时间/预计恢复时间。何时必须调用：问车辆为什么不可租、问维保安排。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/maintenance", args));
        }
    }

    private class StatsTool extends AbstractTool {
        @Override public String name() { return "stats"; }
        @Override public String description() {
            return "运营统计：总车辆/可租数/已租出/维保中/在租订单/出租率/分车型余量/调度建议。"
                    + "何时必须调用：问今天运营、问出租率与调度。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/stats", args));
        }
    }
}
