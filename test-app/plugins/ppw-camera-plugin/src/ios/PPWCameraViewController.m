//
//  PPWCameraViewController.m
//  PPWCamera
//
//  Created by Paul on 2014-10-20.
//  Copyright (c) 2014 Appnovation. All rights reserved.
//

#import "PPWCamera.h"
#import "PPWCameraViewController.h"
#import "MBProgressHUD.h"

#import <QuartzCore/QuartzCore.h>
#import <UIKit/UIKit.h>
#import <CommonCrypto/CommonCrypto.h>
#import <AVFoundation/AVFoundation.h>
#import <AssetsLibrary/AssetsLibrary.h>
#import <ImageIO/ImageIO.h>
#import <CoreLocation/CoreLocation.h>

#define MAX_ZOOM 3
#define SECRET_KEY @"password"
#define CAMERA_ASPECT 1.3333333f //default camera aspect ratio 4:3

#define FLASH_ICON_AUTO @"\ue000"
#define FLASH_ICON_ON @"\ue001"
#define FLASH_ICON_OFF @"\ue003"

#define FLASH_NAME_AUTO @"auto"
#define FLASH_NAME_TORCH @"torch"
#define FLASH_NAME_ON @"on"
#define FLASH_NAME_OFF @"off"

// This assigns a CGColor to a borderColor.
@interface CALayer(XibConfiguration)
@property(nonatomic, assign) UIColor* borderUIColor;
@end

//holder for carousel data
@interface TagItem : NSObject
@property(nonatomic,assign) int count; //item selected in carousel
@property(strong,nonatomic) NSArray* value; //list of carousel items
@property(strong,nonatomic) NSString* btn_id; //id from carousel button
@end

//flash data types
typedef NS_ENUM(NSInteger, FlashDataType) {
    kFlashDataTypeAuto,
    kFlashDataTypeTorch,
    kFlashDataTypeOn,
    kFlashDataTypeOff,
    kFlashDataTypeCount
};

//flash data modifier
@interface FlashData : NSObject
-(FlashDataType)getNextType;
-(void)setTypeByName:(NSString*)name;
-(void)setType:(FlashDataType)type;
-(void)updateButton:(UIButton*)b picker:(UIImagePickerController*)p;
@property(nonatomic,assign) FlashDataType type;
@property(strong,nonatomic) UIColor* color;
@property(strong,nonatomic) NSString* icon;
@property(nonatomic,assign) UIImagePickerControllerCameraFlashMode mode;
@property(strong,nonatomic) NSString* name;
@end

//for debugging
@implementation NSDictionary (BVJSONString)
-(NSString*) toJson:(BOOL) prettyPrint {
    NSError *error;
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:self
                                                       options:(NSJSONWritingOptions)    (prettyPrint ? NSJSONWritingPrettyPrinted : 0)
                                                         error:&error];

    if (! jsonData) {
        NSLog(@"%s: error: %@", __func__, error.localizedDescription);
        return @"{}";
    } else {
        return [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    }
}
@end

@interface PPWCameraViewController () {
    int mPhotoWidth;
    int mPhotoHeight;
    float mPreviewWidth;
    float mPreviewHeight;
    NSString* mEncodingType;
    int mQuality;
    NSString* mFlashType;
    int mThumbnail;
    BOOL mBackNotify;
    NSMutableArray* mDataOutput;
    UIPinchGestureRecognizer* mPinchGestureRecognizer;
    float mPhotoScale;
    float mPhotoScaleLast;
    CGAffineTransform mPreviewTranform;
    float mDateFontSize;
    NSString* mDateFormat;
    NSDictionary* mOptions;
}
@property (strong, nonatomic) UIView *preview;
@property (strong, nonatomic) IBOutlet UIButton *flashBtn;
@property (strong, nonatomic) IBOutlet UIButton *takePictureBtn;
@property (strong, nonatomic) FlashData* flashBtnData;
@property (strong, nonatomic) IBOutlet UIButton *thumbnailBtn;
@property (strong, nonatomic) IBOutlet UIImageView *imageView;
@property (strong, nonatomic) IBOutlet UIButton *imageViewBtn;
@property (strong, nonatomic) CLLocationManager* locationManager;
@property (strong, nonatomic) MBProgressHUD *hud;
@end

@implementation PPWCameraViewController

- (id)initWithNibName:(NSString *)nibNameOrNil bundle:(NSBundle *)nibBundleOrNil {
    if (![UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceRear]) {
        return nil; //no camera available
    }
    self = [super initWithNibName:nibNameOrNil bundle:nibBundleOrNil];
    if (self) {
        self.picker = [[UIImagePickerController alloc] init];
        self.picker.sourceType = UIImagePickerControllerSourceTypeCamera;
        self.picker.cameraCaptureMode = UIImagePickerControllerCameraCaptureModePhoto;
        self.picker.cameraDevice = UIImagePickerControllerCameraDeviceRear;
        self.picker.showsCameraControls = NO;
        self.picker.allowsEditing = NO;
        self.picker.delegate = self;
        self.picker.view.transform = CGAffineTransformMakeRotation(-M_PI_2);
        mPreviewTranform = self.picker.view.transform;
    }
    return self;
}

