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
package com.johnsoft.plugin.json2pojo.actions;

import static com.johnsoft.plugin.json2pojo.utils.ActionUtils.classNameToVariableName;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.johnsoft.plugin.json2pojo.utils.ActionUtils;
import com.johnsoft.plugin.json2pojo.utils.ContentPrinter;
import com.johnsoft.plugin.json2pojo.utils.ThreeParamRunnable;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-03
 */
public class GenJSONMethodAction extends AnAction {
    private final ContentPrinter printer = ActionUtils.newStringPrinter();
    private char index = 'h';

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();
        System.out.println("project: " + project);
        final ModuleManager moduleManager = ModuleManager.getInstance(project);
        final Module[] modules = moduleManager.getModules();
        if (modules != null && modules.length > 0) {
            for (Module module : modules) {
                System.out.println("modules:" + module.getName());
            }
        }

        final Editor editor = anActionEvent.getData(PlatformDataKeys.EDITOR);
        System.out.println("editor: " + editor);
        if (editor == null) {
            return;
        }

        // Navigatable navigatable = anActionEvent.getData(DataKeys.NAVIGATABLE);
        // Messages.showInfoMessage("" + navigatable, "Navigatable");

        PsiFile selectedFile;

        selectedFile = anActionEvent.getData(DataKeys.PSI_FILE);
        if (selectedFile == null) {
            final FileEditorManager editorManager = FileEditorManager.getInstance(project);
            final VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
            if (selectedFiles != null && selectedFiles.length > 0) {
                selectedFile = PsiManager.getInstance(project).findFile(selectedFiles[0]);
            }
        }

