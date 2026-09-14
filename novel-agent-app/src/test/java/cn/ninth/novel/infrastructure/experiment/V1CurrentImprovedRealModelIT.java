package cn.ninth.novel.infrastructure.experiment;

import cn.ninth.novel.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 显式开启后执行 V1-current/V1-improved 真实章节对照。
 * 两组都使用 MemoryMode.V1；本用例只打印机器可读结果，不对模型文本做 Assert。
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("dev")
class V1CurrentImprovedRealModelIT {

    @Autowired
    private MemoryAbExperimentHarness harness;

    @Test
    void runFrozenV1Comparison() {
        if (!"true".equalsIgnoreCase(System.getProperty("runV1Comparison"))) {
            System.out.println(
                    "V1 current/improved real-model comparison is disabled; "
                            + "use -DrunV1Comparison=true explicitly.");
            return;
        }

        V1ComparisonManifest manifest = harness.freezeV1Comparison("novel-001");
        V1ComparisonIsolationReport isolation =
                harness.cloneV1ComparisonEnvironments(manifest);
        V1ComparisonReport report = harness.runV1Comparison(manifest);

        System.out.println("========== V1 COMPARISON MANIFEST ==========");
        System.out.println(harness.toJson(manifest));
        System.out.println("========== V1 COMPARISON ISOLATION ==========");
        System.out.println(harness.toJson(isolation));
        System.out.println("========== V1 COMPARISON RESULTS ==========");
        System.out.println(harness.toJson(report));
        System.out.println("========== END V1 COMPARISON RESULTS ==========");
    }
}
