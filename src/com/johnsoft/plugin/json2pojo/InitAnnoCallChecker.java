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

import java.util.ArrayList;

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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-11
 */
public class InitAnnoCallChecker extends BaseJavaLocalInspectionTool {
    @NotNull
    @Override
    public String getShortName() {
        return "InitAnnoCallCheck";
    }

    @NotNull
    @Override
    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "check the method annotated by @InitMethod of the class annotated by @ManualInit called";
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
        public void visitNewExpression(@NotNull final PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (handler.shouldQuickFix(expression)) {
                holder.registerProblem(expression, handler.getProblemDescription(),
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
                handler.applyFix(project, descriptor, (PsiNewExpression) descriptor.getPsiElement());
            }
        }
    }

    private static final class AnnoInitPsiHandler {
        private ArrayList<PsiMethod> targetMethods = null;

        public String getFixName() {
            return "Call @InitMethod method";
        }

        public String getProblemDescription() {
            return "the class annotated with @Init, but the @InitMethod method"
                    + " is not called first or called with no matches";
        }

        public boolean shouldQuickFix(@NotNull PsiNewExpression expression) {
            final PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
            if (reference == null) {
                return false;
            }
            final PsiExpression[] arrayDimensions = expression.getArrayDimensions();
            if (arrayDimensions.length > 0) {
                return false;
            }
            final Project project = expression.getProject();
            final PsiClass psiClass = JavaPsiFacade.getInstance(project)
                    .findClass(reference.getQualifiedName(), GlobalSearchScope.everythingScope(project));
            if (psiClass == null) {
                return false;
            }
            final PsiModifierList modifierList = psiClass.getModifierList();
            if (modifierList == null) {
                return false;
            }
            if (modifierList.findAnnotation(InitMethodCheckProvider.MANUAL_INIT) == null) {
                return false;
            }

            final PsiMethod[] allMethods = psiClass.getAllMethods();
            if (allMethods.length == 0) {
                return false;
            }

            final ArrayList<PsiMethod> methods = new ArrayList<>();
            for (PsiMethod method : allMethods) {
                final PsiModifierList modifiers = method.getModifierList();
                if (modifiers.findAnnotation(InitMethodCheckProvider.INIT_METHOD) != null) {
                    methods.add(method);
                }
            }

            if (methods.isEmpty()) {
                return false;
            }

            final PsiMethodCallExpression methodCall =
                    PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
            if (methodCall != null) {
                for (PsiMethod method : methods) {
                    if (ActionUtils.isMethodNameAndParamsSame(method, methodCall, true)) {
                        return false;
                    }
                }
            }

            synchronized (this) {
                targetMethods = methods;
            }
            return true;
        }

        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor,
                             @NotNull final PsiNewExpression expression) {
            final ArrayList<PsiMethod> lMethods;
            synchronized (this) {
                lMethods = targetMethods;
            }
            if (lMethods == null || lMethods.isEmpty()) {
                return;
            }

            final PsiFile psiFile = expression.getContainingFile();

            final PsiMethodCallExpression methodCall =
                    PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
            if (methodCall != null) {
                for (PsiMethod lMethod : lMethods) {
                    if (lMethod.getName().equals(methodCall.getMethodExpression().getReferenceName())) {
                        ActionUtils.caretMoveAndShowParams(project, psiFile, methodCall.getArgumentList());
                        return;
                    }
                }
            }

            final String methodName = lMethods.get(0).getName();
            final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            final PsiMethodCallExpression callExpr =
                    (PsiMethodCallExpression) factory.createExpressionFromText("a." + methodName + "()", null);
            callExpr.getMethodExpression().getQualifierExpression().replace(expression);
            final PsiElement newElement = expression.replace(callExpr);

            PsiTestUtil.checkFileStructure(psiFile);

            ActionUtils.caretMoveAndShowParams(project, psiFile, newElement.getLastChild());

            synchronized (this) {
                targetMethods = null;
            }
        }
    }
}
