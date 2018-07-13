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
package test;

import java.util.Random;

import com.johnsoft.base.utils.Initializer;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-13
 */
public class TestInit {
    public static void main(String[] args) {
        new C();
    }
}

class S {
    private String xx;
    S(String x) { xx = x; }
}

class A extends S implements Initializer.OnInitializedCallback {
    public A() {
        this("finish");
    }

    public A(String x) {
        super(x);
        System.out.println("A create " + x);
    }

    @Override
    public void onInitialized() {
        System.out.println("A onInitialized");
    }
}

class B extends A {
    private final String a = String.valueOf(new Random().nextInt());

    public B() {
        this(4);
        System.out.println("B created");
    }

    public B(int x) {
        super("XYZ");
        System.out.println("B create " + x);
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        System.out.println("B onInitialized -> " + a);
    }
}

class C extends B {
}