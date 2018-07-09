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
public class CreateFactoryTest3 {
    private int age;
    private String name;

    public CreateFactoryTest3() { }

    // here not work because of newInstance(int)
    public CreateFactoryTest3(int age) {
        this.age = age;
    }

    public CreateFactoryTest3(int age, String name) {
        this.age = age;
        this.name = name;
    }

    public CreateFactoryTest3 newInstance(int age) {
        this.age = age;
        return this;
    }
}
