package site.awn;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Spring Modulith 边界守卫：模块间只允许 api 包与事件交互，
 * 违反依赖规则（跨模块摸 repository、引用内部实现）会让 verify() 失败。
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(AwnApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        // 产出 target/spring-modulith-docs/（模块图 + 完整依赖关系），供评审与文档引用
        new Documenter(MODULES).writeDocumentation();
    }
}
