// JavaScript Document

//////////////////////////////////////
// PHONEGAP FUNCTIONS
//////////////////////////////////////

function getAppVersion(fnComplete){
	if (typeof(cordova) !== 'undefined' && typeof(cordova.getAppVersion) != 'undefined'){
		cordova.getAppVersion.getVersionNumber(function(sVersion){
			oConstants['sVersion'] = sVersion;
			window.localStorage['current_version'] = sVersion;
			
			fnComplete(sVersion);
		});
	} else {
		if (oConstants['bIsMobile']){
			msg_error('Failed to get app version');
			console.log('getAppVersion undefined');
		}
		fnComplete(null);
	}
}

// Internal 

function getStoragePath(sRoot){
	
	var sPath = '';

	if (!sRoot){
		sPath = 'cdvfile://localhost/persistent/';
	} else if (sRoot == 'internal'){ // #UNUSED
		sPath = 'cdvfile://localhost/persistent/';
	} else {
		sPath = oConstants['sExternalRoot']; // Different on iOS and Android
	}
	
	return sPath;
}

function getFreeSpace(fnComplete){
	
	if (typeof(cordova) != 'undefined'){
		cordova.exec(function(iSize) {
			//alert("Free Disk Space: " + result);

			// Returns kilobytes on Android, bytes on iOS?
			var iBytes = iSize;

			if (oConstants['bIsAndroid']){
				var iBytes = iSize * 1000;
			}

			if (typeof(fnComplete) == 'function'){
				fnComplete(iBytes);
			}
		}, function(error) {
			//alert("Error: " + error);
			if (typeof(fnComplete) == 'function'){
				fnComplete(null);
			}
		}, "File", "getFreeDiskSpace", []);		
	} else {
		fnComplete(0);
	}

}

/**
 * Get the device info from the Cordova Device plugin
 * @returns {object}
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 * @see https://cordova.apache.org/docs/en/latest/reference/cordova-plugin-device/
 */
function getDevice(){
	var oDevice = {
		cordova: '',
		model: '',
		platform: '',
		uuid: 'fake',
		version: '',
		serial: ''
	}
	
	if (typeof(device) !== 'undefined'){
		
		oDevice['cordova'] = device.cordova;
		oDevice['model'] = device.model;
		oDevice['platform'] = device.platform;
		oDevice['uuid'] = device.uuid;
		oDevice['version'] = device.version;
		oDevice['serial'] = device.serial;
	}
	
	return oDevice;
}

function getBuild(){
	var oBuild = {
		name: '',
		packageName: '',
		version: '',
		versionCode: '',
		buildType: '',
		debug: ''
	}
	
	//var aFields
	
	if (typeof(BuildInfo) !== 'undefined'){
		//console.log(BuildInfo);
		
		$.extend(oBuild, BuildInfo);
		
		//console.log(oBuild);
	}
	
	return oBuild;
}

/**
 * Get the connection type from the Cordova Network Information plugin
 * @returns {string}
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 * @see https://cordova.apache.org/docs/en/latest/reference/cordova-plugin-network-information/index.html
 */
function getConnection(){
	var sConnection = 'browser';
	
	if (typeof(navigator.connection) != 'undefined' && typeof(Connection) != 'undefined'){
		sConnection = navigator.connection.type;
	}
	
	return sConnection;
}

/**
 * Get the connection type from the Cordova Geolocation plugin
 * @params {function} fnSuccess - Success callback (object)
 * @params {function} fnError - Error callback (object)
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 * @see https://cordova.apache.org/docs/en/latest/reference/cordova-plugin-geolocation/index.html
 */
