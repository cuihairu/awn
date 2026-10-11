package site.awn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AWN API 入口。
 *
 * <p>只承载对外 HTTP API；后台任务（订单超时、库存释放、对账、通知）
 * 由 awn-worker 进程执行。业务逻辑全部位于 modules/ 下的业务模块中。</p>
 */
@SpringBootApplication
public class AwnApplication {

    public static void main(String[] args) {
        SpringApplication.run(AwnApplication.class, args);
    }
}
