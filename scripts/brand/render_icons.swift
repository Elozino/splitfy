// Renders splitfy's app icons and launch logo as PNGs.
//
//   swift scripts/brand/render_icons.swift
//
// Run from the repository root. The mark is the same three shapes the app draws
// with Icons.Default.Category (a triangle, a square and a circle on a 24-unit grid),
// so the icon matches the in-app logo. Android's adaptive icon is vector XML and
// doesn't come from here; only its pre-Android 8 PNG fallbacks do.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let brand = CGColor(srgbRed: 0xFE / 255.0, green: 0xBB / 255.0, blue: 0x1B / 255.0, alpha: 1)
let ink = CGColor(srgbRed: 0x21 / 255.0, green: 0x21 / 255.0, blue: 0x21 / 255.0, alpha: 1)
let white = CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1)

enum Shape { case square, roundedSquare(Double), circle, none }

/// Draws the mark centred in a [size]-pixel canvas, [markFraction] of its width.
func render(size: Int, background: CGColor?, shape: Shape, mark: CGColor, markFraction: Double,
            opaque: Bool, to path: String) {
    let space = CGColorSpace(name: CGColorSpace.sRGB)!
    let info = opaque ? CGImageAlphaInfo.noneSkipLast.rawValue : CGImageAlphaInfo.premultipliedLast.rawValue
    let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                        space: space, bitmapInfo: info)!
    let s = Double(size)
    // Top-left origin, like the 24-unit icon grid.
    ctx.translateBy(x: 0, y: s)
    ctx.scaleBy(x: 1, y: -1)

    if let background {
        ctx.setFillColor(background)
        let rect = CGRect(x: 0, y: 0, width: s, height: s)
        switch shape {
        case .square: ctx.fill(rect)
        case .roundedSquare(let r):
            ctx.addPath(CGPath(roundedRect: rect, cornerWidth: s * r, cornerHeight: s * r, transform: nil))
            ctx.fillPath()
        case .circle: ctx.fillEllipse(in: rect)
        case .none: break
        }
    }

    // The mark spans x 3–22 and y 2–22 on the 24 grid; centre that box.
    let unit = s * markFraction / 20.0
    ctx.translateBy(x: s / 2 - 12.5 * unit, y: s / 2 - 12 * unit)
    ctx.scaleBy(x: unit, y: unit)
    ctx.setFillColor(mark)
    ctx.move(to: CGPoint(x: 12, y: 2))
    ctx.addLine(to: CGPoint(x: 17.5, y: 11))
    ctx.addLine(to: CGPoint(x: 6.5, y: 11))
    ctx.closePath()
    ctx.fillPath()
    ctx.fillEllipse(in: CGRect(x: 13, y: 13, width: 9, height: 9))
    ctx.fill(CGRect(x: 3, y: 13.5, width: 8, height: 8))

    let url = URL(fileURLWithPath: path)
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
    precondition(CGImageDestinationFinalize(dest), "couldn't write \(path)")
    print("wrote \(path)")
}

let ios = "app/iosApp/iosApp/Assets.xcassets"

// iOS app icon: full-bleed square (iOS applies the mask), no alpha channel, as App
// Store Connect requires. Dark and tinted variants sit on a transparent background,
// which iOS fills itself.
render(size: 1024, background: brand, shape: .square, mark: ink, markFraction: 0.56, opaque: true,
       to: "\(ios)/AppIcon.appiconset/app-icon-1024.png")
render(size: 1024, background: nil, shape: .none, mark: brand, markFraction: 0.56, opaque: false,
       to: "\(ios)/AppIcon.appiconset/app-icon-dark-1024.png")
render(size: 1024, background: nil, shape: .none, mark: white, markFraction: 0.56, opaque: false,
       to: "\(ios)/AppIcon.appiconset/app-icon-tinted-1024.png")

// iOS launch screen logo: the rounded yellow tile from the onboarding header, 112pt.
for scale in 1...3 {
    let suffix = scale == 1 ? "" : "@\(scale)x"
    render(size: 112 * scale, background: brand, shape: .roundedSquare(0.28), mark: ink, markFraction: 0.6,
           opaque: false, to: "\(ios)/LaunchLogo.imageset/launch-logo\(suffix).png")
}

// Android 7 and earlier (no adaptive icons): legacy square and round launcher PNGs.
let res = "app/androidApp/src/main/res"
for (density, px) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    render(size: px, background: brand, shape: .roundedSquare(0.22), mark: ink, markFraction: 0.58,
           opaque: false, to: "\(res)/mipmap-\(density)/ic_launcher.png")
    render(size: px, background: brand, shape: .circle, mark: ink, markFraction: 0.54,
           opaque: false, to: "\(res)/mipmap-\(density)/ic_launcher_round.png")
}