-(void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    _flashBtn.hidden = ![UIImagePickerController isFlashAvailableForCameraDevice:UIImagePickerControllerCameraDeviceRear];

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(orientationChanged:)
                                                 name:UIDeviceOrientationDidChangeNotification
                                               object:nil];
}

-(void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];

    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:UIDeviceOrientationDidChangeNotification
                                                  object:nil];
}

-(void)viewDidLoad {
    [super viewDidLoad];

    //initialize GPS
    self.locationManager = [CLLocationManager new];
    self.locationManager.desiredAccuracy = kCLLocationAccuracyBest;
    self.locationManager.distanceFilter = kCLDistanceFilterNone;
    if ([self.locationManager respondsToSelector:@selector(requestWhenInUseAuthorization)]) {
        [self.locationManager requestWhenInUseAuthorization];
    }
    [self.locationManager startUpdatingLocation];

    //initialize parameters
    mPhotoScale = 1;
    mPhotoScaleLast = 1;

    //calculate screen bounds
    CGRect screenBounds = [UIScreen mainScreen].bounds;
    float screenWidth = screenBounds.size.height > screenBounds.size.width ? screenBounds.size.height : screenBounds.size.width;
    float screenHeight = screenBounds.size.height <= screenBounds.size.width ? screenBounds.size.height : screenBounds.size.width;;
    float screenRatio = screenWidth / screenHeight;
    float previewRatio = mPreviewWidth / mPreviewHeight;

    float width = previewRatio*screenHeight;
    float height = screenHeight;
    if (previewRatio > screenRatio) {
        width = screenWidth;
        height = screenWidth / previewRatio;
    }

    //create preview
    self.preview = [[UIView alloc] initWithFrame:CGRectMake(0, 0, width, height)];
    [self.preview setContentMode:UIViewContentModeScaleAspectFill];
    [self.preview setClipsToBounds:YES];
    [self.view insertSubview:self.preview atIndex:0];

    width = CAMERA_ASPECT*screenHeight;
    height = screenHeight;
    if (previewRatio > screenRatio) {
        width = screenWidth;
        height = screenWidth / CAMERA_ASPECT;
    }

    //add camera view
    [self addChildViewController:self.picker];
    [self.preview addSubview:self.picker.view];
    [self.picker didMoveToParentViewController:self];
    CGRect r = self.picker.view.frame;
    r.origin = CGPointMake(0,0);
    r.size = CGSizeMake(width, height);
    self.picker.view.frame = r;

    self.preview.center = CGPointMake(screenWidth / 2, screenHeight / 2);
    self.picker.view.center = CGPointMake(self.preview.bounds.size.width / 2, self.preview.bounds.size.height / 2);

    switch ([[UIDevice currentDevice] orientation]) {
        case UIDeviceOrientationLandscapeRight:
            self.view.transform = CGAffineTransformMakeRotation(-M_PI_2);
            self.preview.transform = CGAffineTransformMakeRotation(M_PI);
            break;

        default:
            self.view.transform = CGAffineTransformMakeRotation(M_PI_2);
            break;
    }

    //set flash type
    _flashBtn.hidden = ![UIImagePickerController isFlashAvailableForCameraDevice:UIImagePickerControllerCameraDeviceRear];
    _flashBtnData = [[FlashData alloc] init];
    [_flashBtnData setTypeByName:mFlashType];
    if (!_flashBtn.hidden) {
        [_flashBtnData updateButton:_flashBtn picker:_picker];
    }

    //detect camera zoom
    mPinchGestureRecognizer = [[UIPinchGestureRecognizer alloc] initWithTarget:self action:@selector(handlePinchWithGestureRecognizer:)];
    [self.view addGestureRecognizer:mPinchGestureRecognizer];
    [self.preview addGestureRecognizer:mPinchGestureRecognizer];
    [self.picker.view addGestureRecognizer:mPinchGestureRecognizer];
    [self.picker.cameraOverlayView addGestureRecognizer:mPinchGestureRecognizer];
}

-(void)dealloc
{
    if (self.picker) {
        [self.picker willMoveToParentViewController:nil];
        [self.picker.view removeFromSuperview];
        [self.picker removeFromParentViewController];
        [self.picker didMoveToParentViewController:nil];
    }
    if (self.locationManager) {
        [self.locationManager stopUpdatingLocation];
    }
}


