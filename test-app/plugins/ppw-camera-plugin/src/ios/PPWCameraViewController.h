//
//  PPWCameraViewController.h
//  PPWCamera
//
//  Created by Paul on 2014-10-20.
//  Copyright (c) 2014 Appnovation. All rights reserved.
//

#import <UIKit/UIKit.h>

@class PPWCamera;

@interface PPWCameraViewController : UIViewController <UIImagePickerControllerDelegate,UINavigationControllerDelegate,UIGestureRecognizerDelegate>

- (void)setOptions:(NSDictionary*)options;

- (IBAction)takePhotoBtnPressed:(id)sender forEvent:(UIEvent *)event;

@property (strong, nonatomic) PPWCamera* plugin;
@property (strong, nonatomic) UIImagePickerController* picker;

@end
