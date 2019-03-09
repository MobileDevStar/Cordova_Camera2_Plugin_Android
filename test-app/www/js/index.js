
function capturePhoto(){
	$("#debug").html("");

	var aoOverlay = [
		{
			type: 'text', // Plain text (default)
			position: 'top center',  // Centered, also center justified
			value: 'Camera Test',
			size: '12', // always in sp (scale-independant pixels)
			top: 8 // Vertical offset from top (also in scale-independant pixels)
		},
		{
			type: 'text', // Plain text (default)
			position: 'bottom left', // Left justified text
			value: '640x480',
			size: '10',
			left: 4, // Horizontal offset from left
			bottom: 4 // Vertical offset from bottom
		}
	];

	var oSettings = {
		quality: 80, //value from 0 to 100
		encodingType: 'jpg', //only jpg or png
		previewWidth: 640, //camera aspect preview
		previewHeight: 480,
		targetWidth: 640, //output target size
		targetHeight: 480,
		overlay: aoOverlay,
		"options": {
			"bUnflagged": true,
			"bCheckedIn": false,
			"dateFormat": "MM/dd/yyyy",
			"work_order_id": "4",
			"report_id": "8532115",
			"ppw_username": "demo1",
			"oauth_username": "1c85b4254e4f746865154ed3b8afac78",
			"access_token": "643875e7afb11b5ea3db10acae9bfe9a1fb5de8f",
			"title": "WO# WI13703971",
			"json": true,
			"gps_id": 6
		}
	}

	// Required for android API > 23 (6.0.0). Camera will crash without both of these.
	requestCameraAuthorization(function(){
		requestLocationAuthorization(function(){
			// Custom Camera Plugin
			navigator.PPWCamera.getPicture(oSettings, onCaptureSuccess, onCaptureError);

		},function(){
			console.error('requestLocationAuthorization error');
		});
	},function(){
		console.error('requestCameraAuthorization error');
	});
}

function onCaptureSuccess(oImage){
	//console.log('oImage',oImage);

	confirmCamera(); // Keep alive for plugin

	if (!oImage['imageURI'].startsWith('file://')){
		var sFilePath = 'file://' + oImage['imageURI'];
	} else {
		var sFilePath = oImage['imageURI'];
	}

	var sHtml = '';

	sHtml += '<img src="' + sFilePath + '" alt="Test" width="100%" height="100%"></img>';
	$('#container-camera-test').html(sHtml);

	// // Resolve file system for image
	// resolveLocalFileSystemURL(sFilePath, function(fileEntry){
	// 	fileEntryCopy = fileEntry; // Rescope to parent

	// 	console.log('FS: resolveLocalFileSystemURL success ' + fileEntry.fullPath);

	// 	//FileApi.createDirectory(LocalFileSystem.TEMPORARY, 'test', fnCreateSuccess, fnCreateFail); // Move the file to temp
	// 	fnMoveSuccess(fileEntry);
	// }, function(error){
	// 	killCamera(); // For Camera1 (Appnovation) only
	// 	console.error('FS: resolveLocalFileSystemURL failed ' + error.code);
	// });

	// var fnMoveSuccess = function(fileEntry){
	// 	console.log('FS: moveTo success',fileEntry);

	// 	var sHtml = '';

	// 	//var sFilePath = 'cdvfile://localhost/temporary' + fileEntry['fullPath'];
	// 	var sFilePath = fileEntry.nativeURL;
	// 	//sHtml += '<img src="' + sFilePath + '" class="thumbnail" alt="Test" width="25%" height="25%" data-large="' + sFilePath + '"></img>';

	// 	//$('#container-camera-test').html(sHtml).show();

	// 	sHtml += '<img src="' + fileEntry.toInternalURL() + '" alt="Test" width="100%" height="100%"></img>';

	// 	$('#container-camera-test').html(sHtml);

	// }

	// var fnMoveFail = function(error){
	// 	console.error('moveTo failed ' + error.code);
	// 	msg_error('Error moving photo');
	// }
}

function onCaptureError(error){
	alert(error);
	//console.log("camera.getPicture: " + error);
}
