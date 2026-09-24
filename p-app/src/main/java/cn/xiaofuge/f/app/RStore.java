package cn.xiaofuge.f.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** 租车公司数据中心：车辆库/门店/订单/价目/维保/统计 */
@Component
public class RStore {

    /** 车型价目：车型/日租价/押金/说明 */
    static final Map<String, Object[]> RATES = new LinkedHashMap<>();
    static {
        RATES.put("经济轿车", new Object[]{168.0, 2000.0, "日产轩逸/丰田雷凌，5座，油车"});
        RATES.put("舒适SUV", new Object[]{298.0, 3000.0, "本田CR-V/丰田RAV4，5座，油车"});
        RATES.put("新能源", new Object[]{258.0, 3000.0, "比亚迪宋PLUS/特斯拉Model Y，5座，续航500km+"});
        RATES.put("商务MPV", new Object[]{598.0, 5000.0, "别克GL8，7座，适合商务接待"});
        RATES.put("豪华轿车", new Object[]{888.0, 8000.0, "宝马5系/奔驰E级，5座，含免赔保险"});
    }

    public static class Car {
        public String id; public String model; public String plate; public String store;
        public double dailyRate; public double deposit; public String gear; public int seats;
        public String status; // 可租 / 已租出 / 维保中
        public String note;
    }

    public static class Order {
        public String id; public String customer; public String phone; public String carId;
        public String car; public String pickup; public String dropoff;
        public int days; public double total; public String status; // 待取车 / 在租 / 已还车
    }

    public static class Maintenance {
        public String plate; public String car; public String item; public String since; public String note;
    }

    public final List<Car> cars = new ArrayList<>();
    public final List<Order> orders = new ArrayList<>();
    public final List<Maintenance> maintenance = new ArrayList<>();
    public final Map<String, Object[]> stores = new LinkedHashMap<>();
    private int orderSeq = 9001;

    public RStore() { seed(); }

    private void seed() {
        stores.put("S01", new Object[]{"机场店", "浦东机场T2停车场P7", "07:00-22:00"});
        stores.put("S02", new Object[]{"市中心店", "南京西路 188 号", "08:00-20:00"});
        stores.put("S03", new Object[]{"高铁站店", "虹桥站西广场 P9", "07:30-21:30"});

        cars.add(c("C01", "经济轿车", "沪A·7F213", "机场店", 168, 2000, "自动", "可租", "9成新，满油取车"));
        cars.add(c("C02", "舒适SUV", "沪B·3K902", "机场店", 298, 3000, "自动", "可租", "含儿童座椅接口"));
        cars.add(c("C03", "新能源", "沪C·1D556", "市中心店", 258, 3000, "自动", "已租出", "满电取车"));
        cars.add(c("C04", "商务MPV", "沪D·8E118", "市中心店", 598, 5000, "自动", "可租", "航空座椅"));
        cars.add(c("C05", "豪华轿车", "沪E·5A771", "高铁站店", 888, 8000, "自动", "可租", "含免赔保险与代驾券"));
        cars.add(c("C06", "经济轿车", "沪F·2B330", "高铁站店", 168, 2000, "手动", "维保中", "例行保养"));

        orders.add(o("陈先生", "138****6011", "C03", "明天 10:00", "后天 10:00", 2, "在租"));
        orders.add(o("林女士", "139****6022", "C01", "周五 09:00", "周日 09:00", 3, "待取车"));

        maintenance.add(m("沪F·2B330", "经济轿车", "例行保养+更换轮胎", "今天 08:00", "预计明天 12:00 恢复可租"));
    }

    private Car c(String id, String model, String plate, String store, double rate, double deposit,
                  String gear, String status, String note) {
        Car x = new Car(); x.id = id; x.model = model; x.plate = plate; x.store = store;
        x.dailyRate = rate; x.deposit = deposit; x.gear = gear; x.seats = 5;
        if (model.contains("MPV")) x.seats = 7;
        x.status = status; x.note = note; return x;
    }

    private Order o(String customer, String phone, String carId, String pickup, String dropoff, int days, String status) {
        Order x = new Order(); x.id = "O" + orderSeq++; x.customer = customer; x.phone = phone;
        x.carId = carId;
        Car car = cars.stream().filter(cc -> cc.id.equals(carId)).findFirst().orElse(null);
        x.car = car != null ? car.model + " " + car.plate : carId;
        x.pickup = pickup; x.dropoff = dropoff; x.days = days;
        x.total = car != null ? car.dailyRate * days : 0;
        x.status = status; return x;
    }

    private Maintenance m(String plate, String car, String item, String since, String note) {
        Maintenance x = new Maintenance(); x.plate = plate; x.car = car; x.item = item;
        x.since = since; x.note = note; return x;
    }

