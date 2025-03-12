# Trimlight Hubitat Driver

A Hubitat driver for controlling Trimlight LED devices. This driver provides a comprehensive interface to control your Trimlight LED system through the Hubitat home automation platform.

## Features

- **Device Control**
  - Turn lights on/off
  - Set brightness levels
  - Switch between manual and timer modes
  - Real-time device state updates through configurable polling

- **Effect Management**
  - View and activate saved effects
  - Create and save built-in effects
  - Create and save custom pattern effects
  - Preview effects before saving
  - Delete existing effects
  - Adjust effect speed

- **Schedule Management**
  - Daily schedules with multiple repetition options
    - Today only
    - Every day
    - Weekdays only
    - Weekends only
  - Calendar schedules for specific date ranges
  - Enable/disable schedules
  - View active schedules

## Installation

1. In your Hubitat hub's admin interface, go to **Drivers Code**
2. Click **New Driver**
3. Copy the contents of `drivers/trimlight-controller.groovy` into the editor
4. Click **Save**

## Device Setup

1. Go to **Devices** in your Hubitat hub
2. Click **Add Virtual Device**
3. Set a device name and select "Trimlight Controller" as the Type
4. Click **Save Device**
5. Configure the following required settings:
   - **Client ID**: Your Trimlight API client ID
   - **Client Secret**: Your Trimlight API client secret
   - **Device ID**: Your Trimlight device ID
   - **Poll Interval**: How often to refresh device state (default: 10 minutes)
   - **Log Level**: Logging verbosity (INFO/DEBUG/TRACE)

## Usage

### Basic Controls

- **On/Off**: Use the switch capability to turn lights on/off
- **Brightness**: Use the level capability to adjust brightness (0-100%)
- **Mode**: Set device mode to off, manual, or timer
  ```groovy
  setDeviceMode("manual")  // Options: "off", "manual", "timer"
  ```

### Effect Management

#### View/Activate Effects
```groovy
viewEffect(effectId)  // View/activate a saved effect
```

#### Built-in Effects
```groovy
// Preview a built-in effect
previewBuiltinEffect(
    mode,        // Effect mode (0-179)
    speed,       // Animation speed (0-255)
    brightness,  // LED brightness (0-255)
    pixelLength, // Number of LEDs (1-90)
    reverse      // Reverse animation (true/false)
)

// Save a built-in effect
saveBuiltinEffect(
    effectId,    // Effect ID (-1 for new)
    name,        // Effect name
    mode,        // Effect mode (0-179)
    speed,       // Animation speed (0-255)
    brightness,  // LED brightness (0-255)
    pixelLength, // Number of LEDs (1-90)
    reverse      // Reverse animation (true/false)
)
```

#### Custom Pattern Effects
```groovy
// Preview a custom pattern
previewCustomEffect(
    mode,       // Effect mode (0-16)
    speed,      // Animation speed (0-255)
    brightness, // LED brightness (0-255)
    pixels      // Array of pixel objects
)

// Save a custom pattern
saveCustomEffect(
    effectId,   // Effect ID (-1 for new)
    name,       // Effect name
    mode,       // Effect mode (0-16)
    speed,      // Animation speed (0-255)
    brightness, // LED brightness (0-255)
    pixels      // Array of pixel objects
)
```

Pixel Object Format:
```json
{
    "index": 0,     // Pixel segment index (0-29)
    "count": 5,     // Number of LEDs in segment (0-60)
    "color": 16711680, // RGB color (decimal)
    "disable": false   // Whether segment is disabled
}
```

#### Delete Effects
```groovy
deleteEffect(effectId)  // Delete a saved effect
```

### Schedule Management

#### Daily Schedules
```groovy
// Update or create a daily schedule
updateDailySchedule(
    scheduleId,  // Schedule ID (0 or 1)
    effectId,    // Effect to use
    startTime,   // Start time (HH:mm)
    endTime,     // End time (HH:mm)
    repetition,  // "today", "everyday", "weekdays", "weekend"
    enabled      // true/false
)
```

#### Calendar Schedules
```groovy
// Add a calendar schedule
addCalendarSchedule(
    effectId,    // Effect to use
    startDate,   // Start date (MM-dd)
    endDate,     // End date (MM-dd)
    startTime,   // Start time (HH:mm)
    endTime      // End time (HH:mm)
)

// Delete a calendar schedule
deleteCalendarSchedule(scheduleId)
```

#### View Schedules
```groovy
// List all schedules or only enabled ones
listSchedules(enabledOnly)  // true/false
```

## Logging

The driver supports three logging levels:
- **INFO**: Basic operational information
- **DEBUG**: Detailed operation information
- **TRACE**: Full API request/response data

## License

Licensed under the MIT License. See the LICENSE file for details.
