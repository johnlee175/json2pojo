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

/**
 * @author John Kenrinus Lee
 * @version 2018-07-02
 */
public abstract class ThreeParamRunnable<R, S, T> implements Runnable {
    private final R r;
    private final S s;
    private final T t;

    public ThreeParamRunnable(R r, S s, T t) {
        this.r = r;
        this.s = s;
        this.t = t;
    }

    @Override
    public final void run() {
        run(r, s, t);
    }

    public abstract void run(R r, S s, T t);
}
