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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-03
 */
public class NewTplLayoutAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        String platform = ActionUtils.isAndroidStudio() ? "Android Studio" : "Intellij IDEA";
        Notifications.Bus.notify(new Notification("New Template", "Notification",
                "This is a test of notify from " + platform, NotificationType.INFORMATION));

        String className = Messages.showInputDialog("Input class", "Entry", null);
        if (className == null) {
            return;
        }
        PsiClass psiClass = JavaPsiFacade.getInstance(anActionEvent.getProject())
                .findClass(className, GlobalSearchScope.allScope(anActionEvent.getProject()));
        if (psiClass != null) {
            for (PsiClass aClass : ClassInheritorsSearch.search(psiClass).findAll()) {
//                ReferencesSearch.search();
//                AnnotatedMembersSearch.search();
//                ClassesWithAnnotatedMembersSearch.search();
//                MethodImplementationsSearch.getOverridingMethods();
//                OverridingMethodsSearch.search();
//                IndexPatternSearch.search();
                for (PsiMethod psiMethod : aClass.getAllMethods()) {
                    for (PsiReference ref : MethodReferencesSearch.search(psiMethod)) {
                        if (ref.getElement().getParent() instanceof PsiNewExpression) {
                            System.out.println(ref);
                        }
                    }
                }
            }
        }
    }
}
