// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "FoldDisplay",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "CVirtualDisplay", path: "Sources/CVirtualDisplay"),
        .executableTarget(
            name: "FoldDisplay",
            dependencies: ["CVirtualDisplay"],
            path: "Sources/FoldDisplay",
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
