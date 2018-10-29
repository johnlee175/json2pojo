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
package com.johnsoft.plugin.json2pojo.intentions;

import java.util.ArrayList;
import java.util.Collection;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.johnsoft.plugin.json2pojo.utils.ActionUtils;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-07
 */
public class CreateFactoryIntentionAction extends PsiElementBaseIntentionAction {
    private String factoryName = "newInstance";
    private PsiClass targetClass = null;
    private final ArrayList<PsiMethod> targetConstructors = new ArrayList<>();

    @Nls
    @NotNull
    @Override
    public String getText() {
        return "Generate Create Factory Method";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement psiElement) {
        if (!psiElement.isWritable() || !psiElement.isValid()) { return false; }
        if (!(psiElement instanceof PsiIdentifier)) { return false; }

        PsiClass psiClass;
        final PsiElement parent = psiElement.getParent();
        if (parent instanceof PsiClass) { // on the class
            psiClass = (PsiClass) parent;
        } else if (parent instanceof PsiMethod) { // on the constructor
            if (!((PsiMethod) parent).isConstructor()) {
                return false;
            }
            psiClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
        } else {
            return false;
        }

        // make sure normal class, not enum, annotation or interface, not final, not inner class
        if (psiClass == null || !psiClass.isValid()) {
            return false;
        }
        if (psiClass.isEnum() || psiClass.isInterface()
                || psiClass.isAnnotationType()) {
            return false;
        }
        final PsiClass outerClass = psiClass.getContainingClass();
        final PsiModifierList modifierList = psiClass.getModifierList();
        if (outerClass == null) {
            if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) {
                return false;
            }
        } else { // inner class
            if (modifierList == null
                    || modifierList.hasModifierProperty(PsiModifier.FINAL)
                    || !modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }
        }

