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
            url: "https://github.com/kmpbits/Composure/releases/download/v0.3.0/composureIos.xcframework.zip",
            checksum: "c90f3d39bbadc8d20b9f78b3872c663575c0e770f50e3b04dc605f551f4d5c34"
        ),
    ]
)
