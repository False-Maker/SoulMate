I have recorded this technique into the project memory. Here is the summary of the **Runtime Decoupling / Reflection Adapter Pattern** for future reference:

### 🛠️ Fix Technique: Third-Party SDK Isolation via Reflection

**Scenario:**
When integrating a closed-source or legacy SDK (like Xmov) that causes:
1.  **Build Conflicts:** Duplicate classes, conflicting dependency versions (e.g., different OkHttp/Kotlin versions).
2.  **Architecture Mismatches:** ABI incompatibilities where direct linking causes build failures.
3.  **Stability Risks:** Direct dependency causes the main app to crash if the SDK fails to load (e.g., missing SO files on certain devices).

**Solution Pattern:**
Completely decouple the SDK at compile-time by removing all `import` statements and using **Java Reflection** and **Dynamic Proxies** for all interactions.

**Implementation Checklist:**

1.  **Remove Explicit Dependencies:**
    *   Do not `import com.sdk.package.*` in your code.
    *   Ensure the SDK is only available at runtime (or `compileOnly` if possible, though `implementation` is usually needed for AARs to be packaged).

2.  **Dynamic Class Loading:**
    *   Use `Class.forName("com.sdk.ClassName")` to get handles to SDK classes.
    *   *Benefit:* If the class is missing, you catch `ClassNotFoundException` instead of crashing at startup.

3.  **Method Invocation:**
    *   Use `getMethod("methodName", ParamType::class.java).invoke(instance, args)` for all calls.
    *   *Tip:* For complex configuration objects, pass data as JSON strings or primitive types if the SDK supports it, to avoid reflecting on complex data structures.

4.  **Handling Callbacks (The Tricky Part):**
    *   Use `java.lang.reflect.Proxy.newProxyInstance()` to implement SDK listener interfaces dynamically.
    *   *Code Example:*
        ```kotlin
        val listenerClass = Class.forName("com.sdk.IAvatarListener")
        val proxyListener = Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { _, method, args ->
            when (method.name) {
                "onSuccess" -> handleSuccess(args)
                "onError" -> handleError(args)
            }
        }
        ```

5.  **Defensive Wrapping:**
    *   Wrap all reflection calls in `try-catch (e: Exception)`.
    *   This ensures that even if the SDK implementation changes or crashes, the main app functionality (UI, other services) remains intact.

**Why this is a "Fix":**
It turns a "Build Failed" or "Crash on Launch" problem into a "Feature Unavailable" state, which is much easier to handle gracefully (e.g., by hiding the digital human feature instead of crashing the whole app).<mccoremem id="01KGA8Y35A1HR5KDBY66R51PAW" />