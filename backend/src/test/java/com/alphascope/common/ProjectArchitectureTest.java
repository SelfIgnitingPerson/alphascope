package com.alphascope.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/**
 * 使用 JDK 语法树检查项目分层和公开契约，防止清理后重新引入越层依赖、注解 SQL 和长 HTTP 参数列表。
 *
 * @author chenxuefeng
 */
class ProjectArchitectureTest {
  @Test
  void sourceBoundariesAndPublicContractsStayConsistent() throws Exception {
    Path main = Path.of("src/main/java");
    if (!Files.isDirectory(main)) main = Path.of("backend/src/main/java");
    List<Path> sources;
    try (var paths = Files.walk(main)) {
      sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
    }
    var violations = new ArrayList<String>();
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var manager = compiler.getStandardFileManager(null, null, null)) {
      var task =
          (JavacTask)
              compiler.getTask(
                  null,
                  manager,
                  null,
                  List.of("-proc:none"),
                  null,
                  manager.getJavaFileObjectsFromPaths(sources));
      var docs = DocTrees.instance(task);
      for (CompilationUnitTree unit : task.parse()) {
        String file = unit.getSourceFile().getName();
        boolean controller = file.endsWith("Controller.java");
        boolean mapper = file.endsWith("Mapper.java");
        for (var dependency : unit.getImports()) {
          String name = dependency.getQualifiedIdentifier().toString();
          if (name.endsWith(".*")) violations.add(file + ": 通配符导入");
          if (controller && (name.contains(".mapper.") || name.contains(".service.impl."))) {
            violations.add(file + ": Controller越层依赖 " + name);
          }
          if (!file.endsWith("ServiceImpl.java") && name.contains(".service.impl.")) {
            violations.add(file + ": 生产调用方依赖Service实现 " + name);
          }
        }
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitClass(ClassTree type, Void ignored) {
            if (!type.getSimpleName().isEmpty()) {
              var doc = docs.getDocCommentTree(getCurrentPath());
              if (doc == null || !doc.toString().contains("@author chenxuefeng")) {
                violations.add(file + ": 类型缺少职责或作者说明 " + type.getSimpleName());
              }
            }
            return super.visitClass(type, ignored);
          }

          @Override
          public Void visitMethod(MethodTree method, Void ignored) {
            if (method.getReturnType() == null) return null;
            var parent = getCurrentPath().getParentPath().getLeaf();
            boolean contract =
                parent instanceof ClassTree type
                    && type.getKind() == com.sun.source.tree.Tree.Kind.INTERFACE;
            boolean http =
                method.getModifiers().getAnnotations().stream()
                    .anyMatch(
                        annotation ->
                            Set.of(
                                    "GetMapping",
                                    "PostMapping",
                                    "PutMapping",
                                    "DeleteMapping",
                                    "PatchMapping")
                                .contains(annotation.getAnnotationType().toString()));
            var doc = docs.getDocCommentTree(getCurrentPath());
            if ((contract || http) && doc == null)
              violations.add(file + ": 方法缺少Javadoc " + method.getName());
            if (contract && doc != null) {
              for (var parameter : method.getParameters()) {
                if (!Pattern.compile("@param\\s+" + parameter.getName() + "\\b")
                    .matcher(doc.toString())
                    .find()) {
                  violations.add(file + ": 缺少参数说明 " + method.getName() + "." + parameter.getName());
                }
              }
              if (!method.getReturnType().toString().equals("void")
                  && !doc.toString().contains("@return")) {
                violations.add(file + ": 缺少返回说明 " + method.getName());
              }
            }
            if (http
                && (method.getParameters().size() > 2
                    || method.getReturnType().toString().contains("Map<"))) {
              violations.add(file + ": HTTP参数或返回类型未收敛 " + method.getName());
            }
            if (mapper) {
              if (method.getParameters().size() > 3 || method.toString().contains("Map<")) {
                violations.add(file + ": Mapper参数或返回类型未收敛 " + method.getName());
              }
              for (var annotation : method.getModifiers().getAnnotations()) {
                if (Set.of(
                        "Select",
                        "Insert",
                        "Update",
                        "Delete",
                        "SelectProvider",
                        "InsertProvider",
                        "UpdateProvider",
                        "DeleteProvider")
                    .contains(annotation.getAnnotationType().toString())) {
                  violations.add(file + ": SQL未放入XML " + method.getName());
                }
              }
            }
            return super.visitMethod(method, ignored);
          }
        }.scan(unit, null);
      }
    }
    assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
  }
}
