// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "SideScreen",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "CVirtualDisplay", path: "Sources/CVirtualDisplay"),
        .executableTarget(
            name: "SideScreen",
            dependencies: ["CVirtualDisplay"],
            path: "Sources/SideScreen",
            linkerSettings: [
                .linkedFramework("CoreGraphics"),
                .linkedFramework("ScreenCaptureKit"),
                .linkedFramework("VideoToolbox"),
                .linkedFramework("CoreMedia"),
                .linkedFramework("Network"),
            ]
        ),
    ]
)