- (void)setOptions:(NSDictionary*)options {
    mPhotoWidth = 640;
    mPhotoHeight = 480;
    mPreviewWidth = 640;
    mPreviewHeight = 480;
    mEncodingType = @"jpg";
    mQuality = 100;
    mFlashType = FLASH_NAME_AUTO;
    mThumbnail = 25;
    mBackNotify = NO;
    mDataOutput = [[NSMutableArray alloc] init];
    mDateFontSize = 20;
    mDateFormat = @"";

    //scroll through overlay options
    if (!options || options.count <= 0)
        return;

    if (options[@"targetWidth"])
        mPhotoWidth = [options[@"targetWidth"] intValue];
    if (options[@"targetHeight"])
        mPhotoHeight = [options[@"targetHeight"] intValue];
    if (options[@"previewWidth"])
        mPreviewWidth = [options[@"previewWidth"] intValue];
    if (options[@"previewHeight"])
        mPreviewHeight = [options[@"previewHeight"] intValue];
    if (options[@"encodingType"])
        mEncodingType = options[@"encodingType"];
    if (options[@"quality"])
        mQuality = [options[@"quality"] intValue];
    if (options[@"flashType"])
        mFlashType = options[@"flashType"];
    if (options[@"thumbnail"])
        mThumbnail = [options[@"thumbnail"] intValue];
    if (options[@"backNotify"])
        mBackNotify = [options[@"backNotify"] boolValue];
    if (options[@"dateFontSize"])
        mDateFontSize = [options[@"dateFontSize"] intValue];
    if (options[@"dateFormat"])
        mDateFormat = options[@"dateFormat"];
    if (options[@"options"])
        mOptions = options[@"options"];

    NSArray* overlay = options[@"overlay"];
    if (!overlay)
        return;

    for(int i=0; i<[overlay count]; ++i) {
        NSDictionary* item = overlay[i];
        NSString* type = item[@"type"];
        if (!type)
            continue;

        UIView* view = nil;

        //setup text
        if ([type rangeOfString:@"text"].length>0) {
            NSString* value = item[@"value"];
            UILabel* label = [[UILabel alloc] init];
            [label setText:value];
            label.translatesAutoresizingMaskIntoConstraints = NO;
            [label setTextColor:[UIColor whiteColor]];
            [label setShadowColor:[UIColor blackColor]];
            if (item[@"size"])
                [label setFont:[UIFont systemFontOfSize:[item[@"size"] intValue]]];
            [self.view addSubview:label];
            view = label;
        }
        //setup carousel
        else if ([type rangeOfString:@"carousel"].length>0) {
            NSArray* value = item[@"value"];
            TagItem* t = [[TagItem alloc] init];
            t.value = value;
            t.btn_id = item[@"id"];
            t.count = 0;
            NSString* initial = item[@"initial"];
            for(int i=0; i<[value count]; ++i) {
                if (initial && [initial compare:value[i] options:NSCaseInsensitiveSearch] == NSOrderedSame) {
                    t.count = i;
                }
            }
            NSString* title = @"error";
            if (value && [value count] > 0) {
                title = value[t.count];
            }
            else
                return; //exit if no labels provided

            UIButton *button = [UIButton buttonWithType:UIButtonTypeRoundedRect];
            button.tag = [mDataOutput count];
            [mDataOutput addObject:t];

            [button addTarget:self action:@selector(carouselBtnPressed:) forControlEvents:UIControlEventTouchUpInside];
            [button setTitle:title forState:UIControlStateNormal];
            if (item[@"size"])
                [button.titleLabel setFont:[UIFont systemFontOfSize:[item[@"size"] intValue]]];
            [button sizeToFit];
            button.translatesAutoresizingMaskIntoConstraints = NO;
            [button setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
            [button setTitleShadowColor:[UIColor lightGrayColor] forState:UIControlStateNormal];
            button.backgroundColor = [[UIColor darkGrayColor] colorWithAlphaComponent:0.25f];
            [self.view addSubview:button];
            view = button;
        }
        else
            continue;

        //setup layout
        NSLayoutFormatOptions layoutFormatV = NSLayoutFormatAlignAllCenterX;
        NSString* visualFormatV = nil;
        NSLayoutFormatOptions layoutFormatH = NSLayoutFormatAlignAllCenterY;
        NSString* visualFormatH = nil;
        NSString* position = item[@"position"];
        if (position) {
            if ([position rangeOfString:@"top"].length>0) {
                layoutFormatH = NSLayoutFormatAlignAllTop;
                if (item[@"top"])
                    visualFormatV = [NSString stringWithFormat:@"V:|-%@-[view]",item[@"top"]];
                else
                    visualFormatV = @"V:|-0-[view]";
            }
            else if ([position rangeOfString:@"bottom"].length>0) {
                layoutFormatH = NSLayoutFormatAlignAllBottom;
                if (item[@"bottom"])
                    visualFormatV = [NSString stringWithFormat:@"V:[view]-%@-|",item[@"bottom"]];
                else
                    visualFormatV = @"V:[view]-0-|";
            }
            else if ([position hasPrefix:@"center"]) {

            }
            if ([position rangeOfString:@"left"].length>0) {
                layoutFormatV = NSLayoutFormatAlignAllLeft;
                if (item[@"left"])
                    visualFormatH = [NSString stringWithFormat:@"H:|-%@-[view]",item[@"left"]];
                else
                    visualFormatV = @"V:|-0-[view]";
            }
            else if ([position rangeOfString:@"right"].length>0) {
                layoutFormatV = NSLayoutFormatAlignAllRight;
                if (item[@"right"])
                    visualFormatH = [NSString stringWithFormat:@"H:[view]-%@-|",item[@"right"]];
                else
                    visualFormatV = @"V:[view]-0-|";
            }
            else if ([position hasSuffix:@"center"]) {

            }
        }

        if ([[NSLayoutConstraint class] respondsToSelector:@selector(activateConstraints:)]) {
            if (visualFormatV) {
                [NSLayoutConstraint activateConstraints:[NSLayoutConstraint constraintsWithVisualFormat:visualFormatV
                                                                                                options:layoutFormatH
                                                                                                metrics:nil
                                                                                                  views:@{@"view":view}]];
            }
            else {
                [NSLayoutConstraint activateConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"H:[super]-(<=1)-[view]"
                                                                                                options:NSLayoutFormatAlignAllCenterY
                                                                                                metrics:nil
                                                                                                  views:@{@"super":self.view,@"view":view}]];
            }
            if (visualFormatH) {
                [NSLayoutConstraint activateConstraints:[NSLayoutConstraint constraintsWithVisualFormat:visualFormatH
                                                                                                options:layoutFormatV
                                                                                                metrics:nil
                                                                                                  views:@{@"view":view}]];
            }
            else {
                [NSLayoutConstraint activateConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"V:[super]-(<=1)-[view]"
                                                                                                options:NSLayoutFormatAlignAllCenterX
                                                                                                metrics:nil
                                                                                                  views:@{@"super":self.view,@"view":view}]];
            }
        }
        else {
            //add position and margin
            if (visualFormatV) {
                [self.view addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:visualFormatV
                                                                                  options:layoutFormatH
                                                                                  metrics:nil
                                                                                    views:@{@"view":view}]];
            }
            else {
                [self.view addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"H:[super]-(<=1)-[view]"
                                                                                  options:NSLayoutFormatAlignAllCenterY
                                                                                  metrics:nil
                                                                                    views:@{@"super":self.view,@"view":view}]];
            }
            if (visualFormatH) {
                [self.view addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:visualFormatH
                                                                                  options:layoutFormatV
                                                                                  metrics:nil
                                                                                    views:@{@"view":view}]];
            }
            else {
                [self.view addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:@"V:[super]-(<=1)-[view]"
                                                                                  options:NSLayoutFormatAlignAllCenterX
                                                                                  metrics:nil
                                                                                    views:@{@"super":self.view,@"view":view}]];
            }
        }
    }
}

