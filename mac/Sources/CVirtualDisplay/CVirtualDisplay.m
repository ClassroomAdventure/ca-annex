#import "CGVirtualDisplay.h"

static Class cls(NSString *name) { return NSClassFromString(name); }

BOOL CVDIsAvailable(void) {
    Class d = cls(@"CGVirtualDisplay"), desc = cls(@"CGVirtualDisplayDescriptor");
    Class s = cls(@"CGVirtualDisplaySettings"), m = cls(@"CGVirtualDisplayMode");
    if (!d || !desc || !s || !m) return NO;
    return [d instancesRespondToSelector:@selector(initWithDescriptor:)]
        && [d instancesRespondToSelector:@selector(applySettings:)]
        && [d instancesRespondToSelector:@selector(displayID)]
        && [m instancesRespondToSelector:@selector(initWithWidth:height:refreshRate:)];
}

CGVirtualDisplayDescriptor *CVDMakeDescriptor(void) {
    return CVDIsAvailable() ? [cls(@"CGVirtualDisplayDescriptor") new] : nil;
}

CGVirtualDisplay *CVDMakeDisplay(CGVirtualDisplayDescriptor *descriptor) {
    return CVDIsAvailable() ? [(CGVirtualDisplay *)[cls(@"CGVirtualDisplay") alloc] initWithDescriptor:descriptor] : nil;
}

CGVirtualDisplaySettings *CVDMakeSettings(void) {
    return CVDIsAvailable() ? [cls(@"CGVirtualDisplaySettings") new] : nil;
}

CGVirtualDisplayMode *CVDMakeMode(unsigned int width, unsigned int height, double refreshRate) {
    return CVDIsAvailable() ? [(CGVirtualDisplayMode *)[cls(@"CGVirtualDisplayMode") alloc] initWithWidth:width height:height refreshRate:refreshRate] : nil;
}
