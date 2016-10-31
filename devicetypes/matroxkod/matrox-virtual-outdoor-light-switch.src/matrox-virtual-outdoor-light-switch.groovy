/**
 *  Matrox Virtual Outdoor Light Switch
 *
 *  Copyright 2016 Andrew Young
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Matrox Virtual Outdoor Light Switch", namespace: "matroxkod", author: "Andrew Young") {
    	capability "Momentary"
		capability "Switch"
        capability "Lock"
        attribute "Daytime", "string"
	}

	simulator {
		// TODO: define status and reply messages here
	}

    tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "off", label: 'Push', action: "momentary.push", backgroundColor: "#ffffff", nextState: "on"
			state "on", label: 'Push', action: "momentary.push", backgroundColor: "#53a7c0"
		}
		main "switch"
		details "switch"
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'switch' attribute

}

// handle commands
def push() {
	log.debug "Executing 'push'"
    on()
    sendEvent(name: "momentary", value: "pushed", isStateChange: true)
}

def on() {
	log.debug "Executing 'on'"
    log.trace "Executing 'on'"
    
    if(device.currentValue("Daytime") == "false")
    {
    	log.trace "Not Daytime, turning switch on"
        sendEvent(name: "switch", value: "on", isStateChange: true)
        sendEvent(name: "momentary", value: "pushed", isStateChange: true)
        runIn(240, off)
    }
    else
    {
    	log.trace "Currently Daytime, not turning switch on"
    }
}

def off() {
	log.debug "Executing 'off'"
    log.trace "Executing 'off'"
	sendEvent(name: "switch", value: "off", isStateChange: true)
    sendEvent(name: "momentary", value: "pushed", isStateChange: true)
}

def lock() {
	sendEvent(name: "Daytime", value: "true")
    log.trace ("Daytime set to ${device.currentValue("Daytime")}")
}

def unlock() {
	sendEvent(name: "Daytime", value: "false")
    log.trace ("Daytime set to ${device.currentValue("Daytime")}")
}