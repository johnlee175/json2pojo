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

import java.util.Collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author John Kenrinus Lee
 * @version 2018-10-10
 */
public class CodeBlockVariblesMacro extends Macro {
    @Override
    public String getName() {
        return "codeBlockVariables";
    }

    @Override
    public String getPresentableName() {
        return "code block variables";
    }

    @Nullable
    @Override
    public Result calculateResult(@NotNull Expression[] expressions, ExpressionContext expressionContext) {
        final int startOffset = expressionContext.getStartOffset();
        for(PsiElement place = expressionContext.getPsiElementAtStartOffset(); place != null; place = place.getParent()) {
            if (place instanceof PsiCodeBlock) {
                StringBuilder sb = new StringBuilder();
                Collection<PsiLocalVariable> localVariables = PsiTreeUtil.findChildrenOfType(place, PsiLocalVariable.class);
                for (PsiLocalVariable localVariable : localVariables) {
                    PsiIdentifier identifier = localVariable.getNameIdentifier();
                    if (identifier != null && identifier.getTextOffset() < startOffset) {
                        sb.append("\"").append(identifier.getText()).append(" = [\" + ")
                          .append(identifier.getText()).append(" + \"], \" + ");
                    }
                }
                int length = sb.length();
                if (length > 6) {
                    sb.replace(length - 6, length, "\"");  // replace `, " + `
                }
                return new TextResult(sb.toString());
            }
        }
        return new TextResult("");
    }
}
