package cn.ninth.novel.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChildPomVersionPolicyTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final List<String> MODULES = List.of(
            "novel-agent-api",
            "novel-agent-app",
            "novel-agent-domain",
            "novel-agent-trigger",
            "novel-agent-infrastructure",
            "novel-agent-types");

    @Test
    void childModulesMustUseOnlyDependenciesManagedByRootPom() throws Exception {
        Set<String> managedDependencies = rootManagedDependencies();

        for (String module : MODULES) {
            var document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(ROOT.resolve(module).resolve("pom.xml").toFile());
            Element project = document.getDocumentElement();

            assertFalse(hasDirectChild(project, "dependencyManagement"), module);
            assertFalse(hasDirectChild(project, "properties"), module);

            for (String elementName : List.of("dependency", "plugin")) {
                var nodes = project.getElementsByTagName(elementName);
                for (int index = 0; index < nodes.getLength(); index++) {
                    assertFalse(
                            hasDirectChild((Element) nodes.item(index), "version"),
                            module + " contains a managed version in " + elementName);
                }
            }

            var dependencies = project.getElementsByTagName("dependency");
            for (int index = 0; index < dependencies.getLength(); index++) {
                Element dependency = (Element) dependencies.item(index);
                String coordinate = childText(dependency, "groupId")
                        + ":" + childText(dependency, "artifactId");
                assertTrue(
                        managedDependencies.contains(coordinate),
                        module + " dependency is not managed by root pom: " + coordinate);
            }
        }
    }

    private Set<String> rootManagedDependencies() throws Exception {
        var document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(ROOT.resolve("pom.xml").toFile());
        Element dependencyManagement = directChild(
                document.getDocumentElement(), "dependencyManagement");
        Set<String> coordinates = new HashSet<>();
        var dependencies = dependencyManagement.getElementsByTagName("dependency");
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            String coordinate = childText(dependency, "groupId")
                    + ":" + childText(dependency, "artifactId");
            assertTrue(
                    hasDirectChild(dependency, "version"),
                    "root managed dependency must declare a version: " + coordinate);
            coordinates.add(coordinate);
        }
        return coordinates;
    }

    private String childText(Element parent, String name) {
        return directChild(parent, name).getTextContent().trim();
    }

    private Element directChild(Element parent, String name) {
        var children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child
                    && name.equals(child.getTagName())) {
                return child;
            }
        }
        throw new IllegalArgumentException("Missing child element: " + name);
    }

    private boolean hasDirectChild(Element parent, String name) {
        var children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child
                    && name.equals(child.getTagName())) {
                return true;
            }
        }
        return false;
    }
}
