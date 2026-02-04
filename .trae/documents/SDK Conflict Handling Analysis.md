确认是 **Xmov 数字人 SDK (XmovAvatarDriver.kt)**。该模块采用 **全反射 (Full Reflection)** 机制来集成 SDK，这是一种非常特殊的“黑盒”调用方式。

### **1. 业务场景 (Scenario)**
-   **背景**：项目需要集成 Xmov 数字人 SDK 来驱动 3D 形象（语音、动作）。
-   **问题 ("架构不支持")**：
    -   可能是为了 **强解耦**：避免主项目直接依赖 SDK 的具体版本，防止 SDK 内部依赖（如旧版 OkHttp/Gson）与主项目冲突。
    -   可能是 **ABI/架构兼容性**：SDK 可能包含特定的 Native 库，直接引用可能在某些 CPU 架构（如模拟器 x86）上导致 `LinkageError` 或崩溃。
    -   可能是 **动态下发**：为将来动态加载 dex/apk 做准备（虽然目前 AAR 在本地，但代码风格支持动态化）。
-   **解决方案**：不使用 `import com.xmov...`，而是通过 Java 反射机制在运行时动态查找类、创建对象和调用方法。

### **2. 实现方法详解 (Implementation Details)**

#### **A. 动态加载类与获取单例**
代码不直接使用 `IXmovAvatar` 类，而是通过字符串类名加载：
```kotlin
// 1. 加载接口/类定义
val avatarClass = Class.forName("com.xmov.metahuman.sdk.IXmovAvatar")
// 2. 获取 Companion 对象 (Kotlin 的静态伴生对象)
val companionField = avatarClass.getDeclaredField("Companion")
companionField.isAccessible = true
val companion = companionField.get(null)
// 3. 调用 Companion.get() 获取 SDK 单例实例
val getMethod = companion.javaClass.getMethod("get")
avatarInstance = getMethod.invoke(companion)
```

#### **B. 动态构建配置对象 (InitConfig)**
SDK 初始化需要 `InitConfig` 对象，代码通过反射构造它，甚至兼容了不同的构造函数签名：
```kotlin
val initConfigClass = Class.forName("com.xmov.metahuman.sdk.data.InitConfig")
// 查找构造函数（参数数量 >= 4 的那个）
val constructor = initConfigClass.constructors.firstOrNull { it.parameterTypes.size >= 4 }
val initConfig = if (constructor != null) {
    constructor.newInstance(appId, appSecret, gateway, configJson)
} else {
    // 如果找不到特定构造函数，使用无参构造 + 字段反射赋值
    val instance = initConfigClass.getDeclaredConstructor().newInstance()
    initConfigClass.getDeclaredField("appId").apply { set(instance, appId) }
    // ... 设置其他字段
    instance
}
```

#### **C. 动态实现回调接口 (Dynamic Proxy)**
这是最精彩的部分：SDK 需要一个 `IAvatarListener` 回调，但我们不能在代码里 `implements` 它（因为没 import）。于是使用了 **Java 动态代理 (Proxy)**：
```kotlin
val listenerClass = Class.forName("com.xmov.metahuman.sdk.IAvatarListener")
// 创建一个动态代理对象来实现接口
val listener = java.lang.reflect.Proxy.newProxyInstance(
    listenerClass.classLoader,
    arrayOf(listenerClass) // 要实现的接口列表
) { _, method, args ->
    // 拦截接口方法调用
    handleListenerCallback(method.name, args) // 转发给我们可以处理的函数
}
```

#### **D. 动态方法调用**
所有功能调用（说话、暂停、销毁）都通过 `invoke` 执行：
```kotlin
// 调用 speak 方法
val speakMethod = avatarInstance!!.javaClass.getMethod(
    "speak", 
    String::class.java, Boolean::class.java, Boolean::class.java
)
speakMethod.invoke(avatarInstance, text, true, true)
```

### **总结**
这种实现方式将 SDK 视为一个完全的“外部插件”，主项目与 SDK 之间没有编译时的符号链接，只有运行时的字符串契约。这完美解释了您印象中的“架构不支持所以用特殊途径（反射）调用”。