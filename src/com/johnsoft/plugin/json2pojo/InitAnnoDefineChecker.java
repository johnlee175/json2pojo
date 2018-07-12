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

import javax.swing.JComponent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-11
 */
public class InitAnnoDefineChecker extends BaseJavaLocalInspectionTool {
    @NotNull
    @Override
    public String getShortName() {
        return "InitAnnoDefineCheck";
    }

    @NotNull
    @Override
    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "check @InitMethod and @ManualInit define correct or not";
    }

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        return null;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new MyJavaElementVisitor(holder);
    }

    private static final class MyJavaElementVisitor extends JavaElementVisitor {
        private final ProblemsHolder holder;
        private final AnnoInitPsiHandler handler;
        private final LocalQuickFix quickFix;

        MyJavaElementVisitor(@NotNull final ProblemsHolder holder) {
            this.holder = holder;
            this.handler = new AnnoInitPsiHandler();
            this.quickFix = new MyQuickFix(handler);
        }

        @Override
        public void visitAnnotation(PsiAnnotation annotation) {
            super.visitAnnotation(annotation);
            if (handler.shouldQuickFix(annotation)) {
                holder.registerProblem(annotation, handler.getProblemDescription(),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFix);
            }
        }

        private static final class MyQuickFix implements LocalQuickFix {
            private final AnnoInitPsiHandler handler;

            MyQuickFix(AnnoInitPsiHandler handler) {
                this.handler = handler;
            }

            @NotNull
            @Override
            public String getName() {
                return handler.getFixName();
            }

            @NotNull
            @Override
            public String getFamilyName() {
                return getName();
            }

            @Override
            public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
                handler.applyFix(project, descriptor, (PsiAnnotation) descriptor.getPsiElement());
            }
        }
    }

    private static final class AnnoInitPsiHandler {
        private static final String REMOVE_IT = "Remove it";
        private static final String RETURN_THIS = "Return this";
        private static final String[] errorDescriptions = new String[] {
                "Define @ManualInit for class but not found @InitMethod for method in class",
                "@InitMethod shouldn't for a static method",
                "@InitMethod should return this class",
                "@InitMethod should return this class",
        };
        private static final String[] fixNames = new String[] {
                REMOVE_IT,
                REMOVE_IT,
                RETURN_THIS,
                REMOVE_IT
        };

        private int index;

        public String getFixName() {
            return fixNames[index];
        }

        public String getProblemDescription() {
            return errorDescriptions[index];
        }

        public boolean shouldQuickFix(@NotNull PsiAnnotation annotation) {
            if (InitMethodCheckProvider.MANUAL_INIT.equals(annotation.getQualifiedName())) {
                final PsiClass psiClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
                if (psiClass == null || !psiClass.isValid() || !psiClass.isWritable()) {
                    return false;
                }
                final PsiMethod[] allMethods = psiClass.getAllMethods();
                boolean findInitMethod = false;
                for (PsiMethod method : allMethods) {
                    final PsiModifierList modifiers = method.getModifierList();
                    if (modifiers.findAnnotation(InitMethodCheckProvider.INIT_METHOD) != null) {
                        findInitMethod = true;
                    }
                }
                if (!findInitMethod) {
                    index = 0;
                    return true;
                }
            } else if (InitMethodCheckProvider.INIT_METHOD.equals(annotation.getQualifiedName())) {
                final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(annotation, PsiMethod.class);
                if (psiMethod == null) {
                    return false;
                }
                final PsiModifierList modifiers = psiMethod.getModifierList();
                if (modifiers.hasModifierProperty(PsiModifier.STATIC)) {
                    index = 1;
                    return true;
                }
                final PsiClass containingClass = psiMethod.getContainingClass();
                final PsiType returnType = psiMethod.getReturnType();
                if (containingClass == null || returnType == null) {
                    return false;
                }
                if (PsiType.VOID.equals(returnType)) {
                    index = 2;
                    return true;
                }
                final String className = containingClass.getQualifiedName();
                if (className != null && !className.equals(returnType.getCanonicalText())) {
                    index = 3;
                    return true;
                }
            }
            return false;
        }

        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor,
                             @NotNull final PsiAnnotation annotation) {
            final PsiFile psiFile = annotation.getContainingFile();
            final String fixName = getFixName();
            if (REMOVE_IT.equals(fixName)) {
                annotation.delete();
            } else if (RETURN_THIS.equals(fixName)) {
                final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(annotation, PsiMethod.class);
                if (psiMethod != null) {
                    final PsiClass containingClass = psiMethod.getContainingClass();
                    final String qualifiedName;
                    if (containingClass != null && (qualifiedName = containingClass.getQualifiedName()) != null) {
                        final PsiElement returnType = psiMethod.getReturnTypeElement();
                        if (returnType != null) {
                            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                            returnType.replace(elementFactory
                                    .createTypeElementFromText(qualifiedName, null));
                            final PsiCodeBlock body = psiMethod.getBody();
                            if (body != null) {
                                body.addBefore(elementFactory.createStatementFromText("return this;",
                                        null), body.getRBrace());
                            }
                        }
                    }
                }
            }
            PsiTestUtil.checkFileStructure(psiFile);
        }
    }
}
