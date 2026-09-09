package cn.ninth.novel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UiFrameworkContractTest {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void shouldKeepElementPlusAsTheOnlyUiComponentFramework() throws IOException {
        String packageJson = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/package.json"));
        String source = Files.readString(PROJECT_ROOT.resolve("novel-agent-web/src/main.ts"));

        System.out.printf("前端组件框架检查：package=%d, main=%d%n", packageJson.length(), source.length());

        assertThat(packageJson)
                .contains("\"vue\"", "\"element-plus\"")
                .doesNotContain(
                        "ant-design-vue",
                        "@arco-design/web-vue",
                        "naive-ui",
                        "vuetify",
                        "quasar"
                );
        assertThat(source)
                .contains("import ElementPlus from 'element-plus'")
                .doesNotContain("ant-design", "naive-ui", "vuetify", "quasar");

        System.out.println("前端组件框架通过：继续使用 Vue 3 + Element Plus，没有引入第二套 UI 框架");
    }
}
