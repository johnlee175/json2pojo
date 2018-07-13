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
package com.johnsoft.base.utils;

import java.io.File;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.expr.ConstructorCall;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-13
 */
public class InitializerInjector {
    public static void main(String[] args) throws NotFoundException {
        final ClassPool classPool = ClassPool.getDefault();
        classPool.insertClassPath(args[0]);
        inject(classPool, classPool.getCtClass("test.B"));
        inject(classPool, classPool.getCtClass("test.A"));
        inject(classPool, classPool.getCtClass("test.C"));
    }

    private static final String INITIALIZER = "com.johnsoft.base.utils.Initializer";
    private static final String ON_INITIALIZED_CALLBACK = "com.johnsoft.base.utils.Initializer$OnInitializedCallback";
    private static final String FIELD_NAME = "initializer";
    private static final String METHOD_NAME = "markInitialized";

    public static void inject(ClassPool classPool, CtClass ctClass) {
        try {
            final CtClass objectClass = classPool.getCtClass("java.lang.Object");
            boolean shouldInject = false;
            CtClass currentClass = ctClass;
            outer:
            while (currentClass != null && !currentClass.equals(objectClass)) {
                final CtClass[] interfaces = currentClass.getInterfaces();
                if (interfaces != null && interfaces.length > 0) {
                    for (CtClass anInterface : interfaces) {
                        if (ON_INITIALIZED_CALLBACK.equals(anInterface.getName())) {
                            shouldInject = true;
                            break outer;
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            if (!shouldInject) {
                return;
            }
            if (ctClass.isFrozen()) {
                ctClass.defrost();
            }
            if (currentClass == ctClass) { // base class
                final int modifiers = ctClass.getModifiers();
                if (Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers)) {
                    return;
                }
                boolean changed = false;
                try {
                    ctClass.getDeclaredField(FIELD_NAME);
                } catch (NotFoundException e) {
                    ctClass.addField(CtField.make("protected final " + INITIALIZER + " " + FIELD_NAME + ";",
                            ctClass));
                    changed = true;
                }
                final CtConstructor[] constructors = ctClass.getDeclaredConstructors();
                if (constructors != null && constructors.length > 0) {
                    for (CtConstructor constructor : constructors) {
                        final ExprCheck check = new ExprCheck(FIELD_NAME, INITIALIZER, INITIALIZER,
                                "(" + ExprCheck.signatureType(ON_INITIALIZED_CALLBACK) + ")V") {
                            @Override
                            public void edit(NewExpr e) throws CannotCompileException {
                                super.edit(e);
                                if (e.getClassName().equals(targetMethod)
                                        && e.getSignature().equals(methodSignature)
                                        && instanceSignature.equals(signatureType(e.getClassName()))) {
                                    setMethodOk(true);
                                }
                            }
                        };
                        constructor.instrument(check);
                        if (!check.isExistsExpr()) {
                            constructor.insertBeforeBody(FIELD_NAME + " = new " + INITIALIZER + "(this);");
                            changed = true;
                        }
                    }
                } else {
                    CtConstructor constructor = CtNewConstructor.defaultConstructor(ctClass);
                    constructor.insertBeforeBody("super();\n" + FIELD_NAME + " = new " + INITIALIZER + "(this);");
                    ctClass.addConstructor(constructor);
                    changed = true;
                }
                if (changed) {
                    ctClass.writeFile(getClassWrittenDirectory(ctClass));
                }
            } else { // subclass
                boolean changed = false;
                try {
                    ctClass.getField(FIELD_NAME);
                } catch (NotFoundException e) {
                    if (currentClass.isFrozen()) {
                        currentClass.defrost();
                    }
                    currentClass.addField(CtField.make("protected final " + INITIALIZER + " " + FIELD_NAME + ";",
                            currentClass));
                    // no changed: don't currentClass.writeFile(...) here
                }
                final CtConstructor[] constructors = ctClass.getDeclaredConstructors();
                if (constructors != null && constructors.length > 0) {
                    for (CtConstructor constructor : constructors) {
                        final ExprCheck check = new ExprCheck(FIELD_NAME, INITIALIZER, METHOD_NAME,
                                "(Ljava/lang/Object;Ljava/lang/Class;)V") {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                super.edit(m);
                                if (m.getMethodName().equals(targetMethod)
                                        && m.getSignature().equals(methodSignature)
                                        && instanceSignature.equals(signatureType(m.getClassName()))) {
                                    setMethodOk(true);
                                }
                            }
                        };
                        constructor.instrument(check);
                        if (!check.isExistsExpr()) {
                            constructor.insertAfter(FIELD_NAME + "." + METHOD_NAME + "(this, "
                                    + ctClass.getName() + ".class);");
                            changed = true;
                        }
                    }
                } else {
                    CtConstructor constructor = CtNewConstructor.defaultConstructor(ctClass);
                    constructor.insertAfter("super();\n" + FIELD_NAME + "." + METHOD_NAME + "(this, "
                            + ctClass.getName() + ".class);");
                    ctClass.addConstructor(constructor);
                    changed = true;
                }
                if (changed) {
                    ctClass.writeFile(getClassWrittenDirectory(ctClass));
                }
            }
        } catch (Throwable thr) {
            thr.printStackTrace();
        }
    }

    private static String getClassWrittenDirectory(CtClass ctClass) throws Exception {
        final File classFile = new File(ctClass.getURL().toURI());
        final String classInDir = classFile.getParentFile().getAbsolutePath();
        final String packagePath = ctClass.getPackageName().replace(".", File.separator);
        final String baseDir = classInDir.replace(packagePath, "");
        if (baseDir.isEmpty()) {
            return baseDir;
        }
        int i = baseDir.length() - 1;
        while (i >= 0) {
            if (baseDir.charAt(i) == File.separatorChar) {
                --i;
            } else {
                break;
            }
        }
        return baseDir.substring(0, i + 1);
    }

    private static class ExprCheck extends ExprEditor {
        private boolean instanceOk;
        private boolean methodOk;
        private boolean constructorIsSuper;
        final String targetInstance;
        final String instanceSignature;
        final String targetMethod;
        final String methodSignature;

        ExprCheck(String targetInstance, String instanceType,
                  String targetMethod, String methodSignature) {
            this.targetInstance = targetInstance;
            this.instanceSignature = signatureType(instanceType);
            this.targetMethod = targetMethod;
            this.methodSignature = methodSignature;
        }

        final void setInstanceOk(boolean instanceOk) {
            this.instanceOk = instanceOk;
        }

        final void setMethodOk(boolean methodOk) {
            this.methodOk = methodOk;
        }

        final boolean isExistsExpr() {
            return (instanceOk && methodOk) || !constructorIsSuper;
        }

        @Override
        public void edit(ConstructorCall c) throws CannotCompileException {
            super.edit(c);
            constructorIsSuper = c.isSuper();
        }

        @Override
        public void edit(FieldAccess f) throws CannotCompileException {
            super.edit(f);
            if (f.getFieldName().equals(targetInstance) && f.getSignature().equals(instanceSignature)) {
                setInstanceOk(true);
            }
        }

        static String signatureType(String typeName) {
            return 'L' + typeName.replace('.', '/') + ';';
        }
    }
}
