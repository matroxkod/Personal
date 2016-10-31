/**
 *  Matrox Pinger
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
definition(
    name: "Matrox Pinger",
    namespace: "matrox",
    author: "Andrew Young",
    description: "Pings for a certain period of time. Sends alerts if cannot ping.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Select Pingable device") {
        	input (name: "pingDevices", title: "Devices", type: "capability.polling", required: true, multiple: false)
        }
        
    section("Send Notifications") {
    		input "phone", "phone", title: "Enter Phone Number to receive text message", description: "Phone Number", required: true
    }
        
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "Initializing"
    state.unsuccessfulPollCount = 0
    state.maxUnsuccessfulPollCount = 60
    
    subscribe(pingDevices, "ttl", pollHandler)
    poll()
    schedule("0 0/1 * 1/1 * ? *", poll)
}

def poll() {
    log.debug "Polling ${pingDevices}"
    pingDevices.poll()
    state?.unsuccessfulPollCount = state?.unsuccessfulPollCount + 1
    log.debug "Polled ${pingDevices}"
    
	if (state?.unsuccessfulPollCount >= state?.maxUnsuccessfulPollCount)
    {
    	log.warn ("Could not successfully ping ${pingDevices} after ${state?.unsuccessfulPollCount} attempts")
        sendSms(phone, "Could not successfully ping ${pingDevices} after ${state?.unsuccessfulPollCount} attempts")
        // TODO: Reboot
    }
}

def pollHandler (evt) {
	log.debug "pollHandler called ${evt.value}"
    def splitValue = evt.value.split(" ")
    def intValue = splitValue[0].toInteger()
    def strValue = splitValue[1]
    log.debug "intValue is ${intValue} strValue is ${strValue}"
    if(intValue >= 1 && (strValue == "m" || strValue == "h"))
    {
    	if (strValue == "h")
        {
        	// Ping is already screwed
            state?.unsuccessfulPollCount = state?.maxUnsuccessfulPollCount
        }
        log.debug "UnsuccessfulPollCount incremented to ${state?.unsuccessfulPollCount}"
    }
    else
    {
    	// Reset poll count
    	state?.unsuccessfulPollCount = 0
    }
}