#pragma mark - gesture recognizer

-(void)handlePinchWithGestureRecognizer:(UIPinchGestureRecognizer*)pinchGestureRecognizer {
    mPhotoScale *= pinchGestureRecognizer.scale/mPhotoScaleLast;
    mPhotoScaleLast = pinchGestureRecognizer.scale;
    if (pinchGestureRecognizer.state == UIGestureRecognizerStateEnded) {
        mPhotoScaleLast = 1;
    }
    if (mPhotoScale<1) {
        mPhotoScale = 1;
    }
    else if (mPhotoScale>=MAX_ZOOM) {
        mPhotoScale = MAX_ZOOM;
    }
    self.picker.cameraOverlayView.transform = CGAffineTransformIdentity;
    self.picker.view.transform = CGAffineTransformScale(mPreviewTranform,mPhotoScale,mPhotoScale);
}

#pragma mark - Orientation and status bar

- (BOOL)prefersStatusBarHidden {
    return YES;
}
-(BOOL)shouldAutorotate {
    return NO;
}
- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
    return UIInterfaceOrientationMaskPortrait;
}
-(UIInterfaceOrientation)preferredInterfaceOrientationForPresentation {
    return UIInterfaceOrientationPortrait;
}


#pragma volume button callbacks

- (void) volumeUpButtonUpNotif: string {
    [self takeCameraPicture];
}
- (void) volumeDownButtonDownNotif: string {
    [self takeCameraPicture];
}

#pragma orientation callback
- (void)orientationChanged:(NSNotification *)notification{
    switch ([[UIDevice currentDevice] orientation]) {
        case UIDeviceOrientationLandscapeLeft:
            self.view.transform = CGAffineTransformMakeRotation(M_PI_2);
            self.preview.transform = CGAffineTransformMakeRotation(0);
            break;
        case UIDeviceOrientationLandscapeRight:
            self.view.transform = CGAffineTransformMakeRotation(-M_PI_2);
            self.preview.transform = CGAffineTransformMakeRotation(-M_PI);
            break;

        default:
            break;
    }

}

