* [English](README-CN.md)
* [版本信息](https://github.com/iqiyi/TaskManager/wiki/ReleaseNote-CN)

 Task manager 是一个支持关系依赖的Android任务管理SDK。它具备处理复杂任务依赖关系的能力，可用于加速一些耗时任务执行过程。它支持 "与依赖"，“或依赖", 支持”并行任务“、”串行任务“、"事件任务"等等，API 非常灵活易用。除此之外，它还实现了任务兜底机制，为任务异步并发后的稳定性提供了技术支撑。

### 特性

#### 1. 任务分发: 

![img](art/task_dispatcher.png)

#### 2. 事件与数据分发:

![img](art/event_data_dispatcher.png)

 

#### 3. 优势：

a)  通过为任务设置依赖关系，将串行执行的任务，尽量可能的异步并发执行。充分利用CPU的运算能力；

![img](art/parallel_task.png)

 b) 任务执行保证：通过在具体业务之前添加"TM.needTaskSync"调用，可确保异步逻辑在具体业务需要时，已执行完成。

![img](art/need_task_sync.png)

c) 任务循环依赖检测：在debug 模式下，内部实现了循环依赖任务检测能力。如果发现循环依赖的情况，程序将给出崩溃提示。

###  Getting Started

add dependencies in your "build.gradle" file

``` Java
dependencies {
    implementation 'com.iqiyi.taskmanager:taskmanager:1.3.5'
}
```

###  开发指南

* [API document wiki](https://github.com/iqiyi/TaskManager/wiki)

###  License

TaskManager is [Apache v2.0 Licensed](http://gitlab.qiyi.domain/licaifu/TaskManger/LICENSE.txt).