function getGPS_original(fnSuccess,fnError){
	console.log('common: getGPS_original');
	
	var iWorkOrderId = window.localStorage['work_order_id'];
	var tsStart = moment(); // Get current timestamp
	var iRemaining = oGlobals['iGPSTimeout'] / 1000;
	
	showLoading({id: 'getGPS_original',textVisible: true, text: 'Acquiring High Accuracy Location (' + iRemaining + 's)'});
	
	var iNow = moment().unix();
	//var iCount = dbGPS({'expires':{'lt': iNow}}).remove(); // Cleanup expired GPS cache
	
	var fnGPS = function(bHighAccuracy){
		if (typeof(bHighAccuracy) == 'undefined'){
			bHighAccuracy = false;
		}
		
		var pCountdown = setInterval(function(){
			showLoading({id: 'getGPS_original', textVisible: true, text: 'Acquiring Low Accuracy Location (' + iRemaining-- + 's)'});
		},1000);
		
		var oSettings = {
			'enableHighAccuracy': bHighAccuracy,
			'maximumAge': oGlobals['iGPSMaxAge'],
			'timeout': oGlobals['iGPSTimeout']
		};
		
		// Attempt high accuracy first
		navigator.geolocation.getCurrentPosition(
		function(oPosition){	
			var tsEnd = moment();

			var sHtml = '';
			var oData = {};
			
			// Copy position data to new object - storing entire object makes pouchdb cry
			for (var key in oPosition['coords']){
				//if (oPosition['coords'].hasOwnProperty(key)){
					
					// Look for NaN
					if (oPosition['coords'][key] !== oPosition['coords'][key]){
						oData[key] = null;
					} else {
						oData[key] = oPosition['coords'][key];
					}
				//}
			}	
			
			oData['cached'] = false;
			oData['timestamp'] = tsEnd.unix();
			//oData['timestamp_f'] = tsEnd.format(oGlobals['sDateFormatLong']);
			oData['retrieval_time'] = tsEnd.diff(tsStart);
			oData['high_accuracy'] = bHighAccuracy;

			hideLoading({id: 'getGPS_original'});
			window.clearInterval(pCountdown);

			bSuccess = true;
			
			// Insert into table - NOTE: gps timestamp won't change if cached
			jsSQL.upsert('gps','id',oData,function(iId){
				oData['id'] = iId; // Inject last insert id into data
				
				WorkOrder.resetLocal();
				
				fnSuccess(oData); // Fire callback
			});
			
			//var tsExpire = moment().startOf('day').add(1,'day'); // Get timestamp for midnight
		}, 
		function(oError){
			window.clearInterval(pCountdown);
			
			if (bHighAccuracy == false){
				console.error('GPS: Error acquiring low accuracy gps');
				
				hideLoading({id: 'getGPS_original'});
				
				if (oGPS){
					oGPS['data']['timestamp'] = moment().unix(); // Update timestamp
					//oGPS['data']['cached'] = true;
					
					msg_info('Using cached location');
					fnSuccess(oGPS['data']);
				} else if (typeof(fnError) == 'function'){
					fnError(oError);
				}
				
			} else {
				console.error('GPS: Error acquiring high accuracy gps');
				
				iRemaining = oGlobals['iGPSTimeout'] / 1000;
				fnGPS(false);
			}
		},
		oSettings
		);
	}
	
	fnGPS(true); // Attempt high accuracy first, if error try low accuracy
}

/**
 * Get the connection type from the Cordova Geolocation plugin. Attempt low and high accuracy simultaneously.
 * @params {function} fnSuccess - Success callback (object)
 * @params {function} fnError - Error callback (object)
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 * @see https://cordova.apache.org/docs/en/latest/reference/cordova-plugin-geolocation/index.html
 */