#pragma mark UIButton delegates

-(void)carouselBtnPressed:(id)sender {
    UIButton* b = sender;
    TagItem* t = mDataOutput[b.tag];
    t.count++;
    if (t.count>=[t.value count]) {
        t.count = 0;
    }
    NSString* title = t.value[t.count];
    [b setTitle:title forState:UIControlStateNormal];
}

- (IBAction)takePhotoBtnPressed:(id)sender forEvent:(UIEvent *)event {
    [self takeCameraPicture];
}

- (IBAction)closeButtonAction:(id)sender {
    [self.plugin closeCamera];
    if (mBackNotify) {
        [self.plugin sendError:@"close button clicked" code:1];
    }
}
- (IBAction)flashBtnPressed:(id)sender {
    [_flashBtnData setType:[_flashBtnData getNextType]];
    [_flashBtnData updateButton:_flashBtn picker:_picker];
}
- (IBAction)thumbnailBtnPressed:(id)sender {
    [_imageView setHidden:NO];
    [_imageViewBtn setHidden:NO];
}
- (IBAction)imageViewBtnPressed:(id)sender {
    [_imageView setHidden:YES];
    [_imageViewBtn setHidden:YES];
}

#pragma mark - UIImagePicker helper method
-(void) takeCameraPicture {
    if (![self.plugin cameraAccessCheck])
        return;

        if (self.hud) {
        [self.hud hide:YES];
        self.hud = nil;
    }

    self.hud = [MBProgressHUD showHUDAddedTo:self.view animated:YES];
    self.hud.mode = MBProgressHUDModeText;
    self.hud.labelText = @"Saving...";
    [self.picker takePicture];
}

#pragma mark - UIImagePickerControllerDelegate

-(void) imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary *)info {

    NSString* timestamp = [NSString stringWithFormat:@"%.0f",[[NSDate date] timeIntervalSince1970]*1000];
    NSString* filename = [NSString stringWithFormat:@"%@.%@",timestamp,mEncodingType];
    NSString* filenameThumb = [NSString stringWithFormat:@"%@_thumb.%@",timestamp,mEncodingType];
    NSString* filenameData = [NSString stringWithFormat:@"%@.json",timestamp];

    //check free space
    NSError *error = nil;
    NSArray* paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
    NSDictionary * const pathAttributes = [[NSFileManager defaultManager] attributesOfFileSystemForPath:[paths firstObject] error:&error];
    NSAssert(pathAttributes, @"");
    NSNumber * const fileSystemSizeInBytes = [pathAttributes objectForKey: NSFileSystemFreeSize];
    const long long numberOfBytesRemaining = [fileSystemSizeInBytes longLongValue];

    // Image taken
    UIImage* image = [info objectForKey:UIImagePickerControllerOriginalImage];

    // Get Date String
    NSDictionary *metadataDictionary = (NSDictionary *)[info valueForKey:UIImagePickerControllerMediaMetadata];
    NSDateFormatter *timeFormatter  = [NSDateFormatter new];
    [timeFormatter setDateFormat:mDateFormat];
    NSString* dateString = [timeFormatter stringFromDate:[NSDate date]];

    //resize images
    UIImage* imageResize = [self resizeImage:image date:dateString];
    UIImage* imageThumb = nil;
    if (mThumbnail > 0) {
        imageThumb = [self resizeImage:image width:imageResize.size.width*(mThumbnail*0.01f) height:imageResize.size.height*(mThumbnail*0.01f) scale:mPhotoScale date:dateString];
    }

    // Image path
    NSString* documentsDirectory = [paths objectAtIndex:0];
    NSString* dataPath = [documentsDirectory stringByAppendingPathComponent:filenameData];
    NSString* imagePath = [documentsDirectory stringByAppendingPathComponent:filename];
    NSString* imagePathThumb = @"";
    if (mThumbnail > 0) {
        imagePathThumb = [documentsDirectory stringByAppendingPathComponent:filenameThumb];
    }

    // Image data
    NSData* imageData = nil;
    NSData* imageDataThumb = nil;
    if ([mEncodingType rangeOfString:@"png"].length>0) {
        imageData = UIImagePNGRepresentation(imageResize);
        if (mThumbnail > 0) {
            imageDataThumb = UIImagePNGRepresentation(imageThumb);
        }
    } else {
        imageData = UIImageJPEGRepresentation(imageResize, mQuality*0.01f);
        if (mThumbnail > 0) {
            imageDataThumb = UIImageJPEGRepresentation(imageThumb, mQuality*0.01f);
        }
    }

    NSUInteger dataLength = [imageData length];
    if (imageDataThumb) {
        dataLength += [imageDataThumb length];
    }
    if (numberOfBytesRemaining <= dataLength) {
        UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Unable to Save - Disk Full"
                                                        message:[NSString stringWithFormat:@"Available Space: %lld",numberOfBytesRemaining]
                                                       delegate:nil
                                              cancelButtonTitle:@"OK"
                                              otherButtonTitles:nil];
        [alert show];
    }
    else {
        //add exif meta data
        imageData = [self taggedImageData:imageData metadata:metadataDictionary orientation:UIImageOrientationUp];

        // Write the data to the file
        [imageData writeToFile:imagePath atomically:YES];
        if (imageDataThumb) {
            [imageDataThumb writeToFile:imagePathThumb atomically:YES];
        }

        //generate hash of the image
        NSString* hash = [self hmacsha512:[self hexadecimalString:imageData] secret:SECRET_KEY];

        NSMutableDictionary* output = [@{
                                 @"imageURI":imagePath,
                                 @"imageThumbURI": imagePathThumb,
                                 @"lastModifiedDate":timestamp,
                                 @"size":[@([imageData length]) stringValue],
                                 @"type":mEncodingType,
                                 @"hash":hash,
                                 @"flashType":[_flashBtnData name],
                                 @"options":mOptions,
                                 @"root":@"external",
                                 @"jsonURI":dataPath
                                 } mutableCopy];

        if ([mDataOutput count]>0) {
            NSMutableDictionary* data = [[NSMutableDictionary alloc] init];

            for(TagItem* t in mDataOutput) {
                [data setValue:t.value[t.count] forKey:t.btn_id];
            }

            [output setValue:data forKey:@"data"];
        }

        //write json data file
        NSData* jsonData = [[output toJson:true] dataUsingEncoding:NSUTF8StringEncoding];
        [jsonData writeToFile:dataPath atomically:YES];

        //update thumbnail
        if (mThumbnail > 0) {
            [_thumbnailBtn setImage:imageThumb forState:UIControlStateNormal];
            [_thumbnailBtn.imageView setContentMode:UIViewContentModeScaleAspectFill];
            [_thumbnailBtn setHidden:NO];

            //hide button
            [_imageViewBtn setHidden:YES];

            //setup image view
            [_imageView setImage:imageResize];
            [_imageView setHidden:YES];
        }

        // Return output
        [self.plugin resultData:output];
    }
    dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, 0.01 * NSEC_PER_SEC);
    dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
        [MBProgressHUD hideHUDForView:self.view animated:YES];
    });
}

