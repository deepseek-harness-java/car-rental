package cn.xiaofuge.f.app;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 租车助手台 REST 接口。
 * 提供：车辆列表 / 门店列表 / 下单租车 / 订单查询 / 价目查询 / 维保一览 / 运营统计。
 */
@RestController
@RequestMapping("/api")
public class RController {

    private final RStore store;

    public RController(RStore store) {
        this.store = store;
    }

    /** 车辆列表（可按 store/status 过滤） */
    @GetMapping("/cars")
    public Map<String, Object> cars(@RequestParam(required = false) String store,
                                    @RequestParam(required = false) String status) {
        return store == null ? this.store.carList(null, status) : this.store.carList(store, status);
    }

    /** 门店列表 */
    @GetMapping("/stores")
    public Map<String, Object> stores() {
        return this.store.storeList();
    }

    /** 下单租车 */
    @PostMapping("/rent")
    public Map<String, Object> rent(@RequestBody Map<String, Object> body) {
        String customer = String.valueOf(body.getOrDefault("customer", ""));
        String phone = String.valueOf(body.getOrDefault("phone", ""));
        String carId = String.valueOf(body.getOrDefault("carId", ""));
        int days;
        try { days = Integer.parseInt(String.valueOf(body.getOrDefault("days", "1"))); }
        catch (NumberFormatException e) { days = 1; }
        return this.store.rent(customer, phone, carId, days);
    }

    /** 订单查询 */
    @GetMapping("/order")
    public Map<String, Object> order(@RequestParam(required = false) String orderId) {
        return this.store.orderInfo(orderId == null ? "" : orderId);
    }

    /** 价目查询 */
    @GetMapping("/price")
    public Map<String, Object> price(@RequestParam(required = false) String model) {
        return this.store.price(model == null ? "" : model);
    }

    /** 维保一览 */
    @GetMapping("/maintenance")
    public Map<String, Object> maintenance() {
        return this.store.maintenanceList();
    }

    /** 运营统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return this.store.stats();
    }
}