function getGPS(fnSuccess,fnError){
	console.log('common: getGPS');
	saveLog('common: getGPS');

	var iWorkOrderId = window.localStorage['work_order_id'];
	var tsStart = moment(); // Get current timestamp
	var bSuccess = false;
	var bFail = false;
	var iRemaining = oGlobals['iGPSTimeout'] / 1000;
	var pCountdown;
	
	var fnGPS = function(bHighAccuracy){
		//if (typeof(bHighAccuracy) == 'undefined'){
		//	bHighAccuracy = false;
		//}
		
		var oSettings = {
			'enableHighAccuracy': bHighAccuracy,
			'maximumAge': oGlobals['iGPSMaxAge'],
			'timeout': oGlobals['iGPSTimeout']
		};
		
		// Attempt high accuracy first
		navigator.geolocation.getCurrentPosition(
		function(oPosition){

			if (bSuccess){
				return;
			}
			
			var tsEnd = moment();

			var sHtml = '';
			var oData = {};
			
			// Copy position data to new object - storing entire object makes pouchdb cry
			for (var key in oPosition['coords']){
				//if (oPosition['coords'].hasOwnProperty(key)){
					
					// Look for NaN
					if (oPosition['coords'][key] !== oPosition['coords'][key]){
						oData[key] = null;
					} else {
						oData[key] = oPosition['coords'][key];
					}
				//}
			}	
			
			//oData['cached'] = false;
			
			oData['timestamp_api'] = oPosition['timestamp'];
			oData['timestamp'] = tsEnd.unix(); // Override native milliseconds timestamp with seconds
			oData['retrieval_time'] = tsEnd.diff(tsStart);
			oData['high_accuracy'] = bHighAccuracy;

			hideLoading({id: 'getGPS'});
			window.clearInterval(pCountdown);
			
			bSuccess = true;
			
			saveLog('common: getGPS success',1);
			
			// GPS Test will not have a work order id
			if (iWorkOrderId){
				oData['work_order_id'] = iWorkOrderId;
				
				// Insert into table - NOTE: gps timestamp won't change if cached
				jsSQL.upsert('gps','id',oData,function(iId){
					oData['id'] = iId; // Inject last insert id into data

					WorkOrder.resetLocal();

					fnSuccess(oData); // Fire callback
				});
			} else {
				fnSuccess(oData); // Fire callback
			}
			
			//var tsExpire = moment().startOf('day').add(1,'day'); // Get timestamp for midnight

		}, 
		function(oError){
			var sType = '';
			
			if (bHighAccuracy == false){
				sType = 'low accuracy';
			} else {
				sType = ' high accuracy';				
			}
			
			// oError.code = 2 (Permissions)
			
			var sError = 'common: getGPS - error ' + sType + ' code: ' + oError.code + ', message' + oError.message;
			console.error(sError);
			saveLog(sError, 4);
			
			// If first to fail
			if (!bFail){
				bFail = true;
				return;
			}
			
			// If success already fired
			if (bSuccess){
				return;
			}
			
			msg_error('GPS error code ' + oError.code + ': ' + oError.message);
			
			hideLoading({id: 'getGPS'});
			window.clearInterval(pCountdown);
			
			// Attempt to pull from cache	
			
			if (iWorkOrderId){
				oPages['checkin'].getLastGPS(iWorkOrderId,function(oGPS){

					oGPS['timestamp'] = moment().unix(); // Update timestamp
					//oGPS['data']['cached'] = true;

					msg_info('Using cached location');
					fnSuccess(oGPS);

				},fnError);					
			} else {
				fnError(oError);
			}

		},
		oSettings
		);
	}
	
	function isAuthorized(){
		console.log('common: getGPS > isAuthorized');
		
		showLoading({id: 'getGPS',textVisible: true, text: 'Acquiring Location (' + iRemaining + 's)'});

		pCountdown = setInterval(function(){
			showLoading({id: 'getGPS',textVisible: true, text: 'Acquiring Location (' + iRemaining-- + 's)'});
		},1000);
		
		fnGPS(true);
		fnGPS(false);		
	}
	
	if (typeof(cordova) != 'undefined' && typeof(cordova.plugins) !== 'undefined' && typeof(cordova.plugins.diagnostic) !== 'undefined'){
		console.log('Diagnaostic plugin exists');
		
		cordova.plugins.diagnostic.isLocationAuthorized(function(bAuthorized){
			console.log('bAuthorized: ' + bAuthorized);
			
			if (!bAuthorized){
				console.log('Requesting geolocation permission');
				
				requestLocationAuthorization(function(){
					isAuthorized();
				},fnError);
			} else {
				isAuthorized();
			}
			
			//isAuthorized();
		},fnError);
	} else {
		console.log('Diagnaostic plugin doesn\'t exist');
		
		isAuthorized();
	}
}



//////////////////////////////////////
// CAMERA FUNCTIONS
//////////////////////////////////////