#pragma mark - image scale and crop

- (UIImage*)resizeImage:(UIImage*)image date:(NSString*)dateString {
    return [self resizeImage:image width:mPhotoWidth height:mPhotoHeight scale:mPhotoScale date:dateString];
}

- (UIImage*)resizeImage:(UIImage*)image width:(float)photoWidth height:(float)photoHeight scale:(float)photoScale date:(NSString*)dateString{
    float previewRatio = mPreviewWidth / mPreviewHeight;
    float width = CAMERA_ASPECT*photoHeight;
    float height = photoHeight;
    if (previewRatio > CAMERA_ASPECT) {
        width = photoWidth;
        height = photoWidth / CAMERA_ASPECT;
    }

    //down scale and crop
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(photoWidth,photoHeight),YES,1);
    switch (image.imageOrientation) {
        case UIImageOrientationLeft:
        case UIImageOrientationRight:
            image = [UIImage imageWithCGImage:image.CGImage
                                scale:1
                          orientation:UIImageOrientationUp];
            break;
        case UIImageOrientationUp:
        case UIImageOrientationDown:
        default:
            break;
    }
    CGRect cropRect = CGRectMake((photoWidth-(width*photoScale))*0.5f, (photoHeight-(height*photoScale))*0.5f, width*photoScale, height*photoScale);
    UIRectClip(cropRect);
    [image drawInRect:cropRect];

    // Position the date in the bottom right
    NSDictionary* attributes = @{NSFontAttributeName : [UIFont fontWithName:@"GillSans-Bold" size:mDateFontSize * (photoWidth/mPhotoWidth)],
                                 NSStrokeColorAttributeName : [UIColor darkGrayColor],
                                 NSForegroundColorAttributeName : [UIColor yellowColor],
                                 NSStrokeWidthAttributeName : @-5.0};

    const CGFloat dateWidth = [dateString sizeWithAttributes:attributes].width;
    const CGFloat dateHeight = [dateString sizeWithAttributes:attributes].height;
    const CGFloat datePadding = 5;
    [dateString drawAtPoint:CGPointMake(cropRect.size.width - dateWidth - datePadding, cropRect.size.height - dateHeight - datePadding)
             withAttributes:attributes];

    UIImage* scaleImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();

    //maintain orientation if upside down
