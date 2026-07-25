package org.ivcode.aimo.examples.mcp.weather

import org.ivcode.aimo.server.mcp.annotation.McpContext
import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.ivcode.aimo.server.mcp.annotation.McpService
import org.ivcode.aimo.server.mcp.annotation.McpTool
import org.ivcode.aimo.server.mcp.annotation.McpPrompt
import org.slf4j.LoggerFactory

/**
 * Example MCP service providing weather information.
 *
 * This service demonstrates:
 * - Multiple @McpTool methods for different weather operations
 * - @McpPrompt for reusable prompt templates
 * - @McpContext for accessing request metadata
 * - Parameter validation and type conversion
 *
 * @McpService is itself a @Component, so explicit @Component is not needed.
 */
@McpService
class WeatherService {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Get current weather for a city.
     */
    @McpTool(
        name = "get-weather",
        description = "Get current weather conditions for a city"
    )
    fun getWeather(
        @McpParam(description = "City name", required = true) city: String,
        @McpParam(description = "Include forecast", required = false) includeForecast: Boolean = false,
        @McpContext context: Map<String, Any?>
    ): String {
        val requestId = context["requestId"]
        logger.info("Fetching weather for $city (request: $requestId)")

        // Simulate weather data
        val weather = when (city.lowercase()) {
            "seattle" -> "Rainy, 62°F, Humidity: 85%"
            "san francisco" -> "Cloudy, 65°F, Humidity: 75%"
            "new york" -> "Sunny, 75°F, Humidity: 50%"
            "los angeles" -> "Sunny, 85°F, Humidity: 30%"
            "chicago" -> "Partly cloudy, 70°F, Humidity: 60%"
            else -> "Unknown city: $city"
        }

        return if (includeForecast) {
            "$weather\n\nForecast: 5-day rain expected tomorrow"
        } else {
            weather
        }
    }

    /**
     * Get weather alert for a city.
     */
    @McpTool(
        name = "get-weather-alert",
        description = "Check if there's a weather alert for a city"
    )
    fun getWeatherAlert(
        @McpParam(description = "City name") city: String
    ): String {
        return when (city.lowercase()) {
            "seattle" -> "⚠️ Warning: Heavy rain expected, possible flooding"
            "san francisco" -> "✓ No active alerts"
            "new york" -> "✓ No active alerts"
            "los angeles" -> "⚠️ Warning: Heat advisory, high of 105°F"
            "chicago" -> "✓ No active alerts"
            else -> "Unknown city: $city"
        }
    }

    /**
     * Compare weather between two cities.
     */
    @McpTool(
        name = "compare-weather",
        description = "Compare weather conditions between two cities"
    )
    fun compareWeather(
        @McpParam(description = "First city") city1: String,
        @McpParam(description = "Second city") city2: String
    ): String {
        val weather1 = getWeather(city1, false, emptyMap())
        val weather2 = getWeather(city2, false, emptyMap())

        return """
            $city1: $weather1
            $city2: $weather2
            
            Warmer: ${if (city1.lowercase() in listOf("los angeles", "new york")) city1 else city2}
        """.trimIndent()
    }

    /**
     * Get weather for multiple cities.
     */
    @McpTool(
        name = "get-weather-batch",
        description = "Get weather for multiple cities at once"
    )
    fun getWeatherBatch(
        @McpParam(description = "Comma-separated list of cities") cities: String
    ): String {
        val cityList = cities.split(",").map { it.trim() }
        val results = mutableListOf<String>()

        for (city in cityList) {
            val weather = getWeather(city, false, emptyMap())
            results.add("$city: $weather")
        }

        return results.joinToString("\n")
    }

    /**
     * Get help on weather tools.
     */
    @McpPrompt(
        name = "weather-help",
        description = "Get help on available weather tools"
    )
    fun getHelp(): String {
        return """
            # Weather Service Help

            Available tools:
            1. **get-weather** - Get current weather for a city
               - Parameters: city (required), includeForecast (optional)
               - Example: Get weather for Seattle with forecast

            2. **get-weather-alert** - Check weather alerts for a city
               - Parameters: city (required)
               - Example: Check if there's a severe weather alert for NYC

            3. **compare-weather** - Compare weather between two cities
               - Parameters: city1, city2 (both required)
               - Example: Compare weather in Los Angeles vs Seattle

            4. **get-weather-batch** - Get weather for multiple cities
               - Parameters: cities (comma-separated list)
               - Example: Get weather for New York, Chicago, and Denver

            Supported cities: Seattle, San Francisco, New York, Los Angeles, Chicago
        """.trimIndent()
    }

    /**
     * Get weather forecast explanation.
     */
    @McpPrompt(
        name = "forecast-explanation",
        description = "Explain weather forecast terms"
    )
    fun explainForecast(): String {
        return """
            # Weather Forecast Explanation

            **Common Weather Terms:**
            - **Humidity**: Percentage of moisture in the air (0-100%)
            - **Dew Point**: Temperature at which air becomes saturated
            - **Barometric Pressure**: Weight of air in the atmosphere
            - **Visibility**: How far you can see (in miles/km)
            - **Wind Chill**: How cold it feels due to wind

            **Weather Icons:**
            - ☀️ Clear/Sunny
            - ⛅ Partly cloudy
            - ☁️ Cloudy
            - 🌧️ Rain
            - ⛈️ Thunderstorm
            - ❄️ Snow

            **Alert Levels:**
            - 🟢 Green: No alerts
            - 🟡 Yellow: Advisory
            - 🔴 Red: Warning
            - ⚫ Black: Emergency
        """.trimIndent()
    }

    /**
     * Get weather analysis template.
     */
    @McpPrompt(
        name = "weather-analysis",
        description = "Get template for weather analysis"
    )
    fun getAnalysisTemplate(
        @McpParam(description = "City or region to analyze") location: String = "your location"
    ): String {
        return """
            # Weather Analysis for $location

            ## Current Conditions
            - Temperature: [Get with get-weather tool]
            - Conditions: [Sunny/Rainy/Cloudy/etc]
            - Humidity: [Percentage]

            ## Alerts & Warnings
            [Use get-weather-alert to check]

            ## Forecast
            - Next 24h: [Description]
            - Next 5d: [Trend]

            ## Impact
            - Outdoor Activities: [Safe/Not recommended]
            - Travel: [Safe/Difficult/Not recommended]
            - Health: [No concerns/Precautions needed]

            ## Recommendations
            - Clothing: [Light/Warm/Rain gear/etc]
            - Activities: [Suggestions based on weather]
        """.trimIndent()
    }
}