        final PsiMethod[] allMethods = psiClass.getAllMethods();
        if (parent instanceof PsiMethod) {
            final PsiMethod target = (PsiMethod) parent;
            for (PsiMethod method : allMethods) {
                if (method.getName().equals(factoryName)
                        && ActionUtils.isMethodNameAndParamsSame(target, method, false)) {
                    return false;
                }
            }
            synchronized (this) {
                targetClass = psiClass;
                targetConstructors.clear();
                targetConstructors.add(target);
            }
        } else {
            final ArrayList<PsiMethod> target = new ArrayList<>();
            final PsiMethod[] constructors = psiClass.getConstructors();
            if (constructors.length > 0) {
                outer:
                for (PsiMethod constructor : constructors) {
                    for (PsiMethod method : allMethods) {
                        if (method.getName().equals(factoryName)
                                && ActionUtils.isMethodNameAndParamsSame(constructor, method, false)) {
                            continue outer;
                        }
                    }
                    target.add(constructor);
                }
                if (target.isEmpty()) {
                    return false;
                }
            } else {
                for (PsiMethod method : allMethods) {
                    if (method.getName().equals(factoryName)
                            && method.getParameterList().getParametersCount() == 0) {
                        return false;
                    }
                }
            }
            synchronized (this) {
                targetClass = psiClass;
                targetConstructors.clear();
                targetConstructors.addAll(target);
            }
        }
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement psiElement)
            throws IncorrectOperationException {
        final PsiClass lClass;
        final ArrayList<PsiMethod> lConstructors = new ArrayList<>();
        synchronized (this) {
            lClass = targetClass;
            lConstructors.addAll(targetConstructors);
        }
        if (lClass == null) {
            return;
        }

        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory elementFactory = facade.getElementFactory();
        final PsiType classType = PsiType.getTypeByName(lClass.getQualifiedName(), project,
                GlobalSearchScope.fileScope(lClass.getContainingFile()));

        if (lConstructors.isEmpty()) { // no any constructor, we create factory with default constructor
            lClass.addAfter(elementFactory.createMethodFromText("public static " + lClass.getQualifiedName()
                    + " " + factoryName + "() { return new " + lClass.getQualifiedName() + "(); }",
                    null), lClass.getLBrace());
            return;
        }

        for (PsiMethod lConstructor : lConstructors) {
            final PsiModifierList constructorModifiers = lConstructor.getModifierList();
            if (constructorModifiers.hasModifierProperty(PsiModifier.PUBLIC)) {
                constructorModifiers.setModifierProperty(PsiModifier.PROTECTED, true);
            }

            // create factory method:
            final PsiMethod newInstance = elementFactory.createMethod(factoryName, classType);
            final PsiModifierList modifierList = newInstance.getModifierList();
            modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
            modifierList.setModifierProperty(PsiModifier.STATIC, true);
            final PsiParameterList parameterList = newInstance.getParameterList();
            final PsiParameterList constructorParameters = lConstructor.getParameterList();
            for (PsiParameter parameter : constructorParameters.getParameters()) {
                parameterList.add(parameter);
            }
            final PsiCodeBlock body = newInstance.getBody();

            final PsiNewExpression newExpr = createPsiNewExpression(lClass, elementFactory, parameterList);
            final Collection<PsiExpressionStatement> transfers = getTransfers(lConstructor);

            if (!transfers.isEmpty()) { // has overrideable method call in constructors
                if (lConstructors.size() == 1 && (psiElement.getParent() instanceof PsiMethod)) {
                    // locating a constructor trigger, not on the class
                    final PsiMethod initMethod = elementFactory.createMethodFromText(
                            "protected void initialize() { }", null);
                    transfer(initMethod.getBody(), transfers, "this", elementFactory);
                    lClass.addAfter(initMethod, lConstructor);
                    final PsiStatement initStatement =
                            elementFactory.createStatementFromText("this.initialize();", null);
                    // lConstructor.getBody().add(initStatement);
                    transfers.clear();
                    transfers.add((PsiExpressionStatement) initStatement);
                }

                final String instanceName = "instance";
                final PsiDeclarationStatement declare =
                        elementFactory.createVariableDeclarationStatement(instanceName, classType, newExpr);
                final PsiElement declareFirstChild = declare.getFirstChild();
                if (declareFirstChild != null && declareFirstChild instanceof PsiLocalVariable) {
                    final PsiLocalVariable localVariable = (PsiLocalVariable) declareFirstChild;
                    final PsiModifierList localVariableModifiers = localVariable.getModifierList();
                    if (localVariableModifiers != null) {
                        localVariableModifiers.setModifierProperty(PsiModifier.FINAL, true);
                    }
                }
                body.add(declare);

                transfer(body, transfers, instanceName, elementFactory);

                body.add(elementFactory.createStatementFromText("return " + instanceName
                        + ";", null));
            } else {
                final PsiReturnStatement returnStatement = (PsiReturnStatement) elementFactory
                        .createStatementFromText("return ret;", null);
                returnStatement.getReturnValue().replace(newExpr);
                body.add(returnStatement);
            }

            lClass.addAfter(newInstance, lClass.getLBrace());
        }

        PsiTestUtil.checkFileStructure(lClass.getContainingFile());
    }

    @NotNull
    private PsiNewExpression createPsiNewExpression(PsiClass lClass, PsiElementFactory elementFactory,
                                                    PsiParameterList parameterList) {
        final PsiNewExpression newExpr = (PsiNewExpression) elementFactory
                .createExpressionFromText("new Cls()", null);
        newExpr.getClassReference().getReferenceNameElement()
                .replace(elementFactory.createIdentifier(lClass.getName()));
        for (PsiParameter parameter : parameterList.getParameters()) {
            newExpr.getArgumentList().add(elementFactory
                    .createExpressionFromText(parameter.getName(), null));
        }
        return newExpr;
    }

    @NotNull
    private Collection<PsiExpressionStatement> getTransfers(PsiMethod constructor) {
        final ArrayList<PsiExpressionStatement> elements = new ArrayList<>();
        final PsiCodeBlock body = constructor.getBody();
        if (body != null) {
            for (PsiStatement statement : body.getStatements()) {
                if (statement != null && (statement instanceof PsiExpressionStatement)) {
                    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
                    final PsiExpression expression = expressionStatement.getExpression();
                    if (hasOverrideableMethod(expression, constructor)) {
                        elements.add(expressionStatement);
                    } else {
                        elements.clear();
                    }
                }
            }
        }
        return elements;
    }

    private boolean hasOverrideableMethod(PsiExpression expression, PsiMethod constructor) {
        if (expression != null && expression instanceof  PsiMethodCallExpression) {
            final PsiReferenceExpression methodCall = ((PsiMethodCallExpression) expression).getMethodExpression();
            final PsiMethod psiMethod = (PsiMethod) methodCall.resolve();
            if (psiMethod != null) {
                final PsiClass methodClass = psiMethod.getContainingClass();
                final PsiClass constructorClass = constructor.getContainingClass();
                if (constructorClass.equals(methodClass) || constructorClass.isInheritor(methodClass, true)) {
                    final PsiExpression qualifierExpression = methodCall.getQualifierExpression();
                    final PsiModifierList modifierList = psiMethod.getModifierList();
                    if (!modifierList.hasModifierProperty(PsiModifier.STATIC)
                        && !modifierList.hasModifierProperty(PsiModifier.PRIVATE)
                        && !modifierList.hasModifierProperty(PsiModifier.FINAL)) {
                        if (qualifierExpression == null || (qualifierExpression instanceof PsiThisExpression)) {
                            return true;
                        }
                        /*if (qualifierExpression instanceof PsiMethodCallExpression) {
                            // TODO How to better handle method chain?
                            return hasOverrideableMethod(qualifierExpression, constructor);
                        }*/
                    }
                }
            }
        }
        return false;
    }

    // transfer overrideable method calls from constructor to body
    private void transfer(PsiCodeBlock body, Collection<PsiExpressionStatement> methodCalls,
                          String instanceName, PsiElementFactory elementFactory) {
        for (PsiExpressionStatement methodCall : methodCalls) {
            PsiMethodCallExpression expression = (PsiMethodCallExpression) selectQualifier(methodCall.getExpression());
            expression.getMethodExpression().setQualifierExpression(elementFactory
                    .createExpressionFromText(instanceName, null));
            body.add(methodCall);
            methodCall.delete();
        }
    }

    private PsiExpression selectQualifier(PsiExpression expression) {
        final PsiExpression qualifierExpression = ((PsiMethodCallExpression) expression)
                .getMethodExpression().getQualifierExpression();
        if (qualifierExpression == null || !(qualifierExpression instanceof PsiMethodCallExpression)) {
            // maybe PsiReferenceExpression or PsiThisExpression
            return expression;
        } else {
            return selectQualifier(qualifierExpression);
        }
    }
}
