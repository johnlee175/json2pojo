/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package com.johnsoft.plugin.json2pojo;

import static com.johnsoft.plugin.json2pojo.ActionUtils.cap;
import static com.johnsoft.plugin.json2pojo.ActionUtils.jsonNameToFieldName;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-03
 */
public class ParseJsonFileAction extends AnAction {
    public interface JsonPojoVisitor {
        void visitField(String className, String type, String name);
        void visitArrayField(String className, String type, String name);
        void visitSubclassField(String className, String type, String name);
        void visitClassBegin(String className, boolean isArray);
        void visitClassEnd(String className, boolean isArray);
    }

    public interface JsonClassNameFactory {
        String getClassName(String hint);
    }

    public static class SimpleJsonAdapter implements JsonPojoVisitor, JsonClassNameFactory, ContentPrinter {
        private final ContentPrinter printer = ActionUtils.newStringPrinter();
        private boolean wrapDecorate;
        protected final String packageName;
        protected final String publicClassName;
        protected final String originJson;

        public SimpleJsonAdapter(String packageName, String publicClassName, String originJson, boolean wrapDecorate) {
            this.packageName = packageName;
            this.publicClassName = publicClassName;
            this.originJson = originJson;
            this.wrapDecorate = wrapDecorate;
        }

        @Override
        public void visitField(String className, String type, String name) {
            String newName = jsonNameToFieldName(name);
            if (!newName.equals(name)) {
                println("@com.google.gson.annotations.SerializedName(\"" + name + "\")");
            }
            println("public " + type + " " + newName + ";");
        }

        @Override
        public void visitArrayField(String className, String type, String name) {
            String newName = jsonNameToFieldName(name);
            if (!newName.equals(name)) {
                println("@com.google.gson.annotations.SerializedName(\"" + name + "\")");
            }
            println("public java.util.ArrayList<" + type + "> " + newName + " = new java.util.ArrayList<>();");
        }

        @Override
        public void visitSubclassField(String className, String type, String name) {
            String newName = jsonNameToFieldName(name);
            if (!newName.equals(name)) {
                println("@com.google.gson.annotations.SerializedName(\"" + name + "\")");
            }
            println("public " + type + " " + newName
                    + " = new " + type + "();");
        }

        @Override
        public void visitClassBegin(String className, boolean isArray) {
            if (publicClassName.equals(className)) {
                if (wrapDecorate) {
                    println("package " + packageName + ";");
                    println("public class " + className + " implements java.io.Serializable {");
                }
            } else {
                println("public static class " + className + " {");
            }
        }

        @Override
        public void visitClassEnd(String className, boolean isArray) {
            if (publicClassName.equals(className)) {
                String json = originJson.replace("\"", "\\\"");
                json = json.replaceAll("\\s+", "");
                println("public String toString() { return \"" + className + "=\" + toJsonString(this); }");
                println("public static " + className + " mock() { return fromJsonString(\"" + json + "\"); }");
                println("public static org.json.JSONObject toJson(" + className + " obj) { "
                        + "try { return new org.json.JSONObject(toJsonString(obj)); } "
                        + "catch (java.lang.Exception e) { e.printStackTrace(); return null; } "
                        + " }");
                println("public static " + className + " fromJson(org.json.JSONObject object) { "
                        + "return fromJsonString(object.toString()); }");
                println("public static String toJsonString(" + className + " obj) { return gson.toJson(obj); }");
                println("public static " + className + " fromJsonString(String json) {  "
                        + "return gson.fromJson(json, " + className + ".class); }");
                println("private static final com.google.gson.Gson gson = new com.google.gson.Gson();");
                if (wrapDecorate) {
                    println("}");
                }
            } else {
                println("}");
            }
        }

        @Override
        public String getClassName(String hint) {
            if (hint == null) {
                return publicClassName;
            }
            return cap(jsonNameToFieldName(hint));
        }

        @Override
        public void open() {
            printer.open();
        }

        @Override
        public void println(String frag) {
            printer.println(frag);
        }

        @Override
        public void close() {
            printer.close();
        }

        @Override
        public String content() {
            return printer.content();
        }
    }

    private String currentField;
    private final LinkedList<HashMap<String, String>> arrayStack = new LinkedList<>();
    private final LinkedList<String> classNameStack = new LinkedList<>();

