# Thread Safety & SecurityContextHolder

## Critical Requirement

This application MUST use the **thread-per-request model** for Spring Security integration to work correctly.

## Why?

Spring Security uses `ThreadLocal<SecurityContext>` to store authentication information. `ThreadLocal` provides per-thread storage that is automatically isolated:

```kotlin
// Simplified view of how Spring Security works
object SecurityContextHolder {
    private val threadLocal: ThreadLocal<SecurityContext> = ThreadLocal()
    
    fun setContext(context: SecurityContext) = threadLocal.set(context)
    fun getContext(): SecurityContext = threadLocal.get()
}
```

With thread-per-request:
- **Request A** executes on **Thread 1** → `threadLocal` in Thread 1 stores A's SecurityContext
- **Request B** executes on **Thread 2** → `threadLocal` in Thread 2 stores B's SecurityContext
- **No cross-request leakage** ✅

## Configuration

Default Spring Boot Tomcat already uses thread-per-request, but be explicit:

```yaml
server:
  tomcat:
    threads:
      min-spare: 10      # Keep 10 threads ready
      max: 200           # Allow up to 200 concurrent requests
    accept-count: 100    # Queue up to 100 waiting requests
    max-connections: 10000
```

### What This Means

| Setting | Value | Meaning |
|---------|-------|---------|
| `min-spare` | 10 | Always have 10 threads ready in the pool |
| `max` | 200 | Never create more than 200 threads |
| `accept-count` | 100 | Queue up to 100 requests if all threads busy |

Under normal load, you'll use 10-20 threads. Under spike loads, you can scale up to 200. Request 201 will be queued.

## What NOT to Do

❌ **Do NOT use virtual threads** without reactive security changes:
```kotlin
// WRONG: Virtual threads break ThreadLocal
server.servlet.ignore-default-model-on-redirect = true
spring.threads.virtual.enabled = true  // ❌ Breaks SecurityContextHolder
```

Virtual threads are lightweight but don't guarantee same-thread execution per request. Use reactive security instead:
```kotlin
// RIGHT: Use reactive security with virtual threads
spring.security.reactive.enabled = true
```

❌ **Do NOT use async processing** without proper context propagation:
```kotlin
// WRONG: Async loses SecurityContext
@PostMapping("/chat")
fun chat(): CompletableFuture<String> {
    return CompletableFuture.supplyAsync {
        // SecurityContextHolder.getContext() is EMPTY here! ❌
        val user = userProvider.getCurrentUser()
    }
}
```

**Correct approach**: Capture context before async:
```kotlin
// RIGHT: Capture context before going async
@PostMapping("/chat")
fun chat(): CompletableFuture<String> {
    val securityContext = SecurityContextHolder.getContext()
    
    return CompletableFuture.supplyAsync {
        SecurityContextHolder.setContext(securityContext)
        try {
            val user = userProvider.getCurrentUser()
        } finally {
            SecurityContextHolder.clearContext()
        }
    }
}
```

Or better: use Spring Security's `@Async` support:
```kotlin
// BEST: Spring handles context propagation
@Async
fun processAsync() {
    val user = userProvider.getCurrentUser()  // ✅ Works!
}
```

## Monitoring

Monitor thread pool usage:

```kotlin
// Add this endpoint to check thread pool status
@GetMapping("/actuator/health/threadpool")
fun threadPoolHealth(): Map<String, Any> {
    val executor = Executors.newFixedThreadPool(1) as ThreadPoolExecutor
    return mapOf(
        "active-threads" to executor.activeCount,
        "core-threads" to executor.corePoolSize,
        "max-threads" to executor.maximumPoolSize,
        "queue-size" to executor.queue.size
    )
}
```

## aimo-specific: AimoUserProvider

When implementing `AimoUserProvider`, you can safely call:

```kotlin
class MyUserProvider : AimoUserProvider {
    override fun getCurrentUser(): AimoUser {
        // Safe because... we're in servlet thread context
        val auth = SecurityContextHolder.getContext().authentication
        // ...
    }
}
```

This works because:
1. HTTP request arrives on Thread N (from thread pool)
2. Spring Security Filter sets `SecurityContextHolder` in Thread N
3. Your controller calls `userProvider.getCurrentUser()`
4. Still on Thread N → can access `ThreadLocal`
5. Response sent on Thread N
6. Thread returned to pool, `ThreadLocal` gets cleared

## Production Checklist

- [ ] Verify `server.tomcat.threads.max` is appropriate for load
- [ ] Monitor active thread count (should be < max most of the time)
- [ ] If using reactive APIs, use `spring.security.reactive.enabled = true`
- [ ] If using `@Async`, verify Spring Security context propagation works
- [ ] If using virtual threads, switch to reactive security
- [ ] Load test to find optimal thread pool size

## References

- [Spring Security Architecture](https://spring.io/projects/spring-security)
- [Servlet Thread Model](https://tomcat.apache.org/tomcat-10.0-doc/config/executor.html)
- [ThreadLocal Best Practices](https://docs.oracle.com/javase/8/docs/api/java/lang/ThreadLocal.html)

