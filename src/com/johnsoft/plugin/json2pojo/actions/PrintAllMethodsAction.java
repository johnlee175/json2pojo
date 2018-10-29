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

import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.johnsoft.plugin.json2pojo.macros.LogTagMacro;

/**
 * @author John Kenrinus Lee
 * @version 2018-10-29
 */
public class PrintAllMethodsAction extends AnAction {
    private static final String[] options = new String[]{
            "System.out", "System.err", "android.util.Log.i", "android.util.Log.w", "Input Custom Pattern..."
    };
    private static final String[] patterns = new String[] {
            "java.lang.System.out.println(%content%);", "java.lang.System.out.println(%content%);",
            "android.util.Log.i(%tag%, %content%);", "android.util.Log.w(%tag%, %content%);",
            null
    };

    private static String historyCache;

    @Override
    public boolean startInTransaction() {
        return true;
    }

    @Override
    public void update(AnActionEvent event) {
        Pair<Project, PsiElement> pair = getElement(event);
        if (pair == null) {
            event.getPresentation().setVisible(false);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Pair<Project, PsiElement> pair = getElement(event);
        if (pair == null) {
            return;
        }

        Project project = pair.first;
        PsiElement element = pair.second;
        PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (psiClass == null) {
            Messages.showWarningDialog("Can't found context class", "Warning");
            return;
        }
        PsiMethod[] methods = psiClass.getMethods();
        ArrayList<PsiMethodMember> candidates = new ArrayList<>();
        for (PsiMethod method : methods) {
            if (!method.isConstructor() && method.isValid() && method.isWritable()) {
                candidates.add(new PsiMethodMember(method));
            }
        }

        PsiMethodMember[] members = new PsiMethodMember[candidates.size()];
        JCheckBox includePrivate = new JCheckBox("include private methods", true);
        ComboBox<String> printPattern = new ComboBox<>(options);
        printPattern.setSelectedIndex(0);

        MemberChooser<PsiMethodMember> memberChooser = new MemberChooser<>(candidates.toArray(members),
                false, true, project, new JLabel("Select For Print"),
                new JComponent[] { includePrivate, printPattern });
        memberChooser.setTitle("Print All Methods");
        if (memberChooser.showAndGet()) {
            int selectedIndex = printPattern.getSelectedIndex();
            if (selectedIndex < 0 || selectedIndex >= patterns.length) {
                return;
            }
            String pattern = patterns[selectedIndex];
            if (pattern == null) {
                pattern = Messages.showInputDialog("Input format pattern: (like 'android.util.Log.i(%tag%, %content%);')",
                        options[options.length - 1], null, historyCache, null);
                if (StringUtils.isBlank(pattern)) {
                    return;
                }
                historyCache = pattern;
            }
            insertPrintFor(project, memberChooser.getSelectedElements(), includePrivate.isSelected(), pattern);
        }
    }

    private static Pair<Project, PsiElement> getElement(AnActionEvent event) {
        Project project = event.getProject();
        PsiFile psiFile = event.getData(DataKeys.PSI_FILE);
        Editor editor = event.getData(DataKeys.EDITOR);
        if (project != null && psiFile != null && editor != null) {
            PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
            if (element != null) {
                return new Pair<>(project, element);
            }
        }
        return null;
    }

    private static void insertPrintFor(final Project project, final List<PsiMethodMember> elements,
                                final boolean includePrivate, final String printPattern) {
        if (elements == null) {
            return;
        }
        ApplicationManager.getApplication().runWriteAction(() -> {
            for (PsiMethodMember element : elements) {
                final PsiMethod psiMethod = element.getElement();
                if (!includePrivate && psiMethod.hasModifierProperty("private")) {
                    continue;
                }
                CommandProcessor.getInstance().executeCommand(project, () -> {
                    final PsiClass psiClass = psiMethod.getContainingClass();
                    final PsiCodeBlock body = psiMethod.getBody();
                    assert psiClass != null && body != null;
                    PsiElement target = body.getFirstBodyElement();
                    if (target == null) {
                        target = body.getRBrace();
                    }
                    if (target == null) {
                        return;
                    }

                    final StringBuilder sb = new StringBuilder();
                    sb.append("\"").append(getClassName(psiClass)).append('.').append(psiMethod.getName()).append('(');
                    final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
                    for (PsiParameter parameter : parameters) {
                        sb.append(parameter.getName()).append("=\" + ").append(parameter.getName()).append(" + \"");
                    }
                    sb.append(")\"");

                    String logTag = LogTagMacro.searchLogTag(psiMethod);
                    if (logTag.isEmpty()) {
                        logTag = "\"" + getClassName(psiClass) + "\"";
                    }
                    String result = printPattern.replace("%tag%", logTag);
                    result = result.replace("%content%", sb.toString());
                    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                    final PsiStatement statement = elementFactory.createStatementFromText(result, body);
                    body.addBefore(statement, target);

                    PsiJavaFile javaFile = (PsiJavaFile) psiMethod.getContainingFile();
                    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
                    javaCodeStyleManager.removeRedundantImports(javaFile);
                    javaCodeStyleManager.optimizeImports(javaFile);
                    javaCodeStyleManager.shortenClassReferences(psiMethod);
                    new ReformatCodeProcessor(project, javaFile, psiMethod.getTextRange(), false)
                            .runWithoutProgress();
                }, "add print statement for select elements", null);
            }
        });
    }

    private static String getClassName(@NotNull PsiClass psiClass) {
        PsiClass backup = psiClass;
        try {
            final PsiJavaFile psiFile = (PsiJavaFile) psiClass.getContainingFile();
            final PsiClass topClass = psiFile.getClasses()[0];
            if (topClass == null) {
                throw new IllegalStateException("Error with not known top level class");
            }
            StringBuilder name = new StringBuilder();
            while (psiClass != null && psiClass != topClass) {
                if (psiClass instanceof PsiAnonymousClass) {
                    name.insert(0, JavaAnonymousClassesHelper.getName((PsiAnonymousClass) psiClass));
                } else { // ignore local inner class nomenclature
                    name.insert(0, psiClass.getName()).insert(0, '$');
                }
                psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
            }
            if (psiClass == null) {
                throw new IllegalStateException("Error with not matched top level class");
            }
            if (psiClass == topClass) {
                name.insert(0, psiClass.getName());
            }
            return name.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return getClassNameAgain(backup); // conservative
        }
    }

    private static String getClassNameAgain(@NotNull PsiClass psiClass) {
        if (!(psiClass instanceof PsiAnonymousClass)) {
            return psiClass.getName();
        }

        PsiClass iter = psiClass;
        while (iter instanceof PsiAnonymousClass) {
            iter = PsiTreeUtil.getParentOfType(iter, PsiClass.class);
        }
        if (iter == null) {
            iter = psiClass;
            while (iter instanceof PsiAnonymousClass) {
                final PsiElement scope = iter.getScope();
                if (scope instanceof PsiMember) {
                    iter =((PsiMember) scope).getContainingClass();
                }
            }
        }

        if (iter == null) {
            return null;
        }
        return iter.getName() + JavaAnonymousClassesHelper.getName((PsiAnonymousClass) psiClass);
    }
}