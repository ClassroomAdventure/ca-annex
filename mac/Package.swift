// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "Annex",
    platforms: [.macOS(.v14)],
    targets: [
        .target(name: "CVirtualDisplay", path: "Sources/CVirtualDisplay"),
        .executableTarget(
            name: "Annex",
            dependencies: ["CVirtualDisplay"],
            path: "Sources/Annex",
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
