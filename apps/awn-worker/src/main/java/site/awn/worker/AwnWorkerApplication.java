package site.awn.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AWN Worker 入口。
 *
 * <p>与 API 共享业务模块代码，但只启用后台任务：扫描到期库存预留、
 * 处理订单超时、发送通知、启动履约任务、执行支付/退款对账、
 * 重试失败事件。多个实例并行时必须通过行锁 / 条件更新 / 任务租约
 * 保证不产生重复业务副作用。</p>
 *
 * <p>该进程不暴露 HTTP 业务接口（web-application-type=none），
 * 仅保留 Actuator 管理端点。任务实现全部在各业务模块内，
 * Worker 只负责调度与开关（awn.worker.tasks.*）。</p>
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "site.awn")
@EntityScan(basePackages = "site.awn")
@EnableJpaRepositories(basePackages = "site.awn")
public class AwnWorkerApplication {

    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("awn.worker.enabled", "true");
        SpringApplication.run(AwnWorkerApplication.class, args);
    }
}