    /** 车辆列表（可按门店/状态过滤） */
    public Map<String, Object> carList(String store, String status) {
        List<Map<String, Object>> list = cars.stream()
                .filter(x -> store == null || store.isBlank() || x.store.equals(store))
                .filter(x -> status == null || status.isBlank() || x.status.equals(status))
                .map(x -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", x.id); m.put("model", x.model); m.put("plate", x.plate);
                    m.put("store", x.store); m.put("dailyRate", x.dailyRate); m.put("deposit", x.deposit);
                    m.put("gear", x.gear); m.put("seats", x.seats); m.put("status", x.status);
                    m.put("note", x.note); return m; })
                .collect(Collectors.toList());
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", list.size()); r.put("cars", list);
        return r;
    }

    /** 门店列表 */
    public Map<String, Object> storeList() {
        List<Map<String, Object>> list = new ArrayList<>();
        stores.forEach((k, v) -> { Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", k); m.put("name", v[0]); m.put("address", v[1]); m.put("hours", v[2]);
            m.put("available", cars.stream().filter(x -> x.store.equals(v[0]) && "可租".equals(x.status)).count());
            list.add(m); });
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("stores", list);
        return r;
    }

    /** 下单租车：车辆存在 + 可租 校验 */
    public synchronized Map<String, Object> rent(String customer, String phone, String carId, int days) {
        if (customer == null || customer.isBlank() || phone == null || phone.isBlank())
            return Map.of("ok", false, "msg", "请提供租车人姓名和手机号");
        if (days <= 0) return Map.of("ok", false, "msg", "租期至少 1 天");
        Car car = cars.stream().filter(x -> x.id.equalsIgnoreCase(carId)).findFirst().orElse(null);
        if (car == null) return Map.of("ok", false, "msg", "车辆 " + carId + " 不存在，可先查询车辆列表");
        if (!"可租".equals(car.status))
            return Map.of("ok", false, "msg", "「" + car.model + " " + car.plate + "」当前状态：" + car.status + "，不可下单，可换其他车辆");
        car.status = "已租出";
        Order x = new Order(); x.id = "O" + orderSeq++; x.customer = customer; x.phone = phone;
        x.carId = car.id; x.car = car.model + " " + car.plate;
        x.pickup = "明天 09:00"; x.dropoff = "第 " + (days + 1) + " 天 09:00"; x.days = days;
        x.total = car.dailyRate * days; x.status = "待取车";
        orders.add(0, x);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("car", x.car); r.put("store", car.store);
        r.put("pickup", x.pickup); r.put("dropoff", x.dropoff); r.put("days", days);
        r.put("rental", x.total); r.put("deposit", car.deposit);
        r.put("msg", "下单成功，订单号 " + x.id + "，租金 ¥" + x.total + " + 押金 ¥" + car.deposit + "（还车时退还），取车门店：" + car.store);
        return r;
    }

    /** 订单查询 */
    public Map<String, Object> orderInfo(String orderId) {
        Order x = orders.stream().filter(o -> o.id.equalsIgnoreCase(orderId)).findFirst().orElse(null);
        if (x == null) return Map.of("ok", false, "msg", "订单 " + orderId + " 不存在，当前共 " + orders.size() + " 个订单");
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("orderId", x.id); r.put("customer", x.customer); r.put("phone", x.phone);
        r.put("car", x.car); r.put("pickup", x.pickup); r.put("dropoff", x.dropoff);
        r.put("days", x.days); r.put("total", x.total); r.put("status", x.status);
        return r;
    }

    /** 价目查询 */
    public Map<String, Object> price(String model) {
        Object[] rule = RATES.get(model);
        if (rule == null) return Map.of("ok", false, "msg", "车型 " + model + " 不在价目表，可选：" + String.join("/", RATES.keySet()));
        return Map.of("ok", true, "model", model, "dailyRate", (Double) rule[0], "deposit", (Double) rule[1],
                "note", (String) rule[2],
                "msg", model + " ¥" + rule[0] + "/天，押金 ¥" + rule[1] + "，" + rule[2]);
    }

    /** 维保列表 */
    public Map<String, Object> maintenanceList() {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("ok", true); r.put("count", maintenance.size()); r.put("items", maintenance);
        r.put("msg", maintenance.isEmpty() ? "全部车辆正常，无维保中车辆" : "维保中 " + maintenance.size() + " 台，请关注恢复时间");
        return r;
    }

    /** 运营统计 */
    public Map<String, Object> stats() {
        Map<String, Object> byModel = new LinkedHashMap<String, Object>();
        for (String model : RATES.keySet()) {
            long total = cars.stream().filter(x -> x.model.equals(model)).count();
            long avail = cars.stream().filter(x -> x.model.equals(model) && "可租".equals(x.status)).count();
            byModel.put(model, "可租 " + avail + "/" + total);
        }
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("totalCars", cars.size());
        r.put("available", cars.stream().filter(x -> "可租".equals(x.status)).count());
        r.put("rented", cars.stream().filter(x -> "已租出".equals(x.status)).count());
        r.put("maintenance", cars.stream().filter(x -> "维保中".equals(x.status)).count());
        r.put("activeOrders", orders.stream().filter(x -> !"已还车".equals(x.status)).count());
        r.put("utilization", Math.round(cars.stream().filter(x -> "已租出".equals(x.status)).count() * 1000.0 / cars.size()) / 10.0 + "%");
        r.put("byModel", byModel);
        r.put("advice", "C03 在租中；C06 维保预计明天恢复；豪华轿车含免赔适合接待场景可主推；机场店仅剩 2 台可租，建议引导部分客户至市中心店");
        return r;
    }
}