//    if (image.imageOrientation==UIImageOrientationDown) {
//        return [UIImage imageWithCGImage:scaleImage.CGImage
//                                   scale:1
//                             orientation:image.imageOrientation];
//    }
    return scaleImage;
}

#pragma mark - HMAC-SHA512 encoding

/* Returns hexadecimal string of NSData. Empty string if data is empty.   */
- (NSString *)hexadecimalString:(NSData *)data {
    const unsigned char *dataBuffer = (const unsigned char *)[data bytes];

    if (!dataBuffer)
        return [NSString string];

    NSUInteger          dataLength  = [data length];
    NSMutableString     *hexString  = [NSMutableString stringWithCapacity:(dataLength * 2)];

    for (int i = 0; i < dataLength; ++i)
        [hexString appendString:[NSString stringWithFormat:@"%02lx", (unsigned long)dataBuffer[i]]];

    return [NSString stringWithString:hexString];
}

- (NSString *)hmacsha512:(NSString *)data secret:(NSString *)key {

    const char *cKey  = [key cStringUsingEncoding:NSASCIIStringEncoding];
    const char *cData = [data cStringUsingEncoding:NSASCIIStringEncoding];

    unsigned char cHMAC[CC_SHA512_DIGEST_LENGTH];

    CCHmac(kCCHmacAlgSHA512, cKey, strlen(cKey), cData, strlen(cData), cHMAC);

    NSData *HMAC = [[NSData alloc] initWithBytes:cHMAC length:sizeof(cHMAC)];

    NSString *hash = [HMAC base64EncodedStringWithOptions:NSDataBase64Encoding64CharacterLineLength];

    return hash;
}

#pragma mark - exif tagging

- (NSData *)writeMetadataIntoImageData:(NSData *)imageData metadata:(NSMutableDictionary *)metadata {

    CGImageSourceRef source = CGImageSourceCreateWithData((__bridge CFDataRef) imageData, NULL);
    CFStringRef UTI = CGImageSourceGetType(source);
    NSMutableData *dest_data = [NSMutableData data];
    CGImageDestinationRef destination = CGImageDestinationCreateWithData((__bridge CFMutableDataRef)dest_data, UTI, 1, NULL);
    if (!destination) {
        [self.plugin sendError:@"Error: Could not create image destination" code:0];
    }
    CGImageDestinationAddImageFromSource(destination, source, 0, (__bridge CFDictionaryRef) metadata);
    BOOL success = NO;
    success = CGImageDestinationFinalize(destination);
    if (!success) {
        [self.plugin sendError:@"Error: Could not create data from image destination" code:0];
    }
    CFRelease(destination);
    CFRelease(source);
    return dest_data;
}

- (NSData *)taggedImageData:(NSData *)imageData metadata:(NSDictionary *)metadata orientation:(UIImageOrientation)orientation {
    CLLocation *location = [self.locationManager location];
    NSMutableDictionary *newMetadata = [NSMutableDictionary dictionaryWithDictionary:metadata];
    if (!newMetadata[(NSString *)kCGImagePropertyGPSDictionary] && location) {
        newMetadata[(NSString *)kCGImagePropertyGPSDictionary] = [self gpsDictionaryForLocation:location];
    }

    // Reference: http://sylvana.net/jpegcrop/exif_orientation.html
    /* The intended display orientation of the image. If present, the value
     * of this key is a CFNumberRef with the same value as defined by the
     * TIFF and Exif specifications.  That is:
     *   1  =  0th row is at the top, and 0th column is on the left.
     *   2  =  0th row is at the top, and 0th column is on the right.
     *   3  =  0th row is at the bottom, and 0th column is on the right.
     *   4  =  0th row is at the bottom, and 0th column is on the left.
     *   5  =  0th row is on the left, and 0th column is the top.
     *   6  =  0th row is on the right, and 0th column is the top.
     *   7  =  0th row is on the right, and 0th column is the bottom.
     *   8  =  0th row is on the left, and 0th column is the bottom.
     * If not present, a value of 1 is assumed. */
    int newOrientation;
    switch (orientation) {
        case UIImageOrientationUp: newOrientation = 1; break;
        case UIImageOrientationDown: newOrientation = 3; break;
        case UIImageOrientationLeft: newOrientation = 8; break;
        case UIImageOrientationRight: newOrientation = 6; break;
        case UIImageOrientationUpMirrored: newOrientation = 2; break;
        case UIImageOrientationDownMirrored: newOrientation = 4; break;
        case UIImageOrientationLeftMirrored: newOrientation = 5; break;
        case UIImageOrientationRightMirrored: newOrientation = 7; break;
        default:
            newOrientation = -1;
    }
    if (newOrientation != -1) {
        newMetadata[(NSString *)kCGImagePropertyOrientation] = @(newOrientation);
    }
    NSData *newImageData = [self writeMetadataIntoImageData:imageData metadata:newMetadata];
    return newImageData;
}

