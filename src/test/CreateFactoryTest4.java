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

/**
 * @author John Kenrinus Lee
 * @version 2018-07-09
 */
public class CreateFactoryTest4 {
    private int age;
    private String name;

    // Notice: trigger on class and trigger on constructor is different

    public CreateFactoryTest4() {
        defaultConfig("Empty"); // transfer to newInstance()
    }

    public CreateFactoryTest4(int age, String name) {
        this.age = age;
        this.name = name;
        this.doInit(); // transfer to newInstance(int, String)
        defaultConfig("Full"); // transfer to newInstance(int, String)
    }

    protected void doInit() { }

    protected CreateFactoryTest4 defaultConfig(String config) { return this; }
}
