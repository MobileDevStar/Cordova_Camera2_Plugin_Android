// JavaScript Document

var Init = (function(){
	
	"use strict";
	
	init_phonegap();
	
	var aFunctions = [];
	
	//aFunctions.push();
	
	/**
	* Initializes Phonegap
	* 
	* @private
	* @memberOf Init#
	*/
	function init_phonegap(fnNext){

		console.log('INIT: Phonegap');
		
		
		// METHOD 3 - Found here: http://stackoverflow.com/questions/8068052/phonegap-detect-if-running-on-desktop-browser
		if (oConstants['bIsMobile']){

			$(document).on('deviceready', function(){
				//console.log('INIT: Phonegap ready');
				//cordova.exec.setJsToNativeBridgeMode(cordova.exec.jsToNativeModes.XHR_OPTIONAL_PAYLOAD);
				onDeviceReady();
			});
			
			$(document).on('pause', function(){
				console.log('APP PAUSED');
				
				// If using the camera preview plugin it might close itself
				if (oGlobals['iCameraPlugin'] == 1){
					oSession['bIsCameraActive'] = false;
				}
				
				//killCamera();
			});
			
			$(document).on('resume', function(){
				console.log('APP RESUMED');
			});
		} else {
			//oConstants['bDebug'] = true; // Browser should always be in debug mode
						
			onDeviceReady();
		}
	}
	
	//////////////////////////////////////
	// INIT EVENT: Phonegap
	//////////////////////////////////////

	function onDeviceReady(){

		console.log('INIT: onDeviceReady - compiled?: ' + oConstants['bIsMobile']);
	
	
		// Define the external storage path
		if (oConstants['bIsMobile']){
			if (oConstants['bIsiOS']){
				oConstants['sExternalRoot'] = cordova.file.documentsDirectory; // Public, removed with app
			} else if (oConstants['bIsAndroid']){
				//oConstants['sExternalRoot'] = cordova.file.externalRootDirectory + 'ppw/'; // The old backup folder
				oConstants['sExternalRoot'] = cordova.file.externalApplicationStorageDirectory; // Public, removed with app
			}			
		}
		
		$('#status').html('DEVICE READY');
		
		$('#deviceready').show();
	}
})();

// Dummy function
function saveLog(sMsg, iLevel){
}