    private void parse(JsonElement element, JsonPojoVisitor visitor, JsonClassNameFactory nameFactory) {
        if (element.isJsonObject()) {
            HashMap<String, String> map = arrayStack.peekFirst();
            if (map == null) {
                if (currentField != null && !currentField.trim().isEmpty()) {
                    classNameStack.addFirst(nameFactory.getClassName(currentField));
                    visitor.visitSubclassField(classNameStack.peekFirst(), classNameStack.peekFirst(), currentField);
                }
                visitor.visitClassBegin(classNameStack.peekFirst(), false);
            }
            JsonObject jsonObject = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                currentField = entry.getKey();
                parse(entry.getValue(), visitor, nameFactory);
            }
            if (map == null) {
                visitor.visitClassEnd(classNameStack.removeFirst(), false);
            }
        } else if (element.isJsonArray()) {
            classNameStack.addFirst(nameFactory.getClassName(currentField));
            visitor.visitArrayField(classNameStack.peekFirst(), classNameStack.peekFirst(), currentField);
            visitor.visitClassBegin(classNameStack.peekFirst(), true);
            JsonArray jsonArray = element.getAsJsonArray();
            arrayStack.addFirst(new HashMap<>());
            for (JsonElement e : jsonArray) {
                parse(e, visitor, nameFactory);
            }
            HashMap<String, String> map = arrayStack.removeFirst();
            for (String key : map.keySet()) {
                visitor.visitField(classNameStack.peekFirst(), map.get(key), key);
            }
            visitor.visitClassEnd(classNameStack.removeFirst(), true);
        } else if (element.isJsonNull()) {
            HashMap<String, String> map = arrayStack.peekFirst();
            if (map != null) {
                map.put(currentField, "Object");
            } else {
                visitor.visitField(classNameStack.peekFirst(), "Object", currentField);
            }
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive jsonPrimitive = element.getAsJsonPrimitive();
            if (jsonPrimitive.isBoolean()) {
                HashMap<String, String> map = arrayStack.peekFirst();
                if (map != null) {
                    map.put(currentField, "boolean");
                } else {
                    visitor.visitField(classNameStack.peekFirst(), "boolean", currentField);
                }
            } else if (jsonPrimitive.isNumber()) {
                final String type;
                if (jsonPrimitive.getAsString().contains(".")) {
                    type = "double";
                } else {
                    type = "int";
                }
                HashMap<String, String> map = arrayStack.peekFirst();
                if (map != null) {
                    map.put(currentField, type);
                } else {
                    visitor.visitField(classNameStack.peekFirst(), type, currentField);
                }
            } else if (jsonPrimitive.isString()) {
                HashMap<String, String> map = arrayStack.peekFirst();
                if (map != null) {
                    map.put(currentField, "String");
                } else {
                    visitor.visitField(classNameStack.peekFirst(), "String", currentField);
                }
            }
        }
    }

    public void parse(String json, JsonPojoVisitor visitor, JsonClassNameFactory nameFactory) {
        currentField = "";
        arrayStack.clear();
        classNameStack.clear();

        JsonParser parser = new JsonParser();
        JsonObject root = (JsonObject) parser.parse(json);
        classNameStack.addFirst(nameFactory.getClassName(null));
        parse(root, visitor, nameFactory);
    }

    public String parse(String json, String packageName, String className) {
        SimpleJsonAdapter adapter = new SimpleJsonAdapter(packageName, className, json, false);
        adapter.open();
        parse(json, adapter, adapter);
        String result = adapter.content();
        adapter.close();
        return result;
    }

    private HashMap<String, String> options;

    @Override
    public void update(AnActionEvent anActionEvent) {
        boolean enabled = false;
        final Project project = anActionEvent.getProject();
        final IdeView view = anActionEvent.getData(LangDataKeys.IDE_VIEW);
        if (project != null && view != null) {
            final PsiDirectory directory = view.getOrChooseDirectory();
            if (directory != null) {
                final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                final VirtualFile virtualFile = directory.getVirtualFile();
                if (fileIndex.isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.SOURCES)) {
                    enabled = true;
                }
            }
        }

        Presentation presentation = anActionEvent.getPresentation();
        presentation.setVisible(enabled);
        presentation.setEnabled(enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();
        if (project == null) {
            return;
        }
        final IdeView view = anActionEvent.getData(LangDataKeys.IDE_VIEW);
        if (view == null) {
            return;
        }
        final PsiDirectory directory = view.getOrChooseDirectory();
        if (directory == null) {
            return;
        }

        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final VirtualFile selectedVirtualFile = directory.getVirtualFile();
        final Module module = fileIndex.getModuleForFile(selectedVirtualFile);

        if (!fileIndex.isUnderSourceRootOfType(selectedVirtualFile, JavaModuleSourceRootTypes.SOURCES)) {
            return;
        }

        final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(selectedVirtualFile);
        final PsiDirectory sourceRootDir = PsiManager.getInstance(project).findDirectory(sourceRoot);

        final String basePath = sourceRoot.getPath();
        final String selectedPath = selectedVirtualFile.getPath();
        String packageName = "";
        if (basePath.length() < selectedPath.length()) {
            packageName = selectedPath.substring(basePath.length())
                    .replace(File.separator, ".").trim();
            if (packageName.charAt(0) == '.') {
                packageName = packageName.substring(1);
            }
            if (packageName.charAt(packageName.length() - 1) == '.') {
                packageName = packageName.substring(0, packageName.length() - 1);
            }
        }
        final NewFromJsonDialog dialog = new NewFromJsonDialog(project, selectedVirtualFile);
        dialog.show();
        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
            if (!packageName.isEmpty()) {
                String append = dialog.getPackageName();
                if (!append.isEmpty()) {
                    packageName = packageName + '.' + append;
                }
            } else {
                packageName = dialog.getPackageName();
            }
            String className = dialog.getClassName();
            String json = dialog.getJson();

            if (options == null) {
                options = new HashMap<>();
                try {
                    Class.forName("com.android.tools.idea.actions.CreateClassAction");
                    options.put("PACKAGE_NAME", packageName);
                    options.put("IMPORT_BLOCK", "");
                    options.put("SUPERCLASS", "");
                    options.put("INTERFACES", "");
                    options.put("FINAL", "FALSE");
                    options.put("ABSTRACT", "FALSE");
                    options.put("VISIBILITY", "PUBLIC" /* "PACKAGE_PRIVATE" */);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }

            final PsiDirectory pkgDir = PackageUtil.findOrCreateDirectoryForPackage(module,
                    packageName, sourceRootDir, false);
            final PsiClass publicOuterClass = JavaDirectoryService.getInstance().createClass(pkgDir, className,
                    JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME, true, options);
            final PsiJavaFile psiJavaFile = (PsiJavaFile) publicOuterClass.getContainingFile();
            view.selectElement(publicOuterClass);

            final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            final CaretModel caretModel = editor.getCaretModel();

            WriteCommandAction.runWriteCommandAction(project,
                    new ThreeParamRunnable<String, String, String>(json, packageName, className) {
                        @Override
                        public void run(String json, String packageName, String className) {
                            final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

                            final PsiJavaCodeReferenceElement serializable =
                                    JavaPsiFacade.getElementFactory(project)
                                            .createReferenceElementByFQClassName("java.io.Serializable",
                                                    GlobalSearchScope
                                                            .moduleWithDependenciesAndLibrariesScope(module));
                            publicOuterClass.getImplementsList().add(serializable);
                            documentManager.doPostponedOperationsAndUnblockDocument(documentManager.getDocument(psiJavaFile));

                            final String content = parse(json, packageName, className);
                            caretModel.moveToOffset(publicOuterClass.getLBrace().getTextRange().getEndOffset());
                            EditorModificationUtil.insertStringAtCaret(editor, content, false, true);
                            documentManager.doPostponedOperationsAndUnblockDocument(documentManager.getDocument(psiJavaFile));

                            documentManager.commitAllDocuments();
                            final Runnable runnable = new ThreeParamRunnable<Project, PsiJavaFile, Void>(
                                    project, psiJavaFile, null) {
                                @Override
                                public void run(Project project, PsiJavaFile psiJavaFile, Void v) {
                                    final JavaCodeStyleManager styleManager = JavaCodeStyleManager
                                            .getInstance(project);
                                    styleManager.removeRedundantImports(psiJavaFile);
                                    styleManager.optimizeImports(psiJavaFile);
                                    styleManager.shortenClassReferences(psiJavaFile);
                                    new ReformatCodeProcessor(project, psiJavaFile,
                                            psiJavaFile.getTextRange(), false)
                                            .runWithoutProgress();
                                }
                            };
                            documentManager.performWhenAllCommitted(new Runnable() {
                                @Override
                                public void run() {
                                    WriteCommandAction.runWriteCommandAction(project, runnable);
                                }
                            });
                        }
                    });
        }
    }
}
