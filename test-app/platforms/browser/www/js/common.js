// JavaScript Document

/**
 * Provides general purpose Javascript functions
 *
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */

//////////////////////////////////////
// FORMATTING
//////////////////////////////////////

/**
 * Escape reserved characters for jQuery selector
 * @param {string} sStr - The string to escape
 * @returns {string} The escaped string
 * @author Kamal Agrawal
 * @see http://fellowtuts.com/jquery/escaping-special-characters-in-jquery-selector/
 */
function jqEscape(sStr){
	var bIsId = (sStr.substring(0,1) == '#') ? true : false;
	var sSub = (bIsId) ? sStr.substring(1) : sStr;
	var sResult = sSub.replace(/([;&,\.\+\*\~':"\!\^#$%@\[\]\(\)=>\|])/g, '\\$1');
	
	if (bIsId){
		sResult = '#' + sResult;
	}
	return sResult;
}

/**
 * Strip non-numeric characters from a string
 * @param {string} sNum - The string to escape
 * @returns {string} The escaped string
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function stripAlpha(sNum){
	//console.log('stripAlpha');
	//console.log('before: ' + sNum + ' ' + typeof(sNum));
	
	//if (isNaN(parseFloat(sStr))){
	//	sStr = sStr.replace(/[^\d.-]/g, ''); // Remove non-numeric
	//}
	
	if (typeof(sNum) == 'string'){
		sNum = sNum.replace(/[^\d.-]/g, ''); // Remove non-numeric
		dNum = Number(sNum);
	} else {
		dNum = sNum; // It's already a number
	}
	
	
	//console.log('after: ' + dNum + ' ' + typeof(dNum));
	
	return dNum;
}



/**
 * Format bytes into readable format
 * @param {integer} bytes - The number to format
 * @param {integer} decimals - The number of decimal places to show
 * @returns {string} The formatted timestamp. Default format: 'M/D/YYYY h:mma'
 * @author MathieuLescure <http://stackoverflow.com/users/953669/mathieulescure>
 * @see http://stackoverflow.com/questions/15900485/correct-way-to-convert-size-in-bytes-to-kb-mb-gb-in-javascript
 */
function formatBytes(bytes,decimals) {
   if(bytes == 0) return '0 Bytes';
   var k = 1000;
   var dm = decimals + 1 || 3;
   var sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
   var i = Math.floor(Math.log(bytes) / Math.log(k));
   return (bytes / Math.pow(k, i)).toPrecision(dm) + ' ' + sizes[i];
}

/**
 * Escape HTML entities
 * @param {string} sStr - The string to escape
 * @returns {string} The formatted string
 * @author mustache.js
 * @see https://github.com/janl/mustache.js/blob/master/mustache.js#L71
 */
function escapeHtml(sStr) {
	
	// Dreamweaver CC 2017 is converting these entities. I can't stop it.
	var entityMap = {
		"&": 'amp;',
		"<": "lt;",
		">": "gt;",
		'"': "quot;",
		"'": "#39;",
		"/": "#x2F;"
	};
	
	return String(sStr).replace(/[&<>"'\/]/g, function (s) {
    	return "&" + entityMap[s];
    });
}

/**
 * Convert boolean to 1/0
 * @param {boolean} b - The boolean to convert
 * @param {integer} [iDefault] - The value to return if b is undefined
 * @returns {integer} The formatted string
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function booleanToInteger(b,iDefault){
	if (typeof(b) == 'undefined'){
		if (typeof(iDefault) !== 'undefined'){
			return iDefault;
		}
	}
	return b ? 1 : 0;
}


/**
 * Parse a url/cordova path into it's components
 * @param {sPath} b - The path to parse
 * @returns {object} The formatted string
 * @author Tim Berners-Lee <timbl @ w3.org>
 * @see https://tools.ietf.org/html/rfc3986#appendix-B
 * @see http://stackoverflow.com/a/26766402
 * @example
 * var p1 = 'cdvfile://www.localhost/persistent/photos/678718/678718_1469469512548.jpg?test=1&s=2#tag'
 * Object { protocol: "cdvfile", host: "localhost", path: "/persistent/photos/678718/678718_14…", parameters: "test=1&s=2", anchor: "tag" }
 * var p2 = 'photos/678718/678718_1469469512548.jpg'
 * Object { protocol: undefined, host: undefined, path: "photos/678718/678718_1469469512548.…", parameters: undefined, anchor: undefined }
 * var p3 = 'file:///data/data/com.propertypreswizard.app.propertypreswizard/files/files/photos/681485/' 
 * Object { protocol: "file", host: "", path: "/data/data/com.propertypreswizard.a…", parameters: undefined, anchor: undefined }
 */
function parsePath(sPath){
  var rxPath = new RegExp(/^(([^:\/?#]+):)?(\/\/([^\/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?/)

  //var aParsed = sPath.split(rxPath)
  
  var aParsed = rxPath.exec(sPath);
  //console.log(aParsed);
  
  var oOut = {
    //url: aParsed[0],
    //protocol1: aParsed[1],
    protocol: aParsed[2],
    //host1: aParsed[3],
    host: aParsed[4],
    path: aParsed[5],
    //parameters: aParsed[6],
    parameters: aParsed[7],
    //anchor: aParsed[8],
	anchor: aParsed[9],
  }
  
  //console.log(oOut);
  
  return oOut;
}


/**
 * Split a url into folders
 * @param {string} sPath - The url to split
 * @returns {object} The formatted string
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 * @example
 * Input cdvfile://localhost/persistent/photos/8532116/
 * 
 * Result:
 * 
 * aPath:
 * 	0:"photos"
 * 	1:"8532116"
 * length:5
 * sRoot: "cdvfile://localhost/persistent/"
 */
function splitPath(sPath){
	var oPath = parsePath(sPath);
	//console.log(oPath);
	
	var sRoot = '';
	var sPath = stripOuterSlashes(oPath['path']);
	var aPath = sPath.split('/');
	
	//console.log(aPath);
	
	// Possible options: file, cdvfile
	if (typeof(oPath['protocol']) !== 'undefined'){
		sRoot = oPath['protocol'];
		
		switch(oPath['protocol']){
			case 'file':
				sRoot += ':///';
				break;
			case 'cdvfile':
				sRoot += '://' + oPath['host'] + '/' + aPath.shift() + '/'; // Pull temporary or persistent from the path array. Trailing slash is critical
				break;
		}		
	}
	
	var oRoot = {
		sRoot: sRoot,
		aPath: aPath
	};
	
	return oRoot;
}

function stripOuterSlashes(sString){
	return sString.replace(/^\/|\/$/g, '');
}

// Strip properties that aren't allowed (for SQL insert/update)
function whitelist(oObj, aAllowed){
	Object.keys(oObj).forEach(function(currentValue, index, array){
		if (aAllowed.indexOf(currentValue) == -1){
			delete oObj[currentValue];
		}
	});
}

//////////////////////////////////////
// DATE/TIME
//////////////////////////////////////

/**
 * Grab current UNIX timestamp
 * @returns {integer}
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function now(){
	return moment().unix();
}

/**
 * Format UNIX timestamp into readable format
 * @param {string} sStr - The timestamp
 * @returns {string} The formatted timestamp. Default format: 'M/D/YYYY h:mma'
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
//function convertTimestampToLong
function formatTimestamp(sStr){
	return moment.unix(sStr).format(oGlobals['sDateFormatLong']);
}

function convert24hr12hr(sTime){
	return moment(sTime,'H:mm').format('h:mm a');
}

/**
 * Convert U.S. date into UNIX
 * @param {string} sStr - The date
 * @returns {string} The formatted timestamp. Default format: 'M/D/YYYY h:mma'
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
//function convertUStoTimestamp
function parseDateUS(sStr,bUnix){
	var tsMoment = moment(sStr,'MM-DD-YYYY');
	return (bUnix) ? tsMoment.unix() : tsMoment;
}

/**
 * Convert MySQL date into UNIX
 * @param {string} sStr - The date
 * @returns {string} The formatted timestamp. Default format: 'M/D/YYYY h:mma'
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
//function convertUStoTimestamp
function parseDateMySQL(sStr,bUnix){
	var tsMoment = moment(sStr,'YYYY-MM-DD');
	return (bUnix) ? tsMoment.unix() : tsMoment;
}

function momentToUnix(tsMoment){
	return tsMoment.unix();
}

//////////////////////////////////////
// MATHEMATICAL
//////////////////////////////////////

function isPowerOfTwo(x){
    return (x != 0) && ((x & (x - 1)) == 0);
}

//////////////////////////////////////
// NOTIFICATIONS
//////////////////////////////////////

// Popup success message
function msg_success(sText,oOptions){
	if (typeof(oOptions) === 'undefined') var oOptions = {};
	oOptions.additionalClass = 'bg-green';
	oOptions.message = sText;
	//oOptions.button = {text: 'Close',color:'green'};
	msg(oOptions);
}

// Popup info message
function msg_info(sText,oOptions){
	if (typeof(oOptions) === 'undefined') var oOptions = {};
	oOptions.className = 'info';
	oOptions.additionalClass = 'bg-daylight-blue';
	oOptions.message = sText;
	msg(oOptions);
}

// Popup error message
function msg_error(sText,oOptions){
	if (typeof(oOptions) === 'undefined') var oOptions = {};
	oOptions.additionalClass = 'bg-red';
	oOptions.message = sText;
	//oOptions.button = {text: 'Close',color:'red'};
	msg(oOptions);
}

// Popup warning message
function msg_warn(sText,oOptions){
	if (typeof(oOptions) === 'undefined') var oOptions = {};
	oOptions.additionalClass = 'bg-deeporange';
	oOptions.message = sText;
	//oOptions.button = {text: 'Close',color:'yellow'};
	msg(oOptions);
}

/**
 * Popup message
 * @param {string} sText - The message to display
 * @param {object} [oOptions] - The message options
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function msg(oOptions){
	//console.log('msg',oOptions)
	
	var oDefaults = {
		//title: '',
		//subtitle: '',
		//message: '',
		//media: '',
		//hold: 30*1000,
		//closeIcon: '',
		//closeOnClick: true,
		button: {text: '<i class="icon material-icons">clear</i>',color: 'white'}, // Not merging...
	}
	
	var oSettings = $.extend({},oDefaults,oOptions);
	
	// Check for duplicate message
	if (oSettings['message'] == oSession['sLastMessage']){
		myApp.closeNotification(oSession['hLastNotification']);
		//console.log('duplicate');
	} else{
		//console.log('not a duplicate');
	}
	
	oSession['sLastMessage'] = oSettings['message'];
	
	// #F7
	oSession['hLastNotification'] = myApp.addNotification(oSettings);
}

//////////////////////////////////////
// GENERIC FUNCTIONS
//////////////////////////////////////

function canLog(sType){
	return oConstants['oLog'][sType];
}

function getLogStyle(sType){
	return oConstants['oConsoleStyle'][sType];
}

/**
 * Validate an Aspen Grove ABC number
 * @param {string} s - The string to validate
 * @returns {boolean}
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function validateABCNumber(s){
	var bResult = oConstants['rxABCNumber'].test(s);
	
	if (bResult){
		var sState = s.substring(0,2).toUpperCase();
		
		// Verify state is valid
		if (oConstants['aStatesShort'].indexOf(sState) == -1){
			return false; // Invalid state
		} else {
			return true;
		}
	} else {
		return false; // Bad format
	}
}

/**
 * Reset a form by id
 * @param {string} sId - The form id to reset
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function resetForm(sId){
	$('#' + sId)[0].reset();
}

//var aLoaders = {};

function showLoading(oOptions){
	console.log('%c showLoading - id: ' + oOptions['id'], 'color: green;');
	
	MultiProgress.add({id: oOptions['id'], text: oOptions['text']});
}

function hideLoading(oOptions){
	var sId = (typeof oOptions !== 'undefined' && typeof oOptions['id'] !== 'undefined') ? oOptions['id'] : '';
	console.log('%c hideLoading - id: ' + sId, 'color: green;');
	
	var sTimeout = 0;
	
	setTimeout(function(){
		MultiProgress.remove(sId);
	},sTimeout);
}

/**
 * Compare two decimal separated version numbers
 * @param {string} sCurrent- The version to compare against
 * @param {string} sLatest- The version to compare to
 * @returns {integer} -1 if the current version is older, 0 if they are the same, 1 if the current version is newer
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function versionCompare(sCurrent,sLatest){
	var aCurrent = sCurrent.split('.');
	var aLatest = sLatest.split('.');
	
	// Convert each element to int
	for (var x=0;x<aCurrent.length;x++){
		aCurrent[x] = Number(aCurrent[x]);
	}
	
	for (var x=0;x<aLatest.length;x++){
		aLatest[x] = Number(aLatest[x]);
	}	
	
	if (aCurrent.length > aLatest.length){
		// Pad latest version to match length of current
		for (var x = aLatest.length; x < aCurrent.length; x++){
			aLatest[x] = 0;
		}
	} else if (aCurrent.length < aLatest.length){
		// Pad current version to match length of latest
		for (var x = aCurrent.length; x < aLatest.length; x++){
			aCurrent[x] = 0;
		}
	}

	// Perform comparison
	for (var x=0;x<aCurrent.length;x++){
		if (aCurrent[x] < aLatest[x]){
			return -1;
		} else if (aCurrent[x] > aLatest[x]){
			return 1
		}
	}
	
	return 0;
}

/**
 * Compare two flat (non-nested) objects
 * @param {object} oLeft - The object to compare against
 * @param {object} oRight - The newer object
 * @returns {boolean} True if they are the same, false if they are different
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function compareInner(oLeft,oRight){
	
	// Compare existing row
	//console.log(oLeft);
	//console.log(oRight);
	
	var bFail = false;
	
	// Loop through properties of object we are saving
	for (sProperty in oLeft){
		if (typeof(oRight[sProperty]) == 'undefined' || oLeft[sProperty] !== oRight[sProperty]){
			bFail = true;
		}
		//console.log(sProperty + ' ' + oFormData['bidcomp'][sProperty]);
	}
	
	if (bFail){
		return false;
	} else {
		return true;
	}
}

/**
 * Break a reference to an object by cloning it's properties
 * @param {object} oObj - The object to compare against
 * @returns {object} A copy of the original object
 */
function cloneObject(oObj){
	return $.extend(true,{}, oObj);
}

/**
 * Compare two nested objects
 * @param {object} obj1 - The object to compare against
 * @param {object} obj2 - The newer object
 * @returns {boolean} True if they are the same, false if they are different
 * @author Philip Hutchison?
 * @see https://itgotmethinking.com/2010/07/23/comparing-and-cloning-objects-in-javascript/
 */
function compareObjects(obj1, obj2){
 
    var parameter_name;
 
    var compare = function(objA, objB, param){
 
        var param_objA = objA[param],
            param_objB = (typeof objB[param] === 'undefined') ? false : objB[param];
 		
		var result;
		
        switch(typeof objA[param]){
            case 'object': result = compareObjects(param_objA, param_objB);
			break;
            case 'function': result = (param_objA.toString() === param_objB.toString());
			break;
            default: result = (param_objA === param_objB);
        }
		if (!result){
			//console.log('param not equal: ' + param + ' A: ' + param_objA + ' B: ' + param_objB);
		}
		
		return result;
    };
 	
	var b1 = (typeof(obj1) == 'undefined');
	var b2 = (typeof(obj2) == 'undefined');
	
	// If both undefined, return true. If one is undefined return false
	if (b1 & b2){
		return true;
	}
	if (b1 | b2){
		return false;
	}
	
    for(parameter_name in obj1){
        if(typeof obj2[parameter_name] === 'undefined' || !compare(obj1, obj2, parameter_name)){
            return false;
        }
    }
 
    for(parameter_name in obj2){
        if(typeof obj1[parameter_name] === 'undefined' || !compare(obj1, obj2, parameter_name)){
            return false;
        }        
    }
 
    return true;
};

/**
 * Recursively build nested HTML from nested arrays
 * @param {array} aoTree - An array of objects 
 * @param {integer} iDepth - Used internally only
 * @returns {string} A string of HTML elements
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function listify3(aoTree,iDepth){

	var iDepth = (typeof(iDepth) != 'undefined') ? iDepth : 0;
	//console.group("Depth: " + depth)
	console.log(aoTree);
	//console.log(typeof(aoTree['folders']));
	//console.log(aoTree['folders']);
	//console.log(aoTree['folders'].length);
	
	for (sKey in aoTree){
		console.log(sKey);
		console.log(aoTree[sKey]);
	}
	
	var sHtml = (iDepth == 0) ? '<ul class="tree">' : '<ul>';
	var oBranch = {};
	
	oBranch['total_rows'] = 0;
	
	var child_rows = 0;
	
	// Folders
	if (typeof(aoTree['folders']) != 'undefined' && aoTree['folders'].length > 0){
		var aFolders = aoTree['folders'];
		
		for (var x=0;x<aFolders.length;x++){
			var oFolder = aFolders[x];
			var sFolderHtml = '';
			
			console.log(oFolder);

			sHtml += '<li><span>';
			sHtml += oFolder['name']; // Folder name
			
			//sFolderHtml += ' <span class="branch_expand">[+]</span>';
			//sFolderHtml += '</span>';
			
			console.log('Recursing into ' + oFolder['name']);
			
			oBranch_child = listify3(oFolder,iDepth+1);  // RECURSE
			
			//console.log(oBranch_child);
			
			sFolderHtml += oBranch_child['sHtml'];
			
			oBranch['total_rows'] = oBranch_child['total_rows'];
			child_rows += oBranch['total_rows'];
			
			//console.log("Child rows: " + oBranch_child.total_rows)
 
			//console.log("Rows: " + oBranch.total_rows);
			
			if (typeof(oFolder['folders']) != 'undefined'){
				sHtml += ' [' + oFolder['folders'].length + ' folder(s) / ' + oFolder['files'].length + ' file(s)]';
			}
			sHtml += '</span>';
			
			//str += count_str;
			sHtml += sFolderHtml; // Subfolder html
			sHtml += '</li>';
		}
	}
	
	
	// Files
	if (typeof(aoTree['files']) != 'undefined' &&  aoTree['files'].length > 0){
		var aFiles = aoTree['files'];
		
		console.log(aFiles);
		
		for (var x=0;x<aFiles.length;x++){
			var aFile = aFiles[x];
			sHtml += '<li><span>' + aFile['name'] + '</span></li>';
		}
	}
	
	sHtml += '</ul>';
	
	//console.log("Branch: " + branch + " Rows: " + oBranch.total_rows);
	oBranch['total_rows'] = child_rows;
	
	oBranch['sHtml'] = sHtml;
	
	return oBranch;
}

/**
 * Recursively build nested HTML from nested arrays formatted as jQuery Mobile Collapsibles
 * @param {array} aoTree - An array of objects 
 * @param {integer} iDepth - Used internally only
 * @returns {string} A string of HTML elements
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function listifyJquery(aoTree,iDepth){

	var iDepth = (typeof(iDepth) != 'undefined') ? iDepth : 0;
	//console.group("Depth: " + depth)
	//console.log(aoTree);
	//console.log(typeof(aoTree['folders']));
	//console.log(aoTree['folders']);
	//console.log(aoTree['folders'].length);
	
	for (sKey in aoTree){
		//console.log(sKey);
		//console.log(aoTree[sKey]);
	}
	
	//var sHtml = (iDepth == 0) ? '<div data-role="collapsibleset">' : '<div data-role="collapsible">';
	var sHtml = (iDepth == 0) ? '<div data-role="collapsibleset" data-mini="true" data-inset="false">' : '<div data-role="collapsibleset" data-mini="true">';
	var oBranch = {};
	
	oBranch['total_rows'] = 0;
	
	var child_rows = 0;
	
	// Folders
	if (typeof(aoTree['folders']) != 'undefined' && aoTree['folders'].length > 0){
		var aFolders = aoTree['folders'];
		
		for (var x=0;x<aFolders.length;x++){
			var oFolder = aFolders[x];
			var sFolderHtml = '';
			
			//console.log(oFolder);

			sHtml += '<div data-role="collapsible"><h3>';
			sHtml += oFolder['name']; // Folder name
			
			//sFolderHtml += ' <span class="branch_expand">[+]</span>';
			//sFolderHtml += '</span>';
			
			//console.log('Recursing into ' + oFolder['name']);
			
			oBranch_child = listifyJquery(oFolder,iDepth+1);  // RECURSE
			
			//console.log(oBranch_child);
			
			sFolderHtml += oBranch_child['sHtml'];
			
			oBranch['total_rows'] = oBranch_child['total_rows'];
			child_rows += oBranch['total_rows'];
			
			//console.log("Child rows: " + oBranch_child.total_rows)
 
			//console.log("Rows: " + oBranch.total_rows);
			
			if (typeof(oFolder['folders']) != 'undefined'){
				sHtml += ' - ' + oFolder['folders'].length + ' folder(s) / ' + oFolder['files'].length + ' file(s)';
			}
			sHtml += '</h3>';
			
			//str += count_str;
			sHtml += sFolderHtml; // Subfolder html
			sHtml += '</div>';
		}
	}
	
	
	// Files
	if (typeof(aoTree['files']) != 'undefined' &&  aoTree['files'].length > 0){
		var aFiles = aoTree['files'];
		
		//console.log(aFiles);
		sHtml += '<ul data-role="listview">';
		for (var x=0;x<aFiles.length;x++){
			var aFile = aFiles[x];
			sHtml += '<li>'
			
			//console.log(aFile);
			
			if (aFile['name'].substr(aFile['name'].lastIndexOf('.')+1) == 'jpg'){
				sHtml += '<img src="' + aFile['nativeURL'] + '" class="thumbnail" alt="' + aFile['name'] + '" data-path="' + aFile['nativeURL'] + '" data-large="' + aFile['nativeURL'] + '"></img><br>' + aFile['name'];
			} else {	
				sHtml += '<a href="' + aFile['nativeURL'] + '">' + aFile['name'] + '</a>';
			}
			sHtml += '</li>';
		}
		sHtml += '</ul>';
	}
	
	sHtml += '</div>';
	
	//console.log("Branch: " + branch + " Rows: " + oBranch.total_rows);
	oBranch['total_rows'] = child_rows;
	
	oBranch['sHtml'] = sHtml;
	
	return oBranch;
}

/**
 * Temporarily disable element after clicking
 * @param {object} jThis - A javascript object
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function clickDelay(jThis){
	var jqTarget = $(jThis);
	
	var bActive = Timeout.isActive();
	//console.log(bActive);
	
	if (!Timeout.isActive()){
		disable(jqTarget);
		
		// Reenable element after delay
		Timeout.start('',function(){
			enable(jqTarget);
		},1000);
	} else {
		msg_warn('Already clicked');
	}
}

/**
 * Recursively build nested array from a flat array
 * @param {array} aItems - A flat array of objects 
 * @returns {array} A nested array of objects 
 * @author Creative Punch
 * @see http://creative-punch.net/2014/01/creating-nested-array-items-parent-ids/
 */
function nestArray(aItemsOriginal, sPrimary) {
	//var aItems = aItemsOriginal;
	var aItems = jQuery.extend([],aItemsOriginal); // Clone array to avoid altering the original
	
	
	var aNested = [];
	
	for (var i = 0; i < aItems.length; i++) {
		var parent = aItems[i]['parent'];
		if (!parent) {
			aNested.push(aItems[i]);
		} else {
			// You'll want to replace this with a more efficient search
			for (var j = 0; j < aItems.length; j++) {
				aItem = aItems[j];
				
				if (aItem[sPrimary] === parent) {
					aItem['children'] = aItem['children'] || [];
					aItem['children'].push(aItems[i]);
				}
			}
		}
	}
	return aNested;
}

// Uncaught RangeError: Maximum call stack size exceeded
/*function nestArray2(arr, parent) {
    var out = []
    for(var i in arr) {
        if(arr[i].parent == parent) {
            var children = nestArray2(arr, arr[i]['id'])

            if(children.length) {
                arr[i].children = children
            }
            out.push(arr[i])
        }
    }
    return out
}*/

/**
 * Recursively build nested object from a flat object
 * @param {object} oItems - An object of objects
 * @returns {array} A nested array of objects 
 * @author Creative Punch
 * @see http://creative-punch.net/2014/01/creating-nested-array-items-parent-ids/
 */
function nestObject(oItems) {
	var nested = [];
	var oItem = {};
	var oNew = {};

	for (var sKey in oItems) {
		
		//oItem = oItems[sKey];
		
		//oItem['name'] = sKey;
		
		// Strip out page memeber functions
		oItems[sKey] = {
			name: sKey,
			parent: oItems[sKey]['parent'],
			//children: oItems[sKey]['children']
		}
		
		oItem = oItems[sKey];
		
		//oItem = oNew;
		
		//oNew = oItem;
		
		//console.log(JSON.stringify(oItem));
		//console.log(JSON.stringify(oNew));
		
		if (typeof(oItem['parent']) == 'undefined' || oItem['parent'] == null ) {
			// no parent_id so we put it in the root of the array
			//console.log(oItem);
			nested.push(oItem);
		} else {
			pid = oItem['parent'];
			if ( typeof(oItems[pid]) != 'undefined' ) {
				// If the parent ID exists in the source array
				// we add it to the 'children' array of the parent after initializing it.

				if ( typeof(oItems[pid]['children']) == 'undefined') {
					oItems[pid]['children'] = [];
				}

				oItems[pid]['children'].push(oItem);
			}
		}
	}
	return nested;
}

function objectFromString(o, s) {
    s = s.replace(/\[(\w+)\]/g, '.$1'); // convert indexes to properties
    s = s.replace(/^\./, '');           // strip a leading dot
    var a = s.split('.');
    for (var i = 0, n = a.length; i < n; ++i) {
        var k = a[i];
        if (k in o) {
            o = o[k];
        } else {
            return;
        }
    }
    return o;
}

/**
 * Convert a base64 string in a Blob according to the data and contentType.
 * 
 * @param b64Data {String} Pure base64 string without contentType
 * @param contentType {String} the content type of the file i.e (image/jpeg - image/png - text/plain)
 * @param sliceSize {Int} SliceSize to process the byteCharacters
 * @see http://stackoverflow.com/questions/16245767/creating-a-blob-from-a-base64-string-in-javascript
 * @return Blob
 */
function b64toBlob(b64Data, contentType, sliceSize) {
        contentType = contentType || '';
        sliceSize = sliceSize || 512;

        var byteCharacters = atob(b64Data);
        var byteArrays = [];

        for (var offset = 0; offset < byteCharacters.length; offset += sliceSize) {
            var slice = byteCharacters.slice(offset, offset + sliceSize);

            var byteNumbers = new Array(slice.length);
            for (var i = 0; i < slice.length; i++) {
                byteNumbers[i] = slice.charCodeAt(i);
            }

            var byteArray = new Uint8Array(byteNumbers);

            byteArrays.push(byteArray);
        }

      var blob = new Blob(byteArrays, {type: contentType});
      return blob;
}



//////////////////////////////////////
// JQUERY ENHANCEMENTS
//////////////////////////////////////

/**
 * Wrapper for .html that compares the value before updating
 * @params {object} jqTarget - The target element
 * @returns {string} sValue - The value
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function fastHtml(jqTarget,sValue){
	if (jqTarget.html() !== sValue){
		jqTarget.html(sValue);
	}
}

// Emit an event to be caught by jQuery .on
function trigger(sKey, aParams){
	return $(document).trigger(sKey, aParams);
}

/**
 * Get jQuery Queue status
 * @params {string} sQueue - The queue name
 * @returns {boolean} True if active, false if not
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function isQueueActive(sQueue){
	return ($(document).queue(sQueue).length > 0) ? true : false;
}

/**
 * Set the HTML disabled attribute on a jQuery object
 * @param {object} jqTarget - A jQuery object
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function disable(jqTarget){
	//console.log('DISABLE')
	//console.log(jqTarget);
	jqTarget.prop('disabled',true);
}

/**
 * Remove the HTML disabled attribute from a jQuery object
 * @param {object} jqTarget - A jQuery object
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
function enable(jqTarget){
	jqTarget.prop('disabled',false);
}

/**
 * ???
 *
 * @class Timeout
 * @author Matthew Horn <mjhorn@propertypreswizard.com>
 */
var Timeout = (function(){
	var oTimers = {};
	
	var fnStart = function(sTimerId, fnCallback,iDelay){
		//if (typeof(oTimers[sTimerId]) == 'undefined'){
		//	oTimers[sTimerId] = {};
		//}
		//oTimers[sTimerId]['start'] = moment();
		oTimers[sTimerId] = setTimeout(function(){
			fnCallback();
			oTimers[sTimerId] = null;
		
		},iDelay);
	}
	
	var fnActive = function(sTimerId){
		return oTimers[sTimerId] === null;
	}
	
	// Return public methods
	return {
		start: fnStart,
		isActive: fnActive
		//end: fnEnd,
		//diff: fnDiff
	}
})();

