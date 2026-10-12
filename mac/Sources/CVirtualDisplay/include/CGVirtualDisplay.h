// macOS の非公開API CGVirtualDisplay（CoreGraphics 内）の宣言。
// DeskPad / BetterDisplay / Chromium と同じもの。
#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>

NS_ASSUME_NONNULL_BEGIN

@class CGVirtualDisplay;

@interface CGVirtualDisplayDescriptor : NSObject
@property(retain, nonatomic) dispatch_queue_t queue;
@property(retain, nonatomic) NSString *name;
@property(nonatomic) unsigned int maxPixelsHigh;
@property(nonatomic) unsigned int maxPixelsWide;
@property(nonatomic) CGSize sizeInMillimeters;
@property(nonatomic) unsigned int serialNum;
@property(nonatomic) unsigned int productID;
@property(nonatomic) unsigned int vendorID;
@property(copy, nonatomic, nullable) void (^terminationHandler)(id, CGVirtualDisplay *);
@end

@interface CGVirtualDisplayMode : NSObject
@property(readonly, nonatomic) double refreshRate;
@property(readonly, nonatomic) unsigned int height;
@property(readonly, nonatomic) unsigned int width;
- (instancetype)initWithWidth:(unsigned int)width height:(unsigned int)height refreshRate:(double)refreshRate;
@end

@interface CGVirtualDisplaySettings : NSObject
@property(retain, nonatomic) NSArray<CGVirtualDisplayMode *> *modes;
@property(nonatomic) unsigned int hiDPI;
@end

@interface CGVirtualDisplay : NSObject
@property(readonly, nonatomic) CGDirectDisplayID displayID;
@property(readonly, nonatomic) unsigned int hiDPI;
@property(readonly, nonatomic) NSArray *modes;
- (instancetype)initWithDescriptor:(CGVirtualDisplayDescriptor *)descriptor;
- (BOOL)applySettings:(CGVirtualDisplaySettings *)settings;
@end

NS_ASSUME_NONNULL_END

NS_ASSUME_NONNULL_BEGIN
/// 非公開クラスは直接参照せず（参照すると、消えた macOS で起動すらできなくなる）、実行時に探して作る。
/// どれか1つでも無ければ nil / NO を返す。
BOOL CVDIsAvailable(void);
CGVirtualDisplayDescriptor *_Nullable CVDMakeDescriptor(void);
CGVirtualDisplay *_Nullable CVDMakeDisplay(CGVirtualDisplayDescriptor *descriptor);
CGVirtualDisplaySettings *_Nullable CVDMakeSettings(void);
CGVirtualDisplayMode *_Nullable CVDMakeMode(unsigned int width, unsigned int height, double refreshRate);
NS_ASSUME_NONNULL_END
