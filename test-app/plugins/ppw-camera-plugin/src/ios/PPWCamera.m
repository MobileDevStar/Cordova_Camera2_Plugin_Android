//
//  PPWCamera.m
//  PPWCamera
//
//  Created by Paul on 2014-10-20.
//
//

#import "PPWCamera.h"
#import <AVFoundation/AVFoundation.h>

#define SYSTEM_VERSION_LESS_THAN(v)                 ([[[UIDevice currentDevice] systemVersion] compare:v options:NSNumericSearch] == NSOrderedAscending)

static int instanceCount = 0;

@interface PPWCamera () <UIAlertViewDelegate>
@property NSString* confirmErrorMessage;
@property NSTimeInterval confirmationTimeInterval;
@property NSTimer* confirmationTimer;
@property UIWindow* window;
@end

@implementation PPWCamera

- (instancetype)init {
    if (!(self = [super init])) {
        return nil;
    }
    instanceCount = 0;
    return self;
}

-(void)dealloc {
    self.window = nil;
    instanceCount = 0;
}

#pragma mark - command methods

// command method
-(void)openCamera:(CDVInvokedUrlCommand *)command {
    
    //singleton check
    if (instanceCount>0) {
        [self sendError:@"Another camera instance already exists" code:0];
        return;
    }
    
    // Set the hasPendingOperation field to prevent the webview from crashing (undocumented)
    self.hasPendingOperation = YES;
    
    // Save CDVInvokedUrlCommand
    self.latestCommand = command;
    
    // Set confirmation parameters
    self.confirmErrorMessage = @"Error confirming photo captured";
    self.confirmationTimeInterval = 500;
    
    // Present the view
    if ([self cameraAccessCheck]) {
        self.overlay = [[PPWCameraViewController alloc] initWithNibName:@"PPWCameraViewController" bundle:nil];
        if (self.overlay) {
            instanceCount++;
            NSDictionary* options = [command argumentAtIndex:0];
            if (options[@"confirmErrorMessage"]) {
                self.confirmErrorMessage = options[@"confirmErrorMessage"];
            }
            if (options[@"confirmTimeInterval"]) {
                self.confirmationTimeInterval = [options[@"confirmTimeInterval"] intValue];
            }
            [self.overlay setOptions:options];
            self.overlay.plugin = self;
            self.overlay.modalTransitionStyle = UIModalTransitionStyleCrossDissolve;
            
            if (SYSTEM_VERSION_LESS_THAN(@"10")) {
                // add new ui window for PPWCameraViewController
                CGSize screenSize = [UIScreen mainScreen].bounds.size;
                self.window = [[UIWindow alloc] initWithFrame: CGRectMake(0, 0, screenSize.width, screenSize.height)];
                self.window.rootViewController = self.overlay;
                [self.window makeKeyAndVisible];
            } else {
                [self.viewController presentViewController:self.overlay animated:NO completion:nil];
            }
            

        }
        else {
            [self sendError:@"Error presenting camera view" code:0];
        }
    }
}

-(void)closeCamera:(CDVInvokedUrlCommand *)command {
    [self closeCamera];
}

-(void)confirmCamera:(CDVInvokedUrlCommand *)command {
    if (self.confirmationTimer) {
        [self.confirmationTimer invalidate];
        self.confirmationTimer = nil;
    }
    else if (!self.overlay) {
        [self sendError:@"Camera could not be confirmed. Camera activity is not available" code:0];
    }
}

#pragma mark return method

// return method
-(void)resultData:(NSDictionary *)output {
    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:output];
    [result setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:result callbackId:self.latestCommand.callbackId];
    
    // Unset the self.hasPendingOperation property
    self.hasPendingOperation = NO;
    
    //start timer to close if unconfirmed result sent
    if (self.confirmationTimer) {
        [self.confirmationTimer invalidate];
    }
    self.confirmationTimer = [NSTimer scheduledTimerWithTimeInterval: self.confirmationTimeInterval*0.001f
                                                              target: self
                                                            selector:@selector(showConfirmErrorPopup)
                                                            userInfo:nil
                                                             repeats:NO];
}

#pragma mark alertview delegate

- (void)alertView:(UIAlertView *)alertView clickedButtonAtIndex:(NSInteger)buttonIndex
{
    //close camera on error confirm
    [self closeCamera];
}

#pragma mark helper methods

-(void)showConfirmErrorPopup {
    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Error"
                                                    message:self.confirmErrorMessage
                                                   delegate:self
                                          cancelButtonTitle:@"OK"
                                          otherButtonTitles:nil];
    [alert show];
}

-(void)closeCamera {
    //clear singleton
    instanceCount--;
    
    // remove window
    self.window = nil;
    
    //unset property
    self.hasPendingOperation = NO;
    
    //remove view
    if (self.overlay) {
        [self.viewController dismissViewControllerAnimated:YES completion:nil];
        self.overlay = nil;
    }
    else {
        [self sendError:@"Camera could not be closed. Camera activity is not available" code:0];
    }
}

-(BOOL)cameraAccessCheck {
    AVAuthorizationStatus a = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
    switch (a) {
        case AVAuthorizationStatusAuthorized: {
            return YES;
        }
            break;
        case AVAuthorizationStatusNotDetermined: {
            [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo
                                     completionHandler:^(BOOL granted) {
                                         dispatch_async(dispatch_get_main_queue(), ^{
                                             if (granted) {
                                                 [self openCamera:self.latestCommand];
                                             }
                                             else {
                                                 [self sendError:@"Camera Access Failed" code:0];
                                             }
                                         });
                                     }];
            
        }
            break;
        case AVAuthorizationStatusRestricted:
        case AVAuthorizationStatusDenied:
        default: {
            [self sendError:@"Camera Access Denied" code:0];
        }
            break;
    }
    
    return NO;
}

#pragma mark error handling

-(void)sendError {
    [self sendError:nil code:0];
}

//code 0: general error
//code 1: close clicked
//code 2: back clicked
-(void)sendError:(NSString*)msg code:(int)errorId{
    if (!msg) {
        errorId = 0;
        msg = @"Camera Error";
    }
    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                            messageAsDictionary:@{
                                                                  @"code":@(errorId),
                                                                  @"message":msg
                                                                  }];
    [self.commandDelegate sendPluginResult:result callbackId:self.latestCommand.callbackId];
}

@end
