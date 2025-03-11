/**
 *  Trimlight Controller
 *
 *  A Hubitat driver for controlling Trimlight LED devices
 *
 *  Copyright 2025
 *
 *  Licensed under the MIT License
 */

metadata {
    definition (
        name: "Trimlight Controller",
        namespace: "trimlight",
        author: "klieber"
    ) {
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "ColorControl"
        capability "Refresh"

        // Custom commands
        command "setDeviceMode", [[
            name: "Mode*",
            type: "ENUM",
            description: "Set the device operating mode",
            constraints: ["off", "manual", "timer"]
        ]]
        command "setEffect", [[name:"Effect ID*", type:"NUMBER", description:"Effect ID to activate"]]
        command "setEffectSpeed", [[name:"Speed*", type:"NUMBER", description:"Speed (0-255)"]]
        command "previewBuiltinEffect", [
            [name:"Mode*", type:"NUMBER", description:"Effect mode (0-179)"],
            [name:"Speed*", type:"NUMBER", description:"Speed (0-255)"],
            [name:"Brightness*", type:"NUMBER", description:"Brightness (0-255)"],
            [name:"PixelLength*", type:"NUMBER", description:"Pixel length (1-90)"],
            [name:"Reverse", type:"BOOL", description:"Reverse direction"]
        ]
        command "previewCustomEffect", [
            [name:"Mode*", type:"NUMBER", description:"Effect mode"],
            [name:"Speed*", type:"NUMBER", description:"Speed (0-255)"],
            [name:"Brightness*", type:"NUMBER", description:"Brightness (0-255)"],
            [name:"Pixels*", type:"JSON_OBJECT", description:"Array of pixel objects with index, count, color, and disable fields"]
        ]

        // Schedule management commands
        command "listSchedules", [[
            name: "Enabled Only",
            type: "BOOL",
            description: "Only show enabled schedules"
        ]]
        command "updateDailySchedule", [
            [name:"Schedule ID*", type:"NUMBER", description:"Schedule ID (0 or 1)"],
            [name:"Effect ID*", type:"NUMBER", description:"Effect ID to use"],
            [name:"Start Time*", type:"STRING", description:"Start time in HH:mm format"],
            [name:"End Time*", type:"STRING", description:"End time in HH:mm format"],
            [name:"Repetition*", type:"ENUM", description:"Repetition type", constraints: ["today", "everyday", "weekdays", "weekend"]],
            [name:"Enabled", type:"BOOL", description:"Whether schedule is enabled", defaultValue: true]
        ]
        command "addCalendarSchedule", [
            [name:"Effect ID*", type:"NUMBER", description:"Effect ID to use"],
            [name:"Start Date*", type:"STRING", description:"Start date in MM-dd format"],
            [name:"End Date*", type:"STRING", description:"End date in MM-dd format"],
            [name:"Start Time*", type:"STRING", description:"Start time in HH:mm format"],
            [name:"End Time*", type:"STRING", description:"End time in HH:mm format"]
        ]
        command "deleteCalendarSchedule", [[
            name:"Schedule ID*",
            type:"NUMBER",
            description:"ID of calendar schedule to delete"
        ]]
    }

    preferences {
        input name: "clientId", type: "text", title: "Client ID", required: true
        input name: "clientSecret", type: "password", title: "Client Secret", required: true
        input name: "deviceId", type: "text", title: "Device ID", required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    log.debug "Installed..."
    initialize()
}

def updated() {
    log.debug "Updated..."
    initialize()
}

def initialize() {
    log.debug "Initializing..."
    refresh()
}

def parse(String description) {
    logDebug "parse(${description})"
}

// Required capability methods
def on() {
    // If we're already in timer mode, keep it, otherwise use manual mode
    def targetMode = (state.deviceMode == "timer") ? "timer" : "manual"
    setDeviceMode(targetMode)
}

def off() {
    setDeviceMode("off")
}

def setLevel(level, duration=0) {
    def scaledLevel = Math.round(level * 2.55) // Convert 0-100 to 0-255
    logDebug "setLevel(${level}, ${duration}) scaled to ${scaledLevel}"

    if (state.currentEffect) {
        def result = apiRequest("/v1/oauth/resources/device/effect/save", "POST", [
            deviceId: deviceId,
            id: state.currentEffect.id,
            brightness: scaledLevel
        ])
        if (result != null) {
            sendEvent(name: "level", value: level)
            return true
        }
    }
    return false
}

def setColor(Map colorMap) {
    logDebug "setColor(${colorMap})"
    // TODO: Implement color control through API
}

def refresh() {
    logDebug "refresh()"
    def result = getDeviceDetails()
    logDebug "refresh() response: ${result}"
    if (result) {
        // Map switchState to both switch and deviceMode
        def modeMap = [
            0: "off",
            1: "manual",
            2: "timer"
        ]
        def mode = modeMap[result.switchState] ?: "off"
        state.deviceMode = mode
        sendEvent(name: "deviceMode", value: mode)

        // Determine if switch should be on
        def switchOn
        if (mode == "off") {
            switchOn = false
        } else if (mode == "manual") {
            switchOn = true
        } else {  // timer mode
            switchOn = hasActiveSchedule(result)
        }

        sendEvent(name: "switch", value: switchOn ? "on" : "off")
        logDebug "Updated device mode to: ${mode}"
        logDebug "Updated switch state to: ${switchOn ? 'on' : 'off'}"

        if (result.currentEffect) {
            state.currentEffect = result.currentEffect
            if (result.currentEffect.brightness) {
                def level = Math.round(result.currentEffect.brightness / 2.55)
                sendEvent(name: "level", value: level)
                logDebug "Updated brightness level to: ${level}"
            }
        }
        return true
    }
    logDebug "refresh() failed - no response data"
    return false
}

// Custom command implementations
def setEffect(effectId) {
    logDebug "setEffect(${effectId})"
    def result = apiRequest("/v1/oauth/resources/device/effect/view", "POST", [
        deviceId: deviceId,
        payload: [
            id: effectId
        ]
    ])
    if (result != null) {
        refresh()  // Update device state after changing effect
        return true
    }
    return false
}

def setEffectSpeed(speed) {
    logDebug "setEffectSpeed(${speed})"
    if (state.currentEffect) {
        def result = apiRequest("/v1/oauth/resources/device/effect/save", "POST", [
            deviceId: deviceId,
            id: state.currentEffect.id,
            speed: speed
        ])
        if (result != null) {
            return true
        }
    }
    return false
}

def previewBuiltinEffect(mode, speed, brightness, pixelLength, reverse=false) {
    logDebug "previewBuiltinEffect(${mode}, ${speed}, ${brightness}, ${pixelLength}, ${reverse})"
    def result = apiRequest("/v1/oauth/resources/device/effect/preview", "POST", [
        deviceId: deviceId,
        payload: [
            category: 1,  // Built-in effects are always category 1
            mode: mode,
            speed: speed,
            brightness: brightness,
            pixelLen: pixelLength,
            reverse: reverse
        ]
    ])
    return result != null
}

// Add a new method for previewing custom patterns
def previewCustomEffect(mode, speed, brightness, pixels) {
    logDebug "previewCustomEffect(${mode}, ${speed}, ${brightness}, pixels:${pixels})"
    def result = apiRequest("/v1/oauth/resources/device/effect/preview", "POST", [
        deviceId: deviceId,
        payload: [
            category: 2,  // Custom effects are always category 2
            mode: mode,
            speed: speed,
            brightness: brightness,
            pixels: pixels
        ]
    ])
    return result != null
}

// Schedule management methods
def listSchedules(Boolean enabledOnly = false) {
    logDebug "listSchedules(enabledOnly: ${enabledOnly})"
    def result = getDeviceDetails()
    if (result) {
        // Process daily schedules
        def dailySchedules = result.daily.collect { schedule ->
            def repetitionMap = [
                0: "today",
                1: "everyday",
                2: "weekdays",
                3: "weekend"
            ]
            [
                type: "daily",
                id: schedule.id,
                effectId: schedule.effectId,
                startTime: "${schedule.startTime.hours}:${schedule.startTime.minutes.toString().padLeft(2, '0')}",
                endTime: "${schedule.endTime.hours}:${schedule.endTime.minutes.toString().padLeft(2, '0')}",
                repetition: repetitionMap[schedule.repetition] ?: "unknown",
                enabled: schedule.enable
            ]
        }

        // Process calendar schedules
        def calendarSchedules = result.calendar.collect { schedule ->
            [
                type: "calendar",
                id: schedule.id,
                effectId: schedule.effectId,
                startDate: "${schedule.startDate.month}-${schedule.startDate.day}",
                endDate: "${schedule.endDate.month}-${schedule.endDate.day}",
                startTime: "${schedule.startTime.hours}:${schedule.startTime.minutes.toString().padLeft(2, '0')}",
                endTime: "${schedule.endTime.hours}:${schedule.endTime.minutes.toString().padLeft(2, '0')}"
            ]
        }

        // Filter enabled schedules if requested
        if (enabledOnly) {
            dailySchedules = dailySchedules.findAll { it.enabled }
        }

        // Log schedules
        logDebug "Found ${dailySchedules.size()} daily schedules and ${calendarSchedules.size()} calendar schedules"

        dailySchedules.each { schedule ->
            log.info "Daily Schedule ${schedule.id}: ${schedule.startTime}-${schedule.endTime}, Repetition: ${schedule.repetition}, Effect: ${schedule.effectId}, Enabled: ${schedule.enabled}"
        }

        calendarSchedules.each { schedule ->
            log.info "Calendar Schedule ${schedule.id}: ${schedule.startDate} to ${schedule.endDate}, ${schedule.startTime}-${schedule.endTime}, Effect: ${schedule.effectId}"
        }

        return [daily: dailySchedules, calendar: calendarSchedules]
    }
    return [daily: [], calendar: []]
}

def updateDailySchedule(scheduleId, effectId, startTime, endTime, repetition, enabled=true) {
    logDebug "updateDailySchedule(id: ${scheduleId}, effectId: ${effectId}, start: ${startTime}, end: ${endTime}, repetition: ${repetition}, enabled: ${enabled})"

    // Validate schedule ID
    if (scheduleId != 0 && scheduleId != 1) {
        log.error "Invalid daily schedule ID: ${scheduleId}. Must be 0 or 1."
        return false
    }

    // Parse start and end times
    def (startHour, startMinute) = startTime.split(":").collect { it.toInteger() }
    def (endHour, endMinute) = endTime.split(":").collect { it.toInteger() }

    // Map repetition string to value
    def repetitionMap = [
        "today": 0,
        "everyday": 1,
        "weekdays": 2,
        "weekend": 3
    ]
    def repetitionValue = repetitionMap[repetition]
    if (repetitionValue == null) {
        log.error "Invalid repetition value: ${repetition}"
        return false
    }

    def result = apiRequest("/v1/oauth/resources/device/daily/save", "POST", [
        deviceId: deviceId,
        payload: [
            id: scheduleId,
            enable: enabled,
            effectId: effectId,
            repetition: repetitionValue,
            startTime: [
                hours: startHour,
                minutes: startMinute
            ],
            endTime: [
                hours: endHour,
                minutes: endMinute
            ],
            currentDate: getCurrentDate()
        ]
    ])

    if (result != null) {
        log.info "Daily schedule ${scheduleId} updated successfully"
        refresh()  // Refresh device state to get updated schedules
        return true
    }
    log.error "Failed to update daily schedule"
    return false
}

def addCalendarSchedule(effectId, startDate, endDate, startTime, endTime) {
    logDebug "addCalendarSchedule(effectId: ${effectId}, startDate: ${startDate}, endDate: ${endDate}, startTime: ${startTime}, endTime: ${endTime})"

    // Parse dates
    def (startMonth, startDay) = startDate.split("-").collect { it.toInteger() }
    def (endMonth, endDay) = endDate.split("-").collect { it.toInteger() }

    // Parse times
    def (startHour, startMinute) = startTime.split(":").collect { it.toInteger() }
    def (endHour, endMinute) = endTime.split(":").collect { it.toInteger() }

    def result = apiRequest("/v1/oauth/resources/device/calendar/save", "POST", [
        deviceId: deviceId,
        payload: [
            effectId: effectId,
            startDate: [
                month: startMonth,
                day: startDay
            ],
            endDate: [
                month: endMonth,
                day: endDay
            ],
            startTime: [
                hours: startHour,
                minutes: startMinute
            ],
            endTime: [
                hours: endHour,
                minutes: endMinute
            ]
        ]
    ])

    if (result != null) {
        log.info "Calendar schedule added successfully"
        refresh()  // Refresh device state to get updated schedules
        return true
    }
    log.error "Failed to add calendar schedule"
    return false
}

def deleteCalendarSchedule(scheduleId) {
    logDebug "deleteCalendarSchedule(${scheduleId})"
    def result = apiRequest("/v1/oauth/resources/device/calendar/delete", "POST", [
        deviceId: deviceId,
        payload: [
            id: scheduleId
        ]
    ])

    if (result != null) {
        log.info "Calendar schedule ${scheduleId} deleted successfully"
        refresh()  // Refresh device state to get updated schedules
        return true
    }
    log.error "Failed to delete calendar schedule ${scheduleId}"
    return false
}

// Private helper methods
private setDeviceMode(String mode) {
    logDebug "setDeviceMode(${mode})"
    def modeMap = [
        "off": 0,
        "manual": 1,
        "timer": 2
    ]
    def modeValue = modeMap[mode]
    if (modeValue == null) {
        log.error "Invalid mode: ${mode}"
        return false
    }

    def result = setDeviceSwitchState(modeValue)
    if (result) {
        state.deviceMode = mode
        // Update the switch capability state (on for manual/timer, off for off)
        sendEvent(name: "deviceMode", value: mode)
        return true
    }
    return false
}

private setDeviceSwitchState(state) {
    logDebug "setDeviceSwitchState(${state})"
    def result = apiRequest("/v1/oauth/resources/device/update", "POST", [
        deviceId: deviceId,
        payload: [
          switchState: state
        ]
    ])
    if (result != null) {
        sendEvent(name: "switch", value: state == 0 ? "off" : "on")
        return true
    }
    return false
}

private logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

// HTTP request helper methods
private apiRequest(String path, String method = "GET", Map body = null) {
    def baseUrl = "https://trimlight.ledhue.com/trimlight"
    def timestamp = new Date().time
    def accessToken = calculateAccessToken(clientId, clientSecret, timestamp)

    def headers = [
        "Content-Type": "application/json",
        "Accept": "application/json",
        "authorization": accessToken,
        "S-ClientId": clientId,
        "S-Timestamp": "${timestamp}"  // Ensure timestamp is sent as string
    ]

    def params = [
        uri: "${baseUrl}${path}",
        headers: headers,
        requestContentType: "application/json",
        contentType: "application/json",
        timeout: 30
    ]

    if (body) {
        params.body = groovy.json.JsonOutput.toJson(body)
    }

    logDebug "API Request - URL: ${params.uri}"
    logDebug "API Request - Headers: ${headers}"
    logDebug "API Request - Body: ${params.body}"

    try {
        def resp
        switch (method.toUpperCase()) {
            case "GET":
                httpGet(params) { response -> resp = response }
                break
            case "POST":
                httpPost(params) { response -> resp = response }
                break
            case "PUT":
                httpPut(params) { response -> resp = response }
                break
            default:
                log.error "Unsupported HTTP method: ${method}"
                return null
        }

        logDebug "Response received - Status: ${resp.status}"
        logDebug "Response headers: ${resp.headers}"

        if (resp.data) {
            logDebug "Parsed response: ${resp.data}"
            if (resp.data.code == 0) {
                return resp.data.payload
            } else {
                log.error "API request failed with code ${resp.data.code}: ${resp.data.desc}"
            }
        } else {
            log.error "Response status ${resp.status} but no data received"
            logDebug "Raw response: ${resp}"
        }
    } catch (e) {
        log.error "API request failed: ${e.message}"
        log.error "Stack trace: ${e.getStackTrace()}"
    }
    return null
}

private calculateAccessToken(String clientId, String clientSecret, long timestamp) {
    def data = "Trimlight|${clientId}|${timestamp}"
    def mac = javax.crypto.Mac.getInstance("HmacSHA256")
    def secretKeySpec = new javax.crypto.spec.SecretKeySpec(clientSecret.getBytes(), "HmacSHA256")
    mac.init(secretKeySpec)
    def bytes = mac.doFinal(data.getBytes())
    return bytes.encodeBase64().toString()
}

private getCurrentDate() {
    def now = new Date()
    def cal = Calendar.getInstance()
    cal.setTime(now)

    return [
        year: cal.get(Calendar.YEAR) - 2000,  // API expects years since 2000
        month: cal.get(Calendar.MONTH) + 1,    // Calendar months are 0-based
        day: cal.get(Calendar.DAY_OF_MONTH),
        weekday: cal.get(Calendar.DAY_OF_WEEK), // Sunday=1, Monday=2, etc.
        hours: cal.get(Calendar.HOUR_OF_DAY),
        minutes: cal.get(Calendar.MINUTE),
        seconds: cal.get(Calendar.SECOND)
    ]
}

private now() {
    return new Date().getTime()
}

private getDeviceDetails() {
    logDebug "getDeviceDetails()"
    return apiRequest("/v1/oauth/resources/device/get", "POST", [
        deviceId: deviceId,
        currentDate: getCurrentDate()
    ])
}

private isScheduleActive(schedule) {
    def now = new Date()
    def cal = Calendar.getInstance()
    cal.setTime(now)

    def currentHour = cal.get(Calendar.HOUR_OF_DAY)
    def currentMinute = cal.get(Calendar.MINUTE)
    def currentMonth = cal.get(Calendar.MONTH) + 1  // Calendar months are 0-based
    def currentDay = cal.get(Calendar.DAY_OF_MONTH)
    def currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)  // Sunday=1, Monday=2, etc.

    // Convert schedule times to comparable values (minutes since midnight)
    def currentTime = currentHour * 60 + currentMinute
    def scheduleStart = schedule.startTime.hours * 60 + schedule.startTime.minutes
    def scheduleEnd = schedule.endTime.hours * 60 + schedule.endTime.minutes

    if (schedule.type == "daily") {
        // Check if schedule is enabled
        if (!schedule.enable) return false

        // Check repetition type
        switch (schedule.repetition) {
            case 0:  // today only
                return currentTime >= scheduleStart && currentTime < scheduleEnd
            case 1:  // everyday
                return currentTime >= scheduleStart && currentTime < scheduleEnd
            case 2:  // weekdays
                def isWeekday = currentDayOfWeek >= Calendar.MONDAY && currentDayOfWeek <= Calendar.FRIDAY
                return isWeekday && currentTime >= scheduleStart && currentTime < scheduleEnd
            case 3:  // weekend
                def isWeekend = currentDayOfWeek == Calendar.SATURDAY || currentDayOfWeek == Calendar.SUNDAY
                return isWeekend && currentTime >= scheduleStart && currentTime < scheduleEnd
            default:
                return false
        }
    } else if (schedule.type == "calendar") {
        // For calendar schedules, first check if current date falls within schedule dates
        def startMonth = schedule.startDate.month
        def startDay = schedule.startDate.day
        def endMonth = schedule.endDate.month
        def endDay = schedule.endDate.day

        // Create comparable date values (month * 100 + day)
        def currentDate = currentMonth * 100 + currentDay
        def scheduleStartDate = startMonth * 100 + startDay
        def scheduleEndDate = endMonth * 100 + endDay

        // Handle year wrap (e.g., Dec 25 - Jan 5)
        if (scheduleEndDate < scheduleStartDate) {
            // If we're in the start year portion (e.g., Dec 25-31)
            if (currentDate >= scheduleStartDate) {
                return currentTime >= scheduleStart && currentTime < scheduleEnd
            }
            // If we're in the end year portion (e.g., Jan 1-5)
            if (currentDate <= scheduleEndDate) {
                return currentTime >= scheduleStart && currentTime < scheduleEnd
            }
            return false
        }

        // Normal date range within same year
        if (currentDate >= scheduleStartDate && currentDate <= scheduleEndDate) {
            return currentTime >= scheduleStart && currentTime < scheduleEnd
        }
    }

    return false
}

private hasActiveSchedule(deviceDetails) {
    // Check daily schedules
    def hasActiveDaily = deviceDetails.daily?.any { schedule ->
        isScheduleActive([
            type: "daily",
            enable: schedule.enable,
            repetition: schedule.repetition,
            startTime: schedule.startTime,
            endTime: schedule.endTime
        ])
    }
    if (hasActiveDaily) return true

    // Check calendar schedules
    return deviceDetails.calendar?.any { schedule ->
        isScheduleActive([
            type: "calendar",
            startDate: schedule.startDate,
            endDate: schedule.endDate,
            startTime: schedule.startTime,
            endTime: schedule.endTime
        ])
    }
}