/**
 * Keepalive for the Appnovation camera plugin
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function confirmCamera(){
	console.log('common: confirmCamera');
	saveLog('common: confirmCamera');
	
	navigator.PPWCamera.confirmPicture(null, function() {
		//test: true
	},
	function(sError) {
		var sMsg = 'common: confirmCamera - error';
		
		console.error(sMsg);
		saveLog(sMsg,4);
		
		msg_error(sError);
	});
}

/**
 * Kill the Appnovation camera plugin
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function killCamera(){
	console.log('common: killCamera');
	
	navigator.PPWCamera.closeCamera({},
	function(){
		console.log('killCamera success');
	}, 
	function(){
		console.error('killCamera error');
	});
	
	oSession['bIsCameraActive'] = false;
	//console.log('%cSETTING CAMERA INACTIVE','font-size:2em;');
	
	oPages['photos'].onCameraExit();
}

//////////////////////////////////////
// DIAGNOSTIC PLUGIN
//////////////////////////////////////

function getPermissions(fnComplete){
	console.log('common: getPermissions');
	
	var oPermissions = {
		location_available: false,
		location_authorized: false,
		camera_available: false,
		camera_authorized: false,
		wifi_available: false
	}
	
	//var aFields
	
	if (typeof(cordova.plugins) !== 'undefined' && typeof(cordova.plugins.diagnostic) !== 'undefined'){
		//console.log(cordova.plugins.diagnostic);
		
		async.parallel([
			function(fnCallback) {
				cordova.plugins.diagnostic.isLocationAvailable(function(b){
					oPermissions['location_available'] = b;
					
					fnCallback(null);
				}, function(error){
					console.error('The following error occurred: ' + error);
					
					fnCallback(null);
				});
			},
			function(fnCallback) {
				cordova.plugins.diagnostic.isLocationAuthorized(function(b){
					oPermissions['location_authorized'] = b;
					
					fnCallback(null);
				}, function(error){
					console.error('The following error occurred: ' + error);
					
					fnCallback(null);
				});
			},
			function(fnCallback) {
				cordova.plugins.diagnostic.isCameraAvailable(function(b){
					oPermissions['camera_available'] = b;
					
					fnCallback(null);
				}, function(error){
					console.error('The following error occurred: ' + error);
					
					fnCallback(null);
				});
			},
			function(fnCallback) {
				cordova.plugins.diagnostic.isCameraAuthorized(function(b){
					oPermissions['camera_authorized'] = b;
					
					fnCallback(null);
				}, function(error){
					console.error('The following error occurred: ' + error);
					
					fnCallback(null);
				});
			},
/*			function(fnCallback) {
				cordova.plugins.diagnostic.isWifiAvailable(function(b){
					oPermissions['wifi_available'] = b;
					
					fnCallback(null);
				}, function(error){
					console.error('The following error occurred: ' + error);
					
					fnCallback(null);
				});
			},*/
		],
		// optional callback
		function(err, results) {
			if (typeof(fnComplete) == 'function'){
				fnComplete(oPermissions);
			}
		});
		
	} else {
		if (typeof(fnComplete) == 'function'){
			fnComplete(oPermissions);
		}	
	}
}

function requestCameraAuthorization(fnSuccess,fnError){
	
	cordova.plugins.diagnostic.requestCameraAuthorization(function(status){
		var sMsg = (status == cordova.plugins.diagnostic.permissionStatus.GRANTED) ? "granted" : "denied";
    	console.log('Camera permission ' + sMsg);
		
		if (typeof(fnSuccess) == 'function'){
			fnSuccess();	
		}
	}, function(error){
		console.error(error);
	});
}

function requestLocationAuthorization(fnSuccess,fnError){
	var sMode = '';
	
	if (oConstants['bIsiOS']){
		sMode = cordova.plugins.diagnostic.locationAuthorizationMode.ALWAYS;
		
		if (typeof(fnSuccess) == 'function'){
			fnSuccess();	
		}
	} else {
		
		// NOTE: Doesn't throw success or error callback on iOS
		cordova.plugins.diagnostic.requestLocationAuthorization(function(status){
			console.log('requestLocationAuthorization status: ' + status);

			switch(status){
				case cordova.plugins.diagnostic.permissionStatus.NOT_REQUESTED:
					console.log("Permission not requested");
					break;
				case cordova.plugins.diagnostic.permissionStatus.DENIED:
					console.error("Permission denied");
					break;
				case cordova.plugins.diagnostic.permissionStatus.GRANTED:
					console.log("Permission granted always");
					break;
				case cordova.plugins.diagnostic.permissionStatus.GRANTED_WHEN_IN_USE:
					console.log("Permission granted only when in use");
					break;
				case cordova.plugins.diagnostic.permissionStatus.DENIED_ALWAYS:
					console.error("Permission permanently denied");
					break;
			}

			if (typeof(fnSuccess) == 'function'){
				fnSuccess();	
			}
		}, function(error){
			console.error(error);
		}, sMode);		
	}
	

}