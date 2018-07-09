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

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.JBUI;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-02
 */
public class NewFromJsonDialog extends DialogWrapper {
    private Project project;
    private VirtualFile virtualFile;

    private EditorTextField jsonTextArea;
    private JTextField classNameField;

    private final JsonParser parser = new JsonParser();

    public NewFromJsonDialog(Project project, VirtualFile virtualFile) {
        super(project, false, true);
        this.project = project;
        this.virtualFile = virtualFile;

        getPeer().setAppIcons();
        setTitle("New Class From Json");
        init();
    }

    protected JComponent createCenterPanel() {
        jsonTextArea = new EditorTextField("", project, JsonFileType.INSTANCE) {
            @Override
            protected EditorEx createEditor() {
                EditorEx editorEx = super.createEditor();
                editorEx.setHorizontalScrollbarVisible(true);
                editorEx.setVerticalScrollbarVisible(true);
                return editorEx;
            }
        };
        jsonTextArea.setOneLineMode(false);
        jsonTextArea.setPlaceholder("Paste or edit json content here");

        classNameField = new JTextField(10);
        JPanel bottomPanel = JBUI.Panels.simplePanel()
                .addToLeft(new JLabel("New class name:"))
                .addToCenter(classNameField);

        JButton chooseFile = new JButton("Choose json file");
        chooseFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final VirtualFile file = FileChooser.chooseFile(FileChooserDescriptorFactory
                                .createSingleFileDescriptor(JsonFileType.INSTANCE), project, null);
                if (file != null) {
                    final String content = ActionUtils.loadText(file);
                    formatJson(content);
                }
            }
        });
        JButton formatJson = new JButton("Format json");
        formatJson.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                formatJson(jsonTextArea.getText());
            }
        });
        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        topPanel.add(formatJson);
        topPanel.add(chooseFile);

        return JBUI.Panels.simplePanel(0, 10)
                .withMinimumWidth(300)
                .withMinimumHeight(300)
                .addToCenter(jsonTextArea)
                .addToTop(topPanel)
                .addToBottom(bottomPanel);
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        // json
        String json = jsonTextArea.getText().trim();
        if (StringUtil.isEmpty(json)) {
            return new ValidationInfo("Empty json");
        }
        try {
            parser.parse(json);
        } catch (JsonSyntaxException e) {
            return new ValidationInfo("Invalid json");
        }
        // class name
        String text = classNameField.getText().trim();
        text = StringUtil.trimEnd(text, ".java");
        if (!PsiNameHelper.getInstance(project).isQualifiedName(text)) {
            return new ValidationInfo("Class name is invalid");
        }
        if (!Character.isUpperCase(StringUtil.getShortName(text).charAt(0))) {
            return new ValidationInfo("Class name should be title case");
        }
        File file = new File(virtualFile.getPath(), text.replace(".", File.separator) + ".java");
        if (file.exists()) {
            return new ValidationInfo("Target class is exist");
        }
        return null;
    }

    private void formatJson(String content) {
        final PsiFile psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText("NewSnippet.json", JsonFileType.INSTANCE, content);
        CodeStyleManager.getInstance(project).reformat(psiFile);
        jsonTextArea.setText(psiFile.getText());
    }

    public String getClassName() {
        final String text = classNameField.getText().trim();
        return StringUtil.getShortName(text);
    }

    public String getPackageName() {
        final String text = classNameField.getText().trim();
        return StringUtil.getPackageName(text);
    }

    public String getJson() {
        return jsonTextArea.getText().trim();
    }
}
