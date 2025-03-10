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
    setDeviceSwitchState(1) // 1 = manual mode
}

def off() {
    setDeviceSwitchState(0) // 0 = off
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
    def result = apiRequest("/v1/oauth/resources/device/get", "POST", [
        deviceId: deviceId,
        payload: [
            currentDate: getCurrentDate()
        ]
    ])
    logDebug "refresh() response: ${result}"
    if (result) {
        // Update device state based on response
        sendEvent(name: "switch", value: result.switchState == 0 ? "off" : "on")
        logDebug "Updated switch state to: ${result.switchState == 0 ? 'off' : 'on'}"

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

// Private helper methods
private setDeviceSwitchState(state) {
    logDebug "setDeviceSwitchState(${state})"
    def result = apiRequest("/v1/oauth/resources/device/update", "POST", [
        deviceId: deviceId,
        switchState: state
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

    def response = []
    def semaphore = [:]
    def requestStartTime = now()

    synchronized(semaphore) {
        try {
            def handler = [semaphore: semaphore, response: response, startTime: requestStartTime]
            switch (method.toUpperCase()) {
                case "GET":
                    asynchttpGet(handleResponse, params, handler)
                    break
                case "POST":
                    asynchttpPost(handleResponse, params, handler)
                    break
                case "PUT":
                    asynchttpPut(handleResponse, params, handler)
                    break
                default:
                    log.error "Unsupported HTTP method: ${method}"
                    return null
            }
            logDebug "Waiting for response... (timeout: 30s)"

            // Wait for the response with a timeout
            try {
                semaphore.wait(30000) // 30 second timeout
            } catch (InterruptedException e) {
                log.error "Wait interrupted: ${e.message}"
            }

            def elapsed = now() - requestStartTime
            logDebug "Done waiting for response (elapsed: ${elapsed}ms)"

            if (elapsed >= 30000) {
                log.error "Request timed out after ${elapsed}ms"
                return null
            }
        } catch (e) {
            log.error "API request failed: ${e.message}"
            log.error "Stack trace: ${e.getStackTrace()}"
            return null
        }
    }

    // Return the response data if we got one
    if (response && response.size() > 0) {
        return response[0]
    }
    logDebug "No response data received"
    return null
}

private handleResponse(resp, data) {
    def startTime = data.startTime
    def elapsed = now() - startTime
    logDebug "Response received after ${elapsed}ms"

    def semaphore = data.semaphore
    def response = data.response

    synchronized(semaphore) {
        try {
            logDebug "Response received - Status: ${resp.status}"
            logDebug "Response headers: ${resp.headers}"

            if (resp.json != null) {
                logDebug "Response is JSON"
                def result = resp.json
                logDebug "Parsed response: ${result}"
                if (result.code == 0) {
                    response.add(result.payload)
                    logDebug "Added payload to response"
                } else {
                    log.error "API request failed with code ${result.code}: ${result.desc}"
                }
            } else if (resp.data) {
                logDebug "Response has data, attempting to parse"
                def jsonSlurper = new groovy.json.JsonSlurper()
                def result = jsonSlurper.parseText(resp.data)
                logDebug "Parsed response: ${result}"
                if (result.code == 0) {
                    response.add(result.payload)
                    logDebug "Added payload to response"
                } else {
                    log.error "API request failed with code ${result.code}: ${result.desc}"
                }
            } else {
                log.error "Response status ${resp.status} but no data received"
                logDebug "Raw response: ${resp}"
            }
        } catch (e) {
            log.error "Error processing response: ${e.message}"
            log.error "Stack trace: ${e.getStackTrace()}"
            if (e.message.contains("parseText")) {
                logDebug "Raw response data: ${resp.data}"
            }
        } finally {
            logDebug "Notifying semaphore"
            semaphore.notify()
        }
    }
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
    return System.currentTimeMillis()
}
