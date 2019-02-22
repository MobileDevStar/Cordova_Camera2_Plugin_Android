//
//  PPWCamera.h
//  PPWCamera
//
//  Created by Paul on 2014-10-20.
//
//

#import <Cordova/CDV.h>
#import "PPWCameraViewController.h"

@interface PPWCamera : CDVPlugin

// command method
-(void) openCamera:(CDVInvokedUrlCommand*)command;
-(void) closeCamera:(CDVInvokedUrlCommand*)command;
-(void) confirmCamera:(CDVInvokedUrlCommand*)command;
-(void) sendError:(NSString*)msg code:(int)errorId;

// return method
-(void) resultData:(NSDictionary*)output;
-(void) closeCamera;
-(BOOL) cameraAccessCheck;

// properties
@property (strong, nonatomic) PPWCameraViewController* overlay;
@property (strong, nonatomic) CDVInvokedUrlCommand* latestCommand;
@property (readwrite, assign) BOOL hasPendingOperation;

@end
