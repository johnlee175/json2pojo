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
package com.johnsoft.plugin.json2pojo.macros;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author John Kenrinus Lee
 * @version 2018-10-10
 */
public class LogTagMacro extends Macro {
    @Override
    public String getName() {
        return "logTag";
    }

    @Override
    public String getPresentableName() {
        return "log tag";
    }

    @Nullable
    @Override
    public Result calculateResult(@NotNull final Expression[] expressions, final ExpressionContext expressionContext) {
        String logTag = searchLogTag(expressionContext.getPsiElementAtStartOffset());
        if (logTag.isEmpty()) {
            logTag = "\"\"";
        }
        return new TextResult(logTag);
    }

    @NotNull
    public static String searchLogTag(@Nullable PsiElement element) {
        if (element == null) {
            return "";
        }
        PsiFile psiFile = element.getContainingFile();
        if (!(psiFile instanceof PsiJavaFile)) {
            return "";
        }
        PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
        if (classes.length <= 0) {
            return "";
        }
        PsiClass psiClass = classes[0];
        String logTag = searchLogTagInClass(psiClass);
        if (StringUtils.isNotBlank(logTag)) {
            return logTag;
        }
        psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        logTag = searchLogTagInClass(psiClass);
        return logTag;
    }

    private static String searchLogTagInClass(PsiClass psiClass) {
        if (psiClass == null) {
            return "";
        }

        PsiField logTag;

        logTag = psiClass.findFieldByName("LOG_TAG", true);
        if (logTag != null
                && "java.lang.String".equals(logTag.getType().getCanonicalText())
                && logTag.hasModifierProperty("final") && logTag.hasModifierProperty("static")) {
            return "LOG_TAG";
        }

        logTag = psiClass.findFieldByName("TAG", true);
        if (logTag != null
                && "java.lang.String".equals(logTag.getType().getCanonicalText())
                && logTag.hasModifierProperty("final") && logTag.hasModifierProperty("static")) {
            return "TAG";
        }

        PsiImportList importList = ((PsiJavaFile) psiClass.getContainingFile()).getImportList();
        if (importList != null) {
            for (PsiImportStaticStatement importStatic : importList.getImportStaticStatements()) {
                String referenceName = importStatic.getReferenceName();
                if ("LOG_TAG".equals(referenceName) || "TAG".equals(referenceName)) {
                    PsiClass importClass =  importStatic.resolveTargetClass();
                    if (importClass != null) {
                        logTag = importClass.findFieldByName(referenceName, true);
                        if (logTag != null
                                && "java.lang.String".equals(logTag.getType().getCanonicalText())
                                && logTag.hasModifierProperty("final") && logTag.hasModifierProperty("static")) {
                            return referenceName;
                        }
                    }
                }
            }
        }

        return "";
    }

}
