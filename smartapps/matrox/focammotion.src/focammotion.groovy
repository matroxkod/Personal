/**
 *  FocamMotion
 *
 *  Copyright 2015 Andrew Young
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
    name: "FocamMotion",
    namespace: "matrox",
    author: "Andrew Young",
    description: "Registers selected non-HD Foscam cameras as motion detectors and subscribes them to detect motion.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: false)

preferences 
{

	section("Select Foscam Cameras")
    {
		input (name: "motionCameras", title: "Cameras", type: "capability.motionSensor", required: true, multiple: true) 
    }
    
    section("Enter length of motion event 150 - 180 seconds")
    {
    	input (name: "motionThreshold", title: "Motion Threshold", type: "number", required: true, description: "150", range: "120..180")
    }
    
    section("Refresh Only? (Only polls. Will not create sensors)")
    {
    	input (name: "motionPoll", title: "Poll", type: "bool", required: true)
    }    
}
 
def installed()
{
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def uninstalled() 
{
	removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) 
{
    delete.each 
    {
        deleteChildDevice(it.deviceNetworkId)
    }
}

def updated()
{
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize()
{
	log.debug "Initializing"
    state.globalMotion = [:]
    state.childDeviceNamespace = "smartthings"
    state.expirationTime = [:] 
    state.lastLogTime = [:]
    state.timeExpired = [:]
    state.pollRate = 1
    state.failureLimit = 60
    state.failureCount = [:]
    
    if(motionPoll == false)
    {
    	deleteSensors()
    }
    
    loadCamerasAssociations()
    
    // Ensure camera is synced
    SyncDateTime()   
    //schedule("0 0 5 1/1 * ? *", SyncDateTime)
    
    subscribe(motionCameras, "currentLog", logHandler)    
	QueryLog()
   	
    // Run every 2 minutes
	schedule("0 0/${state?.pollRate} * 1/1 * ? *", QueryLog)
}

def deleteSensors()
{
	def delete
	// Delete any that are no longer in settings
	if (!motionCameras)
    {
		log.debug "delete all Motion Sensors"
		    delete.each
            {
        		deleteChildDevice(it.deviceNetworkId)
    		}
    }
    else
    {
        delete = getChildDevices().findAll
        {
            ((!motionCameras.contains(it.device.deviceNetworkId)))
        }
    }
     
	log.trace("Deleting ${delete.size()} FoscamMotion Motion Detectors")
	delete.each
    {
		deleteChildDevice(it.deviceNetworkId)
	}
}

def logHandler(evt)
{
	log.debug "LogHandler called"
    Date nowTime = new Date()
    Date now = Date.parse("yyyy-MM-dd HH:mm:ss", nowTime.format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone('America/Phoenix')))    
    def returnVal = evt.value.trim().split(',,')
    def dni = "${state.childDeviceNamespace}.${returnVal[0]}"
    state?.failureCount[dni] = 0
    def childDev = getChildDevice(dni)
    log.debug "Received log event for $childDev"
        
    // Look for motion event
    String targetString = "var log_text='"
    String targetNewLine = "\\n"
    def cleaned = returnVal[2]?.replaceAll(targetString, "")   
    def motionEvents = cleaned?.trim().tokenize(targetNewLine)
    
    String targetPhrase = "motio"
    
    // Search list in reverse. First occurance of targetPhrase will be most recent motion detected
    def occurance = motionEvents.reverse().find { it.contains(targetPhrase) }
    def cleanedOccurance = occurance?.replaceAll(" $targetPhrase", "")
    log.debug ("$childDev Most Recent Motion Event: $cleanedOccurance - The time now is now ${now}")
    def dates = cleanedOccurance?.split(" ")
    def finalDate = "2012-01-01 00:00:00"
    if(dates?.size() >= 2) 
    {
    	finalDate = "${dates[dates.size() - 2]} ${dates[dates.size() - 1]}"
    }

	// Date and Time of most recent motion detected event
   	Date parsedDate = Date.parse("yyyy-MM-dd HH:mm:ss", finalDate)
    log.debug ("Parsed Log Date is ${parsedDate}")

    def motionThesholdMS = motionThreshold *1000
  
    def deviceCurrentStatus = childDev.currentState("motion").value
    
    // Store log time in Milliseconds
    if(deviceCurrentStatus == "active")
    {
    	// We need to check if we need to update the last logged motion and update threshold
        if( state?.lastLogTime[dni] < parsedDate.time)
        {
            state?.lastLogTime[dni] = parsedDate.time
            state?.expirationTime[dni] = now.time + motionThesholdMS
            state?.timeExpired[dni] = false
            log.debug("Status is active. Last Log time updated to MostRecentLogTime of ${parsedDate.time}")
        }
    }
    else
    {
    	// Just store last logged motion
        log.debug "$childDev LastLogTime: ${state?.lastLogTime[dni]} MostRecentLogTime: ${parsedDate.time}"
        if( state?.lastLogTime[dni] < parsedDate.time)
        {
            state?.lastLogTime[dni] = parsedDate.time
            state?.timeExpired[dni] = false
            log.debug("Status is inactive. Last Log time updated to MostRecentLogtime of ${parsedDate.time}")
        }
    }
        
    String status = "inactive"  
    
    // Log may have occured right as poll ended. Adjust to inflate.
    def adjustedLogTime = state?.lastLogTime[dni] + (((state?.pollRate + 2) * 60) * 1000)
    if(deviceCurrentStatus == "inactive")
    {
    	// Check if the last logged motion is occurring
        log.debug "$childDev LastLogTime ${state?.lastLogTime[dni]} adjusted to $adjustedLogTime Now ${now.time}"
        if(adjustedLogTime < now.time)
        {
        	// No motion is occuring
            log.debug "No motion is occuring. Time Expired? ${state?.timeExpired[dni]}"
        	status = "inactive"
        }
        else
        {
            // A motion is occuring
        	log.debug "A motion is occuring. Time Expired? ${state?.timeExpired[dni]}"
            if(state?.timeExpired[dni] == false)
            {
            	state?.expirationTime[dni] = now.time + motionThesholdMS
            	status = "active"
            }
        }
    } 
    else if(deviceCurrentStatus == "active")
    {
    	if(now.time >= state?.expirationTime[dni])
        {
        	status = "inactive"
            state?.timeExpired[dni] = true
        }
        else
        {
        	status = "active"
        }
    }
    else
    {
    	log.debug "$childDev received unknown status $deviceCurrentStatus"       
    }
    
    log.debug "$childDev Current device status is ${deviceCurrentStatus}"
    if(status == "active")
    {
    	log.debug "$childDev Status set to active... Expires ${state?.expirationTime[dni]}... It is now ${now.time}"
    }
    else
    {
    	log.debug "$childDev Status set to inactive... Expired at ${state?.expirationTime[dni]}... It is now ${now.time}"
    }
    
    // Set motion
    def isChange = childDev.isStateChange(childDev, "motion", status)
    def isDisplayed = isChange
    log.debug "device $childDev, found $dni, statusChanged = ${isChange}, value = ${status}"
    
    childDev.sendEvent(name: "motion", value: status, isStateChange: isChange, displayed: isDisplayed)    
}

def loadCamerasAssociations() {
	log.debug "Loading cameras with device Ids"
   
    for (i in 0..motionCameras?.size() - 1)
    {
    	def dev = motionCameras[i]
        if(dev != null)
        {
            def devID = dev.deviceNetworkId
            if(devID != null)
            {
                state.globalMotion.put("${state.childDeviceNamespace}.$devID", "$dev")
            }  
        } 
    }
    
    // Create Child Device    
    def devices = state.globalMotion.collect 
    {dni ->
    	def dniKey = dni.key
    	def d = getChildDevice(dniKey)
        log.debug ("Checking for child device $dniKey")
        
        if (!d)
        {
        	def labelName = "${dni.value} Motion Detector"
            log.debug "About to create child device with dni $dniKey"
            d = addChildDevice(state.childDeviceNamespace, "Motion Detector", dniKey, null, [label: "${labelName}", completedSetup: true]) 
            state?.lastLogTime.put(dniKey, "0")
            state?.expirationTime.put(dniKey, "0")
            state?.timeExpired.put(dniKey, false)
            state?.timeExpired[dniKey] = false
            //state?.failureCount.put(dniKey, 0)
            state?.failureCount[dniKey] = 0
            
            // Set starting status
            String status = "inactive"
            def isChange = d.isStateChange(d, "motion", status)
            def isDisplayed = isChange
            log.debug "device $d, found $dni.value, statusChanged = ${isChange}, value = ${status}"
            log.debug "device $d initialized as lastlogtime = ${state?.lastLogTime[dniKey]} expirationTime = ${state?.expirationTime[dniKey]} timeExpired = ${state?.timeExpired[dniKey]} failureCount = ${state?.failureCount[dniKey]}"

            d.sendEvent(name: "motion", value: status, isStateChange: isChange, displayed: isDisplayed)
            log.debug "Created ${d.displayName} with id $dniKey"
        } 
        else
        {
            state?.failureCount.put(dniKey, 0)
            log.debug "Already found a device with dni $dniKey."
        }        
    }
}

def QueryLog() 
{
	log.debug ("Querying logs")
    for (i in 0..motionCameras.size() - 1)
    {
        def dev = motionCameras[i]
        if(dev)
        {
        	log.debug ("dev $dev dni ${dev.deviceNetworkId} fc ${state?.failureCount[motionCameras[i].deviceNetworkId]} fl ${state?.failureLimit}")
            def fc = state?.failureCount[motionCameras[i].deviceNetworkId]
            if(!fc)
            {
            	state?.failureCount[motionCameras[i].deviceNetworkId] = 0
            }
            
        	if(state?.failureCount[dev.deviceNetworkId] >= state?.failureLimit)
            {
            	log.debug ("Failure Count for $dev is ${state?.failureCount[dev.deviceNetworkId]} which is higher than ${state?.failureLimit}")
            }
            //state?.failureCount[dev.deviceNetworkId] = state?.failureCount[dev.deviceNetworkId] + 1            
            motionCameras[i]?.getCurrentLog()
            log.debug ("Queried $dev. Current failure count is ${state?.failureCount[dev.deviceNetworkId]}")
        } 
        else
        {
            log.debug ("Could not pull logs. Device is null.")
        }
    }    
}

def SyncDateTime()
{
	log.debug ("Syncing Date and Time")
    for (i in 0..motionCameras.size() - 1)
    {
    	def dev = motionCameras[i]
        if (dev)
        {
        	//motionCameras[i]?.syncDateTime()
            log.debug("Synced $dev")
        }
        else
        {
        	log.debug ("Could not sync. Device is null.")
        }
    }
}