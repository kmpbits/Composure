// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "Composure",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "Composure", targets: ["ComposureIos"]),
    ],
    targets: [
        .binaryTarget(
            name: "ComposureIos",
            url: "https://github.com/kmpbits/Composure/releases/download/v0.2.0/composureIos.xcframework.zip",
            checksum: "6cfcbb2625f716233242ee603bbfb632883c12d0f93a1d61bf6826b258b34e7d"
        ),
    ]
)