- (NSDictionary *)gpsDictionaryForLocation:(CLLocation *)location {
    NSTimeZone      *timeZone   = [NSTimeZone timeZoneWithName:@"UTC"];
    NSDateFormatter *timeFormatter  = [NSDateFormatter new];
    [timeFormatter setTimeZone:timeZone];
    [timeFormatter setDateFormat:@"HH:mm:ss.SS"];

    NSDictionary *gpsDict = @{(NSString *)kCGImagePropertyGPSLatitude: @(fabs(location.coordinate.latitude)),
                              (NSString *)kCGImagePropertyGPSLatitudeRef: ((location.coordinate.latitude >= 0) ? @"N" : @"S"),
                              (NSString *)kCGImagePropertyGPSLongitude: @(fabs(location.coordinate.longitude)),
                              (NSString *)kCGImagePropertyGPSLongitudeRef: ((location.coordinate.longitude >= 0) ? @"E" : @"W"),
                              (NSString *)kCGImagePropertyGPSTimeStamp: [timeFormatter stringFromDate:[location timestamp]],
                              (NSString *)kCGImagePropertyGPSAltitude: @(fabs(location.altitude)),
                              };
    return gpsDict;
}

@end

#pragma mark - helper class implementation

//assigns the CG Color to a border
@implementation CALayer(XibConfiguration)
-(void)setBorderUIColor:(UIColor*)color {
    self.borderColor = color.CGColor;
}
-(UIColor*)borderUIColor{
    return [UIColor colorWithCGColor:self.borderColor];
}
@end

//empty tag structure
@implementation TagItem
@end

//flash data modifier
@implementation FlashData
-(FlashDataType)getNextType {
    return (FlashDataType)(_type+1 >= kFlashDataTypeCount ? 0 : _type+1);
}
-(void)setTypeByName:(NSString *)name {
    if ([name compare:FLASH_NAME_TORCH options:NSCaseInsensitiveSearch] == NSOrderedSame) {
        [self setType:kFlashDataTypeTorch];
    }
    else if ([name compare:FLASH_NAME_ON options:NSCaseInsensitiveSearch] == NSOrderedSame) {
        [self setType:kFlashDataTypeOn];
    }
    else if ([name compare:FLASH_NAME_OFF options:NSCaseInsensitiveSearch] == NSOrderedSame) {
        [self setType:kFlashDataTypeOff];
    }
    else {
        [self setType:kFlashDataTypeAuto];
    }
}
-(void)setType:(FlashDataType)type {
    switch (type) {
        case kFlashDataTypeAuto:
            _color = [UIColor whiteColor];
            _icon = FLASH_ICON_AUTO;
            _mode = UIImagePickerControllerCameraFlashModeAuto;
            _name = FLASH_NAME_AUTO;
            break;
        case kFlashDataTypeTorch:
            _color = [UIColor greenColor];
            _icon = FLASH_ICON_ON;
            _mode = UIImagePickerControllerCameraFlashModeOff;
            _name = FLASH_NAME_TORCH;
            break;
        case kFlashDataTypeOn:
            _color = [UIColor yellowColor];
            _icon = FLASH_ICON_ON;
            _mode = UIImagePickerControllerCameraFlashModeOn;
            _name = FLASH_NAME_ON;
            break;
        case kFlashDataTypeOff:
            _color = [UIColor darkGrayColor];
            _icon = FLASH_ICON_OFF;
            _mode = UIImagePickerControllerCameraFlashModeOff;
            _name = FLASH_NAME_OFF;
            break;
        default:
            break;
    }
    _type = type;
}
-(void)updateButton:(UIButton *)b picker:(UIImagePickerController*)p {
    AVCaptureDevice *device = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
    [device lockForConfiguration:nil];
    if (kFlashDataTypeTorch == _type && [device isTorchModeSupported:AVCaptureTorchModeOn]) {
        [device setTorchMode:AVCaptureTorchModeOn];
        [device setFlashMode:AVCaptureFlashModeOn];
    }
    else {
        [device setTorchMode:AVCaptureTorchModeOff];
        [device setFlashMode:AVCaptureFlashModeOff];
    }
    [device unlockForConfiguration];

    if ([[[UIDevice currentDevice] systemVersion] compare:@"10" options:NSNumericSearch] == NSOrderedDescending) {
        // NOTE: iOS 10 bug, requires camera controls to be shown before being able to set flash, the controls show/hide so fast, it's invisible to see in real life
        p.showsCameraControls = YES;
        p.cameraFlashMode = _mode;
        p.showsCameraControls = NO;
    } else {
        p.cameraFlashMode = _mode;
    }
    b.tag = _mode;
    b.layer.borderUIColor = _color;
    [b setTitleColor:_color forState:UIControlStateNormal];
    [b setTitle:_icon forState:UIControlStateNormal];
}
@end
