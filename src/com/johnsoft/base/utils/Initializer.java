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

import org.jetbrains.annotations.NotNull;

/**
 * Like: <br/>
 * <pre><code>
 * public class Base implements Initializer.OnInitializedCallback {
 *      protected Initializer initializer;
 *
 *      public Base() {
 *          initializer = new Initializer(this);
 *          ... do something base class needs ...
 *      }
 *
 *      public void onInitialized() {
 *          ... do something after subclass constructor called ...
 *      }
 * }
 *
 * class Sub extends Base {
 *      public Sub() {
 *          ... do something sub class needs ...
 *          initializer.markInitialized(this);
 *      }
 *
 *      public void onInitialized() {
 *          super.onInitialized();
 *          ... override and do something when initialize callback needs ...
 *      }
 * }
 * </code></pre>
 *
 * @author John Kenrinus Lee
 * @version 2018-07-13
 */
public class Initializer {
    private OnInitializedCallback callback;
    private boolean initialized;

    public Initializer(OnInitializedCallback callback) {
        this.callback = callback;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized(@NotNull Object thiz, @NotNull Class callClass) {
        if (thiz.getClass().equals(callClass)) {
            markInitialized();
        }
    }

    public void markInitialized(@NotNull Object thiz) {
        final String callClass = Thread.currentThread().getStackTrace()[2].getClassName();
        if (thiz.getClass().getCanonicalName().equals(callClass)) {
            markInitialized();
        }
    }

    public void markInitialized() {
        if (!initialized) {
            initialized = true;
            if (callback != null) {
                callback.onInitialized();
                callback = null;
            }
        }
    }

    public interface OnInitializedCallback {
        void onInitialized();
    }
}