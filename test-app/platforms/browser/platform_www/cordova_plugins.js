cordova.define('cordova/plugin_list', function(require, exports, module) {
module.exports = [
    {
        "file": "plugins/cordova-plugin-camera/www/CameraConstants.js",
        "id": "cordova-plugin-camera.Camera",
        "pluginId": "cordova-plugin-camera",
        "clobbers": [
            "Camera"
        ]
    },
    {
        "file": "plugins/cordova-plugin-camera/www/CameraPopoverOptions.js",
        "id": "cordova-plugin-camera.CameraPopoverOptions",
        "pluginId": "cordova-plugin-camera",
        "clobbers": [
            "CameraPopoverOptions"
        ]
    },
    {
        "file": "plugins/cordova-plugin-camera/www/Camera.js",
        "id": "cordova-plugin-camera.camera",
        "pluginId": "cordova-plugin-camera",
        "clobbers": [
            "navigator.camera"
        ]
    },
    {
        "file": "plugins/cordova-plugin-camera/src/browser/CameraProxy.js",
        "id": "cordova-plugin-camera.CameraProxy",
        "pluginId": "cordova-plugin-camera",
        "runs": true
    },
    {
        "file": "plugins/ppw-camera-plugin/www/ppw_camera.js",
        "id": "ppw-camera-plugin.PPWCamera",
        "pluginId": "ppw-camera-plugin",
        "clobbers": [
            "navigator.PPWCamera"
        ]
    }
];
module.exports.metadata = 
// TOP OF METADATA
{
    "cordova-plugin-whitelist": "1.3.3",
    "cordova-plugin-browsersync": "0.1.7",
    "cordova-plugin-camera": "4.0.3",
    "cordova.plugins.diagnostic": "4.0.11",
    "cordova-plugin-geolocation": "4.0.1",
    "cordova-plugin-filepath": "1.5.1",
    "ppw-camera-plugin": "1.0.41"
}
// BOTTOM OF METADATA
});