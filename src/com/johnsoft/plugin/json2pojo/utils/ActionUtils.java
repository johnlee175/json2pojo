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
package com.johnsoft.plugin.json2pojo.utils;

import java.util.Arrays;

import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.util.PlatformUtils;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-02
 */
public class ActionUtils {
    private static final String keywords[] = { "abstract", "assert", "boolean",
            "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "false",
            "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "true",
            "try", "void", "volatile", "while" };

    public static boolean isJavaKeyword(String keyword) {
        return Arrays.binarySearch(keywords, keyword) >= 0;
    }

    public static String classNameToVariableName(String className) {
        StringBuilder sb = new StringBuilder(className);
        int idx = -1;
        while(true) {
            idx = sb.indexOf(".", idx + 1);
            if (idx < 0 || idx >= sb.length() - 1) {
                break;
            }
            sb.replace(idx, idx + 2, "" + Character.toUpperCase(sb.charAt(idx + 1)));
        }
        sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
        String result = sb.toString();
        if (isJavaKeyword(result)) {
            return "an" + cap(result);
        }
        return result;
    }

    public static String jsonNameToFieldName(String field) {
        StringBuilder sb = new StringBuilder(field);
        int idx = -1;
        while(true) {
            idx = sb.indexOf("_", idx + 1);
            if (idx < 0 || idx >= sb.length() - 1) {
                break;
            }
            sb.replace(idx, idx + 2, "" + Character.toUpperCase(sb.charAt(idx + 1)));
        }
        sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
        String result = sb.toString();
        if (isJavaKeyword(result)) {
            return "an" + cap(result);
        }
        return result;
    }

    public static String cap(String field) {
        return Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    public static String uncap(String className) {
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    public static ContentPrinter newStringPrinter() {
        return new ContentPrinter() {
            private StringBuilder sb;
            @Override
            public void open() {
                sb = new StringBuilder();
            }

            @Override
            public void close() {
                sb = null;
            }

            @Override
            public void println(String content) {
                if (sb != null && content != null) {
                    sb.append(content).append('\n');
                }
            }

            @Override
            public String content() {
                if (sb != null) {
                    return sb.toString();
                }
                return "";
            }
        };
    }

    public static void showPopupBalloon(final Editor editor, final String result) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                final JBPopupFactory factory = JBPopupFactory.getInstance();
                factory.createHtmlTextBalloonBuilder(result, MessageType.INFO, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                        .show(factory.guessBestPopupLocation(editor), Balloon.Position.below);
            }
        });
    }

    public static String loadText(VirtualFile file) {
        return LoadTextUtil.loadText(file).toString();
    }

    public static boolean isAndroidStudio() {
        final String platformPrefix = PlatformUtils.getPlatformPrefix();
        if (platformPrefix == null) {
            return false;
        }
        return "androidstudio".equals(platformPrefix.toLowerCase());
    }

    public static boolean isMethodNameAndParamsSame(PsiMethod left, PsiMethod right, boolean checkName) {
        if (left == null || right == null) {
            return false;
        }
        if (checkName && (!left.getName().equals(right.getName()))) {
            return false;
        }
        final PsiParameterList leftParams = left.getParameterList();
        final PsiParameterList rightParams = right.getParameterList();
        final int paramCount;
        if ((paramCount = leftParams.getParametersCount()) != rightParams.getParametersCount()) {
            return false;
        }
        if (paramCount == 0) {
            return true;
        }
        final PsiParameter[] leftParameters = leftParams.getParameters();
        final PsiParameter[] rightParameters = rightParams.getParameters();
        for (int i = 0; i < paramCount; ++i) {
            if (!leftParameters[i].getType().getCanonicalText()
                    .equals(rightParameters[i].getType().getCanonicalText())) {
                return false;
            }
            if ((leftParameters[i].isVarArgs() && !rightParameters[i].isVarArgs())
                    ||(!leftParameters[i].isVarArgs() && rightParameters[i].isVarArgs())) {
                return false;
            }
        }
        return true;
    }

    public static boolean isMethodNameAndParamsSame(PsiMethod left, PsiMethodCallExpression right, boolean checkName) {
        if (left == null || right == null) {
            return false;
        }
        if (checkName && (!left.getName().equals(right.getMethodExpression().getReferenceName()))) {
            return false;
        }

        final PsiType[] exprTypes = right.getArgumentList().getExpressionTypes();
        final PsiParameter[] params = left.getParameterList().getParameters();
        final int len;
        if ((len = params.length) != exprTypes.length) {
            return false;
        }
        if (len == 0) {
            return true;
        }
        for (int i = 0; i < len; ++i) {
            if (!params[i].getType().getCanonicalText()
                    .equals(exprTypes[i].getCanonicalText())) {
                return false;
            }
            // TODO how to handle var args?
        }
        return true;
    }

    public static void caretMoveAndShowParams(Project project, PsiFile psiFile, PsiElement element) {
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null) {
            final int offset = element.getTextOffset() + 1;
            ShowParameterInfoHandler.invoke(project, editor, psiFile,
                    offset, element, false);
            editor.getCaretModel().moveToOffset(offset);
        }
    }
}
