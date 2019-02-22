var PPWCamera = {
	getPicture: function(options, success, failure){
		cordova.exec(success, failure, "PPWCamera", "openCamera", [options]);
	},
	closeCamera: function(options, success, failure) {
		cordova.exec(success, failure, "PPWCamera", "closeCamera", [options]);
	},
	confirmPicture: function(options, success, failure){
		cordova.exec(success, failure, "PPWCamera", "confirmCamera", [options]);
	}
};
module.exports = PPWCamera;
