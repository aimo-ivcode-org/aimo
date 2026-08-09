# Package org.ivcode.aimo.core.properties

Spring Boot configuration properties for AIMO under the `aimo.*` YAML prefix.

This package contains all Spring `@ConfigurationProperties` classes that map YAML configuration 
to strongly-typed Kotlin objects. These properties are loaded from `application.yml` and 
validated at startup.

Responsibilities
----------------
- Define `AimoProperties` root properties class (prefix: `aimo`)
- Define scope-related properties (`AimoChatScopeProperties`) for role-based access control
- Define guard-rail properties (Phase 7 future feature)
- Define interceptor configuration options
- Provide defaults for all properties
- Perform Spring validation via `@Validated`

Key Configuration Structure
---------------------------
```yaml
aimo:
  data-dir: ./data/conversations          # Directory for file-based DAO
  
  scope:                                  # Chat scope definitions (Phase 2)
    admin:
      tool-refs: ["deleteUser", ...]
      system-message-refs: ["adminContext", ...]
    research:
      tool-refs: ["queryData", ...]
      system-message-refs: ["researchContext", ...]
  
  mcp:                                    # MCP server configuration (aimo-mcp-client)
    required: false                       # Allow startup if servers unreachable
    discovery-interval-minutes: 60        # Auto-refresh interval
    servers:
      - id: my-weather-server
        transport: stdio
        command: ["./weather-server"]
        scope: ["research"]               # Optional scope restriction
  
  guard-rails: {}                         # Guard-rail configs (Phase 7)
  
  interceptors: {}                        # Interceptor configs
```

Property Classes
-----------------
- **`AimoProperties`**: Root configuration under `aimo.*`
- **`AimoChatScopeProperties`**: Scope configuration (`aimo.scope.*`)
- **`AimoChatGuardRailProperties`**: Guard-rail configuration (Phase 7)
- **`AimoChatInterceptorProperties`**: Interceptor configuration

Integration Points
-------------------
- Loaded by Spring Boot via `@EnableConfigurationProperties`
- Injected into `AimoConfig` for model, scope, and interceptor setup
- Injected into `aimo-mcp-client` for MCP server discovery
- Validated at startup; misconfigurations cause application startup failure

Developer Notes
----------------
- Add new properties here for any new AIMO feature requiring YAML configuration
- Use descriptive property names; use camelCase for Kotlin, kebab-case for YAML
- Provide sensible defaults for all properties
- Document properties with Javadoc/KDoc so IDEs show helpful tooltips
- Use Spring validation annotations (`@Valid`, `@NotEmpty`, etc.) for fail-fast validation
- Properties are immutable in typical YAML config but may be mutable if set programmatically

Example Property Access
------------------------
```kotlin
@Bean
fun myService(aimoProps: AimoProperties): MyService {
    val dataDir = aimoProps.dataDir  // "./data/conversations"
    val adminScope = aimoProps.scope["admin"]
    val toolRefs = adminScope?.toolRefs ?: emptyList()
    return MyService(dataDir, toolRefs)
}
```


