# Wicket Hot reload

[Apache Wicket](http://wicket.apache.org) is a Java Web framework providing clean separation between markup and logic.

This project allows hot reloading and auto compilations of classes (makes development faster)

# What's inside

Proof of concept of autocompilation and reloading of Wicket classes. You can run it via the Start class in test sources. 

If you edit the `AnotherPage` class, you should see in your logs that it is compiled and reloaded. 
To see the change you will need to refresh your browser, you can do the same with `SimplePanel`.

If you edit the `StartupPage` class, it will not work (but it will when I will find out how) because this is the HomePage of your application.
The actual workaround is resetting the `Application` class, but it is barely acceptable, so it is disabled.

# How to use it

If you really want to use it and you don't fear atomic war or Ewoks invasion, you'll have to package this project, 
add it as a dependency to your project and set up the following system properties :

  * wicket.hotreload.auto : (true|false) to enable auto compile when your application is accessed
  
  * wicket.hotreload.watch : (true|false) to enable auto compile when sources are modified
  
  * wicket.hotreload.enabled : (true|false) to enable hot reloading without auto compile
  
  * wicket.hotreload.rootPackage : the name of the root package where reloading should be active
  
  * wicket.hotreload.sourceRoots : comma separated list of directories containing the sources you want to compile (defaults to src/main/java,src/main/resources)
  
  * wicket.hotreload.targetClasses : directory used as compilation target (defaults to tmp/classes)
  

You will also need to update your web.xml to use the `HotReloadingWicketFilter` where all the magic is started :

    <filter>
        <filter-name>wicket</filter-name>
        <filter-class>codetroopers.wicket.web.HotReloadingWicketFilter</filter-class>
        <init-param>
            <param-name>applicationClassName</param-name>
            <param-value>your.application.class.name</param-value>
        </init-param>
    </filter>
    
# Bug tracker

Have a bug? Please create an issue here on GitHub!

https://github.com/code-troopers/wicket-hot-reload/issues


# Special notes

The implementation provided here is open for pull request or further integration into WicketStuff.

Thanks to Xavier Hanin for providing the initial implementation of this in his project [Rest.x](http://restx.io).
Thanks to [PlayFramework!](http://playframework.org) for the initial implementation

# Copyright and license

Copyright 2013 Code-Troopers.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this work except in compliance with the License.
You may obtain a copy of the License in the LICENSE file, or at:

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
