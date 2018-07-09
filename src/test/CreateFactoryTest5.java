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

import java.util.ArrayList;

/**
 * @author John Kenrinus Lee
 * @version 2018-07-09
 */
public class CreateFactoryTest5 {
    private int age;
    private String name;
    private CreateFactoryTest5 test5 = this;
    private ArrayList<String> list = new ArrayList<>();

    public CreateFactoryTest5(int age, String name) {
        this.age = age;
        this.name = name;
        doInit(); // if one fail(see following statement), no transfer, because of execute order;
        list.clear(); // Fail: not work, the method belongs to list(ArrayList);
        test5.doInit(); // Fail: we hard check test5 is this yet;
        defaultConfig("Full").doInit(); // Fail: we hard check the first method call return this or new instance yet;
    }

    protected void doInit() { }

    protected CreateFactoryTest5 defaultConfig(String config) { return this; }
}
