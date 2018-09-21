# json2pojo
a intellij idea plugin (android studio work) for json to pojo, Resource interface, and create factory intention
  
About android related dependencies, I use as source code: (studio-master-dev branch)
1. com.android.tools.perflib; (include tools/base/commons)
2. com.android.ddmlib; (include tools/base/commons)
  
If you need jar or maven:
* for 'android/intellij plugin':  
    just use **sdk-tools.jar** in application directory
* 'gradle project': 
    ```
    dependencies {
      compile 'com.squareup.haha:haha:2.0.4'
      compile 'com.android.tools.ddms:ddmlib:26.1.4'
    }
    ```