        if (selectedFile != null && selectedFile instanceof PsiJavaFile) {
            final PsiJavaFile selectedJavaFile = (PsiJavaFile) selectedFile;
            System.out.println(selectedJavaFile);
            /* selectedJavaFile.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitClass(PsiClass aClass) {
                    System.out.println(aClass);
                    super.visitClass(aClass);
                }
                @Override
                public void visitField(PsiField field) {
                    System.out.println(field);
                    super.visitField(field);
                }
            }); */
            System.out.println("--------------------------------------");
            printer.open();

            PsiClass publicOuterClass = null;

            PsiClass[] psiClasses = selectedJavaFile.getClasses();
            if (psiClasses != null && psiClasses.length > 0) {
                for (PsiClass psiClass : psiClasses) {
                    if (selectedJavaFile.getName().equals(psiClass.getName() + ".java")) { // public outer class
                        publicOuterClass = psiClass;
                        break;
                    }
                }
            }

            if (publicOuterClass != null) {
                WriteCommandAction.runWriteCommandAction(project,
                        new ThreeParamRunnable<PsiClass, String, PsiJavaFile>(
                                publicOuterClass,
                                genMethod(publicOuterClass),
                                selectedJavaFile) {
                            @Override
                            public void run(PsiClass psiClass, String content, PsiJavaFile selectedJavaFile) {
                                // write method to class
                                final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
                                final PsiMethod psiMethod = psiElementFactory.createMethodFromText(content, psiClass);
                                final int offset = editor.getCaretModel().getOffset();
                                final PsiElement element = selectedJavaFile.findElementAt(offset);
                                psiClass.addAfter(psiMethod, element);
                                ActionUtils.showPopupBalloon(editor, "Generate successful");

                                // format code
                                // CodeStyleManager.getInstance(project).reformat(selectedJavaFile);
                                JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
                                styleManager.removeRedundantImports(selectedJavaFile);
                                styleManager.optimizeImports(selectedJavaFile);
                                styleManager.shortenClassReferences(psiClass);
                                new ReformatCodeProcessor(project, selectedJavaFile, psiMethod.getTextRange(), false)
                                        .runWithoutProgress();
                            }
                        });
            }

            printer.close();
        }
    }

    private String genMethod(PsiClass psiClass) {
        final String classQualifiedName = psiClass.getQualifiedName();
        final String variableName = classNameToVariableName(classQualifiedName);
        final String variableJsonName = variableName + "Json";

        printer.println("public static " + classQualifiedName + " fromJSON(org.json.JSONObject " + variableJsonName +
                ") {");
        printer.println(classQualifiedName + " " + variableName + " = new " + classQualifiedName + "();");
        printer.println("if (" + variableJsonName + " != null) {");
        parseFields(psiClass);
        printer.println("}");
        printer.println("return " + variableName + ";");
        printer.println("}");
        return printer.content();
    }

    private void parseFields(PsiClass psiClass) {
        final PsiField[] fields = psiClass.getAllFields();
        if (fields != null && fields.length > 0) {
            final String classQualifiedName = psiClass.getQualifiedName();
            final String variableName = classNameToVariableName(classQualifiedName);
            final String variableJsonName = variableName + "Json";

            for (PsiField field : fields) {
                final String fieldName = field.getName();
                final String typeName = field.getType().getCanonicalText();
                String jsonName = fieldName;
                final PsiModifierList modifierList = field.getModifierList();
                if (modifierList != null) {
                    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)
                            || modifierList.hasModifierProperty(PsiModifier.TRANSIENT)
                            || modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                        return;
                    }
                    final PsiAnnotation annotation =
                            modifierList.findAnnotation("com.google.gson.annotations.SerializedName");
                    if (annotation != null) {
                        final PsiAnnotationMemberValue annoValue = annotation.findAttributeValue(null);
                        if (annoValue != null) {
                            final String serializedName =  annoValue.getText();
                            jsonName = serializedName.substring(1, serializedName.length() - 1);
                        }
                    }
                }

                if (typeName.equals("java.lang.String")) {
                    printer.println(variableName + "." + fieldName + " = " + variableJsonName
                            + ".optString(\"" + jsonName + "\",\"\");");
                } else if (typeName.equals("int")) {
                    printer.println(variableName + "." + fieldName + " = " + variableJsonName
                            + ".optInt(\"" + jsonName + "\", 0);");
                } else if (typeName.equals("double")) {
                    printer.println(variableName + "." + fieldName + " = " + variableJsonName
                            + ".optDouble(\"" + jsonName + "\", 0.0);");
                } else if (typeName.equals("boolean")) {
                    printer.println(variableName + "." + fieldName + " = " + variableJsonName
                            + ".optBoolean(\"" + jsonName + "\", false);");
                } else if (typeName.startsWith("java.util.ArrayList")) {
                    final String paramType = typeName.substring(typeName.indexOf("<") + 1, typeName.lastIndexOf(">"));
                    final PsiClass[] psiClasses = psiClass.getInnerClasses();
                    if (psiClasses != null && psiClasses.length > 0) {
                        for (PsiClass cls : psiClasses) {
                            if (cls.getQualifiedName().equals(paramType)) {
                                final String subName = classNameToVariableName(paramType);
                                final String subNameList = subName + "List";
                                final String subJsonArrName = subName + "Array";
                                final String subJsonObjName = subName + "Json";

                                printer.println("org.json.JSONArray " + subJsonArrName + " = " + variableJsonName
                                        + ".optJSONArray(\"" + jsonName + "\");");
                                printer.println("if (" + subJsonArrName + " != null) {");
                                printer.println("ArrayList<" + paramType + "> " + subNameList + " = "
                                        + variableName + "." + fieldName + ";");
                                ++index;
                                printer.println("for (int " + index + " = 0; " + index + " < " + subJsonArrName + ".length();"
                                        + " ++" + index + ") "  + "{");
                                printer.println("org.json.JSONObject " + subJsonObjName + " = " + subJsonArrName
                                        + ".optJSONObject(" + index + ");");
                                printer.println("if (" + subJsonObjName + " != null) {");
                                printer.println(paramType + " " + subName + " = new " + paramType + "();");
                                printer.println(subNameList + ".add(" + subName + ");");
                                parseFields(cls);
                                printer.println("}");
                                printer.println("}");
                                --index;
                                printer.println("}");
                            }
                        }
                    }
                }  else {
                    final PsiClass[] psiClasses = psiClass.getInnerClasses();
                    if (psiClasses != null && psiClasses.length > 0) {
                        for (PsiClass cls : psiClasses) {
                            if (cls.getQualifiedName().equals(typeName)) {
                                final String subName = classNameToVariableName(typeName);
                                final String subJsonName = subName + "Json";
                                printer.println("org.json.JSONObject " + subJsonName + " = "
                                        + variableJsonName + ".optJSONObject(\"" + jsonName + "\");");
                                printer.println("if (" + subJsonName + " != null) {");
                                printer.println(typeName + " " + subName + " = " + variableName + "." + fieldName + ";");
                                parseFields(cls);
                                printer.println("}");
                            }
                        }
                    }
                }
            }
        }
    }
}
