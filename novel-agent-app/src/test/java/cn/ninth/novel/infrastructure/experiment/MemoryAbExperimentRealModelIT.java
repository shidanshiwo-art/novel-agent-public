package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 显式开启后才运行的真实 A/B 实验入口。
 * 章纲冻结失败时不会创建目标项目，也不会调用章节模型。
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class MemoryAbExperimentRealModelIT {

    @Autowired
    private MemoryAbExperimentHarness harness;

    @Test
    void runFrozenMemoryAbExperiment() {
        if (!"true".equalsIgnoreCase(System.getProperty("runMemoryAbExperiment"))) {
            System.out.println(
                    "Memory A/B real-model experiment is disabled; "
                            + "use -DrunMemoryAbExperiment=true explicitly.");
            return;
        }

        MemoryAbExperimentManifest manifest = harness.freezeBaseline("novel-001");
        MemoryAbIsolationReport isolation = harness.cloneEnvironments(manifest);
        MemoryAbExperimentReport report = harness.run(manifest);

        System.out.println("========== MEMORY A/B MANIFEST ==========");
        System.out.println(harness.toJson(new MemoryAbExperimentReport(manifest, java.util.List.of())));
        System.out.println("========== MEMORY A/B ISOLATION ==========");
        System.out.println(harness.toJson(isolation));
        System.out.println("========== MEMORY A/B RESULTS ==========");
        System.out.println(harness.toJson(report));
        System.out.println("========== END MEMORY A/B RESULTS ==========");
    }
